package com.wayfare.app

import android.app.Application
import androidx.room.Room
import com.wayfare.app.data.CoverCoordinator
import com.wayfare.app.data.FxRepository
import com.wayfare.app.data.Preferences
import com.wayfare.app.data.WayfareRepository
import com.wayfare.app.data.local.WayfareDatabase
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class WayfareApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    /** Outlives any one screen: rate refreshes and cache clears must not die with a ViewModel. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val configured = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()

    val supabase = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL.ifBlank { "https://missing.invalid" },
        supabaseKey = BuildConfig.SUPABASE_KEY.ifBlank { "missing" },
    ) {
        install(Auth) {
            scheme = "wayfare"
            host = "auth"
        }
        install(Postgrest)
        install(Storage)
    }

    private val http = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    val database = Room.databaseBuilder(application, WayfareDatabase::class.java, "wayfare.db").build()
    val preferences = Preferences(application)
    val fx = FxRepository(application, http)
    val repository = WayfareRepository(supabase, database, http) { accountId ->
        SyncWorker.enqueue(application, accountId)
    }
    val covers = CoverCoordinator(repository, scope)

    init {
        refreshRates()
    }

    /** Cheap when the cached snapshot is still inside its twelve-hour window. */
    fun refreshRates() {
        scope.launch { runCatching { fx.refresh() } }
    }
}
