package it.gpmica.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max

// ---------------------------------------------------------------- utilità

class Io(
    val save: (String, ByteArray) -> Unit,
    val share: (String, String, ByteArray) -> Unit,
    val ready: (String, String, ByteArray) -> Unit
)

fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

fun shareBytes(ctx: Context, name: String, mime: String, bytes: ByteArray) {
    val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
    val f = File(dir, name)
    f.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Condividi").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun scalePhoto(ctx: Context, uri: Uri): ByteArray? {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 900) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val rot = try {
            ctx.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
        if (rot != 0f) {
            val mx = Matrix().apply { postRotate(rot) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mx, true)
        }
        val r = 400f / max(bmp.width, bmp.height)
        if (r < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * r).toInt(), (bmp.height * r).toInt(), true)
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        return bos.toByteArray()
    } catch (e: Exception) {
        return null
    }
}

fun levelColor(l: Int) = Color(Badge.levelColor(l))

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TXT, unfocusedTextColor = TXT, cursorColor = AMBER,
    focusedBorderColor = AMBER, unfocusedBorderColor = BORDER,
    focusedLabelColor = AMBER, unfocusedLabelColor = MUTED
)

@Composable
fun BreaksEditor(breaks: List<Brk>, onChange: (List<Brk>) -> Unit) {
    val ctx = LocalContext.current
    Column {
        Text("Pause (non retribuite: il tempo in pausa non viene conteggiato)", color = MUTED, fontSize = 12.sp)
        breaks.forEachIndexed { i, b ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = b.on, onCheckedChange = { v -> onChange(breaks.toMutableList().also { it[i] = b.copy(on = v) }) })
                Spacer(Modifier.width(8.dp))
                Text("Pausa ${i + 1}", color = if (b.on) TXT else MUTED, modifier = Modifier.width(64.dp))
                PickerField("Dalle", Fmt.hhmm(b.s), Modifier.weight(1f)) {
                    pickTime(ctx, b.s) { t -> onChange(breaks.toMutableList().also { it[i] = b.copy(s = t) }) }
                }
                Spacer(Modifier.width(8.dp))
                PickerField("Alle", Fmt.hhmm(b.e), Modifier.weight(1f)) {
                    pickTime(ctx, b.e) { t -> onChange(breaks.toMutableList().also { it[i] = b.copy(e = t) }) }
                }
            }
        }
    }
}

@Composable
fun PickerField(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clickable { onClick() }) {
        OutlinedTextField(
            value = value, onValueChange = {}, label = { Text(label) }, enabled = false, singleLine = true,
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = TXT, disabledBorderColor = BORDER, disabledLabelColor = MUTED
            )
        )
    }
}

fun pickTime(ctx: Context, current: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPicked(h * 60 + m) }, current / 60, current % 60, true).show()
}

fun pickDate(ctx: Context, current: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(ctx, { _, y, mo, d -> onPicked(LocalDate.of(y, mo + 1, d)) }, current.year, current.monthValue - 1, current.dayOfMonth).show()
}

@Composable
fun EmpDropdown(list: List<Employee>, selId: Long?, onSel: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val sel = list.firstOrNull { it.id == selId }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, if (sel != null) AMBER else BORDER, RoundedCornerShape(12.dp))
                .clickable { open = true }.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (sel != null) "${sel.surname} ${sel.name}" else "Scegli un dipendente",
                color = if (sel != null) TXT else MUTED, modifier = Modifier.weight(1f)
            )
            Text("▾", color = MUTED)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            list.forEach { e ->
                DropdownMenuItem(text = { Text("${e.surname} ${e.name}") }, onClick = { onSel(e.id); open = false })
            }
        }
    }
}

@Composable
fun Pill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, sel: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (sel) AMBER else Color.Transparent)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (sel) ON_AMBER else MUTED)
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (sel) ON_AMBER else MUTED, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun SectionTitle(s: String) = Text(s, color = TXT, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))

// ---------------------------------------------------------------- schermata admin

@Composable
fun AdminScreen(onExit: () -> Unit) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var ver by remember { mutableIntStateOf(0) }
    var selId by remember { mutableStateOf<Long?>(null) }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var ready by remember { mutableStateOf<Triple<String, String, ByteArray>?>(null) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val b = pending
        if (uri != null && b != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(b) }
                toast(ctx, "File salvato")
            } catch (e: Exception) {
                toast(ctx, "Errore nel salvataggio: ${e.message}")
            }
        }
    }
    val io = Io(
        save = { name, bytes -> pending = bytes; saver.launch(name) },
        share = { name, mime, bytes -> shareBytes(ctx, name, mime, bytes) },
        ready = { n, m, b -> ready = Triple(n, m, b) }
    )
    val bump: () -> Unit = { ver++ }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExit) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(18.dp), tint = TXT)
                Spacer(Modifier.width(6.dp))
                Text("Timbratura", color = TXT, fontSize = 13.sp)
            }
            Text("Admin", color = TXT, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { ym = ym.minusMonths(1) }) { Icon(Icons.Outlined.ChevronLeft, "Mese precedente", tint = TXT) }
            Text(Fmt.month(ym), color = TXT, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { ym = ym.plusMonths(1) }) { Icon(Icons.Outlined.ChevronRight, "Mese successivo", tint = TXT) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Pill("Dipendenti", Icons.Outlined.Group, tab == 0) { tab = 0 }
            Pill("Orari Giornalieri", Icons.Outlined.Schedule, tab == 1) { tab = 1 }
            Pill("Griglia Correzioni", Icons.Outlined.GridView, tab == 2) { tab = 2 }
            Pill("Report", Icons.Outlined.Description, tab == 3) { tab = 3 }
            Pill("Impostazioni", Icons.Outlined.Settings, tab == 4) { tab = 4 }
        }
        HorizontalDivider(color = BORDER)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> EmployeesTab(ver, bump, io)
                1 -> ScheduleTab(ym, ver, bump)
                2 -> GridTab(ym, ver, bump, selId, { selId = it }, io)
                3 -> ReportTab(ym, ver, bump, selId, { selId = it }, io)
                else -> SettingsTab(bump)
            }
        }
    }

    ready?.let { (n, m, b) ->
        AlertDialog(
            onDismissRequest = { ready = null },
            title = { Text("File pronto") },
            text = { Text(n) },
            confirmButton = {
                Row {
                    TextButton(onClick = { io.save(n, b); ready = null }) { Text("Salva") }
                    TextButton(onClick = { io.share(n, m, b); ready = null }) { Text("Condividi") }
                }
            },
            dismissButton = { TextButton(onClick = { ready = null }) { Text("Chiudi") } }
        )
    }
}

// ---------------------------------------------------------------- dipendenti

@Composable
private fun EmployeesTab(ver: Int, bump: () -> Unit, io: Io) {
    val ctx = LocalContext.current
    val list = remember(ver) { Db.employees() }
    var edit by remember { mutableStateOf<Employee?>(null) }
    var badge by remember { mutableStateOf<Employee?>(null) }
    var del by remember { mutableStateOf<Employee?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmberButton("+  Nuovo dipendente") {
                    edit = Employee(0, "", "", "", 1, 40.0, 10.0, "", true, null)
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = {
                    io.ready("GP-Mica_badge_tutti.pdf", "application/pdf", Pdfs.badges(list.filter { it.active }))
                }, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.QrCode2, null, Modifier.size(18.dp), tint = TXT)
                    Spacer(Modifier.width(6.dp))
                    Text("Badge QR (tutti, PDF)", color = TXT)
                }
            }
        }
        items(list, key = { it.id }) { e ->
            val bmp = remember(e.photo) { e.photo?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
            Panel {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(5.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(levelColor(e.level)).border(0.5.dp, MUTED, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(12.dp))
                    if (bmp != null) {
                        Image(bmp.asImageBitmap(), null, Modifier.size(52.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(SURF2), contentAlignment = Alignment.Center) {
                            Text("${e.name.take(1)}${e.surname.take(1)}".uppercase(), color = GREEN, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${e.name} ${e.surname}", color = TXT, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${e.role} · Livello ${e.level}", color = MUTED, fontSize = 13.sp)
                        Text(
                            "${Fmt.eur(e.rate)}/h · ${e.contractHours}h/sett · ${if (e.active) "Attivo" else "Non attivo"}",
                            color = MUTED, fontSize = 12.sp
                        )
                        Text(e.code, color = MUTED, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Column {
                        IconButton(onClick = { badge = e }) { Icon(Icons.Outlined.QrCode2, "Badge", tint = TXT) }
                        IconButton(onClick = { edit = e }) { Icon(Icons.Outlined.Edit, "Modifica", tint = TXT) }
                        IconButton(onClick = { del = e }) { Icon(Icons.Outlined.Delete, "Elimina", tint = RED) }
                    }
                }
            }
        }
    }

    edit?.let { cur ->
        EmployeeDialog(cur, { edit = null }) { saved ->
            Db.saveEmployee(saved)
            edit = null
            bump()
        }
    }
    badge?.let { e ->
        val bmp = remember(e) { Badge.bitmap(e) }
        val name = "Badge_${Fmt.fileSafe(e.surname + "_" + e.name)}.jpg"
        AlertDialog(
            onDismissRequest = { badge = null },
            title = { Text("Badge ${e.name} ${e.surname}") },
            text = { Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().height(380.dp)) },
            confirmButton = {
                Row {
                    TextButton(onClick = { io.save(name, Badge.jpeg(e)) }) { Text("Salva JPEG") }
                    TextButton(onClick = { io.share(name, "image/jpeg", Badge.jpeg(e)) }) { Text("Condividi") }
                }
            },
            dismissButton = { TextButton(onClick = { badge = null }) { Text("Chiudi") } }
        )
    }
    del?.let { e ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("Eliminare ${e.name} ${e.surname}?") },
            text = { Text("Verranno cancellati anche timbrature, correzioni e bonus. Per conservare lo storico disattiva il dipendente (Modifica → Attivo).") },
            confirmButton = {
                TextButton(onClick = { Db.deleteEmployee(e.id); del = null; bump(); toast(ctx, "Dipendente eliminato") }) {
                    Text("Elimina", color = RED)
                }
            },
            dismissButton = { TextButton(onClick = { del = null }) { Text("Annulla") } }
        )
    }
}

@Composable
private fun EmployeeDialog(init: Employee, onDismiss: () -> Unit, onSave: (Employee) -> Unit) {
    val ctx = LocalContext.current
    val isNew = init.id == 0L
    var name by remember { mutableStateOf(init.name) }
    var surname by remember { mutableStateOf(init.surname) }
    var role by remember { mutableStateOf(init.role) }
    var level by remember { mutableIntStateOf(init.level) }
    var hours by remember { mutableStateOf(init.contractHours.toString().removeSuffix(".0")) }
    var rate by remember { mutableStateOf(if (isNew) "" else init.rate.toString().removeSuffix(".0")) }
    var active by remember { mutableStateOf(init.active) }
    var photo by remember { mutableStateOf(init.photo) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val b = scalePhoto(ctx, uri)
            if (b != null) photo = b else toast(ctx, "Impossibile leggere la foto")
        }
    }
    val bmp = remember(photo) { photo?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
    val num: (String) -> Boolean = { v -> v.all { it.isDigit() || it == '.' || it == ',' } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = BG) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    if (isNew) "Nuovo dipendente" else "Modifica dipendente",
                    color = TXT, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider(color = BORDER)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (bmp != null) {
                            Image(bmp.asImageBitmap(), null, Modifier.size(64.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.size(64.dp).clip(CircleShape).background(SURF2), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.PhotoCamera, null, tint = MUTED)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(onClick = { pick.launch("image/*") }) { Text("Scegli foto", color = TXT) }
                    }
                    OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(surname, { surname = it }, label = { Text("Cognome") }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(role, { role = it }, label = { Text("Ruolo") }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                    Text("Livello", color = MUTED, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..6).forEach { l ->
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(levelColor(l))
                                    .border(if (level == l) 3.dp else 1.dp, if (level == l) AMBER else MUTED, CircleShape)
                                    .clickable { level = l },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(l.toString(), color = if (l == 1) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    OutlinedTextField(
                        hours, { if (num(it)) hours = it }, label = { Text("Ore da contratto a settimana (es. 40)") }, singleLine = true,
                        colors = fieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        rate, { if (num(it)) rate = it }, label = { Text("Paga oraria in € (es. 12,50)") }, singleLine = true,
                        colors = fieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = active, onCheckedChange = { active = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Attivo", color = TXT)
                    }
                }
                HorizontalDivider(color = BORDER)
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Text("Annulla", color = TXT) }
                    AmberButton("Salva", Modifier.weight(1f).height(48.dp)) {
                        val h = Fmt.num(hours)
                        val r = Fmt.num(rate)
                        if (name.isBlank() || surname.isBlank()) toast(ctx, "Inserisci nome e cognome")
                        else if (h == null || h <= 0 || r == null || r < 0) toast(ctx, "Ore contratto o paga oraria non valide")
                        else onSave(init.copy(name = name.trim(), surname = surname.trim(), role = role.trim(), level = level, contractHours = h, rate = r, active = active, photo = photo))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- orari giornalieri

@Composable
private fun ScheduleTab(ym: YearMonth, ver: Int, bump: () -> Unit) {
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val def = Cfg.defSched()
    var date by remember { mutableStateOf(today) }
    var start by remember { mutableIntStateOf(def.start) }
    var end by remember { mutableIntStateOf(def.end) }
    var breaks by remember { mutableStateOf(def.breaks) }
    val todaySched = remember(ver) { Db.schedule(today.toString()) }
    val list = remember(ver, ym) { Db.schedules(monthRange(ym).first, monthRange(ym).second).toList().sortedBy { it.first } }

    fun load(d: LocalDate, s: Sched) {
        date = d
        start = s.start
        end = s.end
        breaks = s.breaks
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel {
            val s = todaySched ?: def
            Text("Orario di oggi (${today})", color = TXT, fontWeight = FontWeight.Bold)
            Text(
                "${Fmt.hhmm(s.start)} – ${Fmt.hhmm(s.end)} · ${s.breaksText()} (${if (todaySched != null) "orario impostato" else "orario predefinito"})",
                color = MUTED, fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { load(today, s) }, shape = RoundedCornerShape(10.dp)) { Text("Regola oggi", color = TXT) }
        }
        Panel {
            SectionTitle("Imposta orario per una data (vale per tutti i dipendenti)")
            PickerField("Data", Fmt.dateShort(date)) { pickDate(ctx, date) { date = it } }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerField("Inizio", Fmt.hhmm(start), Modifier.weight(1f)) { pickTime(ctx, start) { start = it } }
                PickerField("Fine", Fmt.hhmm(end), Modifier.weight(1f)) { pickTime(ctx, end) { end = it } }
            }
            Spacer(Modifier.height(8.dp))
            BreaksEditor(breaks) { breaks = it }
            Spacer(Modifier.height(12.dp))
            AmberButton("Salva orario") {
                if (end <= start || !validBreaks(breaks)) toast(ctx, "Controlla orario di fine e pause (la fine deve seguire l'inizio)")
                else {
                    Db.putSchedule(date.toString(), Sched(start, end, breaks))
                    bump()
                    toast(ctx, "Orario salvato")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Chi entra prima conta dall'orario di inizio. Ritardi e uscite anticipate vengono sottratti dalle ore pagate. " +
                    "Uscita oltre la fine: entro ${TOL_OUT} min vale l'orario ufficiale, oltre è straordinario a scatti di mezz'ora.",
                color = MUTED, fontSize = 12.sp
            )
        }
        list.forEach { (d, s) ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d, color = TXT, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("${Fmt.hhmm(s.start)} – ${Fmt.hhmm(s.end)} · ${s.breaksText()}", color = TXT, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { load(LocalDate.parse(d), s) }) { Icon(Icons.Outlined.Edit, "Modifica", tint = TXT) }
                    IconButton(onClick = { Db.deleteSchedule(d); bump() }) { Icon(Icons.Outlined.Delete, "Elimina", tint = RED) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- griglia correzioni

@Composable
private fun GridTab(ym: YearMonth, ver: Int, bump: () -> Unit, selId: Long?, onSel: (Long?) -> Unit, io: Io) {
    val ctx = LocalContext.current
    val emps = remember(ver) { Db.employees() }
    val sel = emps.firstOrNull { it.id == selId }
    var editing by remember { mutableStateOf<DayRes?>(null) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            EmpDropdown(emps, selId, onSel)
            if (sel != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        io.ready("Griglia_${Fmt.fileSafe(sel.surname)}_${ym}.pdf", "application/pdf", Pdfs.grid(sel, ym))
                    }, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(18.dp), tint = TXT)
                        Spacer(Modifier.width(6.dp))
                        Text("PDF griglia", color = TXT)
                    }
                    OutlinedButton(onClick = {
                        io.share("Griglia_${Fmt.fileSafe(sel.surname)}_${ym}.pdf", "application/pdf", Pdfs.grid(sel, ym))
                    }, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Outlined.Share, null, Modifier.size(18.dp), tint = TXT)
                        Spacer(Modifier.width(6.dp))
                        Text("Condividi", color = TXT)
                    }
                }
            }
        }
        if (sel == null) {
            Box(Modifier.fillMaxWidth().padding(16.dp).border(1.dp, BORDER, RoundedCornerShape(16.dp)).padding(28.dp)) {
                Text("Seleziona un dipendente per vedere il mese giorno per giorno.", color = MUTED, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        } else {
            val r = remember(sel, ym, ver) { Calc.month(sel, ym) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("Giorno", color = MUTED, fontSize = 12.sp, modifier = Modifier.width(76.dp))
                Text("Ore", color = MUTED, fontSize = 12.sp, modifier = Modifier.width(56.dp))
                Text("Straord.", color = MUTED, fontSize = 12.sp, modifier = Modifier.width(64.dp))
                Text("Note", color = MUTED, fontSize = 12.sp)
            }
            HorizontalDivider(color = BORDER)
            LazyColumn(Modifier.weight(1f)) {
                items(r.days, key = { it.date.toString() }) { d ->
                    val worked = d.kind == "W" || d.kind == "H"
                    Column(
                        Modifier.fillMaxWidth().background(if (d.isWeekend) SURF else Color.Transparent)
                            .clickable(enabled = !d.isWeekend) { editing = d }.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${String.format("%02d", d.date.dayOfMonth)} ${Fmt.DAYS3[d.date.dayOfWeek.value - 1]}", color = if (d.isWeekend) MUTED else TXT, modifier = Modifier.width(76.dp))
                            Text(if (worked) Fmt.dec(d.ordMin) else "", color = TXT, modifier = Modifier.width(56.dp))
                            Text(if (worked) Fmt.dec(d.extraMin) else "", color = AMBER, modifier = Modifier.width(64.dp))
                            when (d.kind) {
                                "A" -> Text("A  assenza", color = RED, fontWeight = FontWeight.Bold)
                                "F" -> Text("F  ferie", color = Color(0xFF58A6FF), fontWeight = FontWeight.Bold)
                                "M" -> Text("M  malattia", color = Color(0xFFBC8CFF), fontWeight = FontWeight.Bold)
                                "H" -> Text("corretto", color = AMBER)
                                else -> {}
                            }
                        }
                        if (d.tin != null) {
                            val extra = buildString {
                                append("${Fmt.hhmm(d.tin)}–${d.tout?.let { Fmt.hhmm(it) } ?: "…"}")
                                if (d.lateMin > 0) append("  ritardo ${d.lateMin} min")
                                if (d.earlyMin > 0) append("  anticipo ${d.earlyMin} min")
                            }
                            Text(extra, color = MUTED, fontSize = 11.sp, modifier = Modifier.padding(start = 76.dp))
                        }
                    }
                    HorizontalDivider(color = BORDER)
                }
            }
        }
    }

    editing?.let { d ->
        val e = sel ?: return@let
        DayDialog(d, onDismiss = { editing = null }) { kind, h, ot ->
            if (kind == "AUTO") Db.clearOverride(e.id, d.date.toString())
            else Db.setOverride(DayOverride(e.id, d.date.toString(), kind, h, ot))
            editing = null
            bump()
            toast(ctx, "Giorno aggiornato")
        }
    }
}

@Composable
private fun DayDialog(d: DayRes, onDismiss: () -> Unit, onSave: (String, Double, Double) -> Unit) {
    val ctx = LocalContext.current
    var kind by remember { mutableStateOf(if (d.kind == "W" || d.kind == "") "AUTO" else d.kind) }
    var hours by remember { mutableStateOf(if (d.ordMin > 0) (d.ordMin / 60.0).toString().removeSuffix(".0") else "8") }
    var ot by remember { mutableStateOf(if (d.extraMin > 0) (d.extraMin / 60.0).toString().removeSuffix(".0") else "0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Fmt.dateLong(d.date)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("AUTO" to "Automatico", "H" to "Ore", "A" to "A", "F" to "F", "M" to "M").forEach { (k, l) ->
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(if (kind == k) AMBER else SURF2)
                                .clickable { kind = k }.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text(l, color = if (kind == k) ON_AMBER else TXT, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Text(
                    when (kind) {
                        "AUTO" -> "Usa le timbrature (assenza se manca la timbratura)."
                        "H" -> "Ore lavorate 0–8 e straordinario 0–3."
                        "A" -> "Assenza: non pagata."
                        "F" -> "Ferie: pagate come 8 ore."
                        else -> "Malattia: non pagata."
                    }, color = MUTED, fontSize = 12.sp
                )
                if (kind == "H") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            hours, { hours = it }, label = { Text("Ore (0-8)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            ot, { ot = it }, label = { Text("Straord. (0-3)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (kind == "H") {
                    val h = Fmt.num(hours)
                    val o = Fmt.num(ot)
                    if (h == null || o == null || h < 0 || h > 8 || o < 0 || o > 3) toast(ctx, "Ore 0–8, straordinario 0–3")
                    else onSave("H", h, o)
                } else onSave(kind, 0.0, 0.0)
            }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

// ---------------------------------------------------------------- report

@Composable
private fun ReportTab(ym: YearMonth, ver: Int, bump: () -> Unit, selId: Long?, onSel: (Long?) -> Unit, io: Io) {
    val ctx = LocalContext.current
    val emps = remember(ver) { Db.employees() }
    val sel = emps.firstOrNull { it.id == selId }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel {
            SectionTitle("Report Tutti (PDF per il commercialista)")
            Text("Un file unico con la tabella: Cognome, Nome, Ore Totali, Ritardo, Anticipo, Ore Ordinarie, Straordinario, Ferie, Assenze, Malattia, Bonus, Malus, Totale €.", color = MUTED, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            AmberButton("Genera PDF Tutti") {
                io.ready("GP-Mica_report_tutti_${ym}.pdf", "application/pdf", Pdfs.allReport(ym))
            }
        }
        Panel {
            SectionTitle("Report Singolo (PDF dettaglio giornaliero)")
            EmpDropdown(emps, selId, onSel)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = sel != null, shape = RoundedCornerShape(10.dp), onClick = {
                    sel?.let { io.ready("Report_${Fmt.fileSafe(it.surname)}_${ym}.pdf", "application/pdf", Pdfs.single(it, ym)) }
                }) { Text("Genera PDF Singolo", color = if (sel != null) TXT else MUTED) }
                OutlinedButton(enabled = sel != null, shape = RoundedCornerShape(10.dp), onClick = {
                    sel?.let { io.share("Report_${Fmt.fileSafe(it.surname)}_${ym}.pdf", "application/pdf", Pdfs.single(it, ym)) }
                }) { Text("Condividi (WhatsApp/Email)", color = if (sel != null) TXT else MUTED) }
            }
            if (sel != null) {
                val r = remember(sel, ym, ver) { Calc.month(sel, ym) }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = BORDER)
                Spacer(Modifier.height(10.dp))
                Text("Riepilogo ${Fmt.month(ym)}", color = TXT, fontWeight = FontWeight.SemiBold)
                Text("Ore lavorate ${Fmt.hm(r.workedMin)} · Straordinario ${Fmt.hm(r.otMin)}", color = MUTED, fontSize = 13.sp)
                Text("Assenze ${r.absences} · Ferie ${r.ferie} · Malattia ${r.malattia}", color = MUTED, fontSize = 13.sp)
                Text("Ritardi ${Fmt.hm(r.lateMin)} · Uscite anticipate ${Fmt.hm(r.earlyMin)}", color = MUTED, fontSize = 13.sp)
                Text("Paga mensile: ${Fmt.eur(r.total)}", color = GREEN, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                Spacer(Modifier.height(14.dp))
                Text("Bonus / Malus del mese", color = TXT, fontWeight = FontWeight.SemiBold)
                val b = remember(sel.id, ym, ver) { Db.bonus(sel.id, ym.toString()) }
                var amt by remember(sel.id, ym, ver) { mutableStateOf(if (b.amount == 0.0) "" else b.amount.toString()) }
                var note by remember(sel.id, ym, ver) { mutableStateOf(b.note) }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    amt, { amt = it }, label = { Text("Importo € (negativo = malus)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(note, { note = it }, label = { Text("Motivazione (es. premio produttività)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                AmberButton("Salva bonus/malus") {
                    val a = if (amt.isBlank()) 0.0 else Fmt.num(amt)
                    if (a == null) toast(ctx, "Importo non valido")
                    else {
                        Db.putBonus(sel.id, ym.toString(), a, note.trim())
                        bump()
                        toast(ctx, "Salvato")
                    }
                }
            }
        }
        Panel {
            SectionTitle("Badge QR (PDF da stampare e plastificare)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = {
                    io.ready("GP-Mica_badge_tutti.pdf", "application/pdf", Pdfs.badges(emps.filter { it.active }))
                }) { Text("Tutti i dipendenti", color = TXT) }
                OutlinedButton(enabled = sel != null, shape = RoundedCornerShape(10.dp), onClick = {
                    sel?.let { io.ready("Badge_${Fmt.fileSafe(it.surname)}.pdf", "application/pdf", Pdfs.badges(listOf(it))) }
                }) { Text("Dipendente selezionato", color = if (sel != null) TXT else MUTED) }
            }
        }
    }
}

// ---------------------------------------------------------------- impostazioni

@Composable
private fun SettingsTab(bump: () -> Unit) {
    val ctx = LocalContext.current
    var company by remember { mutableStateOf(Cfg.company) }
    var start by remember { mutableIntStateOf(Cfg.defStart) }
    var end by remember { mutableIntStateOf(Cfg.defEnd) }
    var breaks by remember { mutableStateOf(Cfg.breaks) }
    var ot by remember { mutableStateOf(Cfg.otPct.toString().removeSuffix(".0")) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var restoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                restoreBytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) {
                toast(ctx, "Impossibile leggere il file")
            }
        }
    }
    var pendingBackup by remember { mutableStateOf<ByteArray?>(null) }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val b = pendingBackup
        if (uri != null && b != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(b) }
                toast(ctx, "Backup salvato")
            } catch (e: Exception) {
                toast(ctx, "Errore: ${e.message}")
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel {
            SectionTitle("Impostazioni azienda")
            OutlinedTextField(company, { company = it }, label = { Text("Nome azienda") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerField("Inizio predefinito", Fmt.hhmm(start), Modifier.weight(1f)) { pickTime(ctx, start) { start = it } }
                PickerField("Fine predefinita", Fmt.hhmm(end), Modifier.weight(1f)) { pickTime(ctx, end) { end = it } }
            }
            Spacer(Modifier.height(8.dp))
            BreaksEditor(breaks) { breaks = it }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                ot, { v -> if (v.all { it.isDigit() || it == '.' || it == ',' }) ot = v }, label = { Text("Maggiorazione straordinari (%)") }, singleLine = true,
                colors = fieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            AmberButton("Salva impostazioni") {
                val o = Fmt.num(ot)
                if (company.isBlank() || o == null || o < 0 || end <= start || !validBreaks(breaks)) toast(ctx, "Controlla i valori inseriti")
                else {
                    Cfg.company = company.trim()
                    Cfg.defStart = start
                    Cfg.defEnd = end
                    Cfg.breaks = breaks
                    Cfg.otPct = o
                    bump()
                    toast(ctx, "Impostazioni salvate")
                }
            }
        }
        Panel {
            SectionTitle("PIN admin (6 cifre)")
            OutlinedTextField(
                oldPin, { if (it.length <= 6 && it.all { c -> c.isDigit() }) oldPin = it }, label = { Text("PIN attuale") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                newPin, { if (it.length <= 6 && it.all { c -> c.isDigit() }) newPin = it }, label = { Text("Nuovo PIN") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            AmberButton("Cambia PIN") {
                if (oldPin != Cfg.pin) toast(ctx, "PIN attuale errato")
                else if (newPin.length != 6) toast(ctx, "Il nuovo PIN deve avere 6 cifre")
                else {
                    Cfg.pin = newPin
                    oldPin = ""
                    newPin = ""
                    toast(ctx, "PIN aggiornato")
                }
            }
        }
        Panel {
            SectionTitle("Backup e ripristino database")
            Text("Esporta tutti i dati (dipendenti, foto, timbrature, orari, bonus/malus, ferie) in un file .db condivisibile, oppure ripristina da un file esportato in precedenza.", color = MUTED, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = {
                    pendingBackup = Db.exportBytes()
                    backupSaver.launch("GP-Mica_backup_${LocalDate.now()}.db")
                }) {
                    Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp), tint = TXT)
                    Spacer(Modifier.width(6.dp))
                    Text("Esporta backup", color = TXT)
                }
                OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { restorePicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Outlined.FileUpload, null, Modifier.size(18.dp), tint = TXT)
                    Spacer(Modifier.width(6.dp))
                    Text("Ripristina da file", color = TXT)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = {
                shareBytes(ctx, "GP-Mica_backup_${LocalDate.now()}.db", "application/octet-stream", Db.exportBytes())
            }) {
                Icon(Icons.Outlined.Share, null, Modifier.size(18.dp), tint = TXT)
                Spacer(Modifier.width(6.dp))
                Text("Condividi backup", color = TXT)
            }
        }
        Panel {
            SectionTitle("Azzera database")
            Text("Cancella tutti i dati e riporta l'app alle impostazioni iniziali.", color = MUTED, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { confirmReset = true }, shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, RED)
            ) { Text("Reset database", color = RED, fontWeight = FontWeight.SemiBold) }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Azzerare il database?") },
            text = {
                Text("Verranno cancellati TUTTI i dati: dipendenti, foto, timbrature, orari, correzioni, bonus/malus e impostazioni (il PIN tornerà 000000). L'operazione non è reversibile: se non sei sicuro, esporta prima un backup.")
            },
            confirmButton = {
                TextButton(onClick = {
                    Db.reset()
                    confirmReset = false
                    company = Cfg.company
                    start = Cfg.defStart
                    end = Cfg.defEnd
                    breaks = Cfg.breaks
                    ot = Cfg.otPct.toString().removeSuffix(".0")
                    bump()
                    toast(ctx, "Database azzerato")
                }) { Text("Azzera tutto", color = RED) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Annulla") } }
        )
    }

    restoreBytes?.let { bytes ->
        AlertDialog(
            onDismissRequest = { restoreBytes = null },
            title = { Text("Ripristinare il database?") },
            text = { Text("Tutti i dati attuali verranno sostituiti con quelli del file selezionato.") },
            confirmButton = {
                TextButton(onClick = {
                    val ok = Db.restore(bytes)
                    restoreBytes = null
                    if (ok) {
                        company = Cfg.company
                        start = Cfg.defStart
                        end = Cfg.defEnd
                        breaks = Cfg.breaks
                        ot = Cfg.otPct.toString().removeSuffix(".0")
                        bump()
                        toast(ctx, "Database ripristinato")
                    } else toast(ctx, "File non valido")
                }) { Text("Ripristina", color = RED) }
            },
            dismissButton = { TextButton(onClick = { restoreBytes = null }) { Text("Annulla") } }
        )
    }
}
