import co.xenastudios.neoskript.build.ApiCheck
import co.xenastudios.neoskript.build.ApiDump

plugins {
    id("neoskript.java-conventions")
    `maven-publish`
}

description = "NeoSkript's addon API: registering types and syntax, and reaching scripts, variables and threads."

java {
    withSourcesJar()
    withJavadocJar()
}

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
}

// Ready to publish, as co.xenastudios.neoskript:neoskript-api, with its sources and javadoc. Nothing publishes it
// yet: `./gradlew :neoskript-api:publishToMavenLocal` puts it in ~/.m2, and a repository given with
// -Pneoskript.publish.url (and .username, .password) is where `publish` sends it.
publishing {
    publications {
        create<MavenPublication>("api") {
            artifactId = "neoskript-api"
            from(components["java"])
            pom {
                name = "NeoSkript API"
                description = project.description
                url = "https://neoskript.dev/docs/addon-api/"
                licenses {
                    license {
                        name = "GPL-3.0-only"
                        url = "https://www.gnu.org/licenses/gpl-3.0.txt"
                    }
                }
                organization {
                    name = "Xena Studios"
                    url = "https://xenastudios.co"
                }
                developers {
                    developer {
                        name = "Xena Studios"
                        organization = "Xena Studios"
                    }
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
    }
    repositories {
        val url = providers.gradleProperty("neoskript.publish.url")
        if (url.isPresent) {
            maven {
                name = "neoskript"
                setUrl(url.get())
                credentials {
                    username = providers.gradleProperty("neoskript.publish.username").orNull
                    password = providers.gradleProperty("neoskript.publish.password").orNull
                }
            }
        }
    }
}
