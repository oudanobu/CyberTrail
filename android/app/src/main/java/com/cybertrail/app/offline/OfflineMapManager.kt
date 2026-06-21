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
    val mapsDir: File = File(baseDir, "Maps")
    val demDir: File = File(baseDir, "DEM")
    val routesDir: File = File(baseDir, "Routes")
    val tracksDir: File = File(baseDir, "Tracks")
    val poiDir: File = File(baseDir, "POI")
    val cacheDir: File = File(baseDir, "Cache")
    val exportDir: File = File(baseDir, "Export")
    
    init {
        initDirectories()
        copyWorldMapIfNeeded()
    }
    
    private fun initDirectories() {
        if (!baseDir.exists()) baseDir.mkdirs()
        listOf(mapsDir, demDir, routesDir, tracksDir, poiDir, cacheDir, exportDir).forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
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
                name = "世界概览影像离线包 (world.mbtiles) [zoom 0~6]",
                mbtilesUrl = "https://github.com/klokantech/vector-tiles-sample/releases/download/v1.0/countries-raster.mbtiles",
                demUrl = null,
                expectedSizeBytes = 9633792,
                tileCount = 5461,
                bounds = "-180,-85.7380,180,84.7984"
            ),
            OfflineMapRegion(
                id = "china",
                name = "中国普通地形概览图 (china.mbtiles) [zoom 6~8]",
                mbtilesUrl = "https://github.com/cybertrail/assets/releases/download/v1.0/china.mbtiles",
                demUrl = null,
                expectedSizeBytes = 48500000,
                tileCount = 31200,
                bounds = "73.5,18.0,135.0,53.5"
            ),
            OfflineMapRegion(
                id = "liaoning",
                name = "辽宁省离线局域地图 (liaoning.mbtiles) [zoom 9~11]",
                mbtilesUrl = "https://github.com/cybertrail/assets/releases/download/v1.0/liaoning.mbtiles",
                demUrl = null,
                expectedSizeBytes = 156000000,
                tileCount = 125000,
                bounds = "118.5,38.5,125.8,43.5"
            ),
            OfflineMapRegion(
                id = "dandong",
                name = "丹东市区高细节离线影像地图 (dandong.mbtiles) [zoom 12~14]",
                mbtilesUrl = "https://github.com/cybertrail/assets/releases/download/v1.0/dandong.mbtiles",
                demUrl = null,
                expectedSizeBytes = 420000000,
                tileCount = 948000,
                bounds = "124.0,39.8,124.8,40.4"
            )
        )
        
        // Scan local directory for downloaded maps
        regions.forEach { region ->
            val localFile = File(mapsDir, "${region.id}.mbtiles")
            if (localFile.exists()) {
                region.isDownloaded = true
                region.localPath = localFile.absolutePath
            } else {
                region.isDownloaded = false
                region.localPath = null
            }
        }
        
        return regions
    }
    
    fun startDownload(region: OfflineMapRegion): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(region.mbtilesUrl))
            .setTitle("正在下载 ${region.name} 离线地图产品")
            .setDescription("存储路径: /CyberTrail/Maps/")
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

    fun getAvailableDems(): List<OfflineDemRegion> {
        val dems = listOf(
            OfflineDemRegion(
                id = "srtm_hgt",
                name = "航天飞机雷达地形测绘 (SRTM HGT 30m 数字高程模型)",
                demUrl = "https://github.com/cybertrail/assets/releases/download/v1.0/liaoning.hgt",
                demType = "SRTM HGT",
                expectedSizeBytes = 25165824,
                fileName = "liaoning.hgt"
            ),
            OfflineDemRegion(
                id = "aster_gdem",
                name = "先进星载热发射和反辐射计 (ASTER GDEM 30m 遥感数字高程)",
                demUrl = "https://github.com/cybertrail/assets/releases/download/v1.0/liaoning_aster.tif",
                demType = "ASTER GDEM",
                expectedSizeBytes = 45123456,
                fileName = "liaoning_aster.tif"
            )
        )
        
        dems.forEach { dem ->
            val localFile = File(demDir, dem.fileName)
            if (localFile.exists()) {
                dem.isDownloaded = true
                dem.localPath = localFile.absolutePath
            } else {
                dem.isDownloaded = false
                dem.localPath = null
            }
        }
        
        return dems
    }

    fun startDemDownload(dem: OfflineDemRegion): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(dem.demUrl))
            .setTitle("正在下载高程数据: ${dem.name}")
            .setDescription("存储路径: /CyberTrail/DEM/")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(File(demDir, dem.fileName)))
            
        return downloadManager.enqueue(request)
    }

    fun deleteDem(dem: OfflineDemRegion) {
        val file = File(demDir, dem.fileName)
        if (file.exists()) {
            file.delete()
        }
        dem.isDownloaded = false
        dem.localPath = null
    }
}
