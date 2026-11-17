package app.slotwatcher.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.slotwatcher.check.CheckScheduler
import app.slotwatcher.data.CredentialStore
import app.slotwatcher.data.Credentials
import app.slotwatcher.data.Repo
import app.slotwatcher.net.LoginException
import app.slotwatcher.net.MojaruClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** FR-1 to FR-3: enter, test and save the Mojaru login. */
@Composable
fun AccountScreen(onSaved: () -> Unit, onBack: (() -> Unit)?) {
    val ctx = LocalContext.current
    val saved = remember { CredentialStore.get(ctx) }
    var login by remember { mutableStateOf(saved?.login.orEmpty()) }
    var password by remember { mutableStateOf(saved?.password.orEmpty()) }
    var listUrl by remember { mutableStateOf(saved?.listUrl.orEmpty()) }
    var showPassword by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(!saved?.listUrl.isNullOrBlank()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) } // ok, text
    val scope = rememberCoroutineScope()

    Page("Mojaru account", onBack = onBack) {
        Text(
            "Slot Watcher signs in to the Mojaru Teacher Panel with this login. " +
                "It's stored encrypted on this phone only.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = login, onValueChange = { login = it },
            label = { Text("Email/Mobile") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") }, singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "Hide advanced" else "Advanced")
        }
        if (showAdvanced) {
            OutlinedTextField(
                value = listUrl, onValueChange = { listUrl = it },
                label = { Text("Pre Assessment List URL (optional)") },
                supportingText = { Text("Leave empty to find it from the menu automatically.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = !busy && login.isNotBlank() && password.isNotEmpty(),
                onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val client = MojaruClient()
                                val landing = client.login(login.trim(), password)
                                client.findListUrl(landing)
                            }
                        }
                        busy = false
                        result.onSuccess { found ->
                            CredentialStore.save(ctx, Credentials(login.trim(), password, listUrl))
                            message = true to buildString {
                                append("Login OK.")
                                if (listUrl.isBlank()) {
                                    append(
                                        if (found != null) " Found the Pre Assessment List."
                                        else " But the Pre Assessment List link wasn't found. Paste its URL under Advanced.",
                                    )
                                }
                            }
                            if (found != null || listUrl.isNotBlank()) onSaved()
                        }.onFailure { e ->
                            message = false to when (e) {
                                is LoginException -> "Login failed: ${e.message}"
                                else -> "Couldn't reach Mojaru: ${e.message ?: e.javaClass.simpleName}"
                            }
                        }
                    }
                },
            ) { Text("Test & Save") }
            if (busy) {
                Spacer(Modifier.width(16.dp))
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        }
        message?.let { (ok, text) ->
            Spacer(Modifier.height(12.dp))
            Text(
                text,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }

        if (saved != null) {
            Spacer(Modifier.height(32.dp))
            OutlinedButton(onClick = {
                CredentialStore.clear(ctx)
                CheckScheduler.cancel(ctx)
                Repo.stopSession()
                login = ""; password = ""; listUrl = ""
                message = true to "Login deleted from this phone."
            }) { Text("Delete saved login") }
        }
    }
}
