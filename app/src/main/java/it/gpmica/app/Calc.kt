package it.gpmica.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Minuti oltre l'orario di uscita entro cui vale l'orario ufficiale (nessuno straordinario). */
const val TOL_OUT = 15

/** Lo straordinario giornaliero oltre la tolleranza si conta a scatti di mezz'ora. */
const val OT_STEP = 30

object Fmt {
    val DAYS = arrayOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")
    val DAYS3 = arrayOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
    val MONTHS = arrayOf(
        "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
        "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
    )

    fun hhmm(m: Int): String = String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60)
    fun hm(m: Int): String = String.format(Locale.ROOT, "%d:%02d", m / 60, m % 60)
    fun eur(d: Double): String = String.format(Locale.ITALY, "%,.2f €", d)
    fun dec(m: Int): String = when {
        m == 0 -> ""
        m % 60 == 0 -> (m / 60).toString()
        else -> String.format(Locale.ITALY, "%.1f", m / 60.0)
    }

    fun dateLong(d: LocalDate) = "${DAYS[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}"
    fun month(ym: YearMonth) = "${MONTHS[ym.monthValue - 1]} ${ym.year}"
    fun dateShort(d: LocalDate) = String.format(Locale.ROOT, "%02d/%02d/%d", d.dayOfMonth, d.monthValue, d.year)
    fun num(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
    fun fileSafe(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

fun Sched.breaksText(): String {
    val on = breaks.filter { it.on }
    return if (on.isEmpty()) "nessuna pausa" else "pause " + on.joinToString(", ") { "${Fmt.hhmm(it.s)}–${Fmt.hhmm(it.e)}" }
}

fun validBreaks(l: List<Brk>) = l.all { !it.on || it.e > it.s }

data class DayRes(
    val date: LocalDate,
    /** "" vuoto, "W" lavorato (timbrature), "H" ore corrette a mano, "A" assenza, "F" ferie, "M" malattia */
    val kind: String,
    val ordMin: Int,
    val extraMin: Int,
    val lateMin: Int,
    val earlyMin: Int,
    val tin: Int?,
    val tout: Int?
) {
    /** minuti retribuiti (le ferie valgono 8 ore) */
    val paidMin get() = ordMin + extraMin

    /** minuti effettivamente lavorati (ferie escluse) */
    val workedMin get() = if (kind == "F") 0 else ordMin + extraMin
    val isWeekend get() = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
}

data class MonthRes(
    val emp: Employee,
    val ym: YearMonth,
    val days: List<DayRes>,
    val workedMin: Int,
    val absences: Int,
    val ferie: Int,
    val malattia: Int,
    val lateMin: Int,
    val earlyMin: Int,
    val otMin: Int,
    val regMin: Int,
    val basePay: Double,
    val otPay: Double,
    val bonus: Double,
    val bonusNote: String,
    val total: Double
)

object Calc {
    fun day(date: LocalDate, today: LocalDate, p: Punch?, o: DayOverride?, s: Sched): DayRes {
        val we = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        if (we) return DayRes(date, "", 0, 0, 0, 0, null, null)
        if (o != null) {
            return when (o.kind) {
                "H" -> DayRes(date, "H", (o.hours * 60).roundToInt(), (o.ot * 60).roundToInt(), 0, 0, p?.tin, p?.tout)
                "F" -> DayRes(date, "F", 480, 0, 0, 0, p?.tin, p?.tout)
                else -> DayRes(date, o.kind, 0, 0, 0, 0, p?.tin, p?.tout)
            }
        }
        val tin = p?.tin
        val tout = p?.tout
        if (tin != null && tout != null) {
            val effIn = max(tin, s.start)
            val late = max(0, tin - s.start)
            var effOut = tout
            var early = 0
            var extra = 0
            if (tout < s.end) {
                early = s.end - tout
            } else {
                val over = tout - s.end
                effOut = s.end
                if (over > TOL_OUT) extra = (over / OT_STEP) * OT_STEP
            }
            val span = effOut - effIn
            // si sottraggono solo le pause (abilitate) che cadono davvero dentro la fascia lavorata
            var ord = if (span <= 0) 0 else span
            if (span > 0) {
                for (b in s.breaks) {
                    if (b.on) ord -= max(0, min(b.e, effOut) - max(b.s, effIn))
                }
            }
            if (ord < 0) ord = 0
            return DayRes(date, "W", ord, extra, late, early, tin, tout)
        }
        if (date.isBefore(today)) return DayRes(date, "A", 0, 0, 0, 0, tin, tout)
        return DayRes(date, "", 0, 0, 0, 0, tin, tout)
    }

    fun month(e: Employee, ym: YearMonth, today: LocalDate = LocalDate.now()): MonthRes {
        val (from, to) = monthRange(ym)
        val ps = Db.punches(e.id, from, to)
        val os = Db.overrides(e.id, from, to)
        val ss = Db.schedules(from, to)
        val def = Cfg.defSched()
        val days = (1..ym.lengthOfMonth()).map { d ->
            val dt = ym.atDay(d)
            val k = dt.toString()
            day(dt, today, ps[k], os[k], ss[k] ?: def)
        }
        // straordinario: ore oltre quelle di contratto, calcolato per settimana (lun-dom)
        val contract = (e.contractHours * 60).roundToInt()
        var reg = 0
        var ot = 0
        days.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.values.forEach { w ->
            val tot = w.sumOf { it.paidMin }
            reg += min(tot, contract)
            ot += max(0, tot - contract)
        }
        val base = reg / 60.0 * e.rate
        val otPay = ot / 60.0 * e.rate * (1 + Cfg.otPct / 100.0)
        val b = Db.bonus(e.id, ym.toString())
        val total = Math.round((base + otPay + b.amount) * 100) / 100.0
        return MonthRes(
            e, ym, days,
            workedMin = days.sumOf { it.workedMin },
            absences = days.count { it.kind == "A" },
            ferie = days.count { it.kind == "F" },
            malattia = days.count { it.kind == "M" },
            lateMin = days.sumOf { it.lateMin },
            earlyMin = days.sumOf { it.earlyMin },
            otMin = ot, regMin = reg,
            basePay = base, otPay = otPay,
            bonus = b.amount, bonusNote = b.note, total = total
        )
    }
}

data class PunchResult(
    val ok: Boolean,
    val isIn: Boolean,
    val name: String,
    val time: String,
    val late: Int,
    val early: Int,
    val dayMin: Int,
    val monthMin: Int,
    val msg: String
)

object Kiosk {
    private fun err(m: String) = PunchResult(false, false, "", "", 0, 0, 0, 0, m)

    fun punch(codeRaw: String): PunchResult {
        val code = codeRaw.trim().uppercase()
        val e = Db.employeeByCode(code)
        if (e == null || !e.active) return err("Badge non riconosciuto")
        val now = LocalDateTime.now()
        val date = now.toLocalDate()
        val m = now.hour * 60 + now.minute
        val p = Db.punchFor(e.id, date.toString())
        val s = Db.schedule(date.toString()) ?: Cfg.defSched()
        val name = "${e.name} ${e.surname}"
        val ym = YearMonth.from(date)
        if (p == null) {
            Db.insertIn(e.id, date.toString(), m)
            val mr = Calc.month(e, ym)
            return PunchResult(true, true, name, Fmt.hhmm(m), max(0, m - s.start), 0, 0, mr.workedMin, "")
        }
        if (p.tout == null) {
            if (m - p.tin < 1) return err("Entrata già registrata")
            Db.setOut(p.id, m)
            val mr = Calc.month(e, ym)
            val d = mr.days[date.dayOfMonth - 1]
            return PunchResult(true, false, name, Fmt.hhmm(m), 0, d.earlyMin, d.workedMin, mr.workedMin, "")
        }
        return err("Uscita già registrata oggi")
    }
}
