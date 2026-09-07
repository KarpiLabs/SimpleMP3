package io.karpilabs.simplemp3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.data.scrobble.ScrobbleConfig
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette

@Composable
fun ScrobblingScreen(
    config: ScrobbleConfig,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onProviderChange: (String) -> Unit,
    onSaveListenBrainzToken: (String) -> Unit,
    onLoginLastfm: (apiKey: String, apiSecret: String, username: String, password: String, onResult: (Boolean) -> Unit) -> Unit,
    onLogoutLastfm: () -> Unit,
) {
    val palette = LocalSimpleMP3Palette.current

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Scrobbling",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Enable scrobbling",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Send the tracks you play to your listening history",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                        }
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = onEnabledChange,
                            colors =
                                SwitchDefaults.colors(
                                    checkedThumbColor = palette.card,
                                    checkedTrackColor = AccentTeal,
                                    uncheckedThumbColor = palette.textMuted,
                                    uncheckedTrackColor = palette.textMuted.copy(alpha = 0.3f),
                                ),
                        )
                    }
                }
            }

            if (config.enabled) {
                item {
                    Card {
                        Text(
                            "Provider",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        ProviderRow(
                            label = "ListenBrainz",
                            selected = config.isListenBrainz,
                            onClick = { onProviderChange(ScrobbleConfig.PROVIDER_LISTENBRAINZ) },
                        )
                        ProviderRow(
                            label = "Last.fm",
                            selected = config.isLastfm,
                            onClick = { onProviderChange(ScrobbleConfig.PROVIDER_LASTFM) },
                        )
                    }
                }

                if (config.isListenBrainz) {
                    item { ListenBrainzCard(config, onSaveListenBrainzToken) }
                }
                if (config.isLastfm) {
                    item { LastfmCard(config, onLoginLastfm, onLogoutLastfm) }
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val palette = LocalSimpleMP3Palette.current
    Column(
        modifier =
            Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.card)
                .padding(16.dp),
        content = content,
    )
}

@Composable
private fun ProviderRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.height(0.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ListenBrainzCard(
    config: ScrobbleConfig,
    onSave: (String) -> Unit,
) {
    val palette = LocalSimpleMP3Palette.current
    var token by remember(config.listenBrainzToken) { mutableStateOf(config.listenBrainzToken) }
    Card {
        Text(
            "ListenBrainz token",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Paste your user token from listenbrainz.org → Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            singleLine = true,
            placeholder = { Text("Token") },
            modifier = Modifier.fillMaxWidth(),
            colors = tealFieldColors(),
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onSave(token) },
            enabled = token.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
        ) {
            Text("Save token")
        }
        if (config.listenBrainzToken.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            SavedRow("Token saved")
        }
    }
}

@Composable
private fun LastfmCard(
    config: ScrobbleConfig,
    onLogin: (String, String, String, String, (Boolean) -> Unit) -> Unit,
    onLogout: () -> Unit,
) {
    val palette = LocalSimpleMP3Palette.current
    var apiKey by remember(config.lastfmApiKey) { mutableStateOf(config.lastfmApiKey) }
    var apiSecret by remember(config.lastfmApiSecret) { mutableStateOf(config.lastfmApiSecret) }
    var username by remember(config.lastfmUsername) { mutableStateOf(config.lastfmUsername) }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Card {
        Text(
            "Last.fm account",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Create an API account at last.fm/api and paste the key + secret, then log in.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        if (config.lastfmSessionKey.isNotBlank()) {
            SavedRow("Logged in as ${config.lastfmUsername}")
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onLogout) { Text("Log out", color = AccentTeal) }
        } else {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                singleLine = true,
                placeholder = { Text("API key") },
                modifier = Modifier.fillMaxWidth(),
                colors = tealFieldColors(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiSecret,
                onValueChange = { apiSecret = it },
                singleLine = true,
                placeholder = { Text("Shared secret") },
                modifier = Modifier.fillMaxWidth(),
                colors = tealFieldColors(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                singleLine = true,
                placeholder = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                colors = tealFieldColors(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                placeholder = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = tealFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    busy = true
                    status = null
                    onLogin(apiKey.trim(), apiSecret.trim(), username.trim(), password) { ok ->
                        busy = false
                        status = if (ok) "Logged in" else "Login failed — check credentials"
                        if (ok) password = ""
                    }
                },
                enabled =
                    !busy &&
                        apiKey.isNotBlank() &&
                        apiSecret.isNotBlank() &&
                        username.isNotBlank() &&
                        password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
            ) {
                Text(if (busy) "Logging in…" else "Log in")
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
        }
    }
}

@Composable
private fun SavedRow(text: String) {
    val palette = LocalSimpleMP3Palette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AccentTeal)
        Spacer(Modifier.height(0.dp))
        Text("  $text", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
    }
}

@Composable
private fun tealFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AccentTeal,
        cursorColor = AccentTeal,
        focusedLabelColor = AccentTeal,
    )
