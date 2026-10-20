package app.lull

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import app.lull.core.Credentials
import app.lull.core.InnerTube
import app.lull.data.CredentialsStore
import app.lull.data.Repository
import app.lull.data.RecentSeries
import app.lull.data.ResumePositions
import app.lull.data.Settings
import app.lull.playback.NewPipeDownloader
import app.lull.playback.StreamResolver
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Hand-wired singletons. Small on purpose: one HTTP client, one cache, one download manager. */
object AppGraph {
    lateinit var http: OkHttpClient private set
    lateinit var credentials: CredentialsStore private set
    lateinit var settings: Settings private set
    lateinit var resume: ResumePositions private set
    lateinit var recentSeries: RecentSeries private set
    lateinit var repo: Repository private set
    lateinit var resolver: StreamResolver private set
    lateinit var downloadCache: SimpleCache private set
    lateinit var downloadManager: DownloadManager private set

    /** Bumped whenever a download starts, finishes or is removed, so screens can refresh. */
    val downloadsVersion = MutableStateFlow(0)

    fun init(context: Context) {
        http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        NewPipe.init(NewPipeDownloader(http))

        credentials = CredentialsStore(context)
        settings = Settings(context)
        resume = ResumePositions(context)
        recentSeries = RecentSeries(context)
        repo = Repository(InnerTube(http) { credentials.cookie()?.let(::Credentials) })
        resolver = StreamResolver(context, settings)

        val database = StandaloneDatabaseProvider(context)
        downloadCache = SimpleCache(File(context.filesDir, "downloads"), NoOpCacheEvictor(), database)
        downloadManager = DownloadManager(
            context, database, downloadCache, upstreamFactory(), Executors.newFixedThreadPool(2),
        ).apply {
            maxParallelDownloads = 2
            requirements = requirementsFor(settings.downloadWifiOnly)
            addListener(object : DownloadManager.Listener {
                override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
                    downloadsVersion.value++
                }

                override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
                    downloadsVersion.value++
                }
            })
        }
    }

    fun requirementsFor(wifiOnly: Boolean) =
        Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)

    /** lull://video/<id> → a real audio URL, resolved only at the moment it is fetched. */
    private fun upstreamFactory(): DataSource.Factory =
        ResolvingDataSource.Factory(OkHttpDataSource.Factory(http), resolver)

    /**
     * Player source: reads finished downloads from the cache, streams everything else.
     * Streaming never writes to the cache, so storage only grows when you download.
     */
    fun playerDataSourceFactory(): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(upstreamFactory())
            .setCacheWriteDataSinkFactory(null)
            .setCacheKeyFactory { spec -> spec.key ?: spec.uri.lastPathSegment ?: spec.uri.toString() }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
