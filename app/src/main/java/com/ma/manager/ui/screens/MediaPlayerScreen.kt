package com.ma.manager.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import java.io.File

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
    var hasVideo by remember { mutableStateOf(true) }
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
                error = null
            }

            override fun onTracksChanged(tracks: Tracks) {
                hasVideo = tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
            }

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
                if (hasVideo) {
                    view.controllerShowTimeoutMs = 3000
                    view.controllerHideOnTouch = true
                } else {
                    // audio: keep controls on screen
                    view.controllerShowTimeoutMs = 0
                    view.controllerHideOnTouch = false
                    view.showController()
                }
            }
        )

        if (!hasVideo && error == null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF90CAF9),
                    modifier = Modifier.size(96.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    title,
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

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

        // top bar: follows the controller visibility for video, always shown for audio
        if (controlsVisible || !hasVideo || error != null) {
            MAAppBar(
                modifier = Modifier.align(Alignment.TopStart),
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onClose) {
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
                        androidx.compose.material3.IconButton(onClick = {
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
    }
}
