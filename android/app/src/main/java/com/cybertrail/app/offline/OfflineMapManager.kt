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
                id = "tokyo",
                name = "Tokyo",
                mbtilesUrl = "https://raw.githubusercontent.com/klokantech/vector-tiles-sample/master/data/v3.mbtiles", // 示例
                demUrl = null,
                expectedSizeBytes = 22_000_000,
                tileCount = 5000,
                bounds = "139.5, 35.5, 140.0, 36.0"
            ),
            OfflineMapRegion(
                id = "fuji",
                name = "Fuji",
                mbtilesUrl = "https://example.com/fuji.mbtiles",
                demUrl = null,
                expectedSizeBytes = 50_000_000,
                tileCount = 12000,
                bounds = "138.6, 35.2, 138.9, 35.5"
            ),
            OfflineMapRegion(
                id = "yosemite",
                name = "Yosemite",
                mbtilesUrl = "https://example.com/yosemite.mbtiles",
                demUrl = null,
                expectedSizeBytes = 80_000_000,
                tileCount = 20000,
                bounds = "-119.8, 37.6, -119.4, 38.0"
            ),
            OfflineMapRegion(
                id = "test_region",
                name = "Test Region",
                mbtilesUrl = "https://github.com/syncpoint/mbtiles-sample/raw/master/countries.mbtiles", // 小的测试文件
                demUrl = null,
                expectedSizeBytes = 1_000_000,
                tileCount = 100,
                bounds = "-180, -90, 180, 90"
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
