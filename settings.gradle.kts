rootProject.name = "NeoSkript"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

include(
    "neoskript-api",
    "neoskript-core",
    "neoskript-lang",
    "neoskript-platform",
    "neoskript-plugin",
    "neoskript-testkit",
)
