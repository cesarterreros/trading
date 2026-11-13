package com.trading.monitor

import com.trading.monitor.llm.SignalParser
import com.trading.monitor.matrix.Direction
import com.trading.monitor.matrix.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalParserTest {
    private val price = 100_000.0

    private val valid = """
        {"relevant": true, "direction": "bullish", "type": "trade_setup", "conviction": 3, "timeframe": "swing",
         "entry": 100000, "stop": 98000, "targets": [105000, "110k"], "support": ["96,500"],
         "resistance": [250000], "evidence": "long from 100k"}
    """.trimIndent()

    @Test
    fun parsesAValidAnswer() {
        val s = SignalParser.parse(valid, price)!!
        assertEquals(Direction.BULLISH, s.direction)
        assertEquals("trade_setup", s.type)
        assertEquals(3, s.conviction)
        assertEquals(Timeframe.SWING, s.timeframe)
        assertEquals(100_000.0, s.entry!!, 0.0)
        assertEquals(98_000.0, s.stop!!, 0.0)
        assertEquals(listOf(105_000.0, 110_000.0), s.targets)
        assertEquals(listOf(96_500.0), s.support)
        assertEquals("long from 100k", s.evidence)
    }

    @Test
    fun dropsLevelsFarFromThePrice() {
        val s = SignalParser.parse(valid, price)!!
        assertTrue(s.resistance.isEmpty())
        // Without a live price there is nothing to compare against, so the level is kept.
        assertEquals(listOf(250_000.0), SignalParser.parse(valid, null)!!.resistance)
    }

    @Test
    fun acceptsAnswerWrappedInCodeFence() {
        assertNotNull(SignalParser.parse("```json\n$valid\n```", price))
    }

    @Test
    fun rejectsMalformedAnswers() {
        assertNull(SignalParser.parse("I cannot help with that", price))
        assertNull(SignalParser.parse("{\"direction\": ", price))
        assertNull(SignalParser.parse("", price))
    }

    @Test
    fun irrelevantPostBecomesNeutralNoise() {
        val s = SignalParser.parse("""{"relevant": false, "direction": "bullish", "type": "analysis"}""", price)!!
        assertEquals("noise", s.type)
        assertEquals(Direction.NEUTRAL, s.direction)
    }

    @Test
    fun missingOrOddFieldsFallBackToSafeValues() {
        val s = SignalParser.parse("""{"direction": "moon", "conviction": 9, "timeframe": "??", "entry": null, "stop": "n/a"}""", price)!!
        assertEquals(Direction.NEUTRAL, s.direction)
        assertEquals(3, s.conviction)
        assertEquals(Timeframe.SWING, s.timeframe)
        assertNull(s.entry)
        assertNull(s.stop)
        assertTrue(s.targets.isEmpty())
    }

    @Test
    fun userPromptWithoutScreenshotNoteIsUnchanged() {
        val p = SignalParser.userPrompt("alice", "2026-11-12T00:00:00Z", "hello", emptyList(), 100000.0)
        assertFalse(p.contains("screenshots"))
    }

    @Test
    fun userPromptAddsScreenshotNote() {
        val p = SignalParser.userPrompt("alice", "2026-11-12T00:00:00Z", "hello", emptyList(), 100000.0, fromScreenshot = true)
        assertTrue(p.contains("Use only the main post by @alice"))
        assertTrue(p.indexOf("Use only the main post") < p.indexOf("Post text:"))
    }

    @Test
    fun readsCommonPriceNotations() {
        assertEquals(108_500.0, SignalParser.toLevel("$108.5k")!!, 1e-6)
        assertEquals(108_500.0, SignalParser.toLevel("108,500")!!, 1e-6)
        assertEquals(108_500.0, SignalParser.toLevel(108500)!!, 1e-6)
        assertNull(SignalParser.toLevel("soon"))
    }
}
