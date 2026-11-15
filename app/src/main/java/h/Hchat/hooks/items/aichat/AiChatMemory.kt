package h.Hchat.hooks.items.aichat

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

internal data class AiMemoryState(
    val time: Long = 0, val id: Long = 0, val count: Long = 0,
    val facts: String = "", val revision: Long = 0,
    val input: Long = 0, val output: Long = 0,
    val pending: Boolean = false, val error: String = "", val initialDone: Boolean = false
)

/** Only derived facts, period summaries, cursors and sent-message fingerprints are stored here. */
internal class AiChatMemory private constructor(context: Context) : SQLiteOpenHelper(context, "Hchat_ai_chat.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE state (account TEXT NOT NULL, talker TEXT NOT NULL, cursor_time INTEGER NOT NULL DEFAULT 0, cursor_id INTEGER NOT NULL DEFAULT 0, processed INTEGER NOT NULL DEFAULT 0, facts TEXT NOT NULL DEFAULT '', revision INTEGER NOT NULL DEFAULT 0, memory_input INTEGER NOT NULL DEFAULT 0, memory_output INTEGER NOT NULL DEFAULT 0, pending INTEGER NOT NULL DEFAULT 0, error TEXT NOT NULL DEFAULT '', initial_done INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(account,talker))")
        db.execSQL("CREATE TABLE summaries (account TEXT NOT NULL, talker TEXT NOT NULL, start_time INTEGER NOT NULL, end_time INTEGER NOT NULL, end_id INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY(account,talker,end_time,end_id))")
        db.execSQL("CREATE TABLE sent (account TEXT NOT NULL, talker TEXT NOT NULL, fingerprint TEXT NOT NULL, submitted_at INTEGER NOT NULL, source TEXT NOT NULL)")
        db.execSQL("CREATE INDEX sent_lookup ON sent(account,talker,submitted_at)")
        db.execSQL("CREATE TABLE media (account TEXT NOT NULL, talker TEXT NOT NULL, msg_id INTEGER NOT NULL, description TEXT NOT NULL, PRIMARY KEY(account,talker,msg_id))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized fun state(account: String, talker: String): AiMemoryState = state(readableDatabase, account, talker)
    private fun state(db: SQLiteDatabase, account: String, talker: String): AiMemoryState {
        db.rawQuery("SELECT cursor_time,cursor_id,processed,facts,revision,memory_input,memory_output,pending,error,initial_done FROM state WHERE account=? AND talker=?", arrayOf(account, talker)).use { c ->
            return if (!c.moveToFirst()) AiMemoryState() else AiMemoryState(c.getLong(0), c.getLong(1), c.getLong(2), c.getString(3), c.getLong(4), c.getLong(5), c.getLong(6), c.getInt(7) != 0, c.getString(8), c.getInt(9) != 0)
        }
    }
    private fun ensure(db: SQLiteDatabase, account: String, talker: String) {
        db.execSQL("INSERT OR IGNORE INTO state(account,talker) VALUES (?,?)", arrayOf(account, talker))
    }
    @Synchronized fun finishInitialScan(account: String, talker: String, before: AiMemoryState) {
        val db = writableDatabase
        ensure(db, account, talker)
        db.execSQL("UPDATE state SET initial_done=1,revision=revision+1 WHERE account=? AND talker=? AND revision=? AND pending=0",
            arrayOf<Any>(account, talker, before.revision))
    }
    @Synchronized fun beginBatch(account: String, talker: String, before: AiMemoryState): AiMemoryState? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            ensure(db, account, talker)
            val current = state(db, account, talker)
            if (current.revision != before.revision || current.pending || current.error.isNotBlank()) return null
            db.execSQL("UPDATE state SET pending=1,revision=revision+1 WHERE account=? AND talker=?", arrayOf(account, talker))
            db.setTransactionSuccessful()
            return current.copy(revision = current.revision + 1, pending = true)
        } finally { db.endTransaction() }
    }
    @Synchronized fun failBatch(account: String, talker: String, before: AiMemoryState, error: String, usage: AiUsage? = null) {
        val db = writableDatabase
        db.execSQL("UPDATE state SET pending=0,error=?,revision=revision+1,memory_input=memory_input+?,memory_output=memory_output+? WHERE account=? AND talker=? AND revision=?",
            arrayOf<Any>(error.take(300), usage?.input ?: 0L, usage?.output ?: 0L, account, talker, before.revision))
        finishSupersededBatch(db, account, talker, before, usage)
    }

    /** Preserve a manual edit made while a paid request was running, and require an explicit retry. */
    private fun finishSupersededBatch(db: SQLiteDatabase, account: String, talker: String,
                                      before: AiMemoryState, usage: AiUsage?) {
        db.execSQL("UPDATE state SET pending=0,error=?,revision=revision+1,memory_input=memory_input+?,memory_output=memory_output+? WHERE account=? AND talker=? AND pending=1 AND cursor_time=? AND cursor_id=? AND revision>?",
            arrayOf<Any>("记忆在整理期间已修改，请手动继续", usage?.input ?: 0L, usage?.output ?: 0L,
                account, talker, before.time, before.id, before.revision))
    }
    @Synchronized fun retryBatch(account: String, talker: String) {
        val db = writableDatabase
        ensure(db, account, talker)
        db.execSQL("UPDATE state SET pending=0,error='',revision=revision+1 WHERE account=? AND talker=?", arrayOf(account, talker))
    }
    @Synchronized fun commitBatch(account: String, talker: String, before: AiMemoryState, time: Long, id: Long,
                                    count: Int, summary: String, facts: String, usage: AiUsage?): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            ensure(db, account, talker)
            val current = state(db, account, talker)
            if (current.revision != before.revision || current.time != before.time || current.id != before.id) {
                finishSupersededBatch(db, account, talker, before, usage)
                db.setTransactionSuccessful()
                return false
            }
            val values = ContentValues().apply {
                put("cursor_time", time); put("cursor_id", id); put("processed", current.count + count)
                put("facts", facts); put("revision", current.revision + 1)
                put("pending", 0); put("error", "")
                put("memory_input", current.input + (usage?.input ?: 0)); put("memory_output", current.output + (usage?.output ?: 0))
            }
            db.update("state", values, "account=? AND talker=?", arrayOf(account, talker))
            if (summary.isNotBlank()) {
                val entry = ContentValues().apply {
                    put("account", account); put("talker", talker); put("start_time", before.time)
                    put("end_time", time); put("end_id", id); put("text", summary)
                }
                db.insertWithOnConflict("summaries", null, entry, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
            return true
        } finally { db.endTransaction() }
    }
    @Synchronized fun summaries(account: String, talker: String, limit: Int = 8): List<String> {
        readableDatabase.rawQuery("SELECT text FROM summaries WHERE account=? AND talker=? ORDER BY end_time DESC,end_id DESC LIMIT ?", arrayOf(account, talker, limit.toString())).use { c ->
            val values = mutableListOf<String>()
            while (c.moveToNext()) values += c.getString(0)
            return values.asReversed()
        }
    }
    @Synchronized fun editFacts(account: String, talker: String, facts: String) {
        val db = writableDatabase
        ensure(db, account, talker)
        db.execSQL("UPDATE state SET facts=?, revision=revision+1 WHERE account=? AND talker=?", arrayOf(facts, account, talker))
    }
    /** Clearing sets a high-water mark, so old history is not silently imported again. */
    @Synchronized fun clear(account: String, talker: String, latestTime: Long, latestId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            ensure(db, account, talker)
            db.execSQL("UPDATE state SET cursor_time=?,cursor_id=?,processed=0,facts='',revision=revision+1,memory_input=0,memory_output=0,pending=0,error='',initial_done=1 WHERE account=? AND talker=?", arrayOf<Any>(latestTime, latestId, account, talker))
            db.delete("summaries", "account=? AND talker=?", arrayOf(account, talker))
            db.delete("media", "account=? AND talker=?", arrayOf(account, talker))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun rememberSent(account: String, talker: String, text: String, time: Long, source: String = "AI回复") {
        val db = writableDatabase
        db.execSQL("INSERT INTO sent(account,talker,fingerprint,submitted_at,source) VALUES (?,?,?,?,?)", arrayOf<Any>(account, talker, fingerprint(text), time, source))
    }
    @Synchronized fun sentSource(account: String, talker: String, text: String, time: Long): String? {
        readableDatabase.rawQuery("SELECT source FROM sent WHERE account=? AND talker=? AND fingerprint=? AND submitted_at BETWEEN ? AND ? ORDER BY submitted_at DESC LIMIT 1",
            arrayOf(account, talker, fingerprint(text), (time - 120_000).toString(), (time + 120_000).toString())).use { return if (it.moveToFirst()) it.getString(0) else null }
    }
    @Synchronized fun media(account: String, talker: String, id: Long): String? {
        readableDatabase.rawQuery("SELECT description FROM media WHERE account=? AND talker=? AND msg_id=?", arrayOf(account, talker, id.toString())).use { return if (it.moveToFirst()) it.getString(0) else null }
    }
    @Synchronized fun saveMedia(account: String, talker: String, id: Long, description: String) {
        if (id <= 0 || description.isBlank()) return
        val values = ContentValues().apply { put("account", account); put("talker", talker); put("msg_id", id); put("description", description) }
        writableDatabase.insertWithOnConflict("media", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
    companion object {
        private fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        @Volatile private var instance: AiChatMemory? = null
        fun get(context: Context): AiChatMemory = instance ?: synchronized(this) {
            instance ?: AiChatMemory(context.applicationContext ?: context).also { instance = it }
        }
    }
}
