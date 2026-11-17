import co.xenastudios.neoskript.gradle.ApiCheck
import co.xenastudios.neoskript.gradle.ApiDump

plugins {
    id("neoskript.java-conventions")
    alias(libs.plugins.maven.publish)
}

description = "NeoSkript's addon API: registering types and syntax, and reaching scripts, variables and threads."

tasks.javadoc {
    // Every public type and member of the API has its doc, checked here: the build fails on a missing one.
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("Werror", true)
        addStringOption("Xmaxwarns", "1000")
        windowTitle = "NeoSkript API ${project.version}"
        docTitle = "NeoSkript API ${project.version}"
        links("https://docs.oracle.com/en/java/javase/21/docs/api/")
    }
}

// The API's signatures, committed in api/neoskript-api.api. `check` (and so `build`) fails when they change, so a
// change to the addon API is always deliberate: run apiDump and commit the file.
val apiSignatures = layout.projectDirectory.file("api/neoskript-api.api")
val apiClasses = sourceSets.main.map { it.output.classesDirs }

tasks.register<ApiDump>("apiDump") {
    group = "verification"
    description = "Writes the addon API's signatures to api/neoskript-api.api."
    classes.from(apiClasses)
    signatures = apiSignatures
}

val apiCheck = tasks.register<ApiCheck>("apiCheck") {
    group = "verification"
    description = "Checks the addon API's signatures against api/neoskript-api.api."
    classes.from(apiClasses)
    signatures = apiSignatures
    marker = layout.buildDirectory.file("apiCheck/ok")
}

tasks.check {
    dependsOn(apiCheck)
    // The javadoc is checked in every build, not only when it's published.
    dependsOn(tasks.javadoc)
}

// The signature check reads class files with code in build-logic, which has tests of its own: they run with this
// module's checks.
tasks.check {
    dependsOn(gradle.includedBuild("build-logic").task(":test"))
}

// Published to Maven Central, through the Central Portal, as co.xenastudios.neoskript:neoskript-api with its sources
// and javadoc. The version is the plugin's: -Pversion=1.0.0. Signing and the Portal's credentials come from Gradle
// properties or ORG_GRADLE_PROJECT_* variables (signingInMemoryKey, signingInMemoryKeyPassword, mavenCentralUsername,
// mavenCentralPassword); without a key nothing is signed, so local builds and CI don't need them. To publish:
// ./gradlew :neoskript-api:publishToMavenCentral -Pversion=<version>, then release it in the Portal.
mavenPublishing {
    configure(
        com.vanniktech.maven.publish.JavaLibrary(
            javadocJar = com.vanniktech.maven.publish.JavadocJar.Javadoc(),
            sourcesJar = true,
        )
    )
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    coordinates("co.xenastudios.neoskript", "neoskript-api", project.version.toString())
    pom {
        name = "NeoSkript API"
        description = project.description
        inceptionYear = "2026"
        url = "https://neoskript.dev"
        licenses {
            license {
                name = "GPL-3.0-only"
                url = "https://www.gnu.org/licenses/gpl-3.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "xena-studios"
                name = "Xena Studios"
                url = "https://xenastudios.co"
            }
        }
        organization {
            name = "Xena Studios"
            url = "https://xenastudios.co"
        }
        scm {
            url = "https://github.com/xena-studios/neoskript"
            connection = "scm:git:https://github.com/xena-studios/neoskript.git"
            developerConnection = "scm:git:ssh://git@github.com/xena-studios/neoskript.git"
        }
        issueManagement {
            system = "GitHub"
            url = "https://github.com/xena-studios/neoskript/issues"
        }
    }
}
