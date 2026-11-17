/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.bot;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.CompressionDecoder;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Varint21FrameDecoder;
import net.minecraft.network.Varint21LengthFieldPrepender;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.handshake.HandshakeProtocols;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.server.MinecraftServer;

/**
 * A bot's connection over a real socket, as a client's, so it can join through a proxy: packets are framed,
 * compressed once the other side says so, and encoded with each phase's codecs, swapped as the server's own code
 * swaps them. The play phase's registries are this server's, the same vanilla ones a backend sends.
 */
final class NetworkWire implements Bot.Wire {

    private final Channel channel;

    private NetworkWire(Channel channel) {
        this.channel = channel;
    }

    /** Sets up a new channel's pipeline, ending in the bot, which is then given this wire. */
    static NetworkWire install(Channel channel, java.util.function.Function<NetworkWire, Bot> bot) {
        NetworkWire wire = new NetworkWire(channel);
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast("splitter", new Varint21FrameDecoder(null));
        pipeline.addLast("decoder", new PacketDecoder<>(LoginProtocols.CLIENTBOUND));
        pipeline.addLast("prepender", new Varint21LengthFieldPrepender());
        pipeline.addLast("encoder", new PacketEncoder<>(HandshakeProtocols.SERVERBOUND));
        pipeline.addLast("bot", bot.apply(wire));
        return wire;
    }

    @Override
    public void inbound(Bot.Phase phase) {
        ProtocolInfo<?> info = switch (phase) {
            case LOGIN -> LoginProtocols.CLIENTBOUND;
            case CONFIGURATION -> ConfigurationProtocols.CLIENTBOUND;
            case PLAY -> GameProtocols.CLIENTBOUND_TEMPLATE.bind(
                    RegistryFriendlyByteBuf.decorator(MinecraftServer.getServer().registryAccess()));
        };
        ChannelPipeline pipeline = channel.pipeline();
        // After a phase's last packet, the decoder is a placeholder until the next phase's is in.
        String placeholder = pipeline.get("inbound_config") != null ? "inbound_config" : "decoder";
        pipeline.replace(placeholder, "decoder", new PacketDecoder<>(info));
        channel.config().setAutoRead(true);
    }

    @Override
    public void outbound(Bot.Phase phase) {
        ProtocolInfo<?> info = switch (phase) {
            case LOGIN -> LoginProtocols.SERVERBOUND;
            case CONFIGURATION -> ConfigurationProtocols.SERVERBOUND;
            case PLAY -> GameProtocols.SERVERBOUND_TEMPLATE.bind(
                    RegistryFriendlyByteBuf.decorator(MinecraftServer.getServer().registryAccess()),
                    new GameProtocols.Context() {
                        @Override
                        public boolean hasInfiniteMaterials() {
                            return false;
                        }

                        @Override
                        public boolean canUseCommandBlocks() {
                            return false;
                        }
                    });
        };
        ChannelPipeline pipeline = channel.pipeline();
        String placeholder = pipeline.get("outbound_config") != null ? "outbound_config" : "encoder";
        pipeline.replace(placeholder, "encoder", new PacketEncoder<>(info));
    }

    @Override
    public void compress(int threshold) {
        ChannelPipeline pipeline = channel.pipeline();
        if (threshold < 0 || pipeline.get("decompress") != null) {
            return;
        }
        pipeline.addAfter("splitter", "decompress", new CompressionDecoder(threshold, false));
        pipeline.addAfter("prepender", "compress", new CompressionEncoder(threshold));
    }
}
