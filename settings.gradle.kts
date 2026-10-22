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
