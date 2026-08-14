package io.karpilabs.simplemp3.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.karpilabs.simplemp3.data.quickconnect.QrCodeEncoder
import io.karpilabs.simplemp3.data.quickconnect.QuickConnectEvent
import io.karpilabs.simplemp3.data.quickconnect.QuickConnectSession
import io.karpilabs.simplemp3.ui.theme.AccentCoral
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.AccentViolet
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette
import io.karpilabs.simplemp3.ui.viewmodel.QuickConnectViewModel

@Composable
fun QuickConnectScreen(
    viewModel: QuickConnectViewModel,
    onBack: () -> Unit,
) {
    val palette = LocalSimpleMP3Palette.current
    val session by viewModel.session.collectAsStateWithLifecycle()

    // Portal lives only while this screen is in the composition tree.
    DisposableEffect(Unit) {
        viewModel.startPortal()
        onDispose { viewModel.stopPortal() }
    }

    if (session.lockedOut) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Quick Connect locked") },
            text = {
                Text(
                    "Too many incorrect access codes were entered, so the portal has been " +
                        "disabled. Go back and reopen Quick Connect to start a fresh session.",
                )
            },
            confirmButton = {
                TextButton(onClick = onBack) {
                    Text("Go back")
                }
            },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quick Connect",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "LAN portal · closes when you leave",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
            }
            Icon(
                imageVector = if (session.running) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                contentDescription = null,
                tint = if (session.running) palette.accent else AccentCoral,
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                StatusHero(session = session)
            }
            item {
                QrCodeCard(session = session)
            }
            item {
                AccessCodeCard(code = session.accessCode, running = session.running)
            }
            item {
                UrlCard(session = session)
            }
            item {
                HowToCard()
            }
            if (session.events.isNotEmpty()) {
                item {
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.accent,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                items(session.events, key = { "${it.timeMs}-${it.message}" }) { event ->
                    EventRow(event)
                }
            }
        }
    }
}

@Composable
private fun StatusHero(session: QuickConnectSession) {
    val palette = LocalSimpleMP3Palette.current
    Box(
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            AccentTeal.copy(alpha = 0.28f),
                            palette.elevated,
                            AccentViolet.copy(alpha = 0.2f),
                        ),
                    ),
                ).padding(20.dp),
    ) {
        Column {
            Text(
                text = if (session.running) "Portal is live" else "Portal offline",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text =
                    when {
                        session.error != null && session.running ->
                            session.error
                        session.running ->
                            "Keep this screen open. On your computer (same Wi‑Fi), open the address below and enter the access code."
                        else ->
                            session.error ?: "Starting…"
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
            )
        }
    }
}

@Composable
private fun QrCodeCard(session: QuickConnectSession) {
    val palette = LocalSimpleMP3Palette.current
    val qrPayload = session.qrUrl ?: session.url
    val bitmap: Bitmap? =
        remember(qrPayload) {
            qrPayload?.let { QrCodeEncoder.encode(it, sizePx = 640) }
        }

    Column(
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.card)
                .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = palette.accent)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Scan to open",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (bitmap != null && session.running) {
            Box(
                modifier =
                    Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                        .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR code for Quick Connect URL",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Includes the access code — unlocks after scan",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textMuted,
                textAlign = TextAlign.Center,
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.elevated),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (session.running) "QR unavailable" else "Waiting…",
                    color = palette.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AccessCodeCard(
    code: String,
    running: Boolean,
) {
    val palette = LocalSimpleMP3Palette.current
    Column(
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.card)
                .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "ACCESS CODE",
            style = MaterialTheme.typography.labelLarge,
            color = palette.accent,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (running && code.isNotBlank()) code else "——————",
            style =
                MaterialTheme.typography.displayLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 8.sp,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Required once on the computer to unlock the portal",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UrlCard(session: QuickConnectSession) {
    val palette = LocalSimpleMP3Palette.current
    val context = LocalContext.current
    val url = session.url

    Column(
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.card)
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Link, contentDescription = null, tint = palette.accent)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Open on your computer",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.elevated)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = url ?: "No LAN IP yet",
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                color = if (url != null) palette.accent else AccentCoral,
                modifier = Modifier.weight(1f),
            )
            if (url != null) {
                IconButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Quick Connect URL", url))
                    },
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copy URL",
                        tint = palette.accent,
                    )
                }
            }
        }
        if (session.ip != null && session.port > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "IP ${session.ip} · port ${session.port}",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textMuted,
            )
        }
    }
}

@Composable
private fun HowToCard() {
    val palette = LocalSimpleMP3Palette.current
    Column(
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.card)
                .padding(16.dp),
    ) {
        Text(
            text = "How it works",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        HowToStep("1", "Phone and computer on the same Wi‑Fi (or phone hotspot).")
        HowToStep("2", "Scan the QR with your computer/camera, or type the address below.")
        HowToStep("3", "Enter the access code if prompted, then drag MP3s or manage playlists.")
        HowToStep("4", "Leave this screen to shut the portal down immediately.")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Uses plain HTTP on your LAN (fine for a temporary local portal). Only LAN-uploaded tracks can be deleted from the portal; your MediaStore library is never wiped.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textMuted,
        )
    }
}

@Composable
private fun HowToStep(
    n: String,
    text: String,
) {
    val palette = LocalSimpleMP3Palette.current
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(n, color = palette.accent, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun EventRow(event: QuickConnectEvent) {
    val palette = LocalSimpleMP3Palette.current
    Row(
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.card)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AccentTeal),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = event.message,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
        )
    }
}
