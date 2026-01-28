package com.github.alphapaca.claudeclient.data.speech

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Manages extracting and caching of bundled Vosk speech recognition model.
 */
object VoskModelManager {
    private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
    private const val RESOURCE_PATH = "/vosk/$MODEL_NAME"

    private val cacheDir = File(System.getProperty("user.home"), ".claudeclient/vosk-model")

    /**
     * Check if the model is already extracted to cache.
     */
    fun isModelAvailable(): Boolean {
        val modelDir = File(cacheDir, MODEL_NAME)
        val confFile = File(modelDir, "conf/model.conf")
        return modelDir.exists() && modelDir.isDirectory && confFile.exists()
    }

    /**
     * Get the path to the Vosk model directory.
     * Returns null if model is not available.
     */
    fun getModelPathIfAvailable(): String? {
        val modelDir = File(cacheDir, MODEL_NAME)
        return if (isModelAvailable()) modelDir.absolutePath else null
    }

    /**
     * Extract the bundled model from resources to cache.
     * @param onProgress callback with extraction status messages
     * @return true if extraction succeeded
     */
    suspend fun extractModel(onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val modelDir = File(cacheDir, MODEL_NAME)

        if (isModelAvailable()) {
            Logger.d { "Vosk model already available at: ${modelDir.absolutePath}" }
            return@withContext true
        }

        Logger.i { "Extracting bundled Vosk model..." }
        onProgress("Extracting speech model...")

        try {
            cacheDir.mkdirs()

            // List of files/directories to extract from resources
            val filesToExtract = listOf(
                "README",
                "am/final.mdl",
                "conf/mfcc.conf",
                "conf/model.conf",
                "graph/Gr.fst",
                "graph/HCLr.fst",
                "graph/disambig_tid.int",
                "graph/phones/word_boundary.int",
                "ivector/final.dubm",
                "ivector/final.ie",
                "ivector/final.mat",
                "ivector/global_cmvn.stats",
                "ivector/online_cmvn.conf",
                "ivector/splice.conf"
            )

            var extracted = 0
            for (relativePath in filesToExtract) {
                val resourcePath = "$RESOURCE_PATH/$relativePath"
                val targetFile = File(modelDir, relativePath)

                targetFile.parentFile?.mkdirs()

                val inputStream = this::class.java.getResourceAsStream(resourcePath)
                if (inputStream != null) {
                    inputStream.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    extracted++
                    if (extracted % 5 == 0) {
                        onProgress("Extracting speech model... ${extracted}/${filesToExtract.size}")
                    }
                } else {
                    Logger.w { "Resource not found: $resourcePath" }
                }
            }

            Logger.i { "Model extraction complete: $extracted files" }

            isModelAvailable()
        } catch (e: Exception) {
            Logger.e(e) { "Failed to extract Vosk model" }
            // Clean up partial extraction
            modelDir.deleteRecursively()
            false
        }
    }

    /**
     * Get the path to the Vosk model, extracting it if necessary.
     * @return Path to the model directory, or null if extraction failed
     */
    suspend fun getModelPath(): String? = withContext(Dispatchers.IO) {
        val modelDir = File(cacheDir, MODEL_NAME)

        if (isModelAvailable()) {
            Logger.d { "Using cached Vosk model at: ${modelDir.absolutePath}" }
            return@withContext modelDir.absolutePath
        }

        // Try to extract from resources
        val success = extractModel { }
        if (success && modelDir.exists()) {
            modelDir.absolutePath
        } else {
            null
        }
    }
}
