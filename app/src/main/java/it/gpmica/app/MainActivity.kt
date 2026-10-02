package it.gpmica.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

val BG = Color(0xFF0D1117)
val SURF = Color(0xFF161B22)
val SURF2 = Color(0xFF21262D)
val BORDER = Color(0xFF30363D)
val AMBER = Color(0xFFF5B025)
val TXT = Color(0xFFE6EDF3)
val MUTED = Color(0xFF8B949E)
val GREEN = Color(0xFF2EE59D)
val RED = Color(0xFFF85149)
val ON_AMBER = Color(0xFF1B1400)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Db.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { App() }
    }
}

@Composable
fun App() {
    var screen by rememberSaveable { mutableStateOf("kiosk") }
    var result by remember { mutableStateOf<PunchResult?>(null) }
    val scheme = darkColorScheme(
        primary = AMBER, onPrimary = ON_AMBER, background = BG, onBackground = TXT,
        surface = SURF, onSurface = TXT, surfaceVariant = SURF2, onSurfaceVariant = MUTED,
        error = RED, outline = BORDER, secondary = AMBER
    )
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = BG) {
            BackHandler(enabled = screen != "kiosk") { screen = "kiosk" }
            when (screen) {
                "kiosk" -> KioskScreen(onScan = { screen = "scan" }, onAdmin = { screen = "pin" }, onCode = { result = Kiosk.punch(it) })
                "scan" -> ScanScreen(onCode = { code -> result = Kiosk.punch(code); screen = "kiosk" }, onClose = { screen = "kiosk" })
                "pin" -> PinScreen(onOk = { screen = "admin" }, onBack = { screen = "kiosk" })
                else -> AdminScreen(onExit = { screen = "kiosk" })
            }
            result?.let { ResultOverlay(it) { result = null } }
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SURF,
        border = BorderStroke(1.dp, BORDER)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun AmberButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AMBER, contentColor = ON_AMBER, disabledContainerColor = SURF2, disabledContentColor = MUTED)
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun KioskScreen(onScan: () -> Unit, onAdmin: () -> Unit, onCode: (String) -> Unit) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000)
        }
    }
    var code by remember { mutableStateOf("") }
    val submit = {
        if (code.isNotBlank()) {
            onCode(code)
            code = ""
        }
    }
    Box(Modifier.fillMaxSize()) {
        IconButton(onClick = onAdmin, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            Icon(Icons.Outlined.Lock, contentDescription = "Admin", tint = MUTED)
        }
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 560.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("GP-MICA", color = MUTED, fontSize = 12.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(6.dp))
            Text("Gestione Presenze", color = TXT, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(32.dp))
            Text(
                String.format("%02d:%02d", now.hour, now.minute),
                color = TXT, fontSize = 104.sp, fontWeight = FontWeight.ExtraBold
            )
            Text(Fmt.dateLong(now.toLocalDate()), color = MUTED, fontSize = 18.sp)
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onScan,
                modifier = Modifier.fillMaxWidth().height(92.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AMBER, contentColor = ON_AMBER)
            ) {
                Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(26.dp))
                Spacer(Modifier.size(10.dp))
                Text("SCANSIONA BADGE", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code, onValueChange = { code = it.uppercase() },
                    placeholder = { Text("Codice badge (es. GP-4F2K9Q)", color = MUTED) },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(10.dp))
                Button(
                    onClick = { submit() }, shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SURF2, contentColor = TXT),
                    modifier = Modifier.height(56.dp)
                ) { Text("Timbra", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
fun ResultOverlay(r: PunchResult, onDone: () -> Unit) {
    LaunchedEffect(r) {
        delay(4000)
        onDone()
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(BG).clickable { onDone() },
        contentAlignment = Alignment.Center
    ) {
        // dimensioni del testo proporzionali alla larghezza disponibile
        val avail = maxWidth.value - 48f
        val greetSize = (avail / 7.4f).coerceIn(24f, 64f).sp
        val nameSize = (avail / (r.name.length.coerceAtLeast(10) * 0.62f)).coerceIn(20f, 36f).sp
        val lineSize = (avail / 16f).coerceIn(16f, 24f).sp
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val accent = if (r.ok) GREEN else RED
            Box(Modifier.size(120.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(
                    if (!r.ok) Icons.Outlined.ErrorOutline else if (r.isIn) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay,
                    null, tint = accent, modifier = Modifier.size(64.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
            if (!r.ok) {
                Text(r.msg, color = TXT, fontSize = nameSize, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            } else {
                Text(if (r.isIn) "BUONGIORNO" else "ARRIVEDERCI", color = TXT, fontSize = greetSize, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                Spacer(Modifier.height(12.dp))
                Text(r.name, color = TXT, fontSize = nameSize, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                Spacer(Modifier.height(16.dp))
                Text(if (r.isIn) "Entrata registrata: ${r.time}" else "Uscita registrata: ${r.time}", color = TXT, fontSize = lineSize)
                if (r.late > 0) Text("Ritardo ${r.late} min", color = AMBER, fontSize = lineSize)
                if (r.early > 0) Text("Uscita anticipata ${r.early} min", color = AMBER, fontSize = lineSize)
                Spacer(Modifier.height(12.dp))
                if (!r.isIn) Text("Ore di oggi: ${Fmt.hm(r.dayMin)}", color = MUTED, fontSize = lineSize)
                Text("Ore del mese: ${Fmt.hm(r.monthMin)}", color = MUTED, fontSize = lineSize)
            }
        }
    }
}

@Composable
fun PinScreen(onOk: () -> Unit, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    val real = remember { Cfg.pin }
    val check = {
        if (pin == real) onOk() else {
            err = true
            pin = ""
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Icon(Icons.Outlined.Close, "Chiudi", tint = MUTED)
        }
        Panel(Modifier.widthIn(max = 420.dp).padding(24.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(AMBER.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Lock, null, tint = AMBER)
                }
                Spacer(Modifier.height(16.dp))
                Text("Area Admin", color = TXT, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() }) { pin = v; err = false } },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { check() }),
                    isError = err,
                    modifier = Modifier.fillMaxWidth()
                )
                if (err) Text("PIN errato", color = RED, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(12.dp))
                AmberButton("Accedi", Modifier.fillMaxWidth().height(48.dp)) { check() }
                if (real == "000000") {
                    Spacer(Modifier.height(12.dp))
                    Text("PIN predefinito: 000000", color = MUTED, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ScanScreen(onCode: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    var front by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraPreview(front, onCode)
        } else {
            Text(
                "Serve il permesso fotocamera per scansionare i badge.\nPuoi comunque digitare il codice badge a mano.",
                color = TXT, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp)
            )
        }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Chiudi", tint = Color.White) }
            Text("Inquadra il badge QR", color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { front = !front }) { Icon(Icons.Outlined.Cameraswitch, "Cambia camera", tint = Color.White) }
        }
    }
}

@Composable
private fun CameraPreview(front: Boolean, onCode: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onCode)
    val done = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    val previewView = remember { PreviewView(ctx) }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }

    DisposableEffect(front) {
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            if (!disposed) {
                try {
                    val p = future.get()
                    provider = p
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { proxy ->
                        analyze(proxy, scanner, done) { v -> ContextCompat.getMainExecutor(ctx).execute { latest(v) } }
                    }
                    val selector =
                        if (front && p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) CameraSelector.DEFAULT_FRONT_CAMERA
                        else CameraSelector.DEFAULT_BACK_CAMERA
                    p.unbindAll()
                    p.bindToLifecycle(owner, selector, preview, analysis)
                } catch (e: Exception) {
                    // camera non disponibile: resta l'inserimento manuale
                }
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            disposed = true
            try {
                provider?.unbindAll()
            } catch (e: Exception) {
            }
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@OptIn(ExperimentalGetImage::class)
private fun analyze(proxy: ImageProxy, scanner: BarcodeScanner, done: AtomicBoolean, deliver: (String) -> Unit) {
    val media = proxy.image
    if (media == null || done.get()) {
        proxy.close()
        return
    }
    val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    scanner.process(img)
        .addOnSuccessListener { list ->
            val v = list.firstOrNull { it.rawValue != null }?.rawValue
            if (v != null && done.compareAndSet(false, true)) deliver(v)
        }
        .addOnCompleteListener { proxy.close() }
}
