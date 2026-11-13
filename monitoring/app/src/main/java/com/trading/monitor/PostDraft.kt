package com.trading.monitor

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** The post being added by hand. Snapshot state, so screens observe it. */
class PostDraft {
    var open by mutableStateOf(false)
    var text by mutableStateOf("")
    var link by mutableStateOf<XLink?>(null)

    /** Files already copied into the app's private drafts folder. */
    val images = mutableStateListOf<File>()

    /** Shared images that could not be read, shown as a notice on the Add post screen. */
    var failedImages by mutableStateOf(0)

    fun addText(shared: String) {
        link = link ?: findXLink(shared)
        text = mergePostText(text, cleanSharedText(shared))
        open = true
    }

    fun addImage(file: File) {
        images += file
        open = true
    }

    fun clear(deleteFiles: Boolean) {
        if (deleteFiles) images.forEach { it.delete() }
        images.clear()
        failedImages = 0
        text = ""
        link = null
        open = false
    }
}

/** Copies a shared image into filesDir/drafts and returns the file, or null if it cannot be read. Call off the main thread. */
fun importImage(context: Context, uri: Uri): File? = try {
    val dir = File(context.filesDir, "drafts").apply { mkdirs() }
    val file = File(dir, UUID.randomUUID().toString() + ".img")
    val input = context.contentResolver.openInputStream(uri)
    if (input == null) {
        null
    } else {
        input.use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }
        file
    }
} catch (e: Exception) {
    null
}

/** Copies shared images into the draft one after another, keeping their order, and counts the ones that cannot be read. */
suspend fun importImagesInto(context: Context, draft: PostDraft, uris: List<Uri>) = withContext(Dispatchers.IO) {
    for (uri in uris) {
        val file = importImage(context, uri)
        withContext(Dispatchers.Main) { if (file != null) draft.addImage(file) else draft.failedImages++ }
    }
}
