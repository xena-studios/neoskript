/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import org.skriptlang.skript.localization.Localizer;

/** Where an addon's language files are. NeoSkriptCompat doesn't read them yet: nothing translates. */
final class CompatLocalizer implements Localizer {

    private volatile String languageFileDirectory;
    private volatile String dataFileDirectory;

    @Override
    public void setSourceDirectories(String languageFileDirectory, String dataFileDirectory) {
        this.languageFileDirectory = languageFileDirectory;
        this.dataFileDirectory = dataFileDirectory;
    }

    @Override
    public String languageFileDirectory() {
        return languageFileDirectory;
    }

    @Override
    public String dataFileDirectory() {
        return dataFileDirectory;
    }

    @Override
    public String translate(String key) {
        return null;
    }

    @Override
    public Localizer unmodifiableView() {
        return this;
    }
}
