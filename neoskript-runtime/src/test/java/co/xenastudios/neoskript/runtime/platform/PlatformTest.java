/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlatformTest {

    @Test
    void detectsPaperWhenTheFoliaMarkerIsMissing() {
        assertEquals(Platform.PAPER, Platform.detect(Platform.FOLIA_MARKER_CLASS, getClass().getClassLoader()));
    }

    @Test
    void detectsFoliaWhenTheMarkerClassExists() {
        assertEquals(Platform.FOLIA, Platform.detect(getClass().getName(), getClass().getClassLoader()));
    }
}
