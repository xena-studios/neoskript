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
     * unique-local, and IPv4 addresses written as IPv6.
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
                try {
                    byte[] v4 = {bytes[12], bytes[13], bytes[14], bytes[15]};
                    return isPrivate(InetAddress.getByAddress(v4));
                } catch (java.net.UnknownHostException impossible) {
                    return true;
                }
            }
        }
        return false;
    }
}
