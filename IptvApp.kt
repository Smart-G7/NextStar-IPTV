package com.custom.iptvtv

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.custom.iptvtv.net.Net

class IptvApp : Application(), ImageLoaderFactory {
    lateinit var state: AppState
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        state = AppState(this)
        state.start()
    }

    // 2GB RAM için kısık görsel önbelleği; logolar diskte kalıcı
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.08).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(64L * 1024 * 1024).build() }
        .okHttpClient { Net.client(state.prefs.dns) }
        .allowHardware(false) // ucuz TV çiplerinde donanım bitmap'leri çökme yapabiliyor
        .respectCacheHeaders(false)
        .crossfade(false)
        .build()
}

val Context.appState: AppState get() = (applicationContext as IptvApp).state
