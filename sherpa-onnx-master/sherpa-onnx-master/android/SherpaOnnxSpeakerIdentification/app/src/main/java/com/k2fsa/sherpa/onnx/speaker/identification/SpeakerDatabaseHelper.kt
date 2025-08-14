package com.k2fsa.sherpa.onnx.speaker.identification

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream

class SpeakerDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "speakers.db"
        private const val DATABASE_VERSION = 1
        const val TABLE_SPEAKERS = "speakers"
        const val COLUMN_ID = "_id"
        const val COLUMN_NAME = "name"
        const val COLUMN_EMBEDDING = "embedding"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_SPEAKERS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT UNIQUE NOT NULL,
                $COLUMN_EMBEDDING BLOB NOT NULL
            );
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SPEAKERS")
        onCreate(db)
    }

    fun addSpeaker(name: String, embeddings: Array<FloatArray>): Boolean {
        val db = writableDatabase
        val contentValues = ContentValues().apply {
            put(COLUMN_NAME, name)
            put(COLUMN_EMBEDDING, serializeEmbeddings(embeddings))
        }

        return try {
            db.insert(TABLE_SPEAKERS, null, contentValues) != -1L
        } catch (e: Exception) {
            Log.e("Database", "Error adding speaker: ${e.message}")
            false
        } finally {
            db.close()
        }
    }

    fun removeSpeaker(name: String): Boolean {
        val db = writableDatabase
        return try {
            db.delete(TABLE_SPEAKERS, "$COLUMN_NAME = ?", arrayOf(name)) > 0
        } finally {
            db.close()
        }
    }

    fun getAllSpeakers(): List<Pair<String, Array<FloatArray>>> {
        val speakers = mutableListOf<Pair<String, Array<FloatArray>>>()
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_SPEAKERS,
            arrayOf(COLUMN_NAME, COLUMN_EMBEDDING),
            null, null, null, null, null
        )

        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow(COLUMN_NAME))
                val embeddingBlob = it.getBlob(it.getColumnIndexOrThrow(COLUMN_EMBEDDING))
                val embeddings = deserializeEmbeddings(embeddingBlob)
                speakers.add(Pair(name, embeddings))
            }
        }
        db.close()
        return speakers
    }

    private fun serializeEmbeddings(embeddings: Array<FloatArray>): ByteArray {
        ByteArrayOutputStream().use { byteStream ->
            ObjectOutputStream(byteStream).use { objectStream ->
                objectStream.writeObject(embeddings)
                return byteStream.toByteArray()
            }
        }
    }

    private fun deserializeEmbeddings(blob: ByteArray): Array<FloatArray> {
        ByteArrayInputStream(blob).use { byteStream ->
            ObjectInputStream(byteStream).use { objectStream ->
                return objectStream.readObject() as Array<FloatArray>
            }
        }
    }
}