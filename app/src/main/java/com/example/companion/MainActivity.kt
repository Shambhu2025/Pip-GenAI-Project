package com.example.companion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.companion.core.ConversationManager
import com.example.companion.face.FaceScreen
import com.example.companion.models.ModelDownloader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ModelDownloader.verifyOfflineBundle(this)

        setContent {
            MaterialTheme {
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                val scope = rememberCoroutineScope()
                val manager = remember { ConversationManager(context, scope) }

                val appState by manager.appState.collectAsState()
                val emotion by manager.emotion.collectAsState()
                val mouthOpenness by manager.mouthOpenness.collectAsState()
                val isReady by manager.isReady.collectAsState()
                val lastHeard by manager.lastHeardText.collectAsState()
                val lastReply by manager.lastReplyText.collectAsState()
                val metrics by manager.metrics.collectAsState()
                val unansweredCount by manager.unansweredInitiations.collectAsState()
                val nextIdleSec by manager.secondsUntilNextInit.collectAsState()

                var hasMicPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasMicPermission = granted
                    if (granted) {
                        manager.initializeModels(context) {}
                    }
                }

                LaunchedEffect(Unit) {
                    if (!hasMicPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        manager.initializeModels(context) {}
                    }
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) {
                            manager.pauseForBackground()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        manager.close()
                    }
                }

                FaceScreen(
                    appState = appState,
                    emotion = emotion,
                    mouthOpenness = mouthOpenness,
                    isReady = isReady,
                    lastHeardText = lastHeard,
                    lastReplyText = lastReply,
                    metrics = metrics,
                    unansweredCount = unansweredCount,
                    secondsUntilNextInit = nextIdleSec,
                    onToggleTalk = { manager.toggleListening() },
                    onTriggerIdleNow = { manager.triggerManualIdleTest() },
                    onSelectEmotionPreview = { selected -> manager.setEmotionPreview(selected) }
                )
            }
        }
    }
}
