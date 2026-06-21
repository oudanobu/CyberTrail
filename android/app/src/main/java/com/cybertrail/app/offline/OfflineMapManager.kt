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
        val presetMaps = mutableListOf<OfflineMapRegion>()
        try {
            val jsonString = context.assets.open("maps_config.json").use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }
            val jsonArray = org.json.JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                presetMaps.add(
                    OfflineMapRegion(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        mbtilesUrl = obj.optString("mbtilesUrl", null),
                        demUrl = obj.optString("demUrl", null),
                        expectedSizeBytes = obj.optLong("expectedSizeBytes", 0L),
                        tileCount = obj.optInt("tileCount", 0),
                        bounds = obj.optString("bounds", ""),
                        category = obj.optString("category", "未分类"),
                        minZoom = obj.optInt("minZoom", 0),
                        maxZoom = obj.optInt("maxZoom", 22)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing maps_config.json asset", e)
        }

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
            maxzoom <= 6 -> "地球级"
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
                        category = "地球级"
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
