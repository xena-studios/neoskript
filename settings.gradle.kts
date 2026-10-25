pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
            // Apache's parent POMs come from Maven Central only: the proxy here lacks some (org.apache:apache:31,
            // which WorldGuard's log4j BOM needs), and a miss here fails the whole resolution.
            content { excludeGroupByRegex("org\\.apache(\\..*)?") }
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
