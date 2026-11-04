package ug.liso.app.ui.screens

import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ug.liso.app.R
import ug.liso.app.call.IvsCall
import ug.liso.app.data.JoinToken
import ug.liso.app.ui.theme.LisoColors

/** The in-call screen, shared by buyer and seller. Callers check [ug.liso.app.call.VideoCalls.supported] first. */
@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun LiveCall(join: JoinToken, onHangUp: () -> Unit) {
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
