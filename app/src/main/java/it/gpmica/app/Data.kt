package it.gpmica.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.time.YearMonth
import kotlin.random.Random

data class Employee(
    val id: Long,
    val name: String,
    val surname: String,
    val role: String,
    val level: Int,
    val contractHours: Double,
    val rate: Double,
    val code: String,
    val active: Boolean,
    val photo: ByteArray?
)

data class Punch(val id: Long, val emp: Long, val date: String, val tin: Int, val tout: Int?)
data class DayOverride(val emp: Long, val date: String, val kind: String, val hours: Double, val ot: Double)
data class Brk(val on: Boolean, val s: Int, val e: Int)
data class Sched(val start: Int, val end: Int, val breaks: List<Brk>)

val DEFAULT_BREAKS = listOf(Brk(true, 600, 615), Brk(true, 795, 825), Brk(false, 960, 965))

object BreakCodec {
    fun enc(l: List<Brk>): String = l.joinToString(";") { "${if (it.on) 1 else 0},${it.s},${it.e}" }

    fun dec(s: String?): List<Brk>? {
        if (s.isNullOrBlank()) return null
        return try {
            val l = s.split(";").map { p ->
                val x = p.split(",")
                Brk(x[0] == "1", x[1].toInt(), x[2].toInt())
            }
            (l + DEFAULT_BREAKS.drop(l.size)).take(3)
        } catch (e: Exception) {
            null
        }
    }
}
data class BonusRec(val amount: Double, val note: String)

object Db {
    private const val NAME = "gpmica.db"
    private lateinit var appCtx: Context
    private var helper: Helper? = null

    private class Helper(c: Context) : SQLiteOpenHelper(c, NAME, null, 2) {
        override fun onCreate(d: SQLiteDatabase) {
            d.execSQL("CREATE TABLE employees(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, surname TEXT, role TEXT, level INTEGER, contract REAL, rate REAL, code TEXT UNIQUE, active INTEGER, photo BLOB)")
            d.execSQL("CREATE TABLE punches(id INTEGER PRIMARY KEY AUTOINCREMENT, emp INTEGER, date TEXT, tin INTEGER, tout INTEGER, UNIQUE(emp,date))")
            d.execSQL("CREATE TABLE overrides(emp INTEGER, date TEXT, kind TEXT, hours REAL, ot REAL, PRIMARY KEY(emp,date))")
            d.execSQL("CREATE TABLE schedules(date TEXT PRIMARY KEY, start INTEGER, endm INTEGER, pause INTEGER, breaks TEXT)")
            d.execSQL("CREATE TABLE bonus(emp INTEGER, month TEXT, amount REAL, note TEXT, PRIMARY KEY(emp,month))")
            d.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY, v TEXT)")
        }

        override fun onUpgrade(d: SQLiteDatabase, o: Int, n: Int) {
            if (o < 2) {
                try {
                    d.execSQL("ALTER TABLE schedules ADD COLUMN breaks TEXT")
                } catch (e: Exception) {
                }
            }
        }
    }

    fun init(c: Context) {
        appCtx = c.applicationContext
        if (helper == null) helper = Helper(appCtx)
    }

    private val db: SQLiteDatabase get() = helper!!.writableDatabase

    private fun <T> query(sql: String, args: Array<String> = emptyArray(), map: (Cursor) -> T): List<T> {
        val out = ArrayList<T>()
        db.rawQuery(sql, args).use { c -> while (c.moveToNext()) out.add(map(c)) }
        return out
    }

    // ---------- settings ----------
    fun setting(k: String, def: String): String =
        query("SELECT v FROM settings WHERE k=?", arrayOf(k)) { it.getString(0) }.firstOrNull() ?: def

    fun putSetting(k: String, v: String) {
        db.execSQL("INSERT OR REPLACE INTO settings(k,v) VALUES(?,?)", arrayOf<Any>(k, v))
    }

    // ---------- employees ----------
    private const val EMP = "id,name,surname,role,level,contract,rate,code,active,photo"

    private fun toEmp(c: Cursor) = Employee(
        c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "",
        c.getInt(4), c.getDouble(5), c.getDouble(6), c.getString(7) ?: "", c.getInt(8) == 1,
        if (c.isNull(9)) null else c.getBlob(9)
    )

    fun employees(): List<Employee> =
        query("SELECT $EMP FROM employees ORDER BY surname COLLATE NOCASE, name COLLATE NOCASE") { toEmp(it) }

    fun employeeByCode(code: String): Employee? =
        query("SELECT $EMP FROM employees WHERE code=?", arrayOf(code)) { toEmp(it) }.firstOrNull()

    private fun newCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        while (true) {
            val code = "GP-" + (1..6).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
            if (employeeByCode(code) == null) return code
        }
    }

    fun saveEmployee(e: Employee): Long {
        val v = ContentValues()
        v.put("name", e.name)
        v.put("surname", e.surname)
        v.put("role", e.role)
        v.put("level", e.level)
        v.put("contract", e.contractHours)
        v.put("rate", e.rate)
        v.put("active", if (e.active) 1 else 0)
        if (e.photo != null) v.put("photo", e.photo) else v.putNull("photo")
        return if (e.id == 0L) {
            v.put("code", newCode())
            db.insert("employees", null, v)
        } else {
            db.update("employees", v, "id=?", arrayOf(e.id.toString()))
            e.id
        }
    }

    fun deleteEmployee(id: Long) {
        val a = arrayOf(id.toString())
        db.delete("employees", "id=?", a)
        db.delete("punches", "emp=?", a)
        db.delete("overrides", "emp=?", a)
        db.delete("bonus", "emp=?", a)
    }

    // ---------- punches ----------
    fun punchFor(emp: Long, date: String): Punch? =
        query("SELECT id,emp,date,tin,tout FROM punches WHERE emp=? AND date=?", arrayOf(emp.toString(), date)) {
            Punch(it.getLong(0), it.getLong(1), it.getString(2), it.getInt(3), if (it.isNull(4)) null else it.getInt(4))
        }.firstOrNull()

    fun punches(emp: Long, from: String, to: String): Map<String, Punch> =
        query("SELECT id,emp,date,tin,tout FROM punches WHERE emp=? AND date>=? AND date<=?", arrayOf(emp.toString(), from, to)) {
            Punch(it.getLong(0), it.getLong(1), it.getString(2), it.getInt(3), if (it.isNull(4)) null else it.getInt(4))
        }.associateBy { it.date }

    fun insertIn(emp: Long, date: String, tin: Int) {
        db.execSQL("INSERT OR REPLACE INTO punches(emp,date,tin,tout) VALUES(?,?,?,NULL)", arrayOf<Any>(emp, date, tin))
    }

    fun setOut(id: Long, tout: Int) {
        db.execSQL("UPDATE punches SET tout=? WHERE id=?", arrayOf<Any>(tout, id))
    }

    // ---------- overrides (griglia correzioni) ----------
    fun overrides(emp: Long, from: String, to: String): Map<String, DayOverride> =
        query("SELECT emp,date,kind,hours,ot FROM overrides WHERE emp=? AND date>=? AND date<=?", arrayOf(emp.toString(), from, to)) {
            DayOverride(it.getLong(0), it.getString(1), it.getString(2), it.getDouble(3), it.getDouble(4))
        }.associateBy { it.date }

    fun setOverride(o: DayOverride) {
        db.execSQL("INSERT OR REPLACE INTO overrides(emp,date,kind,hours,ot) VALUES(?,?,?,?,?)", arrayOf<Any>(o.emp, o.date, o.kind, o.hours, o.ot))
    }

    fun clearOverride(emp: Long, date: String) {
        db.delete("overrides", "emp=? AND date=?", arrayOf(emp.toString(), date))
    }

    // ---------- schedules ----------
    fun schedule(date: String): Sched? =
        query("SELECT start,endm,breaks FROM schedules WHERE date=?", arrayOf(date)) {
            Sched(it.getInt(0), it.getInt(1), BreakCodec.dec(it.getString(2)) ?: Cfg.breaks)
        }.firstOrNull()

    fun schedules(from: String, to: String): Map<String, Sched> =
        query("SELECT date,start,endm,breaks FROM schedules WHERE date>=? AND date<=?", arrayOf(from, to)) {
            it.getString(0) to Sched(it.getInt(1), it.getInt(2), BreakCodec.dec(it.getString(3)) ?: Cfg.breaks)
        }.toMap()

    fun putSchedule(date: String, s: Sched) {
        db.execSQL("INSERT OR REPLACE INTO schedules(date,start,endm,pause,breaks) VALUES(?,?,?,?,?)", arrayOf<Any>(date, s.start, s.end, 0, BreakCodec.enc(s.breaks)))
    }

    fun deleteSchedule(date: String) {
        db.delete("schedules", "date=?", arrayOf(date))
    }

    // ---------- bonus / malus ----------
    fun bonus(emp: Long, month: String): BonusRec =
        query("SELECT amount,note FROM bonus WHERE emp=? AND month=?", arrayOf(emp.toString(), month)) {
            BonusRec(it.getDouble(0), it.getString(1) ?: "")
        }.firstOrNull() ?: BonusRec(0.0, "")

    fun putBonus(emp: Long, month: String, amount: Double, note: String) {
        db.execSQL("INSERT OR REPLACE INTO bonus(emp,month,amount,note) VALUES(?,?,?,?)", arrayOf<Any>(emp, month, amount, note))
    }

    // ---------- backup / ripristino ----------
    fun exportBytes(): ByteArray {
        helper?.close()
        helper = null
        val bytes = appCtx.getDatabasePath(NAME).readBytes()
        helper = Helper(appCtx)
        return bytes
    }

    /** Azzera completamente il database (PIN e impostazioni tornano ai valori predefiniti). */
    fun reset() {
        helper?.close()
        helper = null
        val f = appCtx.getDatabasePath(NAME)
        f.delete()
        File(f.path + "-journal").delete()
        File(f.path + "-wal").delete()
        File(f.path + "-shm").delete()
        helper = Helper(appCtx)
        helper!!.writableDatabase
    }

    fun restore(bytes: ByteArray): Boolean {
        if (bytes.size < 100 || String(bytes, 0, 15, Charsets.US_ASCII) != "SQLite format 3") return false
        helper?.close()
        helper = null
        val f = appCtx.getDatabasePath(NAME)
        f.writeBytes(bytes)
        File(f.path + "-journal").delete()
        File(f.path + "-wal").delete()
        File(f.path + "-shm").delete()
        helper = Helper(appCtx)
        return try {
            db.rawQuery("SELECT count(*) FROM employees", null).use { it.moveToFirst() }
            db.rawQuery("SELECT count(*) FROM punches", null).use { it.moveToFirst() }
            db.rawQuery("SELECT count(*) FROM settings", null).use { it.moveToFirst() }
            true
        } catch (e: Exception) {
            false
        }
    }
}

object Cfg {
    var company: String
        get() = Db.setting("company", "GP-Mica")
        set(v) = Db.putSetting("company", v)
    var pin: String
        get() = Db.setting("pin", "000000")
        set(v) = Db.putSetting("pin", v)
    var defStart: Int
        get() = Db.setting("start", "480").toIntOrNull() ?: 480
        set(v) = Db.putSetting("start", v.toString())
    var defEnd: Int
        get() = Db.setting("end", "1005").toIntOrNull() ?: 1005
        set(v) = Db.putSetting("end", v.toString())
    var breaks: List<Brk>
        get() = BreakCodec.dec(Db.setting("breaks", "")) ?: DEFAULT_BREAKS
        set(v) = Db.putSetting("breaks", BreakCodec.enc(v))
    var otPct: Double
        get() = Db.setting("ot", "10").toDoubleOrNull() ?: 10.0
        set(v) = Db.putSetting("ot", v.toString())

    fun defSched() = Sched(defStart, defEnd, breaks)
}

fun monthRange(ym: YearMonth) = ym.atDay(1).toString() to ym.atEndOfMonth().toString()
