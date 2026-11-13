package com.trading.monitor

import com.trading.monitor.db.Account
import com.trading.monitor.db.Post
import com.trading.monitor.db.Signal
import com.trading.monitor.matrix.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedGroupingTest {
    private fun account(handle: String, enabled: Boolean = true) = Account(handle, enabled = enabled)

    private fun post(
        id: String,
        handle: String,
        timeMs: Long,
        status: String = "DONE",
        author: String = handle,
        isRepost: Boolean = false,
    ) = Post(
        id = id, handle = handle, author = author, timeMs = timeMs, text = "t", imageUrls = "",
        isRepost = isRepost, status = status, fetchedMs = timeMs,
    )

    private fun signal(
        postId: String,
        handle: String,
        timeMs: Long,
        direction: String = "bullish",
        type: String = "trade_setup",
    ) = Signal(
        postId = postId, handle = handle, timeMs = timeMs, direction = direction, type = type,
        conviction = 3, timeframe = "swing", entry = null, stop = null, targets = "",
        support = "", resistance = "", evidence = "", priceAtSignal = null,
    )

    @Test
    fun sectionsOrderedByNewestPostThenHandle() {
        val accounts = listOf(account("a"), account("b"), account("c"), account("d"))
        val posts = listOf(
            post("1", "a", 100),
            post("2", "b", 300),
            post("3", "c", 100),
            post("4", "d", 200),
        )
        val result = groupFeed(accounts, posts, emptyList())
        // b (300), d (200), then a and c tied at 100 -> alphabetical
        assertEquals(listOf("b", "d", "a", "c"), result.map { it.handle })
    }

    @Test
    fun accountsWithoutPostsComeLastAlphabetically() {
        val accounts = listOf(account("z"), account("m"), account("a"), account("k"))
        val posts = listOf(post("1", "z", 50), post("2", "k", 10))
        val result = groupFeed(accounts, posts, emptyList())
        assertEquals(listOf("z", "k", "a", "m"), result.map { it.handle })
        val empty = result.filter { it.handle == "a" || it.handle == "m" }
        assertTrue(empty.all { it.latestPostMs == null && it.posts.isEmpty() })
    }

    @Test
    fun postsInsideSectionAreNewestFirst() {
        val posts = listOf(post("1", "a", 100), post("3", "a", 300), post("2", "a", 200))
        val section = groupFeed(listOf(account("a")), posts, emptyList()).single()
        assertEquals(listOf("3", "2", "1"), section.posts.map { it.id })
        assertEquals(300L, section.latestPostMs)
    }

    @Test
    fun repostIsGroupedUnderCollectingAccount() {
        val posts = listOf(post("1", "a", 100, author = "other", isRepost = true))
        val result = groupFeed(listOf(account("a"), account("other")), posts, emptyList())
        val a = result.first { it.handle == "a" }
        val other = result.first { it.handle == "other" }
        assertEquals(listOf("1"), a.posts.map { it.id })
        assertTrue(other.posts.isEmpty())
    }

    @Test
    fun pausedAndRemovedStates() {
        val accounts = listOf(account("active"), account("paused", enabled = false))
        val posts = listOf(post("1", "gone", 100))
        val result = groupFeed(accounts, posts, emptyList()).associateBy { it.handle }
        assertEquals(SectionState.ACTIVE, result.getValue("active").state)
        assertEquals(SectionState.PAUSED, result.getValue("paused").state)
        assertEquals(SectionState.REMOVED, result.getValue("gone").state)
        assertEquals(3, result.size)
    }

    @Test
    fun waitingCountsNewAndErrorOnly() {
        val posts = listOf(
            post("1", "a", 1, status = "NEW"),
            post("2", "a", 2, status = "ERROR"),
            post("3", "a", 3, status = "DONE"),
            post("4", "a", 4, status = "SKIPPED"),
            post("5", "a", 5, status = "NEW"),
        )
        val section = groupFeed(listOf(account("a")), posts, emptyList()).single()
        assertEquals(3, section.waiting)
    }

    @Test
    fun latestStanceUsesNewestNonNoiseSignal() {
        val signals = listOf(
            signal("1", "a", 100, direction = "bullish"),
            signal("2", "a", 200, direction = "bearish", type = "analysis"),
            signal("3", "a", 300, direction = "bullish", type = "noise"),
        )
        val section = groupFeed(listOf(account("a")), emptyList(), signals).single()
        assertEquals(Direction.BEARISH, section.latestStance)
    }

    @Test
    fun latestStanceIsNullWithOnlyNoiseOrNoSignals() {
        val signals = listOf(signal("1", "a", 100, type = "noise"))
        val result = groupFeed(listOf(account("a"), account("b")), emptyList(), signals)
            .associateBy { it.handle }
        assertNull(result.getValue("a").latestStance)
        assertNull(result.getValue("b").latestStance)
    }

    @Test
    fun latestStanceIsPerHandle() {
        val signals = listOf(
            signal("1", "a", 100, direction = "bullish"),
            signal("2", "b", 200, direction = "neutral", type = "news"),
        )
        val result = groupFeed(listOf(account("a"), account("b")), emptyList(), signals)
            .associateBy { it.handle }
        assertEquals(Direction.BULLISH, result.getValue("a").latestStance)
        assertEquals(Direction.NEUTRAL, result.getValue("b").latestStance)
    }

    @Test
    fun emptyInputsReturnEmptyList() {
        assertTrue(groupFeed(emptyList(), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun postUrlPointsToThePost() {
        val p = post("123", "alice", 0, author = "alice")
        assertEquals("https://x.com/alice/status/123", postUrl(p))
    }

    @Test
    fun postUrlOfARepostUsesTheOriginalAuthor() {
        val p = post("456", "alice", 0, author = "Bob", isRepost = true)
        assertEquals("https://x.com/Bob/status/456", postUrl(p))
    }

    // --- display name / verified / avatar carried into sections ---

    private fun section(
        handle: String,
        stance: Direction? = null,
        displayName: String? = null,
    ) = FeedSection(
        handle = handle, state = SectionState.ACTIVE, posts = emptyList(), waiting = 0,
        latestStance = stance, latestPostMs = null, displayName = displayName,
    )

    @Test
    fun groupFeedCarriesAccountProfileFields() {
        val accounts = listOf(
            Account("alice", enabled = true, displayName = "Alice A", verified = true, avatarPath = "/a.png"),
        )
        val section = groupFeed(accounts, emptyList(), emptyList()).single()
        assertEquals("Alice A", section.displayName)
        assertTrue(section.verified)
        assertEquals("/a.png", section.avatarPath)
    }

    @Test
    fun groupFeedRemovedHandleHasEmptyProfileFields() {
        val accounts = listOf(account("alice"))
        val posts = listOf(post("1", "gone", 100))
        val removed = groupFeed(accounts, posts, emptyList()).first { it.handle == "gone" }
        assertNull(removed.displayName)
        assertFalse(removed.verified)
        assertNull(removed.avatarPath)
    }

    // --- filterSections ---

    @Test
    fun filterByHandle() {
        val sections = listOf(section("alice"), section("bob"))
        assertEquals(listOf("alice"), filterSections(sections, "ali", SentimentFilter.ALL).map { it.handle })
    }

    @Test
    fun filterByDisplayName() {
        val sections = listOf(section("a1", displayName = "Crypto Whale"), section("b1", displayName = "Bob"))
        assertEquals(listOf("a1"), filterSections(sections, "whale", SentimentFilter.ALL).map { it.handle })
    }

    @Test
    fun filterIgnoresOneLeadingAt() {
        val sections = listOf(section("alice"), section("bob"))
        assertEquals(listOf("alice"), filterSections(sections, "@alice", SentimentFilter.ALL).map { it.handle })
    }

    @Test
    fun filterIsCaseInsensitive() {
        val sections = listOf(section("alice"), section("bob"))
        assertEquals(listOf("alice"), filterSections(sections, "ALI", SentimentFilter.ALL).map { it.handle })
    }

    @Test
    fun blankQueryMatchesAll() {
        val sections = listOf(section("alice"), section("bob"))
        assertEquals(listOf("alice", "bob"), filterSections(sections, "   ", SentimentFilter.ALL).map { it.handle })
    }

    @Test
    fun filterBullish() {
        val sections = listOf(
            section("a", Direction.BULLISH),
            section("b", Direction.BEARISH),
            section("c", Direction.NEUTRAL),
        )
        assertEquals(listOf("a"), filterSections(sections, "", SentimentFilter.BULLISH).map { it.handle })
    }

    @Test
    fun filterBearish() {
        val sections = listOf(
            section("a", Direction.BULLISH),
            section("b", Direction.BEARISH),
            section("c", Direction.NEUTRAL),
        )
        assertEquals(listOf("b"), filterSections(sections, "", SentimentFilter.BEARISH).map { it.handle })
    }

    @Test
    fun filterNeutral() {
        val sections = listOf(
            section("a", Direction.BULLISH),
            section("b", Direction.BEARISH),
            section("c", Direction.NEUTRAL),
        )
        assertEquals(listOf("c"), filterSections(sections, "", SentimentFilter.NEUTRAL).map { it.handle })
    }

    @Test
    fun nullStanceSectionMatchesOnlyAll() {
        val sections = listOf(section("a", Direction.BULLISH), section("n", null))
        assertEquals(listOf("a", "n"), filterSections(sections, "", SentimentFilter.ALL).map { it.handle })
        assertTrue(filterSections(sections, "", SentimentFilter.BULLISH).none { it.handle == "n" })
        assertTrue(filterSections(sections, "", SentimentFilter.BEARISH).none { it.handle == "n" })
        assertTrue(filterSections(sections, "", SentimentFilter.NEUTRAL).none { it.handle == "n" })
    }

    @Test
    fun filterCombinesTextAndSentiment() {
        val sections = listOf(
            section("alice", Direction.BULLISH),
            section("alice2", Direction.BEARISH),
            section("bob", Direction.BULLISH),
        )
        assertEquals(
            listOf("alice"),
            filterSections(sections, "ali", SentimentFilter.BULLISH).map { it.handle },
        )
    }

    @Test
    fun filterPreservesInputOrder() {
        val sections = listOf(
            section("c", Direction.BULLISH),
            section("a", Direction.BEARISH),
            section("b", Direction.BULLISH),
        )
        assertEquals(listOf("c", "b"), filterSections(sections, "", SentimentFilter.BULLISH).map { it.handle })
        assertEquals(listOf("c", "a", "b"), filterSections(sections, "", SentimentFilter.ALL).map { it.handle })
    }

    @Test
    fun filterWithNoMatchReturnsEmptyList() {
        val sections = listOf(section("alice"), section("bob"))
        assertTrue(filterSections(sections, "zzz", SentimentFilter.ALL).isEmpty())
    }

    // --- initialOf ---

    @Test
    fun initialUsesDisplayNameWhenPresent() {
        assertEquals("A", initialOf("trader1", "Alice"))
    }

    @Test
    fun initialFallsBackToHandleWhenDisplayNameBlank() {
        assertEquals("T", initialOf("trader1", ""))
        assertEquals("T", initialOf("trader1", "   "))
        assertEquals("T", initialOf("trader1", null))
    }

    @Test
    fun initialSkipsLeadingAtAndSymbols() {
        assertEquals("A", initialOf("@alice", null))
        assertEquals("A", initialOf("-_-alice", null))
    }

    @Test
    fun initialIsQuestionMarkWithoutLetterOrDigit() {
        assertEquals("?", initialOf("@-_!", "---"))
        assertEquals("?", initialOf("", null))
    }

    @Test
    fun initialIsUpperCase() {
        assertEquals("B", initialOf("bob", "bob"))
        assertEquals("C", initialOf("carl", "carl"))
    }

    // --- avatarColorIndex ---

    @Test
    fun avatarColorIndexIsStable() {
        assertEquals(avatarColorIndex("alice", 6), avatarColorIndex("alice", 6))
    }

    @Test
    fun avatarColorIndexIgnoresAtAndCase() {
        assertEquals(avatarColorIndex("alice", 7), avatarColorIndex("@Alice", 7))
    }

    @Test
    fun avatarColorIndexStaysInRange() {
        val handles = listOf("alice", "@bob", "c", "some_long_handle_123", "-_-")
        for (size in 1..8) {
            for (handle in handles) {
                val index = avatarColorIndex(handle, size)
                assertTrue("index $index out of range for $handle/$size", index in 0 until size)
            }
        }
    }

    @Test
    fun avatarColorIndexWithSingleColorIsZero() {
        assertEquals(0, avatarColorIndex("alice", 1))
    }

    // --- resolveSelectedAccount ---

    @Test
    fun resolveSelectedAccountReturnsExistingHandle() {
        val sections = listOf(section("alice"), section("bob"))
        assertEquals("bob", resolveSelectedAccount("bob", sections))
    }

    @Test
    fun resolveSelectedAccountReturnsNullForMissingHandle() {
        val sections = listOf(section("alice"))
        assertNull(resolveSelectedAccount("ghost", sections))
    }

    @Test
    fun resolveSelectedAccountReturnsNullForNull() {
        assertNull(resolveSelectedAccount(null, listOf(section("alice"))))
    }
}
