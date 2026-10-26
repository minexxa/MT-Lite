package com.ma.manager.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ma.manager.ui.components.MAAppBar
import com.ma.manager.data.FileItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Built-in video / audio player based on Media3 ExoPlayer.
 *
 * - playlist of [paths] starting at [startIndex] (prev / next buttons in the controller)
 * - video: tap to show / hide controls, rotate button for landscape
 * - audio: controls always visible with a music icon + file name
 * - on unsupported formats (e.g. avi / wmv) an error is shown with a button to
 *   hand the file over to a system app via [onOpenSystem]
 */
@OptIn(UnstableApi::class)
@Composable
fun MediaPlayerScreen(
    paths: List<String>,
    startIndex: Int,
    onClose: () -> Unit,
    onOpenSystem: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var title by remember { mutableStateOf("") }
    // audio files start in the audio layout straight away (no PlayerView flash)
    var hasVideo by remember {
        mutableStateOf(File(paths.getOrNull(startIndex) ?: "").extension.lowercase() !in FileItem.AUDIO_EXTS)
    }
    var artwork by remember { mutableStateOf<ByteArray?>(null) }
    var playing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentPath by remember { mutableStateOf(paths.getOrNull(startIndex) ?: "") }
    var controlsVisible by remember { mutableStateOf(true) }
    var speedIndex by remember { mutableStateOf(2) }

    val exo = remember {
        ExoPlayer.Builder(context).build().apply {
            val items = paths.map { p ->
                MediaItem.Builder()
                    .setUri(Uri.fromFile(File(p)))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(File(p).name).build())
                    .build()
            }
            setMediaItems(items, startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), C.TIME_UNSET)
            repeatMode = if (items.size > 1) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                title = mediaItem?.mediaMetadata?.title?.toString().orEmpty()
                currentPath = paths.getOrNull(exo.currentMediaItemIndex) ?: currentPath
                hasVideo = File(currentPath).extension.lowercase() !in FileItem.AUDIO_EXTS
                artwork = null
                error = null
            }

            override fun onTracksChanged(tracks: Tracks) {
                // an empty track list just means "not prepared yet"
                if (tracks.groups.isNotEmpty()) hasVideo = tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                artwork = mediaMetadata.artworkData
            }

            private fun syncPlaying() {
                playing = exo.isPlaying ||
                    (exo.playWhenReady && exo.playbackState == Player.STATE_BUFFERING)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) = syncPlaying()
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = syncPlaying()
            override fun onPlaybackStateChanged(playbackState: Int) = syncPlaying()

            override fun onPlayerError(e: PlaybackException) {
                error = "无法播放: ${e.errorCodeName}"
            }
        }
        exo.addListener(listener)
        title = File(currentPath).name
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // pause when the app goes to background
    DisposableEffect(lifecycleOwner, exo) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exo.pause()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // restore orientation when leaving
    DisposableEffect(activity) {
        val old = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { activity?.requestedOrientation = old }
    }

    BackHandler(onBack = onClose)

    val cover by produceState<ImageBitmap?>(null, artwork, currentPath) {
        value = withContext(Dispatchers.Default) { decodeCover(artwork, currentPath) }
    }

    val topBar: @Composable (Modifier) -> Unit = { m ->
        MAAppBar(
            modifier = m,
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
            },
            title = {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 4.dp)
                )
            },
            actions = {
                TextButton(
                    onClick = {
                        speedIndex = (speedIndex + 1) % SPEEDS.size
                        exo.setPlaybackSpeed(SPEEDS[speedIndex])
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) { Text("${SPEEDS[speedIndex]}x", fontSize = 14.sp) }
                if (hasVideo) {
                    IconButton(onClick = {
                        val a = activity ?: return@IconButton
                        a.requestedOrientation =
                            if (a.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }) {
                        Icon(
                            Icons.Default.ScreenRotation,
                            contentDescription = "旋转屏幕",
                            tint = Color.White
                        )
                    }
                }
            }
        )
    }

    if (!hasVideo && error == null) {
        // ---- audio: cover on top, title, progress bar, then the control buttons ----
        AudioPlayerContent(
            exo = exo,
            title = title,
            cover = cover,
            playing = playing,
            topBar = { topBar(Modifier) }
        )
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = exo
                    useController = true
                    setShowNextButton(true)
                    setShowPreviousButton(true)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { v ->
                            controlsVisible = v == android.view.View.VISIBLE
                        }
                    )
                }
            },
            update = { view ->
                view.controllerShowTimeoutMs = 3000
                view.controllerHideOnTouch = true
            }
        )

        if (error != null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error ?: "", color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(
                    "内置播放器不支持该格式或文件已损坏",
                    color = Color(0xFFBBBBBB),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                TextButton(
                    onClick = { onOpenSystem(currentPath) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF64B5F6))
                ) { Text("用系统应用打开") }
            }
        }

        // top bar follows the controller visibility for video
        if (controlsVisible || error != null) {
            topBar(Modifier.align(Alignment.TopStart))
        }
    }
}

@Composable
private fun AudioPlayerContent(
    exo: ExoPlayer,
    title: String,
    cover: ImageBitmap?,
    playing: Boolean,
    topBar: @Composable () -> Unit
) {
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    LaunchedEffect(exo) {
        while (true) {
            if (!dragging) position = exo.currentPosition.coerceAtLeast(0L)
            duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            delay(250)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        topBar()

        // Cover: the whole picture, never cropped, no background effect, no overlay.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Text(
            title,
            color = Color.White,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(12.dp))

        // progress bar
        val maxValue = duration.coerceAtLeast(1L).toFloat()
        Slider(
            value = (if (dragging) dragValue else position.toFloat()).coerceIn(0f, maxValue),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                exo.seekTo(dragValue.toLong())
                position = dragValue.toLong()
                dragging = false
            },
            valueRange = 0f..maxValue,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0xFF444444)
            ),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val shown = if (dragging) dragValue.toLong() else position
            Text(formatTime(shown), color = Color.White, fontSize = 13.sp)
            Text(formatTime(duration), color = Color(0xFFBBBBBB), fontSize = 13.sp)
        }

        // control buttons, below the progress bar
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { exo.seekToPrevious() }) {
                Icon(Icons.Default.SkipPrevious, "上一首", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { exo.seekTo((exo.currentPosition - 5000).coerceAtLeast(0L)) }) {
                SeekGlyph(label = "5", mirrored = false)
            }
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable {
                        if (exo.isPlaying) {
                            exo.pause()
                        } else {
                            if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
                            exo.play()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "暂停" else "播放",
                    tint = Color.Black,
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = {
                val target = exo.currentPosition + 15000
                exo.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
            }) {
                SeekGlyph(label = "15", mirrored = true)
            }
            IconButton(onClick = { exo.seekToNext() }) {
                Icon(Icons.Default.SkipNext, "下一首", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
    }
}

/** Circular arrow with the skip seconds written inside. */
@Composable
private fun SeekGlyph(label: String, mirrored: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.Replay, null, tint = Color.White,
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer { scaleX = if (mirrored) -1f else 1f }
        )
        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/**
 * Cover picture: embedded artwork first, otherwise a cover image sitting next
 * to the audio file (cover / folder / front .jpg/.png). Down-sampled so very
 * large covers cannot exhaust memory.
 */
private fun decodeCover(embedded: ByteArray?, audioPath: String): ImageBitmap? {
    fun decode(read: () -> ByteArray?): ImageBitmap? = try {
        val bytes = read() ?: return null
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    } catch (_: Throwable) {
        null
    }

    decode { embedded }?.let { return it }
    val dir = File(audioPath).parentFile ?: return null
    for (base in listOf("cover", "folder", "front", "album", "albumart")) {
        for (ext in listOf("jpg", "jpeg", "png", "webp")) {
            val f = File(dir, "$base.$ext")
            if (f.isFile && f.length() < 20L * 1024 * 1024) {
                decode { f.readBytes() }?.let { return it }
            }
        }
    }
    return null
}
