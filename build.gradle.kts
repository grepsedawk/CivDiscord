plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
}

allprojects {
    group = "io.github.grepsedawk.civdiscord"
    version = project.findProperty("version")?.toString()?.takeIf { it.isNotBlank() && it != "unspecified" } ?: "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // JUnit 5.12+ ships platform 1.12 modules; Gradle 8.14's bundled launcher is older
    // and fails discovery with "OutputDirectoryProvider not available". Force the
    // matching launcher onto the test runtime classpath. Safe to remove once Gradle
    // bundles a compatible launcher.
    dependencies {
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }
}
