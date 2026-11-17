package app.slotwatcher.alarm

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LockPerson
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.slotwatcher.ui.Brand
import app.slotwatcher.ui.Space
import app.slotwatcher.ui.SlotWatcherTheme

/** Full-screen alarm, shown over the lock screen (FR-18). */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            SlotWatcherTheme(dark = true) {
                app.slotwatcher.ui.StatusBarIcons(light = true)
                val info by AlarmRingService.current.collectAsState()
                LaunchedEffect(info) { if (info == null) finish() }
                info?.let {
                    AlarmScreen(it) {
                        AlarmRingService.dismiss()
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmScreen(info: AlarmInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val reduceMotion = remember {
        runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE) == 0f
        }.getOrDefault(false)
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (reduceMotion) 1f else 1.08f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale",
    )
    val icon = when (info.kind) {
        AlarmKind.LoginFailed -> Icons.Rounded.LockPerson
        AlarmKind.NoneBooked -> Icons.Rounded.CheckCircle
        else -> Icons.Rounded.NotificationsActive
    }

    Box(Modifier.fillMaxSize().background(Brand.HeroGradient)) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = Space.xl, vertical = Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("SLOT WATCHER", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(Modifier.height(Space.xl))
                Box(
                    Modifier
                        .size(112.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(Brand.Yellow),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = Brand.Navy, modifier = Modifier.size(56.dp))
                }
                Spacer(Modifier.height(Space.xl))
                Text(
                    info.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                info.dateText?.let {
                    Spacer(Modifier.height(Space.sm))
                    Text(it, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(Space.xl))
                info.lines.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(vertical = Space.xs)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(vertical = Space.md, horizontal = Space.lg),
                    )
                }
                Spacer(Modifier.height(Space.xl))
            }
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Brand.Yellow, contentColor = Brand.Navy),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) { Text("Dismiss", style = MaterialTheme.typography.titleLarge) }
            if (info.persistent) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    "The next check still runs on schedule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}
