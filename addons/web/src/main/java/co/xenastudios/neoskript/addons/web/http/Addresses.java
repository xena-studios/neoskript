/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web.http;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/** Which addresses are this machine's or a private network's, for {@code block-private-addresses}. */
public final class Addresses {

    private Addresses() {}

    /**
     * Whether a request to {@code address} stays inside this machine or a private network: loopback, unspecified,
     * private (RFC 1918), carrier-grade NAT, link-local (cloud metadata lives at 169.254.169.254), multicast, IPv6
     * unique-local, and private IPv4 addresses inside IPv6 ones: written as IPv6, through NAT64 (64:ff9b::/96, and all
     * of the local 64:ff9b:1::/48) and 6to4 (2002::/16).
     *
     * @param address the address
     * @return whether it's private
     */
    public static boolean isPrivate(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            // 0.0.0.0/8, 100.64.0.0/10 (carrier-grade NAT), 192.0.0.0/24, 198.18.0.0/15 (benchmarking), 240/4.
            return first == 0 || (first == 100 && second >= 64 && second <= 127)
                    || (first == 192 && second == 0 && (bytes[2] & 0xff) == 0)
                    || (first == 198 && (second == 18 || second == 19)) || first >= 240;
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            // fc00::/7, unique local addresses.
            if ((first & 0xfe) == 0xfc) {
                return true;
            }
            // ::ffff:a.b.c.d and ::a.b.c.d, IPv4 written as IPv6.
            boolean zeros = true;
            for (int i = 0; i < 10; i++) {
                zeros &= bytes[i] == 0;
            }
            if (zeros && ((bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff) || (bytes[10] == 0 && bytes[11] == 0))) {
                return embedded(bytes, 12);
            }
            // 64:ff9b::/96, NAT64's well-known prefix, and 64:ff9b:1::/48, its local one: the IPv4 address is in the
            // last four bytes.
            if (bytes[0] == 0 && bytes[1] == 0x64 && bytes[2] == (byte) 0xff && bytes[3] == (byte) 0x9b
                    && (allZero(bytes, 4, 12) || (bytes[4] == 0 && bytes[5] == 1))) {
                return bytes[5] == 1 || embedded(bytes, 12);
            }
            // 2002::/16, 6to4: the IPv4 address is the next four bytes.
            if (bytes[0] == 0x20 && bytes[1] == 0x02) {
                return embedded(bytes, 2);
            }
        }
        return false;
    }

    private static boolean allZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /** Whether the IPv4 address at {@code at} in an IPv6 address is private. */
    private static boolean embedded(byte[] bytes, int at) {
        try {
            return isPrivate(InetAddress.getByAddress(new byte[] {bytes[at], bytes[at + 1], bytes[at + 2], bytes[at + 3]}));
        } catch (java.net.UnknownHostException impossible) {
            return true;
        }
    }
}
