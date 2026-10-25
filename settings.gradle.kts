pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
        // Plugins NeoSkript hooks into, as Skript does; compile-only, never shaded.
        exclusiveContent {
            forRepository { maven("https://maven.enginehub.org/repo/") { name = "enginehub" } }
            filter {
                includeGroup("com.sk89q.worldguard")
                includeGroup("com.sk89q.worldedit")
                includeGroupByRegex("org\\.enginehub.*")
            }
        }
        exclusiveContent {
            forRepository { maven("https://jitpack.io") { name = "jitpack" } }
            filter {
                includeGroup("com.github.MilkBowl")
                includeGroup("com.github.TechFortress")
            }
        }
    }
}

rootProject.name = "neoskript"

include(
    "neoskript-api",
    "neoskript-frontend",
    "neoskript-compiler",
    "neoskript-runtime",
    "neoskript-paper",
    "neoskript-plugin",
    "neoskript-cli",
    "test-harness",
)
