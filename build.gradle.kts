// Plugins are declared here once so every module shares one classloader for them.
plugins {
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.run.paper) apply false
}
