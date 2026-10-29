package com.pokejournal

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.pokejournal.data.images.ArtworkStore
import com.pokejournal.data.images.LocalArtworkMapper
import com.pokejournal.sync.SyncInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PokeJournalApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncInitializer: SyncInitializer

    @Inject lateinit var artworkStore: ArtworkStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        syncInitializer.start()
    }


    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(LocalArtworkMapper(artworkStore)) }
        .respectCacheHeaders(false)
        .build()
}
