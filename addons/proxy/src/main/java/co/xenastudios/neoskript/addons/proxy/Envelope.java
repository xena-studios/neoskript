/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A network message as it travels inside BungeeCord's {@code Forward}: the channel, the message, who sent it, to
 * which server and when, signed with the network's secret. A forged one (unsigned, or signed with another secret),
 * one replayed, one sent to another server, or one older or newer than a minute isn't let through, so whoever can
 * send plugin messages on the BungeeCord channel can't make scripts believe another server said something.
 *
 * <p>Without a secret, nothing is let through and nothing is sent, unless the secret is {@value #INSECURE}, which
 * turns signing off on purpose.
 *
 * <pre>
 * byte    version (2)
 * long    sent, milliseconds since 1970
 * long    nonce, random
 * UTF     the sender's server name, or empty
 * UTF     the sender's instance, random each start, so a server ignores its own
 * UTF     the server it's for, or ALL
 * UTF     channel
 * int     message length, then the message in UTF-8
 * byte    signature length (0 or 32), then HMAC-SHA256 of everything before it
 * </pre>
 */
final class Envelope {

    /** The subchannel {@code Forward} carries network messages on. */
    static final String SUBCHANNEL = "NeoSkriptNetwork";
    /** The most a message may be: BungeeCord's {@code Forward} carries at most 32,767 bytes, with what's around it. */
    static final int MAX_MESSAGE_BYTES = 30_000;
    private static final byte VERSION = 2;
    /** The secret that turns signing off: messages are sent and taken unsigned. */
    static final String INSECURE = "insecure";
    private static final long WINDOW_MILLIS = 60_000;
    private static final int REMEMBERED = 10_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * A network message, as it arrived.
     *
     * @param sent when it was sent
     * @param origin the sender's server name, or empty
     * @param instance the sender's instance
     * @param channel its channel
     * @param message the message
     */
    record Opened(long sent, String origin, String instance, String target, String channel, String message) {}

    /** Why a message wasn't let through. */
    static final class Refused extends Exception {
        Refused(String reason) {
            super(reason, null, false, false);
        }
    }

    private final byte[] secret;
    private final boolean insecure;
    private final int maxMessageBytes;
    /** Nonces seen in the last minute, so a message can't be replayed. */
    private final Map<Long, Long> seen = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
            return size() > REMEMBERED;
        }
    };

    Envelope(String secret, int maxMessageBytes) {
        this.insecure = INSECURE.equals(secret);
        this.secret = secret == null || secret.isEmpty() || insecure ? null : secret.getBytes(StandardCharsets.UTF_8);
        this.maxMessageBytes = maxMessageBytes;
    }

    boolean signed() {
        return secret != null;
    }

    /** Whether messages can go at all: with a secret, or with signing turned off on purpose. */
    boolean usable() {
        return secret != null || insecure;
    }

    /**
     * Seals a message.
     *
     * @throws IllegalArgumentException if it's longer than {@code max-message-bytes}
     */
    byte[] seal(String origin, String instance, String target, String channel, String message) {
        byte[] text = message.getBytes(StandardCharsets.UTF_8);
        if (text.length > maxMessageBytes) {
            throw new IllegalArgumentException("The message is " + text.length + " bytes, longer than "
                    + maxMessageBytes + " (max-message-bytes in plugins/NeoSkriptProxy/config.yml)");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(text.length + 128);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            out.writeLong(System.currentTimeMillis());
            out.writeLong(RANDOM.nextLong());
            out.writeUTF(origin);
            out.writeUTF(instance);
            out.writeUTF(target);
            out.writeUTF(channel);
            out.writeInt(text.length);
            out.write(text);
            byte[] signature = sign(bytes.toByteArray());
            out.writeByte(signature.length);
            out.write(signature);
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * Opens a message, if it's let through.
     *
     * @throws Refused if it isn't: why
     */
    Opened open(byte[] data) throws Refused {
        if (!usable()) {
            throw new Refused("no secret is set in config.yml, so network messages over plugin messaging are refused");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            if (in.readByte() != VERSION) {
                throw new Refused("it's of another version");
            }
            long sent = in.readLong();
            long nonce = in.readLong();
            String origin = in.readUTF();
            String instance = in.readUTF();
            String target = in.readUTF();
            String channel = in.readUTF();
            int length = in.readInt();
            if (length < 0 || length > maxMessageBytes || length > in.available()) {
                throw new Refused("it's " + length + " bytes long");
            }
            byte[] text = in.readNBytes(length);
            int signed = data.length - in.available();
            int signatureLength = in.readUnsignedByte();
            byte[] signature = in.readNBytes(signatureLength);
            if (in.available() != 0) {
                throw new Refused("it has bytes after its signature");
            }
            if (secret != null) {
                byte[] expected = sign(java.util.Arrays.copyOf(data, signed));
                if (!MessageDigest.isEqual(expected, signature)) {
                    throw new Refused(signatureLength == 0 ? "it isn't signed" : "its signature is wrong");
                }
                long now = System.currentTimeMillis();
                if (Math.abs(now - sent) > WINDOW_MILLIS) {
                    throw new Refused("it was sent " + (now - sent) / 1000 + " seconds ago");
                }
                synchronized (seen) {
                    if (seen.putIfAbsent(nonce, sent) != null) {
                        throw new Refused("it was seen already");
                    }
                }
            }
            return new Opened(sent, origin, instance, target, channel, new String(text, StandardCharsets.UTF_8));
        } catch (IOException cutShort) {
            throw new Refused("it's cut short or garbled");
        }
    }

    private byte[] sign(byte[] data) {
        if (secret == null) {
            return new byte[0];
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException missing) {
            throw new IllegalStateException("HMAC-SHA256 isn't available", missing);
        }
    }
}
