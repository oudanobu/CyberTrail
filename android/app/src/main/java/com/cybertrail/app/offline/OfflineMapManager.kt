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
        val presetMaps = mutableListOf(
            OfflineMapRegion(
                id = "world",
                name = "世界级 (World): 全球基础低分辨率影像覆盖图 (world.mbtiles)",
                mbtilesUrl = "https://openmaptiles.org",
                demUrl = null,
                expectedSizeBytes = 9633792,
                tileCount = 5461,
                bounds = "-180,-85,180,85",
                category = "世界级",
                minZoom = 0,
                maxZoom = 6
            ),
            OfflineMapRegion(
                id = "asia",
                name = "大洲级 (Continent - Asia): 亚洲户外越野混合地形离线地图 (asia.mbtiles)",
                mbtilesUrl = "https://download.geofabrik.de/asia.html",
                demUrl = null,
                expectedSizeBytes = 120500000,
                tileCount = 48200,
                bounds = "60.0,-10.0,150.0,60.0",
                category = "大洲级",
                minZoom = 4,
                maxZoom = 8
            ),
            OfflineMapRegion(
                id = "china",
                name = "国家级 (National - China): 中国陆地区域中层地形鸟瞰矢量图 (china.mbtiles)",
                mbtilesUrl = "https://download.geofabrik.de/asia/china.html",
                demUrl = null,
                expectedSizeBytes = 48500000,
                tileCount = 31200,
                bounds = "73.5,18.0,135.0,53.5",
                category = "国家级",
                minZoom = 6,
                maxZoom = 10
            ),
            OfflineMapRegion(
                id = "japan",
                name = "国家级 (National - Japan): 日本全国高线与自驾道路混合切片地图 (japan.mbtiles)",
                mbtilesUrl = "https://download.geofabrik.de/asia/japan.html",
                demUrl = null,
                expectedSizeBytes = 68000000,
                tileCount = 45000,
                bounds = "122.0,20.0,153.0,46.0",
                category = "国家级",
                minZoom = 6,
                maxZoom = 10
            ),
            OfflineMapRegion(
                id = "korea",
                name = "国家级 (National - Korea): 朝鲜半岛户外徒步等高线地图包 (korea.mbtiles)",
                mbtilesUrl = "https://download.geofabrik.de/asia/south-korea.html",
                demUrl = null,
                expectedSizeBytes = 32000000,
                tileCount = 21000,
                bounds = "124.0,33.0,131.0,43.0",
                category = "国家级",
                minZoom = 6,
                maxZoom = 10
            ),
            OfflineMapRegion(
                id = "liaoning",
                name = "一级行政区 (Provincial): 辽宁省山地森林自驾越野精细地图 (liaoning.mbtiles)",
                mbtilesUrl = "https://download.geofabrik.de/asia/china.html",
                demUrl = null,
                expectedSizeBytes = 156000000,
                tileCount = 125000,
                bounds = "118.5,38.5,125.8,43.5",
                category = "一级行政区",
                minZoom = 9,
                maxZoom = 12
            ),
            OfflineMapRegion(
                id = "tokyo",
                name = "一级行政区 (Provincial): 日本东京都及关东自驾徒步离线瓦片 map (tokyo.mbtiles)",
                mbtilesUrl = "https://download.geofabrik.de/asia/japan.html",
                demUrl = null,
                expectedSizeBytes = 112000000,
                tileCount = 89000,
                bounds = "138.8,35.4,140.2,36.0",
                category = "一级行政区",
                minZoom = 9,
                maxZoom = 12
            ),
            OfflineMapRegion(
                id = "dandong",
                name = "二级行政区 (Municipal): 丹东市区高精度卫星影像及徒步网格 (dandong.mbtiles)",
                mbtilesUrl = "https://download.geofabrik.de/asia/china.html",
                demUrl = null,
                expectedSizeBytes = 420000000,
                tileCount = 948000,
                bounds = "124.0,39.8,124.8,40.4",
                category = "二级行政区",
                minZoom = 12,
                maxZoom = 14
            ),
            OfflineMapRegion(
                id = "shinjuku",
                name = "二级行政区 (Municipal): 日本新宿至大久保极限骑行越野配图 (shinjuku.mbtiles)",
                mbtilesUrl = "https://download.geofabrik.de/asia/japan.html",
                demUrl = null,
                expectedSizeBytes = 45000000,
                tileCount = 31000,
                bounds = "139.68,35.68,139.72,35.72",
                category = "二级行政区",
                minZoom = 12,
                maxZoom = 14
            ),
            OfflineMapRegion(
                id = "mount_fuji",
                name = "三级行政区 (District): 富士山越野徒步三阶爬升路径图 (mount_fuji.mbtiles)",
                mbtilesUrl = "https://download.geofabrik.de/asia/japan.html",
                demUrl = null,
                expectedSizeBytes = 98000000,
                tileCount = 68000,
                bounds = "138.7,35.3,138.8,35.4",
                category = "三级行政区",
                minZoom = 14,
                maxZoom = 17
            )
        )

        val resultList = mutableListOf<OfflineMapRegion>()
        val processedFiles = mutableSetOf<String>()

        // 1. Scan physical mapsDir for existing files and classify them!
        try {
            val mbtilesFiles = mapsDir.listFiles { _, name -> name.endsWith(".mbtiles", ignoreCase = true) }
            if (mbtilesFiles != null) {
                for (file in mbtilesFiles) {
                    val nameLower = file.name.lowercase()
                    processedFiles.add(nameLower)
                    
                    val metadata = extractMbtilesMetadata(file)
                    val presetMatch = presetMaps.firstOrNull { it.id.lowercase() == file.nameWithoutExtension.lowercase() }
                    
                    if (presetMatch != null) {
                        presetMatch.isDownloaded = true
                        presetMatch.localPath = file.absolutePath
                        presetMatch.bounds = if (metadata.bounds.isNotEmpty()) metadata.bounds else presetMatch.bounds
                        presetMatch.minZoom = metadata.minZoom
                        presetMatch.maxZoom = metadata.maxZoom
                        presetMatch.category = metadata.category
                        resultList.add(presetMatch)
                    } else {
                        resultList.add(
                            OfflineMapRegion(
                                id = file.nameWithoutExtension,
                                name = "自定义导入 (User Custom): 自动识别 [${metadata.name}]",
                                mbtilesUrl = "https://openfootprint.org", // default help url
                                demUrl = null,
                                expectedSizeBytes = file.length(),
                                tileCount = -1,
                                bounds = if (metadata.bounds.isNotEmpty()) metadata.bounds else "自动检测范围",
                                isDownloaded = true,
                                localPath = file.absolutePath,
                                category = metadata.category,
                                minZoom = metadata.minZoom,
                                maxZoom = metadata.maxZoom
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed scanning dynamic mbtiles files", e)
        }

        // 2. Add preset maps that are not loaded as placeholders
        presetMaps.forEach { preset ->
            if (!processedFiles.contains("${preset.id}.mbtiles".lowercase())) {
                preset.isDownloaded = false
                preset.localPath = null
                resultList.add(preset)
            }
        }

        return resultList
    }

    private fun extractMbtilesMetadata(file: File): MapMetadata {
        var name = file.nameWithoutExtension
        var minzoom = 0
        var maxzoom = 12
        var bounds = ""
        var category = "一级行政区"

        var db: android.database.sqlite.SQLiteDatabase? = null
        try {
            db = android.database.sqlite.SQLiteDatabase.openDatabase(file.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
            val tableExists = try {
                val c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='metadata'", null)
                val exists = c.count > 0
                c.close()
                exists
            } catch (t: Throwable) {
                false
            }
            if (tableExists) {
                val cursor = db.rawQuery("SELECT name, value FROM metadata", null)
                while (cursor.moveToNext()) {
                    val key = cursor.getString(0)
                    val value = cursor.getString(1)
                    if (key != null && value != null) {
                        when (key.lowercase()) {
                            "name" -> name = value
                            "minzoom" -> minzoom = value.toIntOrNull() ?: minzoom
                            "maxzoom" -> maxzoom = value.toIntOrNull() ?: maxzoom
                            "bounds" -> bounds = value
                        }
                    }
                }
                cursor.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ImportFailed: Metadata query error for ${file.name}", e)
        } finally {
            db?.close()
        }

        // Category Classification Algorithm
        category = when {
            maxzoom <= 6 -> "世界级"
            maxzoom <= 8 -> "大洲级"
            maxzoom <= 10 -> "国家级"
            maxzoom <= 12 -> "一级行政区"
            maxzoom <= 14 -> "二级行政区"
            else -> "三级行政区"
        }

        if (bounds.isNotEmpty()) {
            val parts = bounds.split(",")
            if (parts.size == 4) {
                val minLon = parts[0].toDoubleOrNull()
                val minLat = parts[1].toDoubleOrNull()
                val maxLon = parts[2].toDoubleOrNull()
                val maxLat = parts[3].toDoubleOrNull()
                if (minLon != null && minLat != null && maxLon != null && maxLat != null) {
                    val lonDiff = Math.abs(maxLon - minLon)
                    val latDiff = Math.abs(maxLat - minLat)
                    val area = lonDiff * latDiff
                    if (area > 15000) {
                        category = "世界级"
                    } else if (area > 3000) {
                        category = "大洲级"
                    } else if (area > 300) {
                        category = "国家级"
                    } else if (area > 20) {
                        category = "一级行政区"
                    } else if (area > 1) {
                        category = "二级行政区"
                    } else {
                        category = "三级行政区"
                    }
                }
            }
        }

        Log.d("MAP_DEBUG", "ImportSuccess: file=${file.name}, DetectedBounds=$bounds, DetectedZoomRange=$minzoom~$maxzoom, DetectedLayerType=$category")
        return MapMetadata(category, name, minzoom, maxzoom, bounds)
    }

    data class MapMetadata(
        val category: String,
        val name: String,
        val minZoom: Int,
        val maxZoom: Int,
        val bounds: String
    )

    fun startDownload(region: OfflineMapRegion): Long {
        // App is not downloading anymore: we return -1 and trigger Intent page inside Activity
        return -1L
    }

    fun deleteMap(region: OfflineMapRegion) {
        val file = File(mapsDir, "${region.id}.mbtiles")
        if (file.exists()) {
            file.delete()
        }
        val fileWithExt = File(mapsDir, region.id)
        if (fileWithExt.exists()) {
            fileWithExt.delete()
        }
        region.isDownloaded = false
        region.localPath = null
    }

    fun getAvailableDems(): List<OfflineDemRegion> {
        val presetDems = listOf(
            OfflineDemRegion(
                id = "srtm_hgt",
                name = "SRTM HGT高程包: 30米分辨率雷达地形测绘数高 (.hgt格式)",
                demUrl = "https://earthdata.nasa.gov",
                demType = "SRTM",
                expectedSizeBytes = 25165824,
                fileName = "liaoning.hgt"
            ),
            OfflineDemRegion(
                id = "aster_gdem",
                name = "ASTER GDEM高程包: 全球先进遥感器视差数字高程贴图 (.tif格式)",
                demUrl = "https://asterweb.jpl.nasa.gov",
                demType = "ASTER GDEM",
                expectedSizeBytes = 45123456,
                fileName = "liaoning_aster.tif"
            ),
            OfflineDemRegion(
                id = "geotiff_dem",
                name = "GeoTIFF 高精密遥感高程包: 欧空局高对比全球DEM表面剖面 (.tif格式)",
                demUrl = "https://earth.esa.int",
                demType = "GeoTIFF",
                expectedSizeBytes = 32150000,
                fileName = "liaoning_copernicus.tif"
            )
        )

        val finalDems = mutableListOf<OfflineDemRegion>()
        val processedFileNames = mutableSetOf<String>()

        // Scan actual DEMs
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
                    processedFileNames.add(fileLower)
                    
                    val presetMatch = presetDems.firstOrNull { it.fileName.lowercase() == fileLower }
                    if (presetMatch != null) {
                        presetMatch.isDownloaded = true
                        presetMatch.localPath = file.absolutePath
                        finalDems.add(presetMatch)
                    } else {
                        val extension = file.extension.uppercase()
                        val type = when (extension) {
                            "HGT" -> "SRTM"
                            "TIF" -> "ASTER GDEM / GeoTIFF"
                            "BIL" -> "Copernicus DEM"
                            "IMG" -> "ASTER GDEM"
                            else -> "GeoTIFF"
                        }
                        finalDems.add(
                            OfflineDemRegion(
                                id = file.nameWithoutExtension,
                                name = "自定义导入: ${file.name}",
                                demUrl = "https://earthdata.nasa.gov",
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

        // Add presets not found as placeholders
        presetDems.forEach { preset ->
            if (!processedFileNames.contains(preset.fileName.lowercase())) {
                preset.isDownloaded = false
                preset.localPath = null
                finalDems.add(preset)
            }
        }

        return finalDems
    }

    fun deleteDem(dem: OfflineDemRegion) {
        val file = File(demDir, dem.fileName)
        if (file.exists()) {
            file.delete()
        }
        val fileWithoutExt = File(demDir, dem.id)
        if (fileWithoutExt.exists()) {
            fileWithoutExt.delete()
        }
        dem.isDownloaded = false
        dem.localPath = null
    }

    fun startDemDownload(dem: OfflineDemRegion): Long {
        return -1L
    }

}
