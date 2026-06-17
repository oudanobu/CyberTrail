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
        copyWorldMapIfNeeded()
    }
    
    private fun initDirectories() {
        if (!baseDir.exists()) baseDir.mkdirs()
        if (!mapsDir.exists()) mapsDir.mkdirs()
        if (!demDir.exists()) demDir.mkdirs()
    }

    private fun copyWorldMapIfNeeded() {
        val destFile = File(mapsDir, "world.mbtiles")
        if (!destFile.exists()) {
            try {
                context.assets.open("world.mbtiles").use { inputStream ->
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.i(TAG, "Successfully copied world.mbtiles from assets to maps storage.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy world.mbtiles from assets", e)
            }
        }
    }

    fun getAvailableRegions(): List<OfflineMapRegion> {
        val regions = listOf(
            OfflineMapRegion(
                id = "world",
                name = "World (Raster Default Map)",
                mbtilesUrl = "https://github.com/klokantech/vector-tiles-sample/releases/download/v1.0/countries-raster.mbtiles",
                demUrl = null,
                expectedSizeBytes = 9633792,
                tileCount = 5461,
                bounds = "-180,-85.738076382392,180,84.79842793857"
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
