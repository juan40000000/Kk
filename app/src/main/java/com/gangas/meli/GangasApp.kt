package com.gangas.meli

import android.app.Application
import com.gangas.core.engine.DealEngine
import com.gangas.core.net.ApiSource
import com.gangas.core.net.Http
import com.gangas.core.net.HttpWebSource
import com.gangas.core.net.MeliApiClient
import com.gangas.core.net.MeliRepository
import com.gangas.core.net.MeliWebScraper
import com.gangas.meli.data.BrowserSource
import com.gangas.meli.data.DealScanner
import com.gangas.meli.data.HistoryStore
import com.gangas.meli.data.SettingsStore
import com.gangas.meli.data.WatchStore
import com.gangas.meli.work.Notifier
import com.gangas.meli.work.ScanScheduler

class GangasApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannel(this)
        ScanScheduler.reschedule(this)
    }
}

class AppContainer(app: Application) {
    val settings = SettingsStore(app)
    val watches = WatchStore(app)
    val history = HistoryStore(app)
    private val http = Http.defaultClient()
    private val api = MeliApiClient(http, { settings.credentials() })
    private val scraper = MeliWebScraper(http)
    val repository = MeliRepository(
        api = ApiSource(api),
        web = HttpWebSource(scraper),
        browser = BrowserSource(app, scraper),
        hasCredentials = { settings.credentials().isConfigured },
    )
    val scanner = DealScanner(repository, history) { DealEngine(settings.engineConfig()) }
}
