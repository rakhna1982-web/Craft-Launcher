package com.craftlauncher.android.ui.game

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.craftlauncher.android.ui.theme.AmberSnapshot
import com.craftlauncher.android.ui.theme.CraftLauncherTheme
import com.craftlauncher.android.ui.theme.DiamondCyan
import com.craftlauncher.android.ui.theme.EmeraldAccent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sin

class GameActivity : ComponentActivity() {

    companion object {
        const val EXTRA_INSTANCE_NAME = "extra_instance_name"
        const val EXTRA_MC_VERSION = "extra_mc_version"
        const val EXTRA_ACCOUNT_NAME = "extra_account_name"
        const val EXTRA_NATIVE_READY = "extra_native_ready"
        const val EXTRA_JVM_CMD = "extra_jvm_cmd"
        const val EXTRA_MISSING_LIBS = "extra_missing_libs"
        const val EXTRA_RENDERER = "extra_renderer"
        const val EXTRA_SHOW_FPS = "extra_show_fps"
        const val EXTRA_ENABLE_AUDIO = "extra_enable_audio"

        fun createIntent(
            context: Context,
            instanceName: String,
            mcVersion: String,
            accountName: String,
            nativeReady: Boolean,
            jvmCmdPreview: String,
            missingLibs: String,
            renderer: String,
            showFps: Boolean,
            enableAudio: Boolean
        ): Intent = Intent(context, GameActivity::class.java).apply {
            putExtra(EXTRA_INSTANCE_NAME, instanceName)
            putExtra(EXTRA_MC_VERSION, mcVersion)
            putExtra(EXTRA_ACCOUNT_NAME, accountName)
            putExtra(EXTRA_NATIVE_READY, nativeReady)
            putExtra(EXTRA_JVM_CMD, jvmCmdPreview)
            putExtra(EXTRA_MISSING_LIBS, missingLibs)
            putExtra(EXTRA_RENDERER, renderer)
            putExtra(EXTRA_SHOW_FPS, showFps)
            putExtra(EXTRA_ENABLE_AUDIO, enableAudio)
        }
    }

    private lateinit var glSurfaceView: GLSurfaceView
    private val renderer = VoxelViewportRenderer()

    private val consoleLogs = mutableStateListOf<String>()
    private var fpsState by mutableIntStateOf(0)
    private var selectedHotbarSlot by mutableIntStateOf(0)
    private var pointerCaptured by mutableStateOf(false)
    private var showConsoleDrawer by mutableStateOf(false)
    private var lastKeyEventLabel by mutableStateOf("No key events yet")
    private var cameraYaw by mutableFloatStateOf(25f)
    private var cameraPitch by mutableFloatStateOf(20f)
    private var audioEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        val instanceName = intent.getStringExtra(EXTRA_INSTANCE_NAME) ?: "Minecraft"
        val mcVersion = intent.getStringExtra(EXTRA_MC_VERSION) ?: "1.21.4"
        val accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME) ?: "Player"
        val nativeReady = intent.getBooleanExtra(EXTRA_NATIVE_READY, false)
        val jvmCmd = intent.getStringExtra(EXTRA_JVM_CMD).orEmpty()
        val missingLibs = intent.getStringExtra(EXTRA_MISSING_LIBS).orEmpty()
        val rendererBackend = intent.getStringExtra(EXTRA_RENDERER) ?: "GL4ES"
        val showFps = intent.getBooleanExtra(EXTRA_SHOW_FPS, true)
        audioEnabled = intent.getBooleanExtra(EXTRA_ENABLE_AUDIO, true)

        appendLog("[Engine] Initializing OpenGL ES surface ($rendererBackend)")
        appendLog("[Session] Profile='$accountName' Instance='$instanceName' MC=$mcVersion")
        if (nativeReady) {
            appendLog("[Bridge] Native Pojav/Amethyst libraries verified. Ready for JNI handoff.")
        } else {
            appendLog("[Notice] Native NDK bridge libraries absent: $missingLibs")
            appendLog("[Notice] Running interactive OpenGL ES + Touch/Keyboard/Mouse surface test.")
            showConsoleDrawer = true
        }
        if (jvmCmd.isNotBlank()) {
            appendLog("[JVM Cmd] $jvmCmd")
        }

        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        renderer.onFpsSample = { fps -> runOnUiThread { fpsState = fps } }

        setContent {
            CraftLauncherTheme {
                GameViewportOverlay(
                    glSurfaceView = glSurfaceView,
                    instanceName = instanceName,
                    mcVersion = mcVersion,
                    accountName = accountName,
                    nativeReady = nativeReady,
                    missingLibs = missingLibs,
                    fps = fpsState,
                    showFps = showFps,
                    selectedSlot = selectedHotbarSlot,
                    pointerCaptured = pointerCaptured,
                    lastInput = lastKeyEventLabel,
                    showConsole = showConsoleDrawer,
                    logs = consoleLogs,
                    onToggleConsole = { showConsoleDrawer = !showConsoleDrawer },
                    onRequestMouseCapture = { togglePointerCapture() },
                    onLookDelta = { dx, dy ->
                        cameraYaw += dx * 0.35f
                        cameraPitch = (cameraPitch + dy * 0.35f).coerceIn(-89f, 89f)
                        renderer.setRotation(cameraYaw, cameraPitch)
                    },
                    onVirtualKey = { keyName ->
                        lastKeyEventLabel = "Touch -> GLFW_$keyName"
                        appendLog("[Input] Touch -> GLFW_$keyName")
                        playSoftUiTone()
                    },
                    onSelectSlot = { idx ->
                        selectedHotbarSlot = idx
                        lastKeyEventLabel = "Hotbar Slot ${idx + 1}"
                        playSoftUiTone()
                    },
                    onExit = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val name = KeyEvent.keyCodeToString(keyCode)
        lastKeyEventLabel = "HW KeyDown: $name"
        appendLog("[Keyboard] KeyDown $name")
        if (keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9) {
            selectedHotbarSlot = keyCode - KeyEvent.KEYCODE_1
        }
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            showConsoleDrawer = !showConsoleDrawer
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE) ||
            event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)
        ) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                    if (event.historySize > 0) {
                        val dx = event.x - event.getHistoricalX(0)
                        val dy = event.y - event.getHistoricalY(0)
                        cameraYaw += dx * 0.25f
                        cameraPitch = (cameraPitch + dy * 0.25f).coerceIn(-89f, 89f)
                        renderer.setRotation(cameraYaw, cameraPitch)
                        lastKeyEventLabel = "Mouse Δ(${dx.toInt()}, ${dy.toInt()})"
                    }
                    return true
                }
                MotionEvent.ACTION_SCROLL -> {
                    val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                    val delta = if (vScroll > 0) -1 else 1
                    selectedHotbarSlot = (selectedHotbarSlot + delta + 9) % 9
                    lastKeyEventLabel = "Mouse Wheel -> Slot ${selectedHotbarSlot + 1}"
                    return true
                }
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun togglePointerCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (pointerCaptured) {
                window.decorView.releasePointerCapture()
                pointerCaptured = false
                appendLog("[Mouse] Released pointer capture")
            } else {
                window.decorView.requestPointerCapture()
                pointerCaptured = true
                appendLog("[Mouse] Requested Android 8.0+ pointer capture")
            }
        } else {
            appendLog("[Mouse] Pointer capture requires Android 8.0 (API 26)+")
        }
    }

    private fun playSoftUiTone() {
        if (!audioEnabled) return
        Thread {
            runCatching {
                val sampleRate = 22050
                val count = sampleRate / 45
                val samples = ShortArray(count)
                for (i in 0 until count) {
                    val env = 1f - (i.toFloat() / count)
                    samples[i] = (sin(2.0 * Math.PI * i * 660.0 / sampleRate) * 2800 * env).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep(40)
                track.release()
            }
        }.start()
    }

    private fun appendLog(msg: String) {
        if (consoleLogs.size > 120) consoleLogs.removeAt(0)
        consoleLogs.add(msg)
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { ctrl ->
                ctrl.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                ctrl.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
        }
    }
}

@Composable
private fun GameViewportOverlay(
    glSurfaceView: GLSurfaceView,
    instanceName: String,
    mcVersion: String,
    accountName: String,
    nativeReady: Boolean,
    missingLibs: String,
    fps: Int,
    showFps: Boolean,
    selectedSlot: Int,
    pointerCaptured: Boolean,
    lastInput: String,
    showConsole: Boolean,
    logs: List<String>,
    onToggleConsole: () -> Unit,
    onRequestMouseCapture: () -> Unit,
    onLookDelta: (Float, Float) -> Unit,
    onVirtualKey: (String) -> Unit,
    onSelectSlot: (Int) -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Real OpenGL ES 2.0 Surface
        AndroidView(
            factory = { glSurfaceView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onLookDelta(dragAmount.x, dragAmount.y)
                    }
                }
        )

        // Top HUD Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("game_exit_button")
                        .clickable { onExit() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Exit viewport", tint = Color.White)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            text = "$instanceName ($mcVersion) • $accountName",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldAccent
                        )
                        if (showFps) {
                            Text(
                                text = "GLES2 $fps FPS • $lastInput",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudChipButton(label = "F3", onClick = { onVirtualKey("KEY_F3") })
                HudChipButton(label = "CHAT", onClick = { onVirtualKey("KEY_T") })
                HudChipButton(
                    label = if (pointerCaptured) "MOUSE LOCKED" else "MOUSE",
                    accent = if (pointerCaptured) EmeraldAccent else DiamondCyan,
                    onClick = onRequestMouseCapture
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, if (nativeReady) EmeraldAccent else AmberSnapshot),
                    modifier = Modifier.clickable { onToggleConsole() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.Terminal, contentDescription = "Toggle console", tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("LOGS", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
        }

        // Left Virtual WASD D-Pad + Sneak/Sprint
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .safeDrawingPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TouchKeyButton("W", onClick = { onVirtualKey("KEY_W") })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TouchKeyButton("A", onClick = { onVirtualKey("KEY_A") })
                TouchKeyButton("S", onClick = { onVirtualKey("KEY_S") })
                TouchKeyButton("D", onClick = { onVirtualKey("KEY_D") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HudChipButton("SNEAK", onClick = { onVirtualKey("KEY_LEFT_SHIFT") })
                HudChipButton("SPRINT", onClick = { onVirtualKey("KEY_LEFT_CONTROL") })
            }
        }

        // Right Virtual Action Cluster (Attack, Use, Jump, Inv)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TouchKeyButton("ATK", color = Color(0xFFEF4444), onClick = { onVirtualKey("MOUSE_LEFT") })
                TouchKeyButton("USE", color = DiamondCyan, onClick = { onVirtualKey("MOUSE_RIGHT") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudChipButton("INV (E)", onClick = { onVirtualKey("KEY_E") })
                TouchKeyButton("JUMP", color = EmeraldAccent, widthDp = 78, onClick = { onVirtualKey("KEY_SPACE") })
            }
        }

        // Bottom Center Minecraft Hotbar (1-9)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(9) { idx ->
                val selected = idx == selectedSlot
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (selected) EmeraldAccent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f)
                        )
                        .border(
                            width = if (selected) 1.5.dp else 0.5.dp,
                            color = if (selected) EmeraldAccent else Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(5.dp)
                        )
                        .clickable { onSelectSlot(idx) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${idx + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) EmeraldAccent else Color.White
                    )
                }
            }
        }

        // Engine Console & Execution Bridge Diagnostics Drawer
        if (showConsole) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.76f)
                    .fillMaxHeight(0.68f),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xEE0B0E11),
                border = BorderStroke(1.dp, if (nativeReady) EmeraldAccent else AmberSnapshot)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (nativeReady) "Game Execution Console" else "Viewport & Native Bridge Diagnostics",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (nativeReady) EmeraldAccent else AmberSnapshot
                            )
                            if (!nativeReady) {
                                Text(
                                    text = "Missing native JNI libraries ($missingLibs). OpenGL ES, touch HUD, keyboard & mouse capture are live.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                        IconButton(onClick = onToggleConsole) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close console", tint = Color.White)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TouchKeyButton(
    label: String,
    color: Color = Color.White,
    widthDp: Int = 52,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.65f)),
        modifier = Modifier
            .size(width = widthDp.dp, height = 52.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun HudChipButton(
    label: String,
    accent: Color = Color.White,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

/**
 * Real OpenGL ES 2.0 3D cube renderer proving live EGL/GLES surface context, depth buffer,
 * shader compilation, and interactive yaw/pitch rotation from touch/mouse input.
 */
private class VoxelViewportRenderer : GLSurfaceView.Renderer {
    var onFpsSample: ((Int) -> Unit)? = null

    private var program = 0
    private var mvpHandle = 0
    private var posHandle = 0
    private var colorHandle = 0
    private val mvpMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    @Volatile
    private var yaw = 25f
    @Volatile
    private var pitch = 20f

    private var frameCount = 0
    private var lastFpsTime = System.nanoTime()

    private val vertices = floatArrayOf(
        -0.7f, -0.7f, 0.7f,  0.7f, -0.7f, 0.7f,  0.7f, 0.7f, 0.7f,  -0.7f, 0.7f, 0.7f,
        -0.7f, -0.7f, -0.7f, 0.7f, -0.7f, -0.7f, 0.7f, 0.7f, -0.7f, -0.7f, 0.7f, -0.7f
    )
    private val colors = floatArrayOf(
        0.20f, 0.83f, 0.60f, 1f,  0.13f, 0.77f, 0.37f, 1f,  0.22f, 0.74f, 0.97f, 1f,  0.06f, 0.72f, 0.50f, 1f,
        0.04f, 0.35f, 0.22f, 1f,  0.08f, 0.45f, 0.30f, 1f,  0.10f, 0.55f, 0.42f, 1f,  0.05f, 0.25f, 0.18f, 1f
    )
    private val indices = shortArrayOf(
        0, 1, 2, 0, 2, 3,
        1, 5, 6, 1, 6, 2,
        5, 4, 7, 5, 7, 6,
        4, 0, 3, 4, 3, 7,
        3, 2, 6, 3, 6, 7,
        4, 5, 1, 4, 1, 0
    )

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertices); position(0) }
    private val colorBuffer: FloatBuffer = ByteBuffer.allocateDirect(colors.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(colors); position(0) }
    private val indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
        .order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(indices); position(0) }

    fun setRotation(newYaw: Float, newPitch: Float) {
        yaw = newYaw
        pitch = newPitch
    }

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.04f, 0.06f, 0.08f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        val vShader = """
            uniform mat4 uMVPMatrix;
            attribute vec4 aPosition;
            attribute vec4 aColor;
            varying vec4 vColor;
            void main() {
                vColor = aColor;
                gl_Position = uMVPMatrix * aPosition;
            }
        """.trimIndent()
        val fShader = """
            precision mediump float;
            varying vec4 vColor;
            void main() {
                gl_FragColor = vColor;
            }
        """.trimIndent()

        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vShader)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fShader)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs)
            GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
        }
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "aColor")
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.coerceAtLeast(1).toFloat()
        Matrix.perspectiveM(projMatrix, 0, 55f, ratio, 0.1f, 50f)
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 4.2f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, pitch, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, yaw, 0f, 1f, 0f)

        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, tempMatrix, 0)

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 16, colorBuffer)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.size, GLES20.GL_UNSIGNED_SHORT, indexBuffer)
        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)

        frameCount++
        val now = System.nanoTime()
        if (now - lastFpsTime >= 1_000_000_000L) {
            onFpsSample?.invoke(frameCount)
            frameCount = 0
            lastFpsTime = now
        }
    }

    private fun compileShader(type: Int, code: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, code)
            GLES20.glCompileShader(shader)
        }
    }
}
