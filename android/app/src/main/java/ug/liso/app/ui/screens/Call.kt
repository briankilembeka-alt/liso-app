package ug.liso.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ug.liso.app.R
import ug.liso.app.call.IvsCall
import ug.liso.app.data.Api
import ug.liso.app.data.ApiException
import ug.liso.app.data.JoinToken
import ug.liso.app.ui.theme.LisoColors

private sealed interface CallStep {
    data object Permissions : CallStep
    data object Requesting : CallStep
    data class Ringing(val callId: String) : CallStep
    data class Live(val callId: String, val join: JoinToken) : CallStep
    data class Over(val message: Int) : CallStep
}

private const val RING_TIMEOUT_MS = 60_000L

/**
 * Buyer side of a call: ask for camera and mic, request the call, wait while the seller's phone rings,
 * then join the video room. Needs sign-in and ID first (handled by [onNeedsSignIn] / [onNeedsId]).
 */
@Composable
fun CallScreen(
    api: Api,
    sellerId: String,
    signedIn: Boolean,
    onNeedsSignIn: () -> Unit,
    onNeedsId: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    fun hasPerms() = perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var step by remember { mutableStateOf<CallStep>(if (hasPerms()) CallStep.Requesting else CallStep.Permissions) }
    val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        step = if (result.values.all { it }) CallStep.Requesting else CallStep.Over(R.string.call_needs_permissions)
    }

    LaunchedEffect(signedIn) { if (!signedIn) onNeedsSignIn() }
    LaunchedEffect(step) {
        if (!signedIn) return@LaunchedEffect
        when (val s = step) {
            CallStep.Permissions -> askPerms.launch(perms)
            CallStep.Requesting -> step = try {
                CallStep.Ringing(api.requestCall(sellerId).id)
            } catch (e: ApiException) {
                when (e.code) {
                    "ID_VERIFICATION_REQUIRED" -> { onNeedsId(); CallStep.Requesting }
                    "UNAUTHENTICATED" -> { onNeedsSignIn(); CallStep.Requesting }
                    "SELLER_SUSPENDED" -> CallStep.Over(R.string.call_seller_unavailable)
                    else -> CallStep.Over(R.string.error_offline)
                }
            } catch (e: Exception) { CallStep.Over(R.string.error_offline) }
            is CallStep.Ringing -> {
                // Poll every 2 s; cheap on data and fast enough for a ringing phone.
                val started = System.currentTimeMillis()
                while (true) {
                    val status = runCatching { api.call(s.callId).status }.getOrNull()
                    when (status) {
                        "active" -> { step = runCatching { CallStep.Live(s.callId, api.joinCall(s.callId)) }.getOrElse { CallStep.Over(R.string.error_offline) }; return@LaunchedEffect }
                        "declined", "missed", "ended" -> { step = CallStep.Over(R.string.call_declined); return@LaunchedEffect }
                    }
                    if (System.currentTimeMillis() - started > RING_TIMEOUT_MS) { step = CallStep.Over(R.string.call_no_answer); return@LaunchedEffect }
                    delay(2_000)
                }
            }
            else -> Unit
        }
    }

    when (val s = step) {
        CallStep.Permissions, CallStep.Requesting -> Centered { CircularProgressIndicator() }
        is CallStep.Ringing -> Centered {
            Text(stringResource(R.string.calling_seller), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.call_recorded_notice), style = MaterialTheme.typography.bodySmall)
            CircularProgressIndicator()
        }
        is CallStep.Live -> LiveCall(s.join) {
            scope.launch { runCatching { api.endCall(s.callId) }; onDone() }
        }
        is CallStep.Over -> Centered {
            Text(stringResource(s.message), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onDone) { Text(stringResource(R.string.back)) }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun LiveCall(join: JoinToken, onHangUp: () -> Unit) {
    val context = LocalContext.current
    var remote by remember { mutableStateOf<View?>(null) }
    var micMuted by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val call = remember(join.token) {
        IvsCall(context, join.token, join.publish, onRemoteVideo = { remote = it }, onState = {}, onError = { problem = it })
    }
    DisposableEffect(call) {
        call.join()
        onDispose { call.close() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        remote?.let { v -> AndroidView(factory = { v }, modifier = Modifier.fillMaxSize()) }
            ?: Text(stringResource(R.string.waiting_for_video), color = Color.White, modifier = Modifier.align(Alignment.Center))
        call.localPreview?.let { v ->
            AndroidView(
                factory = { v },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(96.dp, 160.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
        // Both sides are always told the call is recorded.
        Text(
            "● " + stringResource(R.string.recording),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(LisoColors.LiveRed, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            problem?.let { Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = { micMuted = !micMuted; call.setMicMuted(micMuted) }) {
                    Text(stringResource(if (micMuted) R.string.unmute else R.string.mute), color = Color.White)
                }
                Button(onClick = onHangUp, colors = ButtonDefaults.buttonColors(containerColor = LisoColors.LiveRed)) {
                    Text(stringResource(R.string.hang_up))
                }
            }
        }
    }
}
