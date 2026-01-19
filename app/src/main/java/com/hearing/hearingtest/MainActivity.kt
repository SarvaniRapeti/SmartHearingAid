@file:OptIn(ExperimentalMaterial3Api::class)

package com.hearing.hearingtest
import android.Manifest

import android.R.attr.bitmap
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlin.math.*
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.compose.foundation.layout.FlowRow
import androidx.navigation.compose.composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.ln
// add these imports near other imports
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()

                    val liveAudioEngine = remember {
                        LiveAudioEngine()
                    }

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onStart = { navController.navigate("audiogram") },
                                onViewResults = { navController.navigate("view_results") },
                                onStartApp = {
                                    val context = this@MainActivity
                                    if (isHeadphonesConnected(context)) {
                                        navController.navigate("live_audio")
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Please connect headphones to continue",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                            )
                        }
                        // audiogram route — reuse your existing AudiogramApp composable but pass handlers
                        composable("audiogram") {
                            AudiogramApp(
                                onFinishedNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }


                        composable("live_audio") {
                            LiveAudioScreen(
                                onBack = { navController.popBackStack() },
                                onOpenUploadedFiles = {
                                    navController.navigate("uploaded_files")
                                },
                                liveAudioEngine = liveAudioEngine
                            )
                        }

                        // View results list
                        composable("view_results") {
                            ViewResultsScreen(
                                onOpen ={ encodedJson ->
                                    navController.navigate("saved_detail/$encodedJson")
                                },
                                onAnalyze = { name ->
                                    // navigate to analysis screen
                                    navController.navigate("analysis/$name")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("saved_detail/{name}") { backStackEntry ->
                            val context = LocalContext.current
                            val name = backStackEntry.arguments?.getString("name") ?: ""

                            // Load all saved tests from DataStore
                            val savedFlow = remember { SavedTestsManager.getAll(context) }
                            val list by savedFlow.collectAsState(initial = emptyList())

                            // Find the matching saved test
                            val savedRun = list.find { it.name == name }

                            if (savedRun != null) {
                                SavedTestDetailScreen(
                                    savedRun = savedRun,
                                    onBack = { navController.popBackStack() }
                                )
                            } else {
                                Text("Error: Test not found", modifier = Modifier.padding(20.dp))
                            }
                        }

                        composable("analysis/{name}") { backStackEntry ->
                            val context = LocalContext.current
                            val name = backStackEntry.arguments?.getString("name") ?: ""
                            val savedFlow = remember { SavedTestsManager.getAll(context) }
                            val allRuns by savedFlow.collectAsState(initial = emptyList())
                            val initial = allRuns.find { it.name == name } ?: allRuns.firstOrNull()
                            if (initial != null) {
                                AnalysisScreen(savedRun = initial, allRuns = allRuns, onBack = { navController.popBackStack() })
                            } else {
                                Text("No saved data", Modifier.padding(16.dp))
                            }
                        }

                        composable("live_listening") {
                            LiveAudioScreen(
                                onBack = { navController.popBackStack() },
                                onOpenUploadedFiles = {
                                    navController.navigate("uploaded_files")
                                },
                                liveAudioEngine = liveAudioEngine
                            )
                        }

                        composable("uploaded_files") {
                            UploadedFilesScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }



                    }
                }
            }
        }

    }
}

/* =========================
   New: HomeScreen Composable
   This reproduces the Figma home screen:
   - Title
   - View Results / Settings placeholders
   - Start/Stop button at bottom center (toggles visually)
   - On Start click -> calls onStart() (navigates to audiogram)
   ========================= */

@Composable
fun HomeScreen(
    onStartApp: () -> Unit,
    onStart: () -> Unit,
    onViewResults: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "HEAR TESTING APP",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // View Results
            Button(
                onClick = onViewResults,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("View Results", color = Color.White, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ Start App — ALWAYS VISIBLE
            Button(
                onClick = onStartApp,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Start App",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ⬇️ MUST BE LAST
            Spacer(modifier = Modifier.weight(1f))
        }

        // Bottom fixed button
        Button(
            onClick = onStart,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .width(260.dp)
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("Hearing Test", color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
fun AudiogramCanvas(
    frequencies: List<Int>,
    plottedPoints: List<FreqPoint>,
    currentFreq: Int,
    currentDb: Float,
    minDb: Float,
    maxDb: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {

        val w = size.width
        val h = size.height

        // ---------- DB SCALE ----------
        fun dbToY(db: Float): Float {
            val t = ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
            return 20f + t * (h - 40f) // padding
        }

        // ---------- FREQUENCY SCALE (LOG SPACED) ----------
        val minF = frequencies.minOrNull()!!.toFloat()
        val maxF = frequencies.maxOrNull()!!.toFloat()

        fun freqToX(freq: Int): Float {
            val t = (ln(freq.toFloat()) - ln(minF)) / (ln(maxF) - ln(minF))
            return 80f + t * (w - 160f) // padding sides
        }

        // -------- GRID LINES (dB horizontal) --------
        val gridPaint = Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 26f
        }

        // include 0 dB now
        val dbSteps = listOf(0, 20, 40, 60, 80, 100, 120)
        for (db in dbSteps) {
            val y = dbToY(db.toFloat())
            // line
            drawLine(
                start = Offset(60f, y),
                end = Offset(w - 40f, y),
                color = Color(0xFFEEEEEE),
                strokeWidth = 1.5f
            )
            // label
            drawContext.canvas.nativeCanvas.drawText(
                "$db dB",
                10f,
                y + 8f,
                gridPaint
            )
        }

        // ---------- FREQUENCY LABELS ----------
        val freqPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 30f
        }

        fun fmtFreqLabel(f: Int): String =
            if (f >= 1000) "${f / 1000}k" else "$f"

        for (f in frequencies) {
            val x = freqToX(f)
            drawLine(
                start = Offset(x, h - 36f),
                end = Offset(x, h - 12f),
                color = Color.DarkGray,
                strokeWidth = 2f
            )
            drawContext.canvas.nativeCanvas.drawText(
                fmtFreqLabel(f),
                x - (freqPaint.measureText(fmtFreqLabel(f)) / 2f),
                h - 48f,
                freqPaint
            )
        }

        // ---------- PLOT MEASURED POINTS ----------
        if (plottedPoints.isNotEmpty()) {
            val sorted = plottedPoints.sortedBy { it.freq }

            // lines between points
            for (i in 0 until sorted.size - 1) {
                val a = sorted[i]
                val b = sorted[i + 1]
                drawLine(
                    start = Offset(freqToX(a.freq), dbToY(a.db)),
                    end = Offset(freqToX(b.freq), dbToY(b.db)),
                    color = Color(0xFF1976D2),
                    strokeWidth = 4f
                )
            }

            // draw dots
            sorted.forEach { pt ->
                drawCircle(
                    color = Color.Red,
                    center = Offset(freqToX(pt.freq), dbToY(pt.db)),
                    radius = 8f
                )
            }
        }

        // ---------- LIVE TEST POINT (ONLY DURING TEST) ----------
        drawCircle(
            color = Color.Gray,
            center = Offset(freqToX(currentFreq), dbToY(currentDb)),
            radius = 10f,
            style = Stroke(width = 3f)
        )
    }
}

@Composable
fun CombinedAudiogramCanvas(
    frequencies: List<Int>,
    primary: SavedTestsManager.SavedRun,
    secondary: SavedTestsManager.SavedRun,
    minDb: Float = 0f,              // changed to 0 dB by default
    maxDb: Float = 120f
) {
    val density = LocalDensity.current
    val labelTextSizePx = with(density) { 12.sp.toPx() }    // y-axis & freq labels
    val legendTextSizePx = with(density) { 11.sp.toPx() }   // legend text

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // --- Layout / margins (reserve space for legend on the right) ---
        val legendBoxW = 140f
        val legendBoxH = 54f
        val paddingEdge = 12f

        val leftMargin = 60f
        val rightMargin = 16f + legendBoxW + paddingEdge   // reserve space for legend
        val topMargin = 20f
        val bottomMargin = 36f

        val plotLeft = leftMargin
        val plotRight = w - rightMargin
        val plotTop = topMargin
        val plotBottom = h - bottomMargin
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        // safety
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        // helpers
        fun dbToY(db: Float): Float {
            val t = ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
            return plotTop + t * plotHeight
        }

        val minF = frequencies.minOrNull()!!.toFloat()
        val maxF = frequencies.maxOrNull()!!.toFloat()
        fun freqToX(freq: Int): Float {
            val t = (ln(freq.toFloat()) - ln(minF)) / (ln(maxF) - ln(minF))
            return plotLeft + t * plotWidth
        }

        // --- Grid lines (compose draw) ---
        val dbSteps = listOf(0, 20, 40, 60, 80, 100, 120)
        val gridStroke = 1.0f
        dbSteps.forEach { db ->
            val y = dbToY(db.toFloat())
            drawLine(
                color = Color(0xFFEEEEEE),
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = gridStroke
            )
        }

        // Y-axis ticks & labels (left)
        drawIntoCanvas { canvas ->
            try {
                val paint = Paint().apply {
                    isAntiAlias = true
                    textSize = labelTextSizePx
                    color = android.graphics.Color.DKGRAY
                    textAlign = Paint.Align.LEFT
                }
                dbSteps.forEach { db ->
                    val y = dbToY(db.toFloat())
                    // tick
                    drawLine(
                        color = Color.DarkGray,
                        start = Offset(plotLeft - 8f, y),
                        end = Offset(plotLeft, y),
                        strokeWidth = 1.4f
                    )
                    // label
                    canvas.nativeCanvas.drawText("${db} dB", 6f, y + (labelTextSizePx / 2f), paint)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        // Frequency ticks & labels (bottom)
        drawIntoCanvas { canvas ->
            try {
                val freqPaint = Paint().apply {
                    isAntiAlias = true
                    textSize = labelTextSizePx
                    color = android.graphics.Color.DKGRAY
                    textAlign = Paint.Align.LEFT
                }
                frequencies.forEach { f ->
                    val x = freqToX(f)
                    // small tick
                    drawLine(
                        color = Color.DarkGray,
                        start = Offset(x, plotBottom - 8f),
                        end = Offset(x, plotBottom),
                        strokeWidth = 1.5f
                    )
                    val lbl = if (f >= 1000) "${f / 1000}k" else "$f"
                    canvas.nativeCanvas.drawText(lbl, x - 14f, plotBottom + 14f, freqPaint)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        // --- prepare plotted points ---
        fun plotPoints(run: SavedTestsManager.SavedRun): List<Pair<Float, Float>> {
            val map = run.thresholds.associate { it.freq to it.db }
            return frequencies.map { f ->
                val db = map[f] ?: maxDb
                freqToX(f) to dbToY(db)
            }
        }

        val pPts = plotPoints(primary)
        val sPts = plotPoints(secondary)

        // colors
        val rightColor = Color(0xFFD32F2F) // red
        val leftColor = Color(0xFF1976D2)  // blue
        val pColor = if (primary.ear.equals("Right", true)) rightColor else leftColor
        val sColor = if (secondary.ear.equals("Right", true)) rightColor else leftColor

        // --- lines between points (compose draw) ---
        for (i in 0 until pPts.size - 1) {
            drawLine(
                color = pColor,
                start = Offset(pPts[i].first, pPts[i].second),
                end = Offset(pPts[i + 1].first, pPts[i + 1].second),
                strokeWidth = 3.5f,
                cap = StrokeCap.Round
            )
        }
        for (i in 0 until sPts.size - 1) {
            drawLine(
                color = sColor,
                start = Offset(sPts[i].first, sPts[i].second),
                end = Offset(sPts[i + 1].first, sPts[i + 1].second),
                strokeWidth = 3.5f,
                cap = StrokeCap.Round
            )
        }

        // --- markers (compose draw) ---
        primary.thresholds.forEach {
            val x = freqToX(it.freq)
            val y = dbToY(it.db)
            if (primary.ear.equals("Right", true)) {
                drawCircle(pColor, radius = 8f, center = Offset(x, y))
            } else {
                drawLine(color = pColor, start = Offset(x - 8f, y - 8f), end = Offset(x + 8f, y + 8f), strokeWidth = 3f)
                drawLine(color = pColor, start = Offset(x + 8f, y - 8f), end = Offset(x - 8f, y + 8f), strokeWidth = 3f)
            }
        }
        secondary.thresholds.forEach {
            val x = freqToX(it.freq)
            val y = dbToY(it.db)
            if (secondary.ear.equals("Right", true)) {
                drawCircle(sColor, radius = 8f, center = Offset(x, y))
            } else {
                drawLine(color = sColor, start = Offset(x - 8f, y - 8f), end = Offset(x + 8f, y + 8f), strokeWidth = 3f)
                drawLine(color = sColor, start = Offset(x + 8f, y - 8f), end = Offset(x - 8f, y + 8f), strokeWidth = 3f)
            }
        }

        // --- Legend placed OUTSIDE plot area (right margin) ---
        drawIntoCanvas { canvas ->
            try {
                val boxX = plotRight + paddingEdge
                val boxY = plotTop
                val paintRect = Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.WHITE
                }
                val borderPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 1.2f
                    color = android.graphics.Color.LTGRAY
                }
                // background and border
                canvas.nativeCanvas.drawRoundRect(boxX, boxY, boxX + legendBoxW, boxY + legendBoxH, 12f, 12f, paintRect)
                canvas.nativeCanvas.drawRoundRect(boxX, boxY, boxX + legendBoxW, boxY + legendBoxH, 12f, 12f, borderPaint)

                val padding = 8f
                val itemX = boxX + padding + 6f
                var itemY = boxY + 16f + (legendTextSizePx / 2f)

                val textPaint = Paint().apply {
                    isAntiAlias = true
                    textSize = legendTextSizePx
                    color = android.graphics.Color.DKGRAY
                }

                val rightPx = Paint().apply { isAntiAlias = true; color = android.graphics.Color.parseColor("#D32F2F") }
                canvas.nativeCanvas.drawCircle(itemX, itemY - 6f, 6f, rightPx)
                canvas.nativeCanvas.drawText("Right — Red", itemX + 14f, itemY, textPaint)

                itemY += 20f
                val leftPx = Paint().apply { isAntiAlias = true; color = android.graphics.Color.parseColor("#1976D2") }
                canvas.nativeCanvas.drawCircle(itemX, itemY - 6f, 6f, leftPx)
                canvas.nativeCanvas.drawText("Left — Blue", itemX + 14f, itemY, textPaint)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }
}

/* =========================
   Your original AudiogramApp + everything else below
   (unchanged logic). I pasted your original AudiogramApp, AudiogramCanvas,
   AudioEngine classes exactly, so your test behavior remains intact.
   Continue from here with the rest of your code...
   ========================= */

/* ---------- (PASTE THE REST OF YOUR ORIGINAL CODE FROM HERE) ---------- */

// (From here on, paste the rest of your original file contents starting at
// data class FreqPoint(...) and continuing through AudiogramApp, AudiogramCanvas,
// AudioEngine, etc. — this keeps your audio logic untouched.)

// --- In your actual file: retain the contents of data class FreqPoint, enum Response,
// the entire AudiogramApp composable, AudiogramCanvas, and AudioEngine class
// exactly as before. ---

data class FreqPoint(val freq: Int, val db: Float)

data class EarMetrics(
    val pta: Float,
    val avg: Float,
    val category: String
)

fun computeMetricsFromRun(run: SavedTestsManager.SavedRun, toList: List<Int>): EarMetrics {
    // map freq->db for quick lookup
    val map = run.thresholds.associateBy({ it.freq }, { it.db })

    // PTA using 500, 1000, 2000 Hz (if any of those missing, average available ones)
    val ptaFreqs = listOf(500, 1000, 2000)
    val ptaValues = ptaFreqs.mapNotNull { map[it] }
    val pta = if (ptaValues.isNotEmpty()) ptaValues.average().toFloat() else run.thresholds.map { it.db }.average().toFloat()

    // overall average of all thresholds
    val avg = if (run.thresholds.isNotEmpty()) run.thresholds.map { it.db }.average().toFloat() else 0f

    // category based on PTA
    val category = when {
        pta <= 25f -> "Normal"
        pta <= 40f -> "Mild"
        pta <= 55f -> "Moderate"
        pta <= 70f -> "Moderately severe"
        pta <= 90f -> "Severe"
        else -> "Profound"
    }

    return EarMetrics(pta = pta, avg = avg, category = category)
}


enum class Response { HEARD, NOT_HEARD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiogramApp(onFinishedNavigateBack: () -> Unit = {}) {
    // Frequencies to test
    val frequencies = listOf(250, 500, 1000, 2000, 3000, 4000, 6000, 8000)

    var showSaveBeforeRestart by remember { mutableStateOf(false) }

    // dB range & steps
    val minDb = 0f
    val maxDb = 120f
    val downStep = 10f // when heard -> go down 10 dB
    val upStep = 5f    // when not heard -> go up 5 dB

    // UI state
    var earPref by remember { mutableStateOf("Right") }
    var testStarted by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    // Test state
    var currentIndex by remember { mutableStateOf(0) }
    var currentFreq by remember { mutableStateOf(frequencies[0]) }
    var currentDb by remember { mutableStateOf(40f) } // start intensity
    val thresholds = remember { mutableStateListOf<FreqPoint>() } // found thresholds (lowest dB) to plot

    // Adaptive logic helper state (per-frequency)
    var lastResponse by remember { mutableStateOf<Response?>(null) }
    var consecHeardAtLevel by remember { mutableStateOf(0) }
    var lastHeardLevel by remember { mutableStateOf<Float?>(null) }

    // Audio engine
    val audioEngine = remember { AudioEngine() }
    val scope = rememberCoroutineScope()

    // Button enable logic
    val startEnabled = !testStarted
    val otherButtonsEnabled = testStarted

    // new helper to track direction: true = descending phase, false = ascending phase
    var directionDown by remember { mutableStateOf(true) }

    // convenience: app context
    val context = LocalContext.current

    // choose default leakage depending on route (Bluetooth vs wired)
    fun defaultLeakPercentForRoute(context: Context): Float {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return if (audioManager.isBluetoothA2dpOn) {
            0.02f // 2% for Bluetooth
        } else {
            0.005f // 0.5% for wired
        }
    }

    // Helper: record threshold (the lowest db where user heard)
    fun recordThreshold(db: Float) {
        val found = FreqPoint(currentFreq, db.coerceIn(minDb, maxDb))
        val idx = thresholds.indexOfFirst { it.freq == currentFreq }
        if (idx >= 0) thresholds[idx] = found else thresholds.add(found)
    }

    // Accept threshold and move to next frequency (or finish)
    fun acceptThresholdAndAdvance(foundDb: Float) {
        // record result for current frequency
        recordThreshold(foundDb.coerceIn(minDb, maxDb))

        // Stop any playing tone for this freq
        audioEngine.stop()
        isPlaying = false

        // reset per-frequency adaptive state
        lastResponse = null
        consecHeardAtLevel = 0
        lastHeardLevel = null
        directionDown = true // reset direction for next freq

        if (currentIndex < frequencies.lastIndex) {
            // advance to next freq
            currentIndex += 1
            currentFreq = frequencies[currentIndex]
            currentDb = 40f // reset starting level for next freq

            // automatically play a short burst at the starting level for the next frequency
            if (testStarted) {
                scope.launch {
                    try {
                        isPlaying = true
                        audioEngine.stop()

                        val leak = defaultLeakPercentForRoute(context)
                        audioEngine.playToneStereoWithLeak(
                            frequencyHz = currentFreq,
                            db = currentDb,
                            ear = earPref,
                            durationMs = 600L,
                            leakPercent = leak,
                            rampMs = 12
                        )

                        // keep the UI consistent; small delay is handled in the audio call or here
                        delay(400L)
                    } finally {
                        audioEngine.stop()
                        isPlaying = false
                    }
                }
            }
        } else {
            // finished testing entirely
            testStarted = false
            isPlaying = false
            // audioEngine.stop() already called
        }
    }

    // Handler when user presses "Heard"
    fun onHeard() {
        // If we are descending (initial phase): go quieter and continue until NOT HEARD occurs
        if (directionDown) {
            val previousDb = currentDb
            currentDb = (currentDb - downStep).coerceAtLeast(minDb)

            // If we've already hit the minimum intensity -> accept previous as threshold
            if (previousDb <= minDb + 0.001f) {
                acceptThresholdAndAdvance(previousDb.coerceAtLeast(minDb))
                return
            }

            // Play a short burst at the new (quieter) level so user can check
            if (testStarted) {
                scope.launch {
                    try {
                        isPlaying = true
                        audioEngine.stop()

                        val leak = defaultLeakPercentForRoute(context)
                        audioEngine.playToneStereoWithLeak(
                            frequencyHz = currentFreq,
                            db = currentDb,
                            ear = earPref,
                            durationMs = 600L,
                            leakPercent = leak,
                            rampMs = 12
                        )

                        delay(400L)
                    } finally {
                        audioEngine.stop()
                        isPlaying = false
                    }
                }
            }
        } else {
            // We are in ascending phase: a "Heard" here means we've found threshold
            acceptThresholdAndAdvance(currentDb.coerceAtLeast(minDb))
        }
    }

    var showSaveConfirm by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var pendingSaveName by remember { mutableStateOf("") }

    // Intercept back button: if test finished and thresholds exist -> prompt save
    BackHandler(enabled = true) {
        if (!testStarted && thresholds.isNotEmpty()) {
            showSaveConfirm = true
        } else {
            onFinishedNavigateBack()
        }
    }

    // Confirm: Save or Cancel
    if (showSaveConfirm) {
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title = { Text("Save test results?") },
            text = { Text("Do you want to save this test's thresholds?") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveConfirm = false
                    showNameDialog = true
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveConfirm = false
                    // Cancel -> go back without saving
                    onFinishedNavigateBack()
                }) { Text("Cancel") }
            }
        )
    }

    // Name entry dialog
    var savingInProgress by remember { mutableStateOf(false) }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { if (!savingInProgress) showNameDialog = false },
            title = { Text("Name this test") },
            text = {
                Column {
                    Text("Enter a name to save this run:")
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = pendingSaveName,
                        onValueChange = { pendingSaveName = it },
                        placeholder = { Text("e.g., Screening Nov 13") },
                        enabled = !savingInProgress
                    )
                    if (savingInProgress) {
                        Spacer(Modifier.height(8.dp))
                        Text("Saving...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // disable UI while saving
                        savingInProgress = true
                        val nameToSave = if (pendingSaveName.isBlank()) "Untitled ${System.currentTimeMillis()}" else pendingSaveName.trim()
                        pendingSaveName = ""

                        // Launch a coroutine and WAIT for save to finish before navigating
                        scope.launch {
                            try {
                                // perform save on IO - SavedTestsManager is suspend
                                withContext(Dispatchers.IO) {
                                    SavedTestsManager.saveTest(context.applicationContext, nameToSave, earPref, thresholds.toList())
                                }
                            } finally {
                                // back on main thread: re-enable and navigate back
                                savingInProgress = false
                                showNameDialog = false
                                // navigate back after confirmed save
                                onFinishedNavigateBack()
                            }
                        }
                    },
                    enabled = !savingInProgress
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!savingInProgress) showNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Handler when user presses "Not Heard"
    fun onNotHeard() {
        // Switching to ascending phase: increase intensity in small steps until heard
        directionDown = false
        currentDb = (currentDb + upStep).coerceAtMost(maxDb)

        // If we've reached max and still not heard -> record as no-response (maxDb)
        if (currentDb >= maxDb - 0.001f) {
            acceptThresholdAndAdvance(maxDb)
            return
        }

        // Play a short burst at the new (louder) level so user can check
        if (testStarted) {
            scope.launch {
                try {
                    isPlaying = true
                    audioEngine.stop()

                    val leak = defaultLeakPercentForRoute(context)
                    audioEngine.playToneStereoWithLeak(
                        frequencyHz = currentFreq,
                        db = currentDb,
                        ear = earPref,
                        durationMs = 600L,
                        leakPercent = leak,
                        rampMs = 12
                    )

                    delay(400L)
                } finally {
                    audioEngine.stop()
                    isPlaying = false
                }
            }
        }
    }

    // Start test
    fun onStartTest() {
        thresholds.clear()
        currentIndex = 0
        currentFreq = frequencies[0]
        currentDb = 40f
        testStarted = true
        lastResponse = null
        consecHeardAtLevel = 0
        lastHeardLevel = null
        directionDown = true

        // Play an initial short burst at starting level
        scope.launch {
            try {
                isPlaying = true
                audioEngine.stop()

                val leak = defaultLeakPercentForRoute(context)
                audioEngine.playToneStereoWithLeak(
                    frequencyHz = currentFreq,
                    db = currentDb,
                    ear = earPref,
                    durationMs = 600L,
                    leakPercent = leak,
                    rampMs = 12
                )

                delay(400L)
            } finally {
                audioEngine.stop()
                isPlaying = false
            }
        }
    }

    // Skip frequency explicitly
    fun onSkip() {
        acceptThresholdAndAdvance(maxDb)
    }

    // Stop the whole test session (stop tone, mark test ended, optionally reset progress)
    fun onStopTest(resetProgress: Boolean = false) {
        // Stop audio engine and playing flag
        audioEngine.stop()
        isPlaying = false

        // End the testing session
        testStarted = false

        // Optionally reset the test progress back to start (useful if you want a fresh start next time)
        if (resetProgress) {
            currentIndex = 0
            currentFreq = frequencies[0]
            currentDb = 40f
            thresholds.clear() // comment out if you want to keep recorded thresholds
            lastResponse = null
            consecHeardAtLevel = 0
            lastHeardLevel = null
        }
    }

    // UI layout
    // ---------- Replace your existing Scaffold { ... } with this block ----------
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Audiogram") })
        },
        // bottomBar holds the big Start/Stop button so it is always visible
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            // If a test is currently running -> stop it.
                            if (testStarted) {
                                onStopTest(resetProgress = false)
                            } else {
                                // If there are recorded thresholds from previous run, ask whether to save
                                if (thresholds.isNotEmpty()) {
                                    // set flag to show save-before-restart prompt
                                    showSaveBeforeRestart = true
                                } else {
                                    onStartTest()
                                }
                            }
                        },
                        modifier = Modifier
                            .height(64.dp)
                            .widthIn(min = 220.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (testStarted) Color(0xFFD32F2F) else Color(0xFF6A3CB8)
                        )
                    ) {
                        Text(
                            text = if (testStarted) "Stop Test" else "Start Test",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->

        // We'll make the main content scrollable so long lists won't push the bottomBar away.
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Ear preference + canvas + other content remain in a scrollable Column.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(12.dp)
            ) {
                // Ear preference radios
                // Ear preference radios — disabled during test
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (testStarted) 0.5f else 1f),  // visually dim when disabled
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ear preference:", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                    Row {
                        val options = listOf("Right", "Left", "Both")

                        options.forEach { opt ->
                            Row(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .selectable(
                                        selected = earPref == opt,
                                        enabled = !testStarted,       // fully disabled during test
                                        onClick = { earPref = opt }
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = earPref == opt,
                                    enabled = !testStarted,
                                    onClick = { if (!testStarted) earPref = opt }
                                )
                                Text(opt, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Live audiogram canvas (unchanged)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Box(modifier = Modifier.padding(8.dp)) {
                        AudiogramPlot(
                            frequencies = frequencies,
                            plottedPoints = thresholds.toList(),
                            currentFreq = currentFreq,
                            currentDb = currentDb,
                            minDb = minDb,
                            maxDb = maxDb,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Controls (top small controls) - keep these in the scroll area
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {

                        // Play Tone
                        Button(onClick = {
                            if (!isPlaying) {
                                scope.launch {
                                    try {
                                        isPlaying = true
                                        audioEngine.stop()

                                        val leak = defaultLeakPercentForRoute(context)
                                        audioEngine.playToneStereoWithLeak(
                                            frequencyHz = currentFreq,
                                            db = currentDb,
                                            ear = earPref,
                                            durationMs = 600L,
                                            leakPercent = leak,
                                            rampMs = 12
                                        )

                                        delay(400L)
                                    } finally {
                                        audioEngine.stop()
                                        isPlaying = false
                                    }
                                }
                            }
                        }, enabled = otherButtonsEnabled) {
                            Text("Play Tone")
                        }

                        // Skip Frequency
                        Button(onClick = { onSkip() }, enabled = otherButtonsEnabled) {
                            Text("Skip Freq")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(onClick = { onHeard() }, enabled = otherButtonsEnabled) { Text("Heard") }
                        Button(onClick = { onNotHeard() }, enabled = otherButtonsEnabled) { Text("Not Heard") }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Status & thresholds area. Make the thresholds list itself vertical if many entries.
                    Text("Testing: ${currentFreq} Hz  — Intensity: ${currentDb.toInt()} dB  — Ear: $earPref")
                    Spacer(Modifier.height(8.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))
                    Text("Thresholds (lowest dB plotted):", fontWeight = FontWeight.SemiBold)

                    if (thresholds.isEmpty()) {
                        Text("No thresholds recorded yet.")
                    } else {
                        // If thresholds list grows, make this column itself scrollable vertically
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)  // limit height; will scroll if too tall
                                .verticalScroll(rememberScrollState())
                                .padding(4.dp)
                        ) {
                            thresholds.sortedBy { it.freq }.forEach {
                                Text("${it.freq} Hz : ${it.db.toInt()} dB")
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }
            } // end scrollable column
        } // end main column
    } // end Scaffold

    // Prompt shown when user tries to Start again while old thresholds exist
    if (showSaveBeforeRestart) {
        AlertDialog(
            onDismissRequest = { showSaveBeforeRestart = false },
            title = { Text("Previous test found") },
            text = { Text("You have results from the previous test. Do you want to save them before starting a new test?") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveBeforeRestart = false
                    // open name dialog to save
                    showNameDialog = true
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveBeforeRestart = false
                    // discard previous test data and start a fresh new test
                    thresholds.clear()
                    onStartTest()
                }) {
                    Text("Discard")
                }
            }
        )
    }

// ---------- END replacement ----------
}

/**
 * Canvas: draws audiogram with top = minDb (best), bottom = maxDb (worst).
 * Points plotted are the threshold values (lowest dB found) for each frequency.
 */
@Composable
fun AudiogramPlot(
    frequencies: List<Int>,
    plottedPoints: List<FreqPoint>,   // your thresholds list (FreqPoint(freq, db))
    currentFreq: Int,
    currentDb: Float,
    minDb: Float = 0f,
    maxDb: Float = 120f,
    modifier: Modifier = Modifier
) {
    val gridColor = Color(0xFFDDDDDD)
    val leftLineColor = Color(0xFF1976D2)
    val pointColor = Color(0xFFD32F2F)
    val livePointColor = Color(0xFF555555)
    val axisTextColor = Color(0xFF333333)

    // animate DB so live marker moves smoothly
    val animatedDb by animateFloatAsState(targetValue = currentDb)

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val leftPad = 52f
            val rightPad = 24f    // give more room for legend if needed
            val topPad = 12f
            val bottomPad = 64f   // more space for rotated labels

            val innerLeft = leftPad
            val innerRight = w - rightPad
            val innerTop = topPad
            val innerBottom = h - bottomPad
            val innerW = innerRight - innerLeft
            val innerH = innerBottom - innerTop

            fun dbToY(db: Float): Float {
                val t = ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                return innerTop + t * innerH
            }

            val minF = frequencies.minOrNull()?.toFloat() ?: 250f
            val maxF = frequencies.maxOrNull()?.toFloat() ?: 8000f
            fun freqToX(freq: Int): Float {
                if (minF == maxF) return (innerLeft + innerW / 2f)
                val lnFreq = ln(freq.toFloat().toDouble())
                val lnMin = ln(minF.toDouble())
                val lnMax = ln(maxF.toDouble())
                val t = (((lnFreq - lnMin) / (lnMax - lnMin)).coerceIn(0.0, 1.0)).toFloat()
                return innerLeft + t * innerW
            }

            // ---- grid horizontal lines ----
            val dbSteps = listOf(0, 20, 40, 60, 80, 100, 120)
            dbSteps.forEach { d ->
                val y = dbToY(d.toFloat())
                drawLine(start = Offset(innerLeft, y), end = Offset(innerRight, y), color = gridColor, strokeWidth = 1f)
            }

            // ---- vertical grid lines (full) ----
            frequencies.forEach { f ->
                val x = freqToX(f)
                drawLine(start = Offset(x, innerTop), end = Offset(x, innerBottom), color = gridColor.copy(alpha = 0.5f), strokeWidth = 1f)
            }

            // ---- left axis labels (dB) ----
            val labelPaint = Paint().apply {
                textSize = 30f
                color = android.graphics.Color.DKGRAY
            }
            dbSteps.forEach { d ->
                val y = dbToY(d.toFloat())
                drawContext.canvas.nativeCanvas.drawText("${d} dB", 6f, y + 8f, labelPaint)
            }

            fun fmtFreqLabel(freq: Int): String {
                return when {
                    freq >= 1000 && freq % 1000 == 0 -> "${freq / 1000}k"   // exact thousands -> "1k"
                    else -> freq.toString()                                  // e.g., 250, 500
                }
            }
            // ---- frequency labels with anti-overlap logic ----
            /// ---- frequency labels with abbreviated format and anti-overlap ----
            // fixed frequency label set (always shown)
            val freqPaint = Paint().apply {
                textSize = 26f
                color = android.graphics.Color.BLACK
            }

            val desiredLabels = listOf(250, 500, 1000, 2000, 3000, 4000, 6000, 8000)

            frequencies.forEach { f ->
                if (desiredLabels.contains(f)) {
                    val x = freqToX(f)
                    val label = fmtFreqLabel(f)
                    drawContext.canvas.nativeCanvas.drawText(label, x - (freqPaint.measureText(label) / 2f), innerBottom + 28f, freqPaint)
                }
            }


            // helper to format labels: 1000 -> "1k", 2000 -> "2k", else "250"
            // helper to format labels: 1000 -> "1k", 2000 -> "2k", 250 -> "250"



// minimal pixel spacing between successive labels (adaptive)
            val minLabelSpacing = 48f.coerceAtMost(innerW / (frequencies.size.coerceAtLeast(1)).toFloat())
            var lastLabelX = -9999f

            frequencies.forEachIndexed { index, f ->
                val x = freqToX(f)
                val isFirst = index == 0
                val isLast = index == frequencies.lastIndex

                // Always show first and last. For others require spacing.
                if (isFirst || isLast || (x - lastLabelX >= minLabelSpacing)) {
                    val label = fmtFreqLabel(f)
                    // Draw centered under the tick
                    drawContext.canvas.nativeCanvas.drawText(label, x - (freqPaint.measureText(label) / 2f), innerBottom + 28f, freqPaint)
                    lastLabelX = x
                }
            }


            // ---- plotted thresholds (polyline + points) ----
            if (plottedPoints.isNotEmpty()) {
                val sorted = plottedPoints.sortedBy { frequencies.indexOf(it.freq) }
                val path = Path()
                sorted.forEachIndexed { i, p ->
                    val px = freqToX(p.freq)
                    val py = dbToY(p.db)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path = path, color = leftLineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

                sorted.forEach { p ->
                    val cx = freqToX(p.freq)
                    val cy = dbToY(p.db)
                    drawCircle(color = pointColor, radius = 7f, center = Offset(cx, cy))
                }
            }

            // ---- live testing point ----
            val liveX = freqToX(currentFreq)
            val liveY = dbToY(animatedDb)

            // check if live overlaps a plotted point (same freq and very similar db)
            val overlappingPlotted = plottedPoints.find { it.freq == currentFreq && abs(it.db - animatedDb) < 1.0f }

            if (overlappingPlotted != null) {
                // if overlapping, draw the plotted dot first (already drawn above)
                // then draw a hollow ring around it to indicate live
                drawCircle(
                    color = livePointColor,
                    center = Offset(liveX, liveY),
                    radius = 12f,
                    style = Stroke(width = 3f)
                )
            } else {
                // draw hollow + inner translucent fill to indicate live
                drawCircle(color = livePointColor, center = Offset(liveX, liveY), radius = 10f, style = Stroke(width = 3f))
                drawCircle(color = livePointColor.copy(alpha = 0.35f), center = Offset(liveX, liveY), radius = 5f)
            }

            // faint vertical guide for live freq
            drawLine(start = Offset(liveX, innerBottom), end = Offset(liveX, innerTop), color = livePointColor.copy(alpha = 0.06f), strokeWidth = 1f)

        }
    }
}


/**
 * Simple AudioEngine that plays a continuous sine wave until stop() is called.
 * NOTE: amplitude-to-dB mapping is approximate; real calibration requires hardware.
 */
/**
 * AudioEngine: plays a short sine burst (suspending) for a given durationMs.
 * Call from a CoroutineScope: scope.launch { audioEngine.playTone(... ) }
 * Call audioEngine.stop() to cancel early.
 */


class AudioEngine {
    private var track: AudioTrack? = null
    private var playJob: Job? = null

    /**
     * Play a tone to LEFT or RIGHT ear.
     * ear = "Left" or "Right"
     */
    suspend fun playToneStereoWithLeak(
        frequencyHz: Int,
        db: Float,
        ear: String,
        durationMs: Long = 500L,         // use longer default for bluetooth
        leakPercent: Float = 0.02f,      // 2% by default for Bluetooth-ish devices
        rampMs: Int = 12                 // 12 ms linear fade in/out to avoid clicks
    ) {
        stop()

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(4096)

        track = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBuf,
            AudioTrack.MODE_STREAM
        )

        // amplitude from dB (same mapping you used)
        val amp = ((10.0.pow((db - 60.0) / 20.0)) * 0.5).coerceIn(0.0001, 0.99).toFloat()

        // compute leak amplitude as fraction of main amplitude
        val leakAmp = (amp * leakPercent).coerceAtMost(amp)

        val isLeft = ear.equals("Left", true)
        val isRight = ear.equals("Right", true)
        val isBoth = ear.equals("Both", true)

        track?.play()

        val job = CoroutineScope(Dispatchers.Default).launch {
            try {
                val sr = sampleRate
                val freq = frequencyHz.toDouble()
                val bufferSize = 2048
                val stereoBuffer = ShortArray(bufferSize * 2)
                var phase = 0.0
                val twoPiF = 2.0 * Math.PI * freq
                val startTime = System.currentTimeMillis()

                val rampSamples = ((rampMs / 1000.0) * sr).toInt().coerceAtLeast(1)
                val totalSamples = ((durationMs / 1000.0) * sr).toInt()
                var sampleIndex = 0

                while (isActive && System.currentTimeMillis() - startTime < durationMs) {
                    for (i in 0 until bufferSize) {
                        // stop if we've generated all planned samples
                        if (sampleIndex >= totalSamples) break

                        // compute base sine sample in [-1,1]
                        val s = sin(phase).toFloat()
                        // base scaled sample value for main ear
                        val mainVal = (s * amp * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

                        // compute current envelope (ramp in/out)
                        val globalSampleIndex = sampleIndex
                        val env = when {
                            globalSampleIndex < rampSamples -> (globalSampleIndex.toFloat() / rampSamples.toFloat())
                            globalSampleIndex >= totalSamples - rampSamples -> {
                                val tail = totalSamples - globalSampleIndex
                                (tail.coerceAtLeast(0).toFloat() / rampSamples.toFloat())
                            }
                            else -> 1.0f
                        }.coerceIn(0f, 1f)

                        val gatedMain = (mainVal * env).toInt()

                        // leakage sample is same shape scaled by leakAmp
                        val leakVal = if (amp > 0f) (s * leakAmp * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()) else 0

                        // handle the three modes: Left, Right, Both
                        val leftValue: Int = when {
                            isBoth -> gatedMain
                            isLeft -> gatedMain
                            else -> leakVal
                        }

                        val rightValue: Int = when {
                            isBoth -> gatedMain
                            isRight -> gatedMain
                            else -> leakVal
                        }

                        stereoBuffer[i * 2] = leftValue.toShort()
                        stereoBuffer[i * 2 + 1] = rightValue.toShort()

                        phase += twoPiF / sr
                        if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI

                        sampleIndex++
                    }

                    // If we broke early because sampleIndex >= totalSamples, write only the portion filled
                    val toWrite = if (sampleIndex >= totalSamples) {
                        // compute how many shorts actually filled in last buffer
                        val filledFrames = (sampleIndex % bufferSize).let { if (it == 0) bufferSize else it }
                        filledFrames * 2
                    } else {
                        stereoBuffer.size
                    }

                    try {
                        track?.write(stereoBuffer, 0, toWrite)
                    } catch (_: Exception) {
                        break
                    }
                }
            } finally {
                try { track?.stop() } catch (_: Exception) {}
                try { track?.flush(); track?.release() } catch (_: Exception) {}
                track = null
            }
        }

        playJob = job
        try { job.join() } catch (_: CancellationException) { }
        playJob = null
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        track?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.flush() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        track = null
    }
}

@Composable
fun ViewResultsScreen(
    onOpen: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onAnalyze: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val savedFlow = remember { SavedTestsManager.getAll(context) }
    val list by savedFlow.collectAsState(initial = emptyList())

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SavedTestsManager.SavedRun?>(null) }

    // use a coroutine scope for event handlers (delete actions, etc.)
    val scope = rememberCoroutineScope()

    Scaffold(topBar = {
        SmallTopAppBar(
            title = { Text("Saved Tests") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    })
    { padding ->
        if (list.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No saved tests yet.", modifier = Modifier.padding(16.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                items(list) { run ->
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(run.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${run.ear} — ${
                                            SimpleDateFormat("yyyy-MM-dd HH:mm").format(
                                                Date(run.timestamp)
                                            )}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text("Frequencies: ${run.thresholds.size}", style = MaterialTheme.typography.bodySmall)
                                }

                                // delete button (uses coroutine scope inside onClick)
                                IconButton(onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        deleteTarget = run
                                        showDeleteDialog = true
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row {
                                Button(onClick = { onOpen(run.name) }) { Text("Open") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { onAnalyze(run.name) }) { Text("View Analysis") }
                                // Add more actions if desired
                            }
                        }
                    }
                }
            }
            // Delete confirmation dialog
            if (showDeleteDialog && deleteTarget != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Delete this test?") },
                    text = { Text("Are you sure you want to delete this saved test? This action cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            // Capture name then close dialog immediately
                            val nameToDelete = deleteTarget!!.name
                            showDeleteDialog = false
                            deleteTarget = null

                            // Perform deletion on IO thread
                            scope.launch(Dispatchers.IO) {
                                SavedTestsManager.deleteTest(context, nameToDelete)
                            }
                        }) {
                            Text("Delete", color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showDeleteDialog = false
                            deleteTarget = null
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }

        }
    }
}

@Composable
fun SavedTestDetailScreen(
    savedRun: SavedTestsManager.SavedRun,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text(savedRun.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Ear tested: ${savedRun.ear}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            Text(
                "Date: ${
                    SimpleDateFormat("yyyy-MM-dd HH:mm")
                        .format(Date(savedRun.timestamp))
                }"
            )
            Spacer(Modifier.height(12.dp))

            // 📊 Show the graph with saved points
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Box(Modifier.padding(8.dp)) {
                    AudiogramCanvas(
                        frequencies = listOf(250,500,1000,2000,3000,4000,6000,8000),
                        plottedPoints = savedRun.thresholds,
                        currentFreq = 1000,     // irrelevant in saved view
                        currentDb = 40f,        // irrelevant
                        minDb = 0f,
                        maxDb = 120f
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Thresholds:", style = MaterialTheme.typography.titleMedium)

            savedRun.thresholds.sortedBy { it.freq }.forEach { fp ->
                Text("${fp.freq} Hz → ${fp.db.toInt()} dB")
            }
        }
    }
}

// ---------- AnalysisScreen with run-selection + freq-selection ----------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisScreen(
    savedRun: SavedTestsManager.SavedRun,
    allRuns: List<SavedTestsManager.SavedRun>,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Selected primary and secondary run names (primary defaults to the tapped run)
    var primaryName by remember { mutableStateOf(savedRun.name) }
    var secondaryName by remember { mutableStateOf<String?>(null) }

    // Show/hide the inline edit controls
    var showEditControls by remember { mutableStateOf(false) }

    // Frequency selection (default PTA freq set)
    val defaultFreqs = listOf(500, 1000, 2000)
    val allFreqs = listOf(250, 500, 1000, 2000, 3000, 4000, 6000, 8000)
    var selectedFreqs by remember { mutableStateOf(defaultFreqs.toMutableSet()) }

    // Derived selected runs
    val primaryRun = allRuns.find { it.name == primaryName } ?: savedRun
    val secondaryRun = secondaryName?.let { allRuns.find { r -> r.name == it } }

    // compute metrics with chosen frequency set
    val primaryMetrics by remember(primaryRun, selectedFreqs) {
        mutableStateOf(computeMetricsFromRun(primaryRun, selectedFreqs.toList()))
    }
    val secondaryMetrics by remember(secondaryRun, selectedFreqs) {
        mutableStateOf(secondaryRun?.let { computeMetricsFromRun(it, selectedFreqs.toList()) })
    }

    val primarySeverity = classifyHearingLoss(primaryMetrics.pta)
    val secondarySeverity = secondaryMetrics?.let { classifyHearingLoss(it.pta) }
    val symmetryDb = secondaryMetrics?.let { abs(primaryMetrics.pta - it.pta) }
    val symmetryLabel = when {
        symmetryDb == null -> "Not Compared"
        symmetryDb <= 15 -> "Symmetric"
        else -> "Asymmetric"
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Analysis") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // REAL SHARE BUTTON LOGIC (only one)

// inside actions = { ... }
                    val activity = LocalContext.current as? Activity
                    val scope = rememberCoroutineScope()
                    IconButton(onClick = {
                        // quick immediate feedback on device
                        activity?.let { a ->
                            Toast.makeText(a, "Share button clicked", Toast.LENGTH_SHORT).show()
                        }

                        Log.d("PDF_EXPORT", "Share CLICKED (onClick) - activity=${activity != null}")

                        if (activity == null) {
                            Log.e("PDF_EXPORT", "Activity is null - cannot proceed")
                            return@IconButton
                        }

                        scope.launch {
                            try {
                                Log.d("PDF_EXPORT", "Coroutine started")
                                val metrics = activity.resources.displayMetrics
                                val pageWidth = metrics.widthPixels
                                val pageHeight = metrics.heightPixels
                                Log.d("PDF_EXPORT", "Metrics: ${pageWidth}x${pageHeight}")

                                Log.d("PDF_EXPORT", "About to capture composable -> this may take a moment")
                                val bitmap = captureComposableAsBitmap(activity, pageWidth) {
                                    // debug inside the printable composable (these logs are inside the ComposeView render)
                                    Log.d("PDF_EXPORT", "Inside printable composable: before AnalysisPrintableContent()")
                                    AnalysisPrintableContent(
                                        primaryRun = primaryRun,
                                        secondaryRun = secondaryRun,
                                        allFreqs = allFreqs,
                                        selectedFreqs = selectedFreqs.sorted().toList(),
                                        primaryMetrics = primaryMetrics,
                                        secondaryMetrics = secondaryMetrics
                                    )
                                    Log.d("PDF_EXPORT", "Inside printable composable: after AnalysisPrintableContent()")
                                }
                                Log.d("PDF_EXPORT", "Bitmap captured: ${bitmap.width} x ${bitmap.height}")

                                val pdfFile = withContext(Dispatchers.Default) {
                                    Log.d("PDF_EXPORT", "Creating PDF file (background thread)")
                                    // use the paginated function if you have it; fallback to createPdfFromBitmap if short
                                    createPdfFromBitmap(activity, bitmap, pageWidth, pageHeight)
                                }
                                Log.d("PDF_EXPORT", "PDF created at ${pdfFile.absolutePath}")

                                sharePdfFile(activity, pdfFile)
                                Log.d("PDF_EXPORT", "sharePdfFile() called")

                            } catch (e: Throwable) {
                                Log.e("PDF_EXPORT", "Error during export", e)
                                // show the error on device so you don't have to hunt logs
                                activity?.runOnUiThread {
                                    Toast.makeText(activity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share Analysis")
                    }
                }
            )
        }
    ) { padding ->

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState)
                .fillMaxWidth()
                .padding(16.dp)
        ) {

            // ===========================
            // SUMMARY CARD
            // ===========================
            Text("Considering:", style = MaterialTheme.typography.titleMedium)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Hearing Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                    Spacer(Modifier.height(8.dp))

                    Text("Primary Ear (${primaryRun.ear}):", fontWeight = FontWeight.SemiBold)
                    Text("• Severity: $primarySeverity")
                    Text("• PTA: ${primaryMetrics.pta.toInt()} dB")

                    Spacer(Modifier.height(8.dp))

                    if (secondaryRun != null && secondaryMetrics != null) {
                        val overall = computeOverallSeverity(primaryMetrics.pta, secondaryMetrics!!.pta)
                        val overallSeverity = classifyHearingLoss(overall)

                        Text("Secondary Ear (${secondaryRun.ear}):", fontWeight = FontWeight.SemiBold)
                        Text("• Severity: $secondarySeverity")
                        Text("• PTA: ${secondaryMetrics!!.pta.toInt()} dB")

                        Spacer(Modifier.height(8.dp))

                        Text("Symmetry: $symmetryLabel")
                        Text("Difference: ${symmetryDb?.toInt()} dB")

                        Spacer(Modifier.height(12.dp))
                        Text("Overall Hearing Severity:", fontWeight = FontWeight.Bold)
                        Text("• $overallSeverity (PTA ${overall.toInt()} dB)")
                    } else {
                        Text("Secondary Ear: Not selected")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ===========================
            // PRIMARY & SECONDARY CARDS
            // ===========================
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Primary", style = MaterialTheme.typography.bodySmall)
                        Text("${primaryRun.name}  •  ${primaryRun.ear}", fontWeight = FontWeight.SemiBold)
                        Text(
                            SimpleDateFormat("yyyy-MM-dd").format(Date(primaryRun.timestamp)),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Compare (optional)", style = MaterialTheme.typography.bodySmall)
                        if (secondaryRun != null) {
                            Text("${secondaryRun.name} • ${secondaryRun.ear}", fontWeight = FontWeight.SemiBold)
                            Text(
                                SimpleDateFormat("yyyy-MM-dd").format(Date(secondaryRun.timestamp)),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text("None selected", fontStyle = FontStyle.Italic)
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                Button(onClick = { showEditControls = !showEditControls }) {
                    Text(if (showEditControls) "Done" else "Edit")
                }
            }

            Spacer(Modifier.height(12.dp))

            // ===========================
            // EDIT CONTROLS
            // ===========================
            if (showEditControls) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Select runs to include", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))

                        // PRIMARY DROPDOWN
                        var primaryExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { primaryExpanded = true }) {
                                Text("Primary: $primaryName")
                            }
                            DropdownMenu(expanded = primaryExpanded, onDismissRequest = { primaryExpanded = false }) {
                                allRuns.forEach { r ->
                                    DropdownMenuItem(
                                        text = { Text("${r.name} (${r.ear})") },
                                        onClick = {
                                            primaryName = r.name
                                            primaryExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // SECONDARY DROPDOWN
                        var secondaryExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { secondaryExpanded = true }) {
                                Text("Secondary: ${secondaryName ?: "None"}")
                            }
                            DropdownMenu(expanded = secondaryExpanded, onDismissRequest = { secondaryExpanded = false }) {
                                DropdownMenuItem(text = { Text("None") }, onClick = {
                                    secondaryName = null
                                    secondaryExpanded = false
                                })
                                allRuns.filter { it.name != primaryName }.forEach { r ->
                                    DropdownMenuItem(
                                        text = { Text("${r.name} (${r.ear})") },
                                        onClick = {
                                            secondaryName = r.name
                                            secondaryExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // FREQUENCY CHIPS
                        Text("Frequencies used for PTA / averages", style = MaterialTheme.typography.bodySmall)

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            allFreqs.forEach { f ->
                                val label = if (f >= 1000) "${f / 1000}k" else "$f"
                                val selected = selectedFreqs.contains(f)

                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedFreqs = selectedFreqs.toMutableSet().also {
                                            if (selected) it.remove(f) else it.add(f)
                                        }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            // ===========================
            // PRIMARY GRAPH
            // ===========================
            Text("Graph (Primary)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Box(Modifier.padding(8.dp)) {
                    AudiogramCanvas(
                        frequencies = allFreqs,
                        plottedPoints = primaryRun.thresholds,
                        currentFreq = primaryRun.thresholds.firstOrNull()?.freq ?: 1000,
                        currentDb = primaryRun.thresholds.firstOrNull()?.db ?: 40f,
                        minDb = 0f,
                        maxDb = 120f
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ===========================
            // SINGLE EAR METRICS
            // ===========================
            Text("Single-ear metrics (Primary)", style = MaterialTheme.typography.titleMedium)
            MetricRow("PTA (${selectedFreqs.sorted().joinToString(", ") { if (it >= 1000) "${it/1000}k" else "$it" }})", "${primaryMetrics.pta.toInt()} dB")
            MetricRow("Avg (selected freqs)", "${primaryMetrics.avg.toInt()} dB")
            MetricRow("Category", primaryMetrics.category)
            MetricRow("Severity", classifyHearingLoss(primaryMetrics.pta))

            Spacer(Modifier.height(12.dp))

            // ===========================
            // SECONDARY + BINAURAL METRICS
            // ===========================
            if (secondaryRun != null && secondaryMetrics != null) {
                Divider()
                Spacer(Modifier.height(8.dp))

                Text("Secondary (for comparison)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(Modifier.padding(8.dp)) {
                        AudiogramCanvas(
                            frequencies = allFreqs,
                            plottedPoints = secondaryRun.thresholds,
                            currentFreq = secondaryRun.thresholds.firstOrNull()?.freq ?: 1000,
                            currentDb = secondaryRun.thresholds.firstOrNull()?.db ?: 40f,
                            minDb = 0f,
                            maxDb = 120f
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text("Binaural / comparison metrics", style = MaterialTheme.typography.titleMedium)
                MetricRow("Primary PTA", "${primaryMetrics.pta.toInt()} dB")
                MetricRow("Secondary PTA", "${secondaryMetrics!!.pta.toInt()} dB")
                MetricRow("Secondary Severity", classifyHearingLoss(secondaryMetrics!!.pta))
                MetricRow("Symmetry (abs diff)", "${symmetryDb?.toInt()} dB")
                if (symmetryDb != null) {
                    MetricRow("Symmetry interpretation", if (symmetryDb <= 15f) "Symmetric" else "Asymmetric")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun AnalysisPrintableContent(
    primaryRun: SavedTestsManager.SavedRun,
    secondaryRun: SavedTestsManager.SavedRun?,
    allFreqs: List<Int>,
    selectedFreqs: List<Int>,
    primaryMetrics: EarMetrics,
    secondaryMetrics: EarMetrics?
) {
    // static layout for PDF (no interactive elements)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp)
            .background(Color.White) // ensure white background for PDF
    ) {
        // Header
        Text("Hearing Analysis Report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        // Summary card
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Summary", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Primary: ${primaryRun.name} — ${primaryRun.ear}")
                Text("PTA: ${primaryMetrics.pta.toInt()} dB — ${classifyHearingLoss(primaryMetrics.pta)}")
                if (secondaryRun != null && secondaryMetrics != null) {
                    Spacer(Modifier.height(6.dp))
                    Text("Secondary: ${secondaryRun.name} — ${secondaryRun.ear}")
                    Text("PTA: ${secondaryMetrics.pta.toInt()} dB — ${classifyHearingLoss(secondaryMetrics.pta)}")
                    Spacer(Modifier.height(6.dp))
                    val diff = abs(primaryMetrics.pta - secondaryMetrics.pta)
                    Text("Symmetry: ${if (diff <= 15f) "Symmetric" else "Asymmetric"} (diff ${diff.toInt()} dB)")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Combined graph if both runs present, otherwise single ear graph
        if (secondaryRun != null) {
            Text("Combined Audiogram", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(Color(0xFFF6F4FB), RoundedCornerShape(8.dp))) {
                CombinedAudiogramCanvas(
                    frequencies = allFreqs,
                    primary = primaryRun,
                    secondary = secondaryRun,
                    minDb = 0f,
                    maxDb = 120f
                )
            }
        } else {
            Text("Audiogram", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(Color(0xFFF6F4FB), RoundedCornerShape(8.dp))) {
                AudiogramCanvas(
                    frequencies = allFreqs,
                    plottedPoints = primaryRun.thresholds,
                    currentFreq = primaryRun.thresholds.firstOrNull()?.freq ?: 1000,
                    currentDb = primaryRun.thresholds.firstOrNull()?.db ?: 40f,
                    minDb = 0f,
                    maxDb = 120f
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Detailed thresholds table
        Text("Thresholds (Hz → dB)", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Column {
            (primaryRun.thresholds.sortedBy { it.freq }).forEach { t ->
                Text("${t.freq} Hz -> ${t.db.toInt()} dB")
            }
        }

        if (secondaryRun != null) {
            Spacer(Modifier.height(8.dp))
            Text("Secondary thresholds", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Column {
                (secondaryRun.thresholds.sortedBy { it.freq }).forEach { t ->
                    Text("${t.freq} Hz -> ${t.db.toInt()} dB")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Generated by Hearing Test App", style = MaterialTheme.typography.bodySmall)
    }
}

fun classifyHearingLoss(pta: Float): String {
    return when {
        pta <= 20 -> "Normal"
        pta <= 34 -> "Mild"
        pta <= 49 -> "Moderate"
        pta <= 64 -> "Moderately Severe"
        pta <= 79 -> "Severe"
        pta <= 94 -> "Profound"
        else -> "Complete / Total Loss"
    }
}

fun computeOverallSeverity(rightPta: Float, leftPta: Float): Float {
    val better = minOf(rightPta, leftPta)
    val worse = maxOf(rightPta, leftPta)
    return (4 * better + worse) / 5f
}

// small reusable MetricRow (ensure unique name if you already have it)
@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

suspend fun captureComposableAsBitmap(
    activity: Activity,
    widthPx: Int,
    composable: @Composable () -> Unit
): Bitmap = withContext(Dispatchers.Main) {
    // Create ComposeView and set content
    val composeView = ComposeView(activity).apply {
        // make sure background is white for PDF clarity
        setContent {
            Surface(color = Color.White) {
                composable()
            }
        }
        // keep invisible so user doesn't see flicker
        visibility = View.INVISIBLE
    }

    // Attach to activity decorView so Compose has a WindowRecomposer
    val rootView = activity.window?.decorView as? ViewGroup
        ?: throw IllegalStateException("Activity does not have decorView")

    try {
        // Add to root with wrap_content height so it can expand vertically
        val lp = FrameLayout.LayoutParams(widthPx, FrameLayout.LayoutParams.WRAP_CONTENT)
        rootView.addView(composeView, lp)

        // Measure & layout (exact width, unspecified height so content expands)
        val widthSpec = MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        composeView.measure(widthSpec, heightSpec)
        composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

        // Render to bitmap
        val bitmap = Bitmap.createBitmap(
            composeView.measuredWidth.coerceAtLeast(1),
            composeView.measuredHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        composeView.draw(canvas)

        bitmap
    } finally {
        // clean up - remove the view so it doesn't stay attached
        try { rootView.removeView(composeView) } catch (_: Throwable) {}
    }
}

fun createPdfFromBitmap(context: Context, bitmap: Bitmap, pageWidth: Int, pageHeight: Int): File {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas
    canvas.drawBitmap(bitmap, 0f, 0f, null)
    pdfDocument.finishPage(page)

    val file = File(context.cacheDir, "analysis_report_${System.currentTimeMillis()}.pdf")
    val outputStream = FileOutputStream(file)
    pdfDocument.writeTo(outputStream)
    pdfDocument.close()
    return file
}

fun sharePdfFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share analysis PDF"))
}

fun isHeadphonesConnected(context: Context): Boolean {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    return devices.any {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
    }
}

private val liveAudioEngine = LiveAudioEngine()

@Composable
fun LiveAudioScreen(onBack: () -> Unit,liveAudioEngine: LiveAudioEngine,
                    onOpenUploadedFiles: () -> Unit) {


    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var noiseCancellationEnabled by remember { mutableStateOf(false) }

    // -------- Engine --------
    val engine = remember {
        LiveAudioEngine().apply {
            this.context = context.applicationContext
        }
    }

    val waveform = remember { mutableStateListOf<Float>() }
    var running by remember { mutableStateOf(false) }

    // -------- STEP 4: Engine auto-stop callback (PERSISTENT & SAFE) --------
    engine.onStopped = {
        running = false
        Toast.makeText(
            context,
            "Live listening stopped automatically for safety",
            Toast.LENGTH_SHORT
        ).show()
    }

    // -------- AudioManager for headphone detection --------
    val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // -------- Permission launcher (ONLY on button click) --------
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && !running) {
            running = true
            engine.start(
                scope = scope,
                waveform = waveform,
                noiseCancellationEnabled = noiseCancellationEnabled
            )

        } else if (!granted) {
            Toast.makeText(
                context,
                "Microphone permission is required to start listening",
                Toast.LENGTH_LONG
            ).show()
        }
    }




    // -------- Headphone unplug detection --------
    val deviceCallback = remember {
        object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                val stillConnected = audioManager
                    .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .any {
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    }

                if (!stillConnected && running) {
                    running = false
                    engine.stop()

                    Toast.makeText(
                        context,
                        "Headphones disconnected. Live listening stopped.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // -------- Register / unregister callbacks safely --------
    DisposableEffect(Unit) {
        audioManager.registerAudioDeviceCallback(deviceCallback, null)

        onDispose {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            running = false
            engine.stop()
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult

        val success = UploadedFilesManager.save(context, uri)

        Toast.makeText(
            context,
            if (success) "Audio file uploaded" else "File too large (max 20MB)",
            Toast.LENGTH_LONG
        ).show()
    }
    // -------- UI --------
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Live Listening") },
                navigationIcon = {
                    IconButton(onClick = {
                        running = false
                        engine.stop()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            AudioWaveform(waveform)

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {

                    // ---- Stop ----
                    if (running) {
                        running = false
                        engine.stop()
                        return@Button
                    }

                    // ---- Start ----
                    val hasPermission =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        running = true
                        engine.start(
                            scope = scope,
                            waveform = waveform,
                            noiseCancellationEnabled = noiseCancellationEnabled
                        )

                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            ) {
                Text(if (running) "Stop Listening" else "Start Listening")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Noise Cancellation",
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = noiseCancellationEnabled,
                    onCheckedChange = {
                        noiseCancellationEnabled = it
                        liveAudioEngine.setNoiseCancellationEnabled(it)
                    }
                )
            }


            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    uploadLauncher.launch(arrayOf("audio/*"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Upload Audio File")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onOpenUploadedFiles,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Uploaded Files")
            }

        }
    }
}


@Composable
fun AudioWaveform(samples: List<Float>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color.Black)
    ) {
        if (samples.isEmpty()) return@Canvas

        val mid = size.height / 2
        val step = size.width / samples.size

        samples.forEachIndexed { i, amp ->
            val x = i * step
            val y = amp * mid
            drawLine(
                color = Color.Green,
                start = Offset(x, mid - y),
                end = Offset(x, mid + y),
                strokeWidth = 2f
            )
        }
    }
}

@Composable
fun UploadedFilesScreen(onBack: () -> Unit) {

    val context = LocalContext.current
    var files by remember { mutableStateOf(UploadedFilesManager.getAll(context)) }
    var selected by remember { mutableStateOf<UploadedAudio?>(null) }
    var currentFile by remember { mutableStateOf<UploadedAudio?>(null) }

    var isEnhancing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val wavPlayer = remember(context.applicationContext) {
        WavAudioPlayer(context.applicationContext)
    }


    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0) }
    var position by remember { mutableStateOf(0) }

    LaunchedEffect(wavPlayer) {

        wavPlayer.onDurationReady = { d ->
            duration = d
        }

        wavPlayer.onPositionUpdate = { p ->
            position = p
        }

        wavPlayer.onCompleted = {
            isPlaying = false
            position = duration
        }
    }


    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Uploaded Files") },
                navigationIcon = {
                    IconButton(onClick = {
                        wavPlayer.stop()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {


            if (isEnhancing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 🔹 FILE LIST
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(files) { file ->
                    ListItem(
                        modifier = Modifier.clickable { selected = file },
                        headlineContent = { Text(file.name) },
                        supportingContent = {
                            Text("${file.sizeBytes / 1024} KB")
                        }
                    )
                    Divider()
                }
            }

            // 🔹 PLAYER CONTROLS (only if something is selected)
            if (currentFile != null) {
                PlayerControls(
                    isPlaying = isPlaying,
                    position = position,
                    duration = duration,
                    onPlayPause = {
                        Log.d("UI", "Play/Pause clicked")

                        if (isPlaying) {
                            // ⏸ Pause
                            wavPlayer.pause()
                            isPlaying = false
                        } else {
                            // ▶ Resume OR start
                            if (position > 0) {
                                wavPlayer.resume()
                            } else {
                                currentFile?.let {
                                    wavPlayer.play(
                                        file = File(it.filePath),
                                        scope = scope
                                    )
                                }
                            }
                            isPlaying = true
                        }

                    }
                )
            }
        }
    }

    // 🔹 OPTIONS DIALOG
    selected?.let { file ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(file.name) },
            confirmButton = {
                Column {
                    TextButton(
                        onClick = {
                            try {

                                currentFile = file

                                wavPlayer.play(
                                    file = File(file.filePath),
                                    scope = scope
                                )

                                isPlaying = true
                                selected = null

                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Cannot play WAV file",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Text("Play Original")
                    }

                    TextButton(
                        onClick = {

                            // 1️⃣ Close dialog immediately
                            selected = null
                            isEnhancing = true

                            scope.launch {
                                try {
                                    // 2️⃣ Heavy work off UI thread
                                    val enhancedFile = withContext(Dispatchers.IO) {

                                        val inputFile = File(file.filePath)

                                        // 🔹 Read WAV (mono FloatArray)
                                        val wav = WavUtils.readWav(inputFile)

                                        // 🔹 REAL COMPLEX ALGORITHM: Spectral Subtraction
                                        val enhancedSamples = SpectralSubtraction.enhance(
                                            wav.samples,
                                            wav.sampleRate
                                        )

                                        // 🔹 Write enhanced WAV
                                        val outFile = File(
                                            context.cacheDir,
                                            "enhanced_${file.id}.wav"
                                        )

                                        WavUtils.writeWav(
                                            outFile,
                                            enhancedSamples,
                                            wav.sampleRate
                                        )

                                        outFile
                                    }

                                    // 3️⃣ Back on UI thread: play audio
                                    isEnhancing = false

                                    wavPlayer.stop()
                                    currentFile = file

                                    wavPlayer.play(
                                        file = enhancedFile,
                                        scope = scope
                                    )

                                    isPlaying = true

                                } catch (e: Exception) {
                                    isEnhancing = false
                                    e.printStackTrace()
                                    Toast.makeText(
                                        context,
                                        "Enhancement failed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    ) {
                        Text("Play Enhanced")
                    }






                    TextButton(
                        onClick = {
                            wavPlayer.stop()
                            currentFile = null
                            UploadedFilesManager.delete(context, file.id)
                            files = UploadedFilesManager.getAll(context)
                            selected = null
                        }
                    ) {
                        Text("Delete", color = Color.Red)
                    }
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            wavPlayer.stop()
        }
    }
}


@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    position: Int,
    duration: Int,
    onPlayPause: () -> Unit
) {
    val safeDuration = if (duration > 0) duration else 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onPlayPause,
            modifier = Modifier.size(80.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = if (isPlaying)
                    Icons.Default.Pause
                else
                    Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        val safeDuration = if (duration > 0) duration else 1

        Text("${position / 1000}s / ${safeDuration / 1000}s")

        LinearProgressIndicator(
            progress = if (duration > 0)
                position.toFloat() / duration.toFloat()
            else 0f,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(6.dp)
        )
    }
}


