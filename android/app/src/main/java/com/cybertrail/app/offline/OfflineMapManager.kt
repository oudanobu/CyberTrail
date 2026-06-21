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
        val regions = mutableListOf(
            OfflineMapRegion(
                id = "world",
                name = "世界级 (World): 全球基础低分辨率影像包 (world.mbtiles) [zoom 0~6]",
                mbtilesUrl = "https://github.com/klokantech/vector-tiles-sample/releases/download/v1.0/countries-raster.mbtiles",
                demUrl = null,
                expectedSizeBytes = 9633792,
                tileCount = 5461,
                bounds = "-180,-85.7380,180,84.7984"
            ),
            OfflineMapRegion(
                id = "china",
                name = "国家级 (National): 中国陆地地形遥感概述图 (china.mbtiles) [zoom 6~8]",
                mbtilesUrl = "https://github.com/cybertrail/assets/releases/download/v1.0/china.mbtiles",
                demUrl = null,
                expectedSizeBytes = 48500000,
                tileCount = 31200,
                bounds = "73.5,18.0,135.0,53.5"
            ),
            OfflineMapRegion(
                id = "liaoning",
                name = "省级 (Provincial - Level 1): 辽宁省山地离线精细地图 (liaoning.mbtiles) [zoom 9~11]",
                mbtilesUrl = "https://github.com/cybertrail/assets/releases/download/v1.0/liaoning.mbtiles",
                demUrl = null,
                expectedSizeBytes = 156000000,
                tileCount = 125000,
                bounds = "118.5,38.5,125.8,43.5"
            ),
            OfflineMapRegion(
                id = "dandong",
                name = "市级 (Municipal - Level 2): 丹东市区高精度卫星离线地图 (dandong.mbtiles) [zoom 12~14]",
                mbtilesUrl = "https://github.com/cybertrail/assets/releases/download/v1.0/dandong.mbtiles",
                demUrl = null,
                expectedSizeBytes = 420000000,
                tileCount = 948000,
                bounds = "124.0,39.8,124.8,40.4"
            ),
            OfflineMapRegion(
                id = "dandong_district",
                name = "区县级 (District - Level 3): 丹东宽甸自驾徒步极细微地图包 (dandong_district.mbtiles) [zoom 14~16]",
                mbtilesUrl = "https://github.com/cybertrail/assets/releases/download/v1.0/dandong_district.mbtiles",
                demUrl = null,
                expectedSizeBytes = 680000000,
                tileCount = 1845000,
                bounds = "124.4,40.1,124.9,40.6"
            )
        )
        
        // Scan local directory for downloaded preset maps
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

        // Automatic Discovery: Scan mapsDir for all custom user *.mbtiles files
        try {
            val presetIds = regions.map { it.id.lowercase() }.toSet()
            val mbtilesFiles = mapsDir.listFiles { _, name -> name.endsWith(".mbtiles", ignoreCase = true) }
            if (mbtilesFiles != null) {
                for (file in mbtilesFiles) {
                    val nameWithoutExt = file.nameWithoutExtension.lowercase()
                    if (!presetIds.contains(nameWithoutExt)) {
                        regions.add(
                            OfflineMapRegion(
                                id = file.nameWithoutExtension,
                                name = "自定义导入 (User Custom): 自动发现本地地图 [${file.name}]",
                                mbtilesUrl = "",
                                demUrl = null,
                                expectedSizeBytes = file.length(),
                                tileCount = -1,
                                bounds = "全地理范围 / 自动覆盖",
                                isDownloaded = true,
                                localPath = file.absolutePath
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed scanning custom MBTiles", e)
        }
        
        return regions
    }
    
    fun startDownload(region: OfflineMapRegion): Long {
        if (region.mbtilesUrl.isNullOrEmpty()) {
            Log.e(TAG, "Cannot download MBTiles: empty URL")
            return -1L
        }
        return try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(region.mbtilesUrl))
                .setTitle("正在下载 ${region.name} 离线地图产品")
                .setDescription("存储路径: /CyberTrail/Maps/")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(File(mapsDir, "${region.id}.mbtiles")))
                
            val downloadId = downloadManager.enqueue(request)
            Log.i(TAG, "Successfully enqueued download with ID $downloadId for ${region.id}")
            downloadId
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: startDownload failed for ${region.id}", e)
            -1L
        }
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
        val presetDems = listOf(
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
            ),
            OfflineDemRegion(
                id = "copernicus_dem",
                name = "欧洲空间局地表测绘遥感 (Copernicus Global DEM 30m 融合高程)",
                demUrl = "https://github.com/cybertrail/assets/releases/download/v1.0/liaoning_copernicus.bil",
                demType = "Copernicus DEM",
                expectedSizeBytes = 32150000,
                fileName = "liaoning_copernicus.bil"
            )
        )

        val finalDems = mutableListOf<OfflineDemRegion>()
        val presetFileNames = presetDems.map { it.fileName.lowercase() }.toSet()
        
        presetDems.forEach { dem ->
            val localFile = File(demDir, dem.fileName)
            if (localFile.exists()) {
                dem.isDownloaded = true
                dem.localPath = localFile.absolutePath
            } else {
                dem.isDownloaded = false
                dem.localPath = null
            }
            finalDems.add(dem)
        }

        // Automatic Discovery: Scan demDir for custom *.hgt, *.tif, *.bil, *.img files
        try {
            val customFiles = demDir.listFiles { _, name ->
                name.endsWith(".hgt", ignoreCase = true) ||
                name.endsWith(".tif", ignoreCase = true) ||
                name.endsWith(".bil", ignoreCase = true) ||
                name.endsWith(".img", ignoreCase = true)
            }
            if (customFiles != null) {
                for (file in customFiles) {
                    val fileLower = file.name.lowercase()
                    if (!presetFileNames.contains(fileLower)) {
                        val extension = file.extension.uppercase()
                        val type = when (extension) {
                            "HGT" -> "SRTM HGT"
                            "TIF" -> "ASTER GDEM / GeoTIFF"
                            "BIL" -> "Copernicus DEM / BIL"
                            "IMG" -> "ERDAS IMG / DEM"
                            else -> "Custom DEM Grid File"
                        }
                        finalDems.add(
                            OfflineDemRegion(
                                id = file.nameWithoutExtension,
                                name = "自定义高程: ${file.name}",
                                demUrl = "",
                                demType = type,
                                expectedSizeBytes = file.length(),
                                fileName = file.name,
                                isDownloaded = true,
                                localPath = file.absolutePath
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed scanning custom DEMs", e)
        }
        
        return finalDems
    }

    fun startDemDownload(dem: OfflineDemRegion): Long {
        if (dem.demUrl.isNullOrEmpty()) {
            Log.e(TAG, "Cannot download DEM: empty URL")
            return -1L
        }
        return try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(dem.demUrl))
                .setTitle("正在下载高程数据: ${dem.name}")
                .setDescription("存储路径: /CyberTrail/DEM/")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(File(demDir, dem.fileName)))
                
            val downloadId = downloadManager.enqueue(request)
            Log.i(TAG, "Successfully enqueued DEM download with ID $downloadId for ${dem.id}")
            downloadId
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: startDemDownload failed for ${dem.id}", e)
            -1L
        }
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
