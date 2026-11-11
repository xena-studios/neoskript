/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.bot;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.network.Connection;
import net.minecraft.network.UnconfiguredPipelineHandler;
import net.minecraft.network.protocol.Packet;

/**
 * The server end of a bot's in-memory channel, where a network connection has its codecs. Packets
 * travel as objects, so there's nothing to encode, but it does the two other things the codecs do:
 * it gives the connection a socket address, as the login events expect one, and after a packet that
 * ends a protocol phase it holds back further packets until the server has set up the next phase.
 */
final class ServerTransport extends ChannelDuplexHandler {

    private final Connection connection;
    private final InetSocketAddress address;
    private final Queue<Object> held = new ArrayDeque<>();
    private boolean paused;

    ServerTransport(Connection connection, InetSocketAddress address) {
        this.connection = connection;
        this.address = address;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // The connection takes the channel's address here, which for an in-memory channel isn't a socket.
        ctx.fireChannelActive();
        connection.address = address;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (paused) {
            held.add(msg);
        } else {
            forward(ctx, msg);
        }
    }

    private void forward(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof Packet<?> packet && packet.isTerminal()) {
            paused = true;
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof Packet<?>) {
            ctx.write(msg, promise);
            return;
        }
        // A protocol switch, which would swap the codecs. Once the inbound side has switched, the
        // held packets can go on to the new listener.
        ReferenceCountUtil.release(msg);
        promise.trySuccess();
        if (msg instanceof UnconfiguredPipelineHandler.InboundConfigurationTask) {
            ctx.executor().execute(() -> resume(ctx));
        }
    }

    private void resume(ChannelHandlerContext ctx) {
        paused = false;
        Object msg;
        while (!paused && (msg = held.poll()) != null) {
            forward(ctx, msg);
        }
    }
}
