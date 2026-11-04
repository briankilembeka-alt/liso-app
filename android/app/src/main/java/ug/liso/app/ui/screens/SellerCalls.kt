package ug.liso.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ug.liso.app.R
import ug.liso.app.call.VideoCalls
import ug.liso.app.data.Api
import ug.liso.app.data.IncomingCall
import ug.liso.app.data.JoinToken

/**
 * Seller side: a list of buyers waiting to call (checked every 3 s while the screen is open),
 * accept or decline, the call itself, then [onCallEnded] to write up the deal.
 * TODO(milestone 1): push notification so the phone rings even when Liso is closed.
 */
@Composable
fun SellerCallsScreen(api: Api, sellerId: String, onCallEnded: (callId: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var calls by remember { mutableStateOf<List<IncomingCall>>(emptyList()) }
    var live by remember { mutableStateOf<Pair<String, JoinToken>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }
    val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    fun hasPerms() = perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun accept(callId: String) = scope.launch {
        failed = false
        live = try { callId to api.acceptCall(callId) } catch (e: Exception) { failed = true; null }
    }
    val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val id = pending
        pending = null
        if (id != null && result.values.all { it }) accept(id)
    }

    LaunchedEffect(live) {
        while (live == null) {
            runCatching { api.incomingCalls(sellerId) }.onSuccess { calls = it }
            delay(3_000)
        }
    }

    val current = live
    if (current != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        LiveCall(current.second) {
            scope.launch {
                runCatching { api.endCall(current.first) }
                live = null
                onCallEnded(current.first)
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.incoming_calls), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (!VideoCalls.supported) Text(stringResource(R.string.call_needs_android_9), color = MaterialTheme.colorScheme.error)
        if (failed) Text(stringResource(R.string.error_offline), color = MaterialTheme.colorScheme.error)
        if (calls.isEmpty()) Text(stringResource(R.string.no_calls_waiting), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(calls, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(c.buyerName ?: stringResource(R.string.a_buyer), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.call_recorded_notice), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = VideoCalls.supported,
                                onClick = { if (hasPerms()) accept(c.id) else { pending = c.id; askPerms.launch(perms) } },
                            ) { Text(stringResource(R.string.accept)) }
                            OutlinedButton(onClick = {
                                scope.launch { runCatching { api.declineCall(c.id) }; calls = calls - c }
                            }) { Text(stringResource(R.string.decline)) }
                        }
                    }
                }
            }
        }
    }
}
