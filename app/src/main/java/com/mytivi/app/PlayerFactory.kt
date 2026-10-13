@file:OptIn(UnstableApi::class)

package com.mytivi.app

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

fun buildPlayer(context: Context): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent("MyTivi/1.0")
        .setAllowCrossProtocolRedirects(true)   // nhiều server IPTV redirect http -> https
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(15_000)
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(http))
        .build()
        .apply { playWhenReady = true }
}
