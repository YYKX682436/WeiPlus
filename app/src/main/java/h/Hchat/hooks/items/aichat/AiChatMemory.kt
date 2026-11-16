package h.Hchat.hooks.items.aichat

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import h.Hchat.hooks.api.model.WeChatMessage
import java.security.MessageDigest

internal data class AiMemoryState(
    val time: Long = 0, val id: Long = 0, val count: Long = 0,
    val facts: String = "", val revision: Long = 0,
    val input: Long = 0, val output: Long = 0,
    val pending: Boolean = false, val error: String = "", val initialDone: Boolean = false
)

/** Only derived facts, period summaries, cursors and sent-message fingerprints are stored here. */
internal data class AiSent(val source: String, val body: String, val configId: String, val model: String, val metadata: String)
internal data class AiUsageRecord(val account: String, val configId: String, val provider: AiProvider, val model: String,
                                  val purpose: AiPurpose, val started: Long, val usage: AiUsage?, val status: String)
internal data class AiSearchUsage(val account: String, val started: Long, val credits: Long?, val status: String)

internal class AiChatMemory private constructor(context: Context) : SQLiteOpenHelper(context, "Hchat_ai_chat.db", null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE state (account TEXT NOT NULL, talker TEXT NOT NULL, cursor_time INTEGER NOT NULL DEFAULT 0, cursor_id INTEGER NOT NULL DEFAULT 0, processed INTEGER NOT NULL DEFAULT 0, facts TEXT NOT NULL DEFAULT '', revision INTEGER NOT NULL DEFAULT 0, memory_input INTEGER NOT NULL DEFAULT 0, memory_output INTEGER NOT NULL DEFAULT 0, pending INTEGER NOT NULL DEFAULT 0, error TEXT NOT NULL DEFAULT '', initial_done INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(account,talker))")
        db.execSQL("CREATE TABLE summaries (account TEXT NOT NULL, talker TEXT NOT NULL, start_time INTEGER NOT NULL, end_time INTEGER NOT NULL, end_id INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY(account,talker,end_time,end_id))")
        db.execSQL("CREATE TABLE sent (account TEXT NOT NULL, talker TEXT NOT NULL, fingerprint TEXT NOT NULL, submitted_at INTEGER NOT NULL, source TEXT NOT NULL, local_id INTEGER NOT NULL DEFAULT 0, server_id INTEGER NOT NULL DEFAULT 0, body TEXT NOT NULL DEFAULT '', config_id TEXT NOT NULL DEFAULT '', model TEXT NOT NULL DEFAULT '', metadata TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE INDEX sent_lookup ON sent(account,talker,submitted_at)")
        indexSentIds(db)
        db.execSQL("CREATE TABLE media (account TEXT NOT NULL, talker TEXT NOT NULL, msg_id INTEGER NOT NULL, description TEXT NOT NULL, PRIMARY KEY(account,talker,msg_id))")
        createUsage(db)
        createSearchUsage(db)
        createRecentImages(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            listOf("local_id INTEGER NOT NULL DEFAULT 0", "server_id INTEGER NOT NULL DEFAULT 0", "body TEXT NOT NULL DEFAULT ''",
                "config_id TEXT NOT NULL DEFAULT ''", "model TEXT NOT NULL DEFAULT ''", "metadata TEXT NOT NULL DEFAULT ''")
                .forEach { db.execSQL("ALTER TABLE sent ADD COLUMN $it") }
            indexSentIds(db)
            createUsage(db)
        }
        if (oldVersion < 3 && newVersion >= 3) createSearchUsage(db)
        if (oldVersion < 4 && newVersion >= 4) createRecentImages(db)
    }
    private fun indexSentIds(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX sent_local_id ON sent(account,talker,local_id)")
        db.execSQL("CREATE INDEX sent_server_id ON sent(account,talker,server_id)")
    }
    private fun createUsage(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE usage (id INTEGER PRIMARY KEY AUTOINCREMENT, account TEXT NOT NULL, config_id TEXT NOT NULL, provider TEXT NOT NULL, model TEXT NOT NULL, purpose TEXT NOT NULL, started INTEGER NOT NULL, input INTEGER, output INTEGER, cached INTEGER, search INTEGER, fetch INTEGER, image_search INTEGER, status TEXT NOT NULL)")
        db.execSQL("CREATE INDEX usage_account_time ON usage(account,started)")
    }
    private fun createSearchUsage(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE search_usage (id INTEGER PRIMARY KEY AUTOINCREMENT, account TEXT NOT NULL, started INTEGER NOT NULL, credits INTEGER, status TEXT NOT NULL)")
        db.execSQL("CREATE INDEX search_usage_account_time ON search_usage(account,started)")
    }
    private fun createRecentImages(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE recent_images (account TEXT NOT NULL, talker TEXT NOT NULL, participant TEXT NOT NULL, msg_id INTEGER NOT NULL, server_id INTEGER NOT NULL, image_time INTEGER NOT NULL, last_used INTEGER NOT NULL, match_hint TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE INDEX recent_images_lookup ON recent_images(account,talker,participant,last_used)")
    }

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
            db.delete("recent_images", "account=? AND talker=?", arrayOf(account, talker))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun rememberSent(account: String, talker: String, text: String, time: Long, source: String = "AI回复",
                                   body: String = "", configId: String = "", model: String = "", metadata: String = "") {
        val db = writableDatabase
        db.execSQL("INSERT INTO sent(account,talker,fingerprint,submitted_at,source,body,config_id,model,metadata) VALUES (?,?,?,?,?,?,?,?,?)",
            arrayOf<Any>(account, talker, fingerprint(text), time, source, body, configId, model, metadata))
    }
    @Synchronized fun sent(account: String, message: WeChatMessage, text: String): AiSent? {
        if (!message.isSend()) return null
        val db = writableDatabase
        val columns = "rowid,source,body,config_id,model,metadata"
        fun read(c: android.database.Cursor): AiSent = AiSent(c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5))
        if (message.msgId > 0 || message.msgSvrId > 0) {
            db.rawQuery("SELECT $columns FROM sent WHERE account=? AND talker=? AND ((local_id>0 AND local_id=?) OR (server_id>0 AND server_id=?)) LIMIT 1",
                arrayOf(account, message.talker, message.msgId.toString(), message.msgSvrId.toString())).use { if (it.moveToFirst()) return read(it) }
        }
        // Fingerprints only bridge the short interval before WeChat assigns a real message ID.
        if (message.msgId <= 0 && message.msgSvrId <= 0) return null
        val time = message.createTime
        db.rawQuery("SELECT $columns FROM sent WHERE account=? AND talker=? AND local_id=0 AND server_id=0 AND fingerprint=? AND submitted_at BETWEEN ? AND ? LIMIT 2",
            arrayOf(account, message.talker, fingerprint(text), (time - 10_000).toString(), (time + 10_000).toString())).use { c ->
            if (!c.moveToFirst()) return null
            val row = c.getLong(0); val sent = read(c)
            if (c.moveToNext()) return null
            db.execSQL("UPDATE sent SET local_id=?,server_id=? WHERE rowid=?", arrayOf<Any>(message.msgId, message.msgSvrId, row))
            return sent
        }
    }
    @Synchronized fun media(account: String, talker: String, id: Long): String? {
        readableDatabase.rawQuery("SELECT description FROM media WHERE account=? AND talker=? AND msg_id=?", arrayOf(account, talker, id.toString())).use {
            return if (it.moveToFirst()) AiChatOutput.visibleAnswer(it.getString(0)) else null
        }
    }
    @Synchronized fun saveMedia(account: String, talker: String, id: Long, description: String) {
        if (id <= 0 || description.isBlank()) return
        val values = ContentValues().apply { put("account", account); put("talker", talker); put("msg_id", id); put("description", description) }
        writableDatabase.insertWithOnConflict("media", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
    @Synchronized fun recentImages(account: String, talker: String, participant: String): List<AiRecentImage> {
        if (account.isBlank() || talker.isBlank() || participant.isBlank()) return emptyList()
        val result = mutableListOf<AiRecentImage>()
        readableDatabase.rawQuery("SELECT msg_id,server_id,image_time,last_used,match_hint FROM recent_images WHERE account=? AND talker=? AND participant=? ORDER BY last_used DESC LIMIT ?",
            arrayOf(account, talker, participant, AiImageContext.MAX_SAVED.toString())).use { c ->
            while (c.moveToNext()) result += AiRecentImage(c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getString(4))
        }
        return result
    }
    @Synchronized fun rememberImage(account: String, talker: String, participant: String,
                                    image: WeChatMessage, lastUsed: Long, reply: String) {
        if (account.isBlank() || talker.isBlank() || participant.isBlank() || image.talker != talker ||
            !image.isImage() || (image.msgId <= 0 && image.msgSvrId <= 0) || image.createTime <= 0 || lastUsed < image.createTime) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val args = arrayOf(account, talker, participant, image.msgId.toString(), image.msgSvrId.toString())
            val predicate = "account=? AND talker=? AND participant=? AND ((msg_id>0 AND msg_id=?) OR (server_id>0 AND server_id=?))"
            var prior = ""
            db.rawQuery("SELECT match_hint FROM recent_images WHERE $predicate LIMIT 1", args).use { if (it.moveToFirst()) prior = it.getString(0).orEmpty() }
            db.delete("recent_images", predicate, args)
            val hint = listOf(prior, reply.trim().take(200)).filter { it.isNotBlank() }.joinToString("\n").takeLast(400)
            val values = ContentValues().apply {
                put("account", account); put("talker", talker); put("participant", participant)
                put("msg_id", image.msgId); put("server_id", image.msgSvrId); put("image_time", image.createTime)
                put("last_used", lastUsed); put("match_hint", hint)
            }
            db.insertOrThrow("recent_images", null, values)
            db.execSQL("DELETE FROM recent_images WHERE account=? AND talker=? AND participant=? AND rowid NOT IN (SELECT rowid FROM recent_images WHERE account=? AND talker=? AND participant=? ORDER BY last_used DESC LIMIT ?)",
                arrayOf<Any>(account, talker, participant, account, talker, participant, AiImageContext.MAX_SAVED))
            db.execSQL("DELETE FROM recent_images WHERE account=? AND image_time<?", arrayOf<Any>(account, lastUsed - AiImageContext.MAX_AGE_MS))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun recordUsage(account: String, config: AiChatConfig, purpose: AiPurpose, started: Long, usage: AiUsage?) {
        val values = ContentValues().apply {
            put("account", account); put("config_id", config.id); put("provider", config.provider.name)
            put("model", config.model); put("purpose", purpose.name); put("started", started)
            usage?.input?.let { put("input", it) }; usage?.output?.let { put("output", it) }; usage?.cached?.let { put("cached", it) }
            usage?.search?.let { put("search", it) }; usage?.fetch?.let { put("fetch", it) }; usage?.imageSearch?.let { put("image_search", it) }
            put("status", if (usage == null || (usage.input == null && usage.output == null)) "用量未知" else "已返回")
        }
        writableDatabase.insertOrThrow("usage", null, values)
    }
    @Synchronized fun recordSearchUsage(account: String, started: Long, credits: Long?, status: String) {
        val values = ContentValues().apply {
            put("account", account); put("started", started); credits?.let { put("credits", it) }; put("status", status)
        }
        writableDatabase.insertOrThrow("search_usage", null, values)
    }
    @Synchronized fun searchUsage(account: String): List<AiSearchUsage> {
        val rows = mutableListOf<AiSearchUsage>()
        readableDatabase.rawQuery("SELECT account,started,credits,status FROM search_usage WHERE account=? ORDER BY started DESC", arrayOf(account)).use { c ->
            while (c.moveToNext()) rows += AiSearchUsage(c.getString(0), c.getLong(1), if (c.isNull(2)) null else c.getLong(2), c.getString(3))
        }
        return rows
    }
    @Synchronized fun usage(account: String, since: Long = 0): List<AiUsageRecord> {
        val rows = mutableListOf<AiUsageRecord>()
        readableDatabase.rawQuery("SELECT account,config_id,provider,model,purpose,started,input,output,cached,search,fetch,image_search,status FROM usage WHERE account=? AND started>=? ORDER BY started DESC",
            arrayOf(account, since.toString())).use { c ->
            fun number(index: Int): Long? = if (c.isNull(index)) null else c.getLong(index)
            while (c.moveToNext()) {
                val counts = AiUsage(number(6), number(7), number(8), number(9), number(10), number(11))
                rows += AiUsageRecord(c.getString(0), c.getString(1), runCatching { AiProvider.valueOf(c.getString(2)) }.getOrDefault(AiProvider.CUSTOM),
                    c.getString(3), runCatching { AiPurpose.valueOf(c.getString(4)) }.getOrDefault(AiPurpose.CHAT), c.getLong(5), counts, c.getString(12))
            }
        }
        return rows
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
