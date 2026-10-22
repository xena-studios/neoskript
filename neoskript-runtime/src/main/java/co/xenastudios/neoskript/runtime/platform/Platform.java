/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.platform;

/** The server software NeoSkript is running on. Decides which scheduler implementation is used. */
public enum Platform {
    PAPER("Paper"),
    FOLIA("Folia");

    /** A class that only exists on Folia's regionized server. */
    static final String FOLIA_MARKER_CLASS = "io.papermc.paper.threadedregions.RegionizedServer";

    private final String displayName;

    Platform(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Detects the platform of the running server. */
    public static Platform detect() {
        return detect(FOLIA_MARKER_CLASS, Platform.class.getClassLoader());
    }

    static Platform detect(String foliaMarkerClass, ClassLoader loader) {
        try {
            Class.forName(foliaMarkerClass, false, loader);
            return FOLIA;
        } catch (ClassNotFoundException e) {
            return PAPER;
        }
    }
}
