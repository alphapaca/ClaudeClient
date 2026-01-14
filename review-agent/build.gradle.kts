plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

group = "com.github.alphapaca"
version = "1.0.0"

kotlin {
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
                }
            }
        }
    }

    sourceSets {
        jvmMain.dependencies {
            // Reuse embedding-indexer for VoyageAIService, VectorStore, KotlinChunker
            implementation(project(":embedding-indexer"))

            // Kotlinx Serialization
            implementation(libs.kotlinx.serialization.json)

            // Ktor client for Claude and GitHub APIs
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Logging
            implementation(libs.logback.classic)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// Fat JAR configuration
tasks.named<Jar>("jvmJar") {
    manifest {
        attributes["Main-Class"] = "com.github.alphapaca.reviewagent.MainKt"
    }

    val jvmRuntimeClasspath = configurations["jvmRuntimeClasspath"]
    from(jvmRuntimeClasspath.map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    archiveBaseName.set("review-agent")
    archiveClassifier.set("all")
}
