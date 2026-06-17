package com.cybertrail.app.offline

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File

class OfflineMapManager(private val context: Context) {
    
    private val TAG = "OfflineMapManager"
    
    val baseDir: File = File(Environment.getExternalStorageDirectory(), "CyberTrail")
    val mapsDir: File = File(baseDir, "maps")
    val demDir: File = File(baseDir, "dem")
    
    init {
        initDirectories()
    }
    
    private fun initDirectories() {
        if (!baseDir.exists()) baseDir.mkdirs()
        if (!mapsDir.exists()) mapsDir.mkdirs()
        if (!demDir.exists()) demDir.mkdirs()
    }

    fun getAvailableRegions(): List<OfflineMapRegion> {
        val regions = listOf(
            OfflineMapRegion(
                id = "world",
                name = "World",
                mbtilesUrl = "https://github.com/syncpoint/mbtiles-sample/raw/master/countries.mbtiles",
                demUrl = null,
                expectedSizeBytes = 1_000_000,
                tileCount = 100,
                bounds = "-180, -90, 180, 90"
            ),
            OfflineMapRegion(
                id = "asia_china_liaoning_dandong",
                name = "Asia > China > Liaoning > Dandong",
                mbtilesUrl = "https://raw.githubusercontent.com/klokantech/vector-tiles-sample/master/data/v3.mbtiles",
                demUrl = null,
                expectedSizeBytes = 22_000_000,
                tileCount = 5000,
                bounds = "124.0, 40.0, 125.0, 41.0"
            )
        )
        
        // Scan local directory for downloaded maps
        val localFiles = mapsDir.listFiles { _, name -> name.endsWith(".mbtiles") } ?: arrayOf()
        val localNames = localFiles.map { it.nameWithoutExtension }
        
        regions.forEach { region ->
            val localFile = File(mapsDir, "${region.id}.mbtiles")
            if (localFile.exists()) {
                region.isDownloaded = true
                region.localPath = localFile.absolutePath
            }
        }
        
        return regions
    }
    
    fun startDownload(region: OfflineMapRegion): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(region.mbtilesUrl))
            .setTitle("Downloading ${region.name} Map")
            .setDescription("MBTiles for offline use")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(File(mapsDir, "${region.id}.mbtiles")))
            
        return downloadManager.enqueue(request)
    }
    
    fun deleteMap(region: OfflineMapRegion) {
        val file = File(mapsDir, "${region.id}.mbtiles")
        if (file.exists()) {
            file.delete()
        }
        region.isDownloaded = false
        region.localPath = null
    }
}
