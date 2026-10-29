package com.tencent.wcdb.database;

import android.content.ContentValues;

/** Class-name fixture for the real production WCDB fallback hook path. */
public class SQLiteDatabase {
    public long insert(String table, String nullColumnHack, ContentValues values) { return 1L; }
    public int update(String table, ContentValues values, String where, String[] args) { return 1; }
    public int delete(String table, String where, String[] args) { return 1; }
}
