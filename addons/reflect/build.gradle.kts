// NeoSkriptReflect: Java from scripts, with skript-reflect's syntax, its calls compiled to method handles.
// See addons/README.md.
plugins {
    id("neoskript.addon-conventions")
}

description = "Java from scripts: imports, method calls, fields, constructors and Java events, with skript-reflect's syntax."

neoskriptAddon {
    pluginName = "NeoSkriptReflect"
    mainClass = "co.xenastudios.neoskript.addons.reflect.ReflectAddon"
    // skript-reflect, and the name it had before: skript-mirror.
    replaces = listOf("skript-reflect", "skript-mirror")
}

dependencies {
    // Scripts compiled through the real standard library in the tests.
    testImplementation(project(":neoskript-compiler"))
    testImplementation(project(":neoskript-paper"))
    testImplementation(libs.paper.api)
}
