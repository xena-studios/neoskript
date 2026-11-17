// NeoSkriptGui: inventory menus from scripts, with skript-gui's syntax. See addons/README.md.
plugins {
    id("neoskript.addon-conventions")
}

description = "Inventory menus from scripts, with skript-gui's syntax."

neoskriptAddon {
    pluginName = "NeoSkriptGui"
    mainClass = "co.xenastudios.neoskript.addons.gui.GuiAddon"
    replaces = listOf("skript-gui")
}
