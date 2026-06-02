plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.snakeyaml)
    compileOnly(libs.paper.api)
    compileOnly(
        files(
            "../libs/civmodcore-paper.jar",
            "../libs/jukealert-paper.jar",
            "../libs/namelayer-paper.jar",
            "../libs/acf-paper.jar",
            "../libs/civchat2.jar",
        ),
    )

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.mockk)
    testImplementation(libs.mockbukkit)
    testImplementation(
        files(
            "../libs/civmodcore-paper.jar",
            "../libs/jukealert-paper.jar",
            "../libs/namelayer-paper.jar",
            "../libs/civchat2.jar",
        ),
    )
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("CivDiscord-Paper")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
