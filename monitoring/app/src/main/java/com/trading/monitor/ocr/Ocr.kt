package com.trading.monitor.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device text recognition (ML Kit, bundled model, works offline). */
object Ocr {
    private const val MAX_LINES = 150
    private const val MAX_CHARS = 4000
    private val axisNumber = Regex("""[$]?\d[\d,.]*[kK]?""")

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(format(it, bitmap.width, bitmap.height)) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /**
     * One line per recognised text line: "[x%,y%] text", top to bottom.
     * Bare numbers at the right or left edge are tagged [axis] so the model can ignore price-scale ticks.
     */
    private fun format(result: Text, width: Int, height: Int): String {
        data class Line(val x: Int, val y: Int, val text: String)

        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            val text = line.text.trim()
            if (text.isEmpty()) return@mapNotNull null
            Line(box.centerX() * 100 / width.coerceAtLeast(1), box.centerY() * 100 / height.coerceAtLeast(1), text)
        }.sortedWith(compareBy({ it.y }, { it.x }))

        val out = StringBuilder()
        for (line in lines.take(MAX_LINES)) {
            val axis = (line.x >= 88 || line.x <= 6) && axisNumber.matches(line.text)
            val row = "[${line.x}%,${line.y}%] ${if (axis) "[axis] " else ""}${line.text}\n"
            if (out.length + row.length > MAX_CHARS) break
            out.append(row)
        }
        return out.toString().trimEnd()
    }
}
