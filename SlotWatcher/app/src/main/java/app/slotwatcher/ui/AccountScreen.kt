package app.slotwatcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.slotwatcher.R
import app.slotwatcher.data.CredentialStore
import app.slotwatcher.data.Credentials
import app.slotwatcher.net.LoginException
import app.slotwatcher.net.MojaruClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** FR-1 to FR-3: enter, test and save the Mojaru login. Also the first-run welcome. */
@Composable
fun AccountScreen(firstRun: Boolean, onSaved: () -> Unit, onBack: (() -> Unit)?) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val saved = remember { CredentialStore.get(ctx) }
    var login by remember { mutableStateOf(saved?.login.orEmpty()) }
    var password by remember { mutableStateOf(saved?.password.orEmpty()) }
    var listUrl by remember { mutableStateOf(saved?.listUrl.orEmpty()) }
    var showPassword by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(!saved?.listUrl.isNullOrBlank()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val canSubmit = !busy && login.isNotBlank() && password.isNotEmpty()

    fun submit() {
        if (!canSubmit) return
        focus.clearFocus()
        busy = true
        error = null
        success = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val client = MojaruClient()
                    client.findListUrl(client.login(login.trim(), password))
                }
            }
            busy = false
            result.onSuccess { found ->
                CredentialStore.save(ctx, Credentials(login.trim(), password, listUrl))
                if (found != null || listUrl.isNotBlank()) {
                    onSaved()
                } else {
                    showAdvanced = true
                    success = "Signed in, but the Pre Assessment List link wasn't found in the menu. " +
                        "Paste its address below, then save again."
                }
            }.onFailure { e ->
                error = when (e) {
                    is LoginException -> e.message ?: "Mojaru rejected this login."
                    else -> "Couldn't reach Mojaru. Check your internet and try again."
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        HeroHeader {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.padding(bottom = Space.sm)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            } else {
                Spacer(Modifier.height(Space.xl))
            }
            Image(
                painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(Space.lg))
            Text(
                if (firstRun) "Welcome to Slot Watcher" else "Mojaru account",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                "Sign in with your Mojaru Teacher Panel login. Slot Watcher uses it to check your pre-assessment slots.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(Space.sm))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter),
        ) {
            Spacer(Modifier.height(Space.xl))
            OutlinedTextField(
                value = login,
                onValueChange = { login = it; error = null },
                label = { Text("Email or mobile") },
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.md))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                singleLine = true,
                isError = error != null,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                        )
                    }
                },
                supportingText = error?.let { msg ->
                    {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                            Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(Space.xs))
                            Text(msg)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            success?.let {
                Spacer(Modifier.height(Space.sm))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = AppTheme.status.success, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.sm))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }

            TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.padding(top = Space.xs)) {
                Icon(Icons.Rounded.Link, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Space.xs))
                Text(if (showAdvanced) "Hide advanced" else "Advanced")
            }
            AnimatedVisibility(showAdvanced) {
                OutlinedTextField(
                    value = listUrl,
                    onValueChange = { listUrl = it },
                    label = { Text("Pre Assessment List address (optional)") },
                    supportingText = { Text("Leave empty to find it from the panel menu automatically.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(Space.lg))
            AppCard(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Space.md))
                    Text(
                        "Your password is encrypted with the Android Keystore and only ever sent to teacher.mojaru.com.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Space.xl))
        }

        Button(
            onClick = ::submit,
            // Stays enabled while busy so the progress indicator keeps full contrast; submit() ignores taps.
            enabled = canSubmit || busy,
            colors = ButtonDefaults.buttonColors(),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(Space.lg)
                .heightIn(min = 52.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(Space.md))
                Text("Signing in…", style = MaterialTheme.typography.titleSmall)
            } else {
                Text(if (saved == null) "Sign in & continue" else "Test & save", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
