package ru.reset.renplay.utils

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.ConcurrentLinkedQueue

object RenPlayTranslator {
    private var translator: Translator? = null
    private val translatedQueue = ConcurrentLinkedQueue<Pair<String, String>>()
    private var isReady = false

    @JvmStatic
    fun initializeFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("RenPlayPrefs", Context.MODE_PRIVATE)
        val sourceLang = prefs.getString("trans_source", "en") ?: "en"
        val targetLang = prefs.getString("trans_target", "ru") ?: "ru"
        initialize(context, sourceLang, targetLang)
    }

    @JvmStatic
    fun getDownloadedTranslateModels(callback: (Set<String>) -> Unit) {
        val modelManager = RemoteModelManager.getInstance()
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                callback(models.map { it.language }.toSet())
            }
            .addOnFailureListener { callback(emptySet()) }
    }

    @JvmStatic
    fun checkModelDownloaded(lang: String, callback: (Boolean) -> Unit) {
        val modelManager = RemoteModelManager.getInstance()
        val model = TranslateRemoteModel.Builder(lang).build()
        modelManager.isModelDownloaded(model).addOnSuccessListener { downloaded ->
            callback(downloaded)
        }.addOnFailureListener { callback(false) }
    }

    @JvmStatic
    fun downloadModel(lang: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val modelManager = RemoteModelManager.getInstance()
        val model = TranslateRemoteModel.Builder(lang).build()
        val conditions = DownloadConditions.Builder().build()
        modelManager.download(model, conditions)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    @JvmStatic
    fun deleteModel(lang: String, onComplete: () -> Unit) {
        translator?.close()
        val modelManager = RemoteModelManager.getInstance()
        val model = TranslateRemoteModel.Builder(lang).build()
        modelManager.deleteDownloadedModel(model).addOnCompleteListener { onComplete() }
    }

    @JvmStatic
    fun initialize(context: Context, sourceLang: String, targetLang: String) {
        try {
            com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(context.applicationContext)
        } catch (e: Exception) {
            Log.e("RenPlayTranslator", "MlKitContext initialization failed", e)
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        
        translator?.close()
        val newTranslator = Translation.getClient(options)
        translator = newTranslator

        val conditions = DownloadConditions.Builder().build()
        newTranslator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                isReady = true
                Log.d("RenPlayTranslator", "Translation model downloaded and ready.")
            }
            .addOnFailureListener { e ->
                Log.e("RenPlayTranslator", "Failed to download translation model", e)
            }
    }

    @JvmStatic
    fun requestTranslation(text: String) {
        if (!isReady || text.isBlank()) {
            if (text.isNotBlank()) translatedQueue.add(Pair(text, text))
            return
        }

        translator?.translate(text)
            ?.addOnSuccessListener { translatedText ->
                translatedQueue.add(Pair(text, translatedText))
            }
            ?.addOnFailureListener { e ->
                Log.e("RenPlayTranslator", "Translation failed for text: $text", e)
                translatedQueue.add(Pair(text, text))
            }
    }

    @JvmStatic
    fun getTranslatedItemsStr(): String? {
        if (translatedQueue.isEmpty()) return null
        
        val sb = StringBuilder()
        while (translatedQueue.isNotEmpty()) {
            val item = translatedQueue.poll()
            if (item != null) {
                sb.append(item.first).append("|||").append(item.second).append("|||")
            }
        }
        return sb.toString()
    }
    
    @JvmStatic
    fun destroy() {
        translator?.close()
        translator = null
        isReady = false
        translatedQueue.clear()
    }
}