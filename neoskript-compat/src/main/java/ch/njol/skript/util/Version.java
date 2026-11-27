/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.util;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A version, as Skript compares them: numbers, then a pre-release postfix sorts before none. */
public class Version implements Serializable, Comparable<Version> {

    public static final Pattern versionPattern = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-(.*))?");
    private final int[] version = new int[3];
    private final String postfix;

    public Version(int... version) {
        if (version.length < 1 || version.length > 3) {
            throw new IllegalArgumentException("Versions must have a minimum of 2 and a maximum of 3 numbers");
        }
        System.arraycopy(version, 0, this.version, 0, version.length);
        this.postfix = null;
    }

    public Version(int major, int minor, String postfix) {
        this.version[0] = major;
        this.version[1] = minor;
        this.postfix = postfix == null || postfix.isEmpty() ? null : postfix;
    }

    public Version(String version) {
        Matcher m = versionPattern.matcher(version.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("'" + version + "' is not a valid version string");
        }
        for (int i = 0; i < 3; i++) {
            if (m.group(i + 1) != null) {
                this.version[i] = Integer.parseInt(m.group(i + 1));
            }
        }
        this.postfix = m.group(4);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Version other && compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(version) * 31 + (postfix == null ? 0 : postfix.hashCode());
    }

    @Override
    public int compareTo(Version other) {
        if (other == null) {
            return 1;
        }
        for (int i = 0; i < 3; i++) {
            if (version[i] != other.version[i]) {
                return Integer.compare(version[i], other.version[i]);
            }
        }
        if (postfix == null) {
            return other.postfix == null ? 0 : 1;
        }
        return other.postfix == null ? -1 : postfix.compareTo(other.postfix);
    }

    public int compareTo(int... other) {
        for (int i = 0; i < version.length; i++) {
            int value = i < other.length ? other[i] : 0;
            if (version[i] != value) {
                return Integer.compare(version[i], value);
            }
        }
        return 0;
    }

    public boolean isSmallerThan(Version other) {
        return compareTo(other) < 0;
    }

    public boolean isLargerThan(Version other) {
        return compareTo(other) > 0;
    }

    public boolean isStable() {
        return postfix == null;
    }

    public int getMajor() {
        return version[0];
    }

    public int getMinor() {
        return version[1];
    }

    public int getRevision() {
        return version[2];
    }

    @Override
    public String toString() {
        return version[0] + "." + version[1] + (version[2] == 0 ? "" : "." + version[2]) + (postfix == null ? "" : "-" + postfix);
    }

    public static int compare(String v1, String v2) {
        return new Version(v1).compareTo(new Version(v2));
    }
}
