package com.experimento.wifiradar

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import kotlin.math.pow

/** Punto de acceso con su ubicación estimada a partir de todas las observaciones. */
data class Ap(
    val bssid: String,
    val ssid: String,
    val caps: String,
    val freq: Int,
    val bestRssi: Int,
    val lat: Double,
    val lon: Double,
    val count: Int,
    val firstTs: Long,
    val lastTs: Long,
)

/**
 * Guarda cada observación (red + posición GPS) y mantiene por cada BSSID un
 * centroide ponderado por potencia: cuanto más fuerte se oyó la red en un
 * punto, más "tira" de la ubicación estimada del router hacia ese punto.
 */
class Db(ctx: Context) : SQLiteOpenHelper(ctx, "wifiradar.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE obs(id INTEGER PRIMARY KEY, bssid TEXT, ssid TEXT, caps TEXT, rssi INTEGER, " +
                "freq INTEGER, lat REAL, lon REAL, alt REAL, acc REAL, ts INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE ap(bssid TEXT PRIMARY KEY, ssid TEXT, caps TEXT, freq INTEGER, best_rssi INTEGER, " +
                "wsum REAL, wlat REAL, wlon REAL, n INTEGER, first_ts INTEGER, last_ts INTEGER)"
        )
        db.execSQL("CREATE TABLE track(ts INTEGER, lat REAL, lon REAL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun record(nets: List<Net>, loc: Location) {
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insert("track", null, ContentValues().apply {
                put("ts", now); put("lat", loc.latitude); put("lon", loc.longitude)
            })
            for (n in nets) {
                db.insert("obs", null, ContentValues().apply {
                    put("bssid", n.bssid); put("ssid", n.ssid); put("caps", n.caps)
                    put("rssi", n.rssi); put("freq", n.freq)
                    put("lat", loc.latitude); put("lon", loc.longitude)
                    put("alt", loc.altitude); put("acc", loc.accuracy.toDouble()); put("ts", now)
                })
                // Peso lineal en mW: -50 dBm pesa 100 veces más que -70 dBm.
                val w = 10.0.pow(n.rssi / 10.0)
                val updated = db.compileStatement(
                    "UPDATE ap SET ssid=?, caps=?, freq=?, best_rssi=MAX(best_rssi, ?), wsum=wsum+?, " +
                        "wlat=wlat+?, wlon=wlon+?, n=n+1, last_ts=? WHERE bssid=?"
                ).use { st ->
                    st.bindString(1, n.ssid); st.bindString(2, n.caps); st.bindLong(3, n.freq.toLong())
                    st.bindLong(4, n.rssi.toLong()); st.bindDouble(5, w)
                    st.bindDouble(6, w * loc.latitude); st.bindDouble(7, w * loc.longitude)
                    st.bindLong(8, now); st.bindString(9, n.bssid)
                    st.executeUpdateDelete()
                }
                if (updated == 0) {
                    db.insert("ap", null, ContentValues().apply {
                        put("bssid", n.bssid); put("ssid", n.ssid); put("caps", n.caps); put("freq", n.freq)
                        put("best_rssi", n.rssi); put("wsum", w)
                        put("wlat", w * loc.latitude); put("wlon", w * loc.longitude)
                        put("n", 1); put("first_ts", now); put("last_ts", now)
                    })
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun aps(): List<Ap> {
        val out = ArrayList<Ap>()
        readableDatabase.rawQuery(
            "SELECT bssid, ssid, caps, freq, best_rssi, wlat/wsum, wlon/wsum, n, first_ts, last_ts " +
                "FROM ap WHERE wsum > 0", null
        ).use { c ->
            while (c.moveToNext()) {
                out += Ap(
                    c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getInt(3), c.getInt(4),
                    c.getDouble(5), c.getDouble(6), c.getInt(7), c.getLong(8), c.getLong(9),
                )
            }
        }
        return out
    }

    fun track(limit: Int = 5000): List<DoubleArray> {
        val out = ArrayList<DoubleArray>()
        readableDatabase.rawQuery(
            "SELECT lat, lon FROM (SELECT ts, lat, lon FROM track ORDER BY ts DESC LIMIT $limit) ORDER BY ts", null
        ).use { c -> while (c.moveToNext()) out += doubleArrayOf(c.getDouble(0), c.getDouble(1)) }
        return out
    }

    fun count(table: String): Long =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { c -> c.moveToFirst(); c.getLong(0) }

    /** Recorre las observaciones para exportarlas (formato CSV de WiGLE). */
    fun forEachObs(block: (bssid: String, ssid: String, caps: String, rssi: Int, freq: Int,
                           lat: Double, lon: Double, alt: Double, acc: Double, ts: Long) -> Unit) {
        readableDatabase.rawQuery(
            "SELECT bssid, ssid, caps, rssi, freq, lat, lon, alt, acc, ts FROM obs ORDER BY ts", null
        ).use { c ->
            while (c.moveToNext()) {
                block(
                    c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getInt(3), c.getInt(4),
                    c.getDouble(5), c.getDouble(6), c.getDouble(7), c.getDouble(8), c.getLong(9),
                )
            }
        }
    }

    fun clear() {
        writableDatabase.apply {
            execSQL("DELETE FROM obs"); execSQL("DELETE FROM ap"); execSQL("DELETE FROM track")
        }
    }
}
