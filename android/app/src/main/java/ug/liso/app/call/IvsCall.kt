package ug.liso.app.call

import android.content.Context
import android.view.View
import com.amazonaws.ivs.broadcast.AudioDevice
import com.amazonaws.ivs.broadcast.AudioLocalStageStream
import com.amazonaws.ivs.broadcast.BroadcastConfiguration
import com.amazonaws.ivs.broadcast.BroadcastException
import com.amazonaws.ivs.broadcast.CameraSource
import com.amazonaws.ivs.broadcast.Device
import com.amazonaws.ivs.broadcast.DeviceDiscovery
import com.amazonaws.ivs.broadcast.ImageLocalStageStream
import com.amazonaws.ivs.broadcast.LocalStageStream
import com.amazonaws.ivs.broadcast.ParticipantInfo
import com.amazonaws.ivs.broadcast.Stage
import com.amazonaws.ivs.broadcast.StageRenderer
import com.amazonaws.ivs.broadcast.StageStream
import com.amazonaws.ivs.broadcast.StageVideoConfiguration
import ug.liso.app.data.PublishLimits

/**
 * One 1:1 video call on Amazon IVS Real-Time.
 * Publishes the front camera and microphone within the server's limits (360p by default)
 * and reports the other person's video view through [onRemoteVideo].
 */
class IvsCall(
    context: Context,
    token: String,
    limits: PublishLimits,
    private val onRemoteVideo: (View?) -> Unit,
    private val onState: (Stage.ConnectionState) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val discovery = DeviceDiscovery(context)
    private val devices = discovery.listLocalDevices()
    private val camera: Device? = devices.filterIsInstance<CameraSource>()
        .let { cams -> cams.firstOrNull { it.descriptor.position == Device.Descriptor.Position.FRONT } ?: cams.firstOrNull() }
    private val mic: Device? = devices.firstOrNull { it is AudioDevice }

    private val videoConfig = StageVideoConfiguration().apply {
        setSize(BroadcastConfiguration.Vec2(limits.height.toFloat(), limits.width.toFloat())) // portrait
        setMaxBitrate(limits.maxBitrateKbps * 1000)
        setTargetFramerate(15)
    }
    private val videoStream = camera?.let { ImageLocalStageStream(it, videoConfig) }
    private val audioStream = mic?.let { AudioLocalStageStream(it) }
    private val publishing: List<LocalStageStream> = listOfNotNull(videoStream, audioStream)

    private val strategy = object : Stage.Strategy {
        override fun stageStreamsToPublishForParticipant(stage: Stage, info: ParticipantInfo) = publishing
        override fun shouldPublishFromParticipant(stage: Stage, info: ParticipantInfo) = true
        override fun shouldSubscribeToParticipant(stage: Stage, info: ParticipantInfo) = Stage.SubscribeType.AUDIO_VIDEO
    }

    private val renderer = object : StageRenderer {
        override fun onConnectionStateChanged(stage: Stage, state: Stage.ConnectionState, exception: BroadcastException?) {
            onState(state)
            exception?.let { onError(it.message ?: "connection problem") }
        }
        override fun onStreamsAdded(stage: Stage, info: ParticipantInfo, streams: MutableList<StageStream>) {
            if (info.isLocal) return
            streams.firstOrNull { it.streamType == StageStream.Type.VIDEO }?.let { onRemoteVideo(it.preview) }
        }
        override fun onParticipantLeft(stage: Stage, info: ParticipantInfo) {
            if (!info.isLocal) onRemoteVideo(null)
        }
        override fun onError(exception: BroadcastException) {
            onError(exception.message ?: "video error")
        }
    }

    private val stage = Stage(context, token, strategy).apply { addRenderer(renderer) }

    /** The phone's own camera, for the small preview in the corner. */
    val localPreview: View? = videoStream?.preview

    fun join() {
        try { stage.join() } catch (e: BroadcastException) { onError(e.message ?: "could not join the call") }
    }

    fun setMicMuted(muted: Boolean) = audioStream?.setMuted(muted)
    fun setCameraOff(off: Boolean) = videoStream?.setMuted(off)

    fun close() {
        stage.leave()
        stage.release()
        discovery.release()
    }
}
