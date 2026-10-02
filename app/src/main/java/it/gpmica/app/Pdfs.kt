package it.gpmica.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max

class Pdf(val w: Int, val h: Int) {
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var n = 0
    val m = 36f
    var y = 0f
    lateinit var c: Canvas

    init {
        newPage()
    }

    fun newPage() {
        page?.let { doc.finishPage(it) }
        n++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(w, h, n).create())
        page = p
        c = p.canvas
        y = m
    }

    fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.color = color
        }

    fun ensure(space: Float) {
        if (y + space > h - m) newPage()
    }

    fun line(s: String, size: Float = 10f, bold: Boolean = false) {
        ensure(size + 8)
        c.drawText(s, m, y + size, paint(size, bold))
        y += size + 6
    }

    fun title(a: String, b: String) {
        c.drawText(a, m, y + 16, paint(16f, true))
        y += 24
        c.drawText(b, m, y + 10, paint(10f, false, Color.GRAY))
        y += 22
    }

    fun table(
        headers: List<String>,
        widths: List<Float>,
        rows: List<List<String>>,
        leftCols: Int = 1,
        shade: Set<Int> = emptySet(),
        rowH: Float = 18f,
        fs: Float = 9f
    ) {
        val hp = paint(fs, true)
        val bp = paint(fs)
        val fill = Paint().apply { color = Color.rgb(225, 225, 225) }
        val shd = Paint().apply { color = Color.rgb(242, 242, 242) }
        val ln = Paint().apply { color = Color.rgb(170, 170, 170); strokeWidth = 0.5f }
        val total = widths.sum()

        fun cells(r: List<String>, p: Paint) {
            var x = m
            r.forEachIndexed { i, s ->
                val wd = widths[i]
                val cnt = p.breakText(s, true, wd - 6, null)
                val t = s.substring(0, cnt)
                if (i < leftCols) {
                    p.textAlign = Paint.Align.LEFT
                    c.drawText(t, x + 3, y + rowH / 2 + fs * 0.35f, p)
                } else {
                    p.textAlign = Paint.Align.CENTER
                    c.drawText(t, x + wd / 2, y + rowH / 2 + fs * 0.35f, p)
                }
                x += wd
            }
        }

        fun head() {
            c.drawRect(m, y, m + total, y + rowH, fill)
            cells(headers, hp)
            y += rowH
        }

        ensure(rowH * 2)
        head()
        rows.forEachIndexed { i, r ->
            if (y + rowH > h - m) {
                newPage()
                head()
            }
            if (i in shade) c.drawRect(m, y, m + total, y + rowH, shd)
            cells(r, bp)
            c.drawLine(m, y + rowH, m + total, y + rowH, ln)
            y += rowH
        }
    }

    fun finish(): ByteArray {
        page?.let { doc.finishPage(it) }
        val b = ByteArrayOutputStream()
        doc.writeTo(b)
        doc.close()
        return b.toByteArray()
    }
}

object Badge {
    val LEVELS = intArrayOf(
        Color.parseColor("#FFFFFF"), // 1 bianco
        Color.parseColor("#2E9E4F"), // 2 verde
        Color.parseColor("#1F6FEB"), // 3 blu
        Color.parseColor("#D32F2F"), // 4 rosso
        Color.parseColor("#7E3FD1"), // 5 viola
        Color.parseColor("#000000")  // 6 nero
    )

    fun levelColor(level: Int): Int = LEVELS[(level - 1).coerceIn(0, 5)]

    fun qr(text: String, size: Int): Bitmap {
        val hints = hashMapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 1)
        val bm = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val w = bm.width
        val h = bm.height
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) px[y * w + x] = if (bm.get(x, y)) Color.BLACK else Color.WHITE
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    fun bitmap(e: Employee): Bitmap {
        val w = 800
        val h = 1100
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = Color.parseColor("#222222")
        c.drawRoundRect(RectF(10f, 10f, w - 10f, h - 10f), 40f, 40f, p)
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER

        fun centered(t: String, size: Float, bold: Boolean, color: Int, yy: Float) {
            p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            p.color = color
            p.textSize = size
            while (p.measureText(t) > w - 80 && p.textSize > 20f) p.textSize -= 2f
            c.drawText(t, w / 2f, yy, p)
        }

        centered(Cfg.company, 46f, true, Color.parseColor("#333333"), 100f)
        val q = qr(e.code, 600)
        c.drawBitmap(q, (w - q.width) / 2f, 150f, null)
        centered("${e.name} ${e.surname}", 58f, true, Color.BLACK, 850f)
        centered(e.role, 40f, false, Color.DKGRAY, 910f)
        val sq = RectF(w / 2f - 45f, 960f, w / 2f + 45f, 1050f)
        p.style = Paint.Style.FILL
        p.color = levelColor(e.level)
        c.drawRect(sq, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = Color.BLACK
        c.drawRect(sq, p)
        return b
    }

    fun jpeg(e: Employee): ByteArray {
        val bos = ByteArrayOutputStream()
        bitmap(e).compress(Bitmap.CompressFormat.JPEG, 95, bos)
        return bos.toByteArray()
    }
}

object Pdfs {
    fun badges(list: List<Employee>): ByteArray {
        val pdf = Pdf(595, 842)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        list.forEachIndexed { i, e ->
            val k = i % 4
            if (i > 0 && k == 0) pdf.newPage()
            val x = 23f + (k % 2) * 297f
            val y = 40f + (k / 2) * 390f
            pdf.c.drawBitmap(Badge.bitmap(e), null, RectF(x, y, x + 250f, y + 344f), paint)
        }
        return pdf.finish()
    }

    fun allReport(ym: YearMonth): ByteArray {
        val pdf = Pdf(842, 595)
        val rs = Db.employees().filter { it.active }.map { Calc.month(it, ym) }
        pdf.title("${Cfg.company} - Report presenze ${Fmt.month(ym)}", "Generato il ${Fmt.dateShort(LocalDate.now())}")
        val headers = listOf(
            "Cognome", "Nome", "Ore Totali", "Ritardo", "Anticipo", "Ore Ord.", "Straord.",
            "Ferie gg", "Assenze gg", "Malattia gg", "Bonus", "Malus", "Totale €"
        )
        val widths = listOf(80f, 70f, 52f, 50f, 52f, 52f, 52f, 46f, 50f, 52f, 60f, 60f, 80f)
        val rows = rs.map { r ->
            listOf(
                r.emp.surname, r.emp.name, Fmt.hm(r.workedMin), Fmt.hm(r.lateMin), Fmt.hm(r.earlyMin),
                Fmt.hm(max(0, r.workedMin - r.otMin)), Fmt.hm(r.otMin),
                r.ferie.toString(), r.absences.toString(), r.malattia.toString(),
                if (r.bonus > 0) Fmt.eur(r.bonus) else "-",
                if (r.bonus < 0) Fmt.eur(-r.bonus) else "-",
                Fmt.eur(r.total)
            )
        }
        pdf.table(headers, widths, rows, leftCols = 2)
        pdf.y += 10
        pdf.line("Totale complessivo: ${Fmt.eur(rs.sumOf { it.total })}", 12f, true)
        pdf.line("Ore Totali = ore lavorate (ferie escluse). Straordinario = ore oltre il contratto settimanale, maggiorato del ${Cfg.otPct}%. Le ferie sono pagate 8 ore, malattia e assenze non pagate.", 8f)
        return pdf.finish()
    }

    fun single(e: Employee, ym: YearMonth): ByteArray {
        val r = Calc.month(e, ym)
        val pdf = Pdf(595, 842)
        pdf.title("${Cfg.company} - Report ${e.name} ${e.surname}", "${Fmt.month(ym)} · ${e.role} · Livello ${e.level} · ${e.contractHours}h/sett · ${Fmt.eur(e.rate)}/h")
        val rows = ArrayList<List<String>>()
        val shade = HashSet<Int>()
        r.days.forEachIndexed { i, d ->
            if (d.isWeekend) shade.add(i)
            rows.add(
                listOf(
                    "${String.format("%02d", d.date.dayOfMonth)} ${Fmt.DAYS3[d.date.dayOfWeek.value - 1]}",
                    d.tin?.let { Fmt.hhmm(it) } ?: "",
                    d.tout?.let { Fmt.hhmm(it) } ?: "",
                    if (d.kind == "W" || d.kind == "H") Fmt.hm(d.ordMin) else "",
                    if (d.extraMin > 0) Fmt.hm(d.extraMin) else "",
                    if (d.lateMin > 0) "${d.lateMin} min" else "",
                    if (d.earlyMin > 0) "${d.earlyMin} min" else "",
                    if (d.kind == "H") "Corretto" else if (d.kind == "W") "" else d.kind
                )
            )
        }
        pdf.table(
            listOf("Giorno", "Entrata", "Uscita", "Ore ord.", "Extra", "Ritardo", "Anticipo", "Stato"),
            listOf(70f, 58f, 58f, 62f, 55f, 62f, 62f, 56f), rows, 1, shade, 16f, 8.5f
        )
        pdf.y += 10
        pdf.ensure(170f)
        pdf.line("Totale ore lavorate: ${Fmt.hm(r.workedMin)}", 10f, true)
        pdf.line("Giorni di assenza: ${r.absences}   Ferie: ${r.ferie}   Malattia: ${r.malattia}")
        pdf.line("Totale ritardi: ${Fmt.hm(r.lateMin)}   Totale uscite anticipate: ${Fmt.hm(r.earlyMin)}")
        pdf.line("Ore di straordinario (oltre contratto): ${Fmt.hm(r.otMin)}")
        pdf.line("Paga ore ordinarie (${Fmt.hm(r.regMin)}): ${Fmt.eur(r.basePay)}")
        pdf.line("Paga straordinario (+${Cfg.otPct}%): ${Fmt.eur(r.otPay)}")
        pdf.line("${if (r.bonus < 0) "Malus" else "Bonus"}: ${Fmt.eur(r.bonus)}${if (r.bonusNote.isNotBlank()) "  (${r.bonusNote})" else ""}")
        pdf.y += 6
        pdf.line("PAGA MENSILE: ${Fmt.eur(r.total)}", 14f, true)
        return pdf.finish()
    }

    fun grid(e: Employee, ym: YearMonth): ByteArray {
        val r = Calc.month(e, ym)
        val pdf = Pdf(595, 842)
        pdf.title("${Cfg.company} - Griglia presenze ${e.name} ${e.surname}", Fmt.month(ym))
        val rows = ArrayList<List<String>>()
        val shade = HashSet<Int>()
        r.days.forEachIndexed { i, d ->
            val lbl = "${String.format("%02d", d.date.dayOfMonth)} ${Fmt.DAYS3[d.date.dayOfWeek.value - 1]}"
            if (d.isWeekend) {
                shade.add(i)
                rows.add(listOf(lbl, "", "", "", "", ""))
            } else {
                val worked = d.kind == "W" || d.kind == "H"
                rows.add(
                    listOf(
                        lbl,
                        if (worked) Fmt.dec(d.ordMin) else "",
                        if (worked) Fmt.dec(d.extraMin) else "",
                        if (d.kind == "A") "A" else "",
                        if (d.kind == "F") "F" else "",
                        if (d.kind == "M") "M" else ""
                    )
                )
            }
        }
        pdf.table(
            listOf("Giorno", "Ore lavorate", "Straordinario", "A", "F", "M"),
            listOf(90f, 100f, 100f, 60f, 60f, 60f), rows, 1, shade, 20f, 10f
        )
        pdf.y += 10
        pdf.line("A = assenza (mancata timbratura)   F = ferie (pagate 8 ore)   M = malattia (non pagata)", 8f)
        pdf.line("Totali: ore lavorate ${Fmt.hm(r.workedMin)} · assenze ${r.absences} · ferie ${r.ferie} · malattia ${r.malattia}", 10f, true)
        return pdf.finish()
    }
}
