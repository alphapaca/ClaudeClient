import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.buildConfig)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    jvm()
    
    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // Ktor Client
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)

            // Kotlin Serialization
            implementation(libs.kotlinx.serialization.json)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // OkHttp
            implementation(libs.okhttp)

            // Koin
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // datastore
            implementation(libs.androidx.datastore.preferences)

            // navigation
            implementation(libs.androidx.navigation3.ui)

            // logs
            implementation(libs.kermit)

            // markdown
            implementation(libs.markdown.renderer.m3)

            // SQLDelight
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            // MCP SDK
            implementation(libs.mcp.kotlin.sdk)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.driver.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.sqldelight.driver.jvm)
            // For code session indexer
            implementation(libs.sqlite.jdbc)
            implementation(libs.ktor.client.cio)
        }
    }
}

buildConfig {
    val localProperties = Properties().apply {
        load(FileInputStream(rootProject.file("local.properties")))
    }
    buildConfigField("ANTHROPIC_API_KEY", localProperties["ANTHROPIC_API_KEY"] as String)
    buildConfigField("DEEPSEEK_API_KEY", localProperties["DEEPSEEK_API_KEY"] as String)
    buildConfigField("VOYAGEAI_API_KEY", localProperties["VOYAGEAI_API_KEY"] as String)
}

android {
    namespace = "com.github.alphapaca.claudeclient"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.github.alphapaca.claudeclient"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.github.alphapaca.claudeclient.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.github.alphapaca.claudeclient"
            packageVersion = "1.0.0"
        }
    }
}

sqldelight {
    databases {
        create("ClaudeClientDatabase") {
            packageName.set("com.github.alphapaca.claudeclient.data.db")
        }
    }
}

// Make run task depend on mcp-server jars being built first
afterEvaluate {
    tasks.findByName("run")?.dependsOn(":mcp-server:jar", ":recipe-book-mcp:jar", ":mcp-foundation-search:jar")
}

// Helper to get the mcp-server jar path for runtime usage
val mcpServerJarPath: Provider<String> = provider {
    project(":mcp-server").tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath
}

// Helper to get the recipe-book-mcp jar path for runtime usage
val recipeBookMcpJarPath: Provider<String> = provider {
    project(":recipe-book-mcp").tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath
}

// Helper to get the mcp-foundation-search jar path for runtime usage
val foundationSearchMcpJarPath: Provider<String> = provider {
    project(":mcp-foundation-search").tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath
}

// Generate build config with mcp-server jar paths
buildConfig {
    buildConfigField("MCP_SERVER_JAR_PATH", mcpServerJarPath)
    buildConfigField("RECIPE_BOOK_MCP_JAR_PATH", recipeBookMcpJarPath)
    buildConfigField("FOUNDATION_SEARCH_MCP_JAR_PATH", foundationSearchMcpJarPath)
}
