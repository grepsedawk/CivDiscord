plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.snakeyaml)
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
    implementation(libs.jda) { exclude(module = "opus-java") }
    implementation(libs.mariadb.jdbc)
    implementation(libs.hikaricp)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.mockk)
    testImplementation(libs.h2)
    testImplementation(libs.velocity.api)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("CivDiscord-Velocity")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.processResources {
    filesMatching("velocity-plugin.json") {
        expand("version" to project.version)
    }
}
