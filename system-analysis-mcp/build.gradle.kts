plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlinxSerialization)
}

group = "com.github.alphapaca"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

dependencies {
    // MCP SDK
    implementation(libs.mcp.kotlin.sdk)

    // Ktor client
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Logging
    implementation(libs.logback.classic)

    // Testing
    testImplementation(libs.kotlin.test)
}

// Fat JAR configuration
tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.github.alphapaca.systemanalysis.MainKt"
    }

    // Include all dependencies
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    archiveBaseName.set("system-analysis-mcp")
    archiveClassifier.set("all")
}
