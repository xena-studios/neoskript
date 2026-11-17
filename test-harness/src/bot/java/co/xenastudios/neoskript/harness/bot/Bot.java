/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.bot;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.configuration.ClientboundCodeOfConductPacket;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.network.protocol.configuration.ServerboundAcceptCodeOfConductPacket;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ServerboundSelectKnownPacks;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;

/**
 * One bot: the client end of an in-memory connection to the server. It logs in through the server's
 * own handshake, login and configuration listeners, answers what a client has to (keep-alives,
 * teleports, chunk batches, known packs), and logs every message it's shown as
 * {@code [bot:<name>] <plain text>}. Everything but the constructor runs on the channel's event loop.
 */
final class Bot extends SimpleChannelInboundHandler<Packet<?>> {

    private enum State {
        LOGIN,
        CONFIGURATION,
        PLAY
    }

    private static final Logger LOG = Bukkit.getLogger();

    /**
     * Where the bot's packets go: as objects in memory (the server's own connection), or encoded over a socket
     * ({@link NetworkWire}), whose codecs change with each phase of the protocol.
     */
    interface Wire {
        /** The packets read are of this phase from now on. */
        default void inbound(Phase phase) {}

        /** The packets written are of this phase from now on. */
        default void outbound(Phase phase) {}

        /** Packets are compressed from now on, past {@code threshold} bytes. */
        default void compress(int threshold) {}
    }

    /** A phase of the protocol. */
    enum Phase {
        LOGIN,
        CONFIGURATION,
        PLAY
    }

    private final String name;
    private final int port;
    private final String host;
    private final Wire wire;
    private final Consumer<Bot> onClose;
    private volatile Channel channel;
    private State state = State.LOGIN;
    /** Packets for the game phase, sent once the player is in the world. */
    private final Queue<Packet<?>> waitingForPlay = new ArrayDeque<>();

    private PositionMoveRotation position = new PositionMoveRotation(Vec3.ZERO, Vec3.ZERO, 0, 0);
    private boolean loaded;
    private int sequence;
    /** The screen open, 0 for the player's own inventory, and the server's state id for it. */
    private volatile int containerId;
    private volatile int stateId;

    Bot(String name, int port, Consumer<Bot> onClose) {
        this(name, "localhost", port, new Wire() {}, onClose);
    }

    /** A bot for {@code host}, which a proxy's forced hosts may send to a server of its own. */
    Bot(String name, String host, int port, Wire wire, Consumer<Bot> onClose) {
        super(false);
        this.name = name;
        this.host = host;
        this.port = port;
        this.wire = wire;
        this.onClose = onClose;
    }

    String name() {
        return name;
    }

    /** Called with the channel as soon as it's created, so actions can queue before it connects. */
    void attach(Channel channel) {
        this.channel = channel;
    }

    /** Offline-mode UUID, as the server derives it from the name. */
    static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    @SuppressWarnings("deprecation") // Paper deprecates building the handshake packet, which only clients do.
    public void channelActive(ChannelHandlerContext ctx) {
        // The channel can be active before Bots attaches it.
        channel = ctx.channel();
        send(new ClientIntentionPacket(SharedConstants.getProtocolVersion(), host, port, ClientIntent.LOGIN));
        wire.outbound(Phase.LOGIN);
        send(new ServerboundHelloPacket(name, offlineUuid(name)));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        LOG.info("[bot] " + name + " disconnected");
        onClose.accept(this);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.log(Level.WARNING, "[bot] " + name + " failed", cause);
        ctx.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet<?> packet) {
        handle(packet);
    }

    private void handle(Packet<?> packet) {
        switch (packet) {
            // Login
            case net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket compression ->
                    wire.compress(compression.getCompressionThreshold());
            case ClientboundLoginFinishedPacket ignored -> {
                wire.inbound(Phase.CONFIGURATION);
                send(ServerboundLoginAcknowledgedPacket.INSTANCE);
                wire.outbound(Phase.CONFIGURATION);
                state = State.CONFIGURATION;
                send(new ServerboundClientInformationPacket(ClientInformation.createDefault()));
            }
            case ClientboundLoginDisconnectPacket disconnect -> disconnected(disconnect.reason());
            // Configuration
            case ClientboundSelectKnownPacks packs -> send(new ServerboundSelectKnownPacks(packs.knownPacks()));
            case ClientboundCodeOfConductPacket ignored -> send(new ServerboundAcceptCodeOfConductPacket());
            case ClientboundFinishConfigurationPacket ignored -> {
                wire.inbound(Phase.PLAY);
                send(ServerboundFinishConfigurationPacket.INSTANCE);
                wire.outbound(Phase.PLAY);
            }
            case ClientboundLoginPacket ignored -> {
                state = State.PLAY;
                Packet<?> waiting;
                while ((waiting = waitingForPlay.poll()) != null) {
                    send(waiting);
                }
            }
            case ClientboundStartConfigurationPacket ignored -> {
                state = State.CONFIGURATION;
                wire.inbound(Phase.CONFIGURATION);
                send(ServerboundConfigurationAcknowledgedPacket.INSTANCE);
                wire.outbound(Phase.CONFIGURATION);
            }
            // Any phase after login
            case ClientboundKeepAlivePacket keepAlive -> send(new ServerboundKeepAlivePacket(keepAlive.getId()));
            case ClientboundPingPacket ping -> send(new ServerboundPongPacket(ping.getId()));
            case ClientboundDisconnectPacket disconnect -> disconnected(disconnect.reason());
            case ClientboundResourcePackPushPacket pack -> {
                send(new ServerboundResourcePackPacket(pack.id(), ServerboundResourcePackPacket.Action.ACCEPTED));
                send(new ServerboundResourcePackPacket(pack.id(), ServerboundResourcePackPacket.Action.DOWNLOADED));
                send(new ServerboundResourcePackPacket(
                        pack.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            }
            // Game
            case ClientboundBundlePacket bundle -> bundle.subPackets().forEach(this::handle);
            case ClientboundPlayerPositionPacket teleport -> {
                position = PositionMoveRotation.calculateAbsolute(position, teleport.change(), teleport.relatives());
                Vec3 at = position.position();
                float yRot = position.yRot();
                float xRot = position.xRot();
                // A client confirms a tick or two later, once it has moved. Confirming within the same
                // tick makes the server think the player moved too quickly, from where it was before.
                channel.eventLoop().schedule(() -> {
                    send(new ServerboundAcceptTeleportationPacket(teleport.id(), at.x, at.y, at.z, yRot, xRot));
                    if (!loaded) {
                        // It says it has loaded the world once it knows where it is.
                        loaded = true;
                        send(new ServerboundPlayerLoadedPacket());
                    }
                }, 100, TimeUnit.MILLISECONDS);
            }
            case ClientboundChunkBatchFinishedPacket ignored -> send(new ServerboundChunkBatchReceivedPacket(64));
            // Screens: which is open, and its state id, which clicks send back
            case ClientboundOpenScreenPacket open -> {
                containerId = open.getContainerId();
                LOG.info("[bot:" + name + "] [screen] " + ChatFormatting.stripFormatting(open.getTitle().getString()));
            }
            case ClientboundContainerSetContentPacket content -> {
                if (content.containerId() == containerId) {
                    stateId = content.stateId();
                }
            }
            case ClientboundContainerSetSlotPacket slot -> {
                if (slot.getContainerId() == containerId) {
                    stateId = slot.getStateId();
                }
            }
            case ClientboundContainerClosePacket closed -> {
                containerId = 0;
                LOG.info("[bot:" + name + "] [screen closed]");
            }
            // What the player is shown
            case ClientboundSystemChatPacket chat -> show(chat.overlay() ? "[action bar] " : "", chat.content());
            case ClientboundPlayerChatPacket chat -> show("", chat.chatType().decorate(
                    chat.unsignedContent().orElseGet(() -> Component.literal(chat.body().content()))));
            case ClientboundDisguisedChatPacket chat -> show("", chat.chatType().decorate(chat.message()));
            case ClientboundSetActionBarTextPacket actionBar -> show("[action bar] ", actionBar.text());
            case ClientboundSetTitleTextPacket title -> show("[title] ", title.text());
            case ClientboundSetSubtitleTextPacket subtitle -> show("[subtitle] ", subtitle.text());
            default -> {}
        }
    }

    private void disconnected(Component reason) {
        show("[disconnected] ", reason);
        channel.close();
    }

    /** Logs a message the player is shown, one console line per line of text, without formatting codes. */
    private void show(String kind, Component message) {
        String text = ChatFormatting.stripFormatting(message.getString());
        for (String line : text.split("\n", -1)) {
            LOG.info("[bot:" + name + "] " + kind + line);
        }
    }

    private void send(Packet<?> packet) {
        channel.writeAndFlush(packet, channel.voidPromise());
    }

    /** Sends a game packet now if the player is in the world, or as soon as it is. Any thread. */
    private void play(Packet<?> packet) {
        Channel channel = this.channel;
        channel.eventLoop().execute(() -> {
            if (state == State.PLAY) {
                send(packet);
            } else {
                waitingForPlay.add(packet);
            }
        });
    }

    void chat(String message) {
        // Unsigned, as from a client without a chat session, which offline-mode servers accept.
        play(new ServerboundChatPacket(
                message,
                Instant.now(),
                ThreadLocalRandom.current().nextLong(),
                Optional.empty(),
                new LastSeenMessages.Update(0, new BitSet(), LastSeenMessages.Update.IGNORE_CHECKSUM)));
    }

    void command(String command) {
        play(new ServerboundChatCommandPacket(command.startsWith("/") ? command.substring(1) : command));
    }

    /** Asks for completions of {@code text} ({@code /some command}), as pressing tab in chat does. */
    void tabComplete(String text) {
        play(new net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket(
                ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE), text));
    }

    void respawn() {
        play(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
    }

    void swapHands() {
        playerAction(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
    }

    void drop(boolean wholeStack) {
        playerAction(wholeStack
                ? ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
                : ServerboundPlayerActionPacket.Action.DROP_ITEM);
    }

    private void playerAction(ServerboundPlayerActionPacket.Action action) {
        play(new ServerboundPlayerActionPacket(action, BlockPos.ZERO, Direction.DOWN));
    }

    /** Right-clicks with the main hand, at the air in front of the player. */
    void use() {
        Channel channel = this.channel;
        channel.eventLoop().execute(() -> play(new ServerboundUseItemPacket(
                InteractionHand.MAIN_HAND, ++sequence, position.yRot(), position.xRot())));
    }

    /** Swings the main hand, as left-clicking the air does. */
    void punch() {
        play(new ServerboundPunchPacket());
    }

    void selectSlot(int slot) {
        play(new ServerboundSetCarriedItemPacket(slot));
    }

    /**
     * Clicks a slot of the open screen (or of the player's own inventory if none is open), as a client does:
     * {@code left}, {@code right}, {@code shift}, {@code double}, {@code middle}, {@code drop}, {@code offhand} (the
     * swap-with-offhand key), or a number key {@code 1}-{@code 9}. The client's guess at the result is left out, so
     * the server works it out and sends back what really happened.
     */
    void click(int slot, String how) {
        net.minecraft.world.inventory.ContainerInput input;
        int button;
        switch (how) {
            case "left" -> { input = net.minecraft.world.inventory.ContainerInput.PICKUP; button = 0; }
            case "right" -> { input = net.minecraft.world.inventory.ContainerInput.PICKUP; button = 1; }
            case "shift" -> { input = net.minecraft.world.inventory.ContainerInput.QUICK_MOVE; button = 0; }
            case "double" -> { input = net.minecraft.world.inventory.ContainerInput.PICKUP_ALL; button = 0; }
            case "middle" -> { input = net.minecraft.world.inventory.ContainerInput.CLONE; button = 2; }
            case "drop" -> { input = net.minecraft.world.inventory.ContainerInput.THROW; button = 0; }
            case "offhand" -> { input = net.minecraft.world.inventory.ContainerInput.SWAP; button = 40; }
            default -> { input = net.minecraft.world.inventory.ContainerInput.SWAP; button = Integer.parseInt(how) - 1; }
        }
        containerClick(slot, button, input);
    }

    /** Drags the item on the cursor over {@code slots}, spreading it as a left-button drag does. */
    void drag(int... slots) {
        net.minecraft.world.inventory.ContainerInput craft = net.minecraft.world.inventory.ContainerInput.QUICK_CRAFT;
        containerClick(-999, 0, craft);
        for (int slot : slots) {
            containerClick(slot, 1, craft);
        }
        containerClick(-999, 2, craft);
    }

    private void containerClick(int slot, int button, net.minecraft.world.inventory.ContainerInput input) {
        Channel channel = this.channel;
        channel.eventLoop().execute(() -> play(new ServerboundContainerClickPacket(containerId, stateId, (short) slot,
                (byte) button, input, new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(),
                net.minecraft.network.HashedStack.EMPTY)));
    }

    /** Closes the open screen, as pressing escape does. */
    void closeScreen() {
        Channel channel = this.channel;
        channel.eventLoop().execute(() -> play(new ServerboundContainerClosePacket(containerId)));
    }

    void quit() {
        Channel channel = this.channel;
        if (channel != null) {
            channel.close();
        }
    }
}
