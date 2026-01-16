plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlinxSerialization)
    application
}

group = "com.github.alphapaca"
version = "1.0.0"

java {
    toolchain {
        // Koog requires JDK 17+
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass.set("com.github.alphapaca.webagent.MainKt")
}

// Koog version
val koogVersion = "0.6.0"

dependencies {
    // Use classes configuration to avoid fat JAR issues
    implementation(project(":embedding-indexer")) {
        // Exclude transitive dependencies to avoid conflicts
        isTransitive = false
    }
    // Add the embedding-indexer's dependencies directly
    implementation(libs.sqlite.jdbc)

    // Koog AI agent framework
    implementation("ai.koog:koog-agents:$koogVersion")
    implementation("ai.koog:agents-mcp:$koogVersion")

    // Ktor server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.static)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Ktor client for VoyageAI and GitHub API
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Logging
    implementation(libs.logback.classic)

    // Testing
    testImplementation(libs.kotlin.test)
}

// Copy frontend assets from web-agent-frontend when building
tasks.register<Copy>("copyFrontend") {
    dependsOn(":web-agent-frontend:wasmJsBrowserDistribution")
    from("${project(":web-agent-frontend").projectDir}/build/dist/wasmJs/productionExecutable")
    into("$projectDir/src/main/resources/web")
}

tasks.processResources {
    // Optionally depend on copyFrontend if frontend is built
    // dependsOn("copyFrontend")
}

// Fat JAR configuration for deployment
tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.github.alphapaca.webagent.MainKt"
    }

    // Include all dependencies
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    archiveBaseName.set("web-agent-backend")
    archiveClassifier.set("all")
}
