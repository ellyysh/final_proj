package com.pokejournal.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.pokejournal.BuildConfig
import com.pokejournal.data.local.AppDatabase
import com.pokejournal.data.local.dao.CollectionDao
import com.pokejournal.data.local.dao.FilterPresetDao
import com.pokejournal.data.local.dao.HistoryDao
import com.pokejournal.data.local.dao.NoteDao
import com.pokejournal.data.local.dao.ProfileDao
import com.pokejournal.data.local.dao.SyncDao
import com.pokejournal.data.local.dao.UserStateDao
import com.pokejournal.data.network.ConnectivityNetworkMonitor
import com.pokejournal.data.network.NetworkMonitor
import com.pokejournal.data.remote.PokeApi
import com.pokejournal.data.repository.CollectionRepository
import com.pokejournal.data.repository.HistoryRepository
import com.pokejournal.data.repository.ImportantPokemonSource
import com.pokejournal.data.repository.JournalRepository
import com.pokejournal.data.repository.OfflineFirstPokemonRepository
import com.pokejournal.data.repository.PokemonRepository
import com.pokejournal.data.repository.PresetRepository
import com.pokejournal.data.repository.ProfileRepository
import com.pokejournal.data.repository.RoomCollectionRepository
import com.pokejournal.data.repository.RoomHistoryRepository
import com.pokejournal.data.repository.RoomImportantPokemonSource
import com.pokejournal.data.repository.RoomJournalRepository
import com.pokejournal.data.repository.RoomPresetRepository
import com.pokejournal.data.repository.RoomProfileRepository
import com.pokejournal.data.settings.DataStoreSettingsRepository
import com.pokejournal.data.settings.SettingsRepository
import com.pokejournal.domain.logic.TimeProvider
import com.pokejournal.sync.SyncScheduler
import com.pokejournal.sync.WorkManagerSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            },
        )
        .build()

    @Provides
    @Singleton
    fun providePokeApi(client: OkHttpClient, json: Json): PokeApi = Retrofit.Builder()
        .baseUrl(PokeApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(PokeApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addCallback(AppDatabase.seedCallback)
            .build()

    @Provides fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()

    @Provides fun provideUserStateDao(db: AppDatabase): UserStateDao = db.userStateDao()

    @Provides fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()

    @Provides fun provideCollectionDao(db: AppDatabase): CollectionDao = db.collectionDao()

    @Provides fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides fun provideFilterPresetDao(db: AppDatabase): FilterPresetDao = db.filterPresetDao()

    @Provides fun provideSyncDao(db: AppDatabase): SyncDao = db.syncDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("settings") })

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = TimeProvider { System.currentTimeMillis() }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun bindPokemonRepository(impl: OfflineFirstPokemonRepository): PokemonRepository

    @Binds abstract fun bindJournalRepository(impl: RoomJournalRepository): JournalRepository

    @Binds abstract fun bindCollectionRepository(impl: RoomCollectionRepository): CollectionRepository

    @Binds abstract fun bindHistoryRepository(impl: RoomHistoryRepository): HistoryRepository

    @Binds abstract fun bindPresetRepository(impl: RoomPresetRepository): PresetRepository

    @Binds abstract fun bindProfileRepository(impl: RoomProfileRepository): ProfileRepository

    @Binds abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds abstract fun bindImportantSource(impl: RoomImportantPokemonSource): ImportantPokemonSource

    @Binds abstract fun bindNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor

    @Binds abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}
