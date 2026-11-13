package com.trading.monitor.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val handle: String,
    val weight: Double = 1.0,
    val enabled: Boolean = true,
    val lastCheckedMs: Long = 0,
    val lastError: String? = null,
    /** Set by the user in Accounts; the app never reads profiles from X. */
    val displayName: String? = null,
    val verified: Boolean = false,
    /** File name of the chosen avatar picture in the app's files directory. */
    val avatarPath: String? = null,
)

/** status: NEW (waiting for interpretation), DONE, SKIPPED (repost, too old, empty), ERROR. */
@Entity(tableName = "posts")
data class Post(
    @PrimaryKey val id: String,
    val handle: String,
    val author: String,
    val timeMs: Long,
    val text: String,
    /** Image URLs separated by newlines. */
    val imageUrls: String,
    val ocrText: String = "",
    val isRepost: Boolean,
    val status: String = "NEW",
    val error: String? = null,
    val fetchedMs: Long,
)

/** Level lists are comma-separated numbers. outcome: null (open), WIN, LOSS, EXPIRED, INVALID. */
@Entity(tableName = "signals")
data class Signal(
    @PrimaryKey val postId: String,
    val handle: String,
    val timeMs: Long,
    val direction: String,
    val type: String,
    val conviction: Int,
    val timeframe: String,
    val entry: Double?,
    val stop: Double?,
    val targets: String,
    val support: String,
    val resistance: String,
    val evidence: String,
    val priceAtSignal: Double?,
    val outcome: String? = null,
    val resolvedMs: Long? = null,
)

@Entity(tableName = "usage")
data class Usage(@PrimaryKey val day: String, val tokens: Long)

@Dao
interface MonitorDao {
    @Query("SELECT * FROM accounts ORDER BY handle")
    fun accounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY handle")
    suspend fun accountsOnce(): List<Account>

    @Query("SELECT * FROM accounts WHERE enabled = 1 ORDER BY lastCheckedMs")
    suspend fun enabledAccounts(): List<Account>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addAccount(account: Account)

    @Update
    suspend fun updateAccount(account: Account)

    @Query("UPDATE accounts SET lastCheckedMs = :ms, lastError = :error WHERE handle = :handle")
    suspend fun markChecked(handle: String, ms: Long, error: String?)

    @Query("DELETE FROM accounts WHERE handle = :handle")
    suspend fun deleteAccount(handle: String)

    /** Returns -1 when the post is already stored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPost(post: Post): Long

    @Update
    suspend fun updatePost(post: Post)

    @Query("SELECT * FROM posts WHERE status = 'NEW' ORDER BY timeMs")
    suspend fun pendingPosts(): List<Post>

    @Query("DELETE FROM posts WHERE id = :id")
    suspend fun deletePost(id: String)

    @Query("DELETE FROM signals WHERE postId = :postId")
    suspend fun deleteSignal(postId: String)

    @Query("SELECT * FROM posts WHERE id = :id")
    suspend fun postById(id: String): Post?

    @Query("SELECT * FROM posts ORDER BY timeMs DESC LIMIT 500")
    fun recentPosts(): Flow<List<Post>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignal(signal: Signal)

    @Query("SELECT * FROM signals ORDER BY timeMs DESC LIMIT 1000")
    fun signals(): Flow<List<Signal>>

    @Query("SELECT * FROM signals ORDER BY timeMs DESC LIMIT 1000")
    suspend fun signalsOnce(): List<Signal>

    @Query("SELECT * FROM signals WHERE outcome IS NULL AND stop IS NOT NULL AND targets != '' AND direction != 'neutral'")
    suspend fun unresolvedSignals(): List<Signal>

    @Query("UPDATE signals SET outcome = :outcome, resolvedMs = :ms WHERE postId = :postId")
    suspend fun setOutcome(postId: String, outcome: String, ms: Long)

    @Query("SELECT tokens FROM usage WHERE day = :day")
    suspend fun tokens(day: String): Long?

    @Query("SELECT tokens FROM usage WHERE day = :day")
    fun tokensFlow(day: String): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setUsage(usage: Usage)
}

@Database(entities = [Account::class, Post::class, Signal::class, Usage::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): MonitorDao
}

/**
 * Version 1 to 2: accounts gain [Account.displayName], [Account.verified] and [Account.avatarPath].
 * Existing rows and all other tables are left as they are; the new values default to null,
 * false and null.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN displayName TEXT")
        db.execSQL("ALTER TABLE accounts ADD COLUMN verified INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE accounts ADD COLUMN avatarPath TEXT")
    }
}
