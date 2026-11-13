package com.trading.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualPostTest {
    private val id = "2120525675752776172"

    @Test
    fun findsLinkWithQuery() {
        val l = findXLink("https://x.com/BitcoinMagazine/status/$id?s=20")!!
        assertEquals("BitcoinMagazine", l.handle)
        assertEquals(id, l.postId)
        assertEquals(1794407693L, l.timeMs / 1000)
    }

    @Test
    fun findsLinkVariants() {
        assertEquals("jack", findXLink("https://twitter.com/jack/status/$id")!!.handle)
        assertEquals("jack", findXLink("https://mobile.twitter.com/jack/status/$id")!!.handle)
        assertEquals("jack", findXLink("x.com/jack/status/$id")!!.handle)
        val photo = findXLink("https://x.com/jack/status/$id/photo/1")!!
        assertEquals("jack", photo.handle)
        assertEquals(id, photo.postId)
        assertEquals("a_b", findXLink("Look at this https://x.com/a_b/status/$id wow")!!.handle)
    }

    @Test
    fun rejectsNonPostLinks() {
        assertNull(findXLink("no link here"))
        assertNull(findXLink("https://x.com/BitcoinMagazine"))
        assertNull(findXLink("https://x.com/i/status/$id"))
        assertNull(findXLink("https://x.com/i/web/status/$id"))
        assertNull(findXLink("https://twitter.com/jack/status/20"))
    }

    @Test
    fun readsTimeFromPostId() {
        assertEquals(1794407693L, timeFromPostId(id)!! / 1000)
        assertNull(timeFromPostId("abc"))
        assertNull(timeFromPostId(""))
        assertNull(timeFromPostId("-5"))
        assertNull(timeFromPostId("20"))
    }

    @Test
    fun cleansLinkAtEnd() {
        assertEquals("hello world", cleanSharedText("hello world https://x.com/a/status/$id?s=20\n"))
    }

    @Test
    fun cleansLinkInMiddle() {
        assertEquals("one\n\ntwo", cleanSharedText("one\nhttps://x.com/a/status/$id/photo/1\ntwo"))
    }

    @Test
    fun cleanLeavesPlainTextAlone() {
        assertEquals("just text", cleanSharedText("  just text \n"))
    }

    @Test
    fun cleansLinkThatFollowsTextWithoutASpace() {
        assertEquals("target 76000_", cleanSharedText("target 76000_https://x.com/a_b/status/2120525675752776172?s=20"))
        assertEquals("a_b", findXLink("target 76000_https://x.com/a_b/status/2120525675752776172")!!.handle)
        // A longer domain name that merely ends in x.com is not a post link.
        assertNull(findXLink("https://notx.com/a_b/status/2120525675752776172"))
    }

    @Test
    fun cleanOfOnlyALinkIsEmpty() {
        assertEquals("", cleanSharedText("https://twitter.com/a/status/$id"))
    }

    @Test
    fun cleanCollapsesNewlines() {
        assertEquals("a\n\nb", cleanSharedText("a\n\n\n\nb"))
    }

    @Test
    fun guessPrefersKnownAccount() {
        assertEquals("doctorcatx", guessHandle("@someone replying to @DoctorCatX", listOf("doctorcatx")))
    }

    @Test
    fun guessFallsBackToFirstMention() {
        assertEquals("someone", guessHandle("@SomeOne replying to @Other", listOf("doctorcatx")))
    }

    @Test
    fun guessIsNullWithoutMentions() {
        assertNull(guessHandle("no mentions at all", listOf("doctorcatx")))
        assertNull(guessHandle("mail me at joe@example.com", emptyList()))
    }

    @Test
    fun manualIdsRoundTrip() {
        val m = manualPostId(1234L)
        assertEquals("m-1234", m)
        assertTrue(isManualId(m))
        assertFalse(isManualId(id))
        assertNotNull(m)
    }

    @Test
    fun mergeIgnoresBlankAdded() {
        assertEquals("a", mergePostText(" a ", "  "))
    }

    @Test
    fun mergeTakesAddedWhenExistingBlank() {
        assertEquals("b", mergePostText("", " b "))
    }

    @Test
    fun mergeKeepsExistingWhenContained() {
        assertEquals("one two", mergePostText("one two", "two"))
    }

    @Test
    fun mergeJoinsWithBlankLine() {
        assertEquals("a\n\nb", mergePostText("a", "b"))
    }
}
