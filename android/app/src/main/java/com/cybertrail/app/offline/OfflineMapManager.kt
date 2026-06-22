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

    fun getCustomMapsJsonFile(): File {
        return File(baseDir, "custom_maps.json")
    }

    fun saveCustomMapMeta(id: String, name: String, category: String, parentName: String, directoryName: String) {
        try {
            val file = getCustomMapsJsonFile()
            val jsonArray = if (file.exists()) {
                try {
                    org.json.JSONArray(file.readText())
                } catch (e: Exception) {
                    org.json.JSONArray()
                }
            } else {
                org.json.JSONArray()
            }

            var found = false
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("id").lowercase() == id.lowercase()) {
                    obj.put("name", name)
                    obj.put("category", category)
                    obj.put("parentName", parentName)
                    obj.put("directoryName", directoryName)
                    found = true
                    break
                }
            }
            if (!found) {
                val obj = org.json.JSONObject()
                obj.put("id", id)
                obj.put("name", name)
                obj.put("category", category)
                obj.put("parentName", parentName)
                obj.put("directoryName", directoryName)
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString(2))
            Log.d(TAG, "Saved custom map meta successfully for $id")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save custom map meta", e)
        }
    }

    private data class CustomMapMeta(
        val id: String,
        val name: String,
        val category: String,
        val parentName: String,
        val directoryName: String
    )

    private fun loadCustomMapsMeta(): Map<String, CustomMapMeta> {
        val result = mutableMapOf<String, CustomMapMeta>()
        try {
            val file = getCustomMapsJsonFile()
            if (file.exists()) {
                val jsonArray = org.json.JSONArray(file.readText())
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getString("id")
                    result[id.lowercase()] = CustomMapMeta(
                        id = id,
                        name = obj.getString("name"),
                        category = obj.getString("category"),
                        parentName = obj.optString("parentName", "中国"),
                        directoryName = obj.optString("directoryName", id)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load custom maps meta", e)
        }
        return result
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
                val id = obj.getString("id")
                val parent = if (obj.isNull("parentName")) null else obj.optString("parentName", null)
                val dir = obj.optString("directoryName", obj.getString("name"))
                val category = obj.optString("category", "一级行政区")
                presetMaps.add(
                    OfflineMapRegion(
                        id = id,
                        name = obj.getString("name"),
                        mbtilesUrl = obj.optString("mbtilesUrl", null),
                        demUrl = obj.optString("demUrl", null),
                        expectedSizeBytes = obj.optLong("expectedSizeBytes", 0L),
                        tileCount = obj.optInt("tileCount", 0),
                        bounds = obj.optString("bounds", ""),
                        category = category,
                        minZoom = obj.optInt("minZoom", 0),
                        maxZoom = obj.optInt("maxZoom", 22),
                        parentName = parent,
                        directoryName = dir
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing maps_config.json asset", e)
        }

        val resultList = mutableListOf<OfflineMapRegion>()
        val processedFiles = mutableSetOf<String>()
        val customMetaMap = loadCustomMapsMeta()

        // 1. Scan physical mapsDir for existing files and classify them!
        try {
            val mbtilesFiles = mapsDir.listFiles { _, name -> name.endsWith(".mbtiles", ignoreCase = true) }
            if (mbtilesFiles != null) {
                for (file in mbtilesFiles) {
                    val nameLower = file.name.lowercase()
                    processedFiles.add(nameLower)
                    
                    val metadata = extractMbtilesMetadata(file)
                    val id = file.nameWithoutExtension
                    val presetMatch = presetMaps.firstOrNull { it.id.lowercase() == id.lowercase() }
                    val customMatch = customMetaMap[id.lowercase()]
                    
                    if (presetMatch != null) {
                        presetMatch.isDownloaded = true
                        presetMatch.localPath = file.absolutePath
                        presetMatch.bounds = if (metadata.bounds.isNotEmpty()) metadata.bounds else presetMatch.bounds
                        presetMatch.minZoom = metadata.minZoom
                        presetMatch.maxZoom = metadata.maxZoom
                        resultList.add(presetMatch)
                    } else if (customMatch != null) {
                        resultList.add(
                            OfflineMapRegion(
                                id = id,
                                name = customMatch.name,
                                mbtilesUrl = "https://github.com",
                                demUrl = null,
                                expectedSizeBytes = file.length(),
                                tileCount = -1,
                                bounds = if (metadata.bounds.isNotEmpty()) metadata.bounds else "自动检测范围",
                                isDownloaded = true,
                                localPath = file.absolutePath,
                                category = customMatch.category,
                                minZoom = metadata.minZoom,
                                maxZoom = metadata.maxZoom,
                                parentName = customMatch.parentName,
                                directoryName = customMatch.directoryName
                            )
                        )
                    } else {
                        // Fully generic fallback if placed in folder manually
                        val level = when (metadata.category) {
                            "地球级" -> "世界"
                            "大洲级" -> "大洲"
                            "国家级" -> "国家"
                            "一级行政区" -> "一级行政区"
                            "二级行政区" -> "二级行政区"
                            "三级行政区" -> "三级行政区"
                            else -> "一级行政区"
                        }
                        resultList.add(
                            OfflineMapRegion(
                                id = id,
                                name = "本地导入: ${id}",
                                mbtilesUrl = "https://github.com",
                                demUrl = null,
                                expectedSizeBytes = file.length(),
                                tileCount = -1,
                                bounds = if (metadata.bounds.isNotEmpty()) metadata.bounds else "自动检测范围",
                                isDownloaded = true,
                                localPath = file.absolutePath,
                                category = metadata.category,
                                minZoom = metadata.minZoom,
                                maxZoom = metadata.maxZoom,
                                parentName = "中国",
                                directoryName = id
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

        // 3. Add custom maps that are in custom_maps.json but not physically present as placeholders
        customMetaMap.forEach { (id, meta) ->
            if (!processedFiles.contains("${id}.mbtiles") && !processedFiles.contains(id)) {
                resultList.add(
                    OfflineMapRegion(
                        id = meta.id,
                        name = meta.name,
                        mbtilesUrl = "https://github.com",
                        demUrl = null,
                        expectedSizeBytes = 0L,
                        tileCount = 0,
                        bounds = "自定义未下载地图",
                        isDownloaded = false,
                        localPath = null,
                        category = meta.category,
                        minZoom = 0,
                        maxZoom = 16,
                        parentName = meta.parentName,
                        directoryName = meta.directoryName
                    )
                )
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

    fun getCustomDemsJsonFile(): File {
        return File(baseDir, "custom_dems.json")
    }

    fun saveCustomDem(name: String, coverage: String, format: String, fileSizeStr: String, githubUrl: String, fileName: String, category: String, parentName: String, directoryName: String) {
        try {
            val file = getCustomDemsJsonFile()
            val jsonArray = if (file.exists()) {
                try {
                    org.json.JSONArray(file.readText())
                } catch (e: Exception) {
                    org.json.JSONArray()
                }
            } else {
                org.json.JSONArray()
            }

            val obj = org.json.JSONObject()
            obj.put("id", "github_dem_" + System.currentTimeMillis())
            obj.put("name", name)
            obj.put("coverage", coverage)
            obj.put("format", format)
            obj.put("fileSizeStr", fileSizeStr)
            obj.put("githubUrl", githubUrl)
            obj.put("fileName", fileName)
            obj.put("category", category)
            obj.put("parentName", parentName)
            obj.put("directoryName", directoryName)
            jsonArray.put(obj)

            file.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save custom DEM", e)
        }
    }

    private data class CustomDemMeta(
        val id: String,
        val name: String,
        val coverage: String,
        val format: String,
        val fileSizeStr: String,
        val githubUrl: String,
        val fileName: String,
        val category: String,
        val parentName: String,
        val directoryName: String
    )

    private fun loadCustomDemsMeta(): List<CustomDemMeta> {
        val result = mutableListOf<CustomDemMeta>()
        try {
            val file = getCustomDemsJsonFile()
            if (file.exists()) {
                val jsonArray = org.json.JSONArray(file.readText())
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    result.add(
                        CustomDemMeta(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            coverage = obj.optString("coverage", "全球/自定义区域"),
                            format = obj.optString("format", "GeoTIFF"),
                            fileSizeStr = obj.optString("fileSizeStr", "未知大小"),
                            githubUrl = obj.optString("githubUrl", "https://github.com"),
                            fileName = obj.optString("fileName", "custom.tif"),
                            category = obj.optString("category", "二级行政区"),
                            parentName = obj.optString("parentName", "中国"),
                            directoryName = obj.optString("directoryName", "自定义")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load custom DEMs meta", e)
        }
        return result
    }

    fun getAvailableDems(): List<OfflineDemRegion> {
        // Updated with official ready-to-import resources
        val presetDems = listOf(
            OfflineDemRegion(
                id = "world_dem",
                name = "全球基础 DEM 离线地形高程图 (World DEM)",
                demUrl = "https://opentopography.org",
                demType = "GeoTIFF格式 (300米精度)",
                expectedSizeBytes = 45123456,
                fileName = "world_dem.tif",
                category = "地球级",
                parentName = "DEM数据",
                directoryName = "世界"
            ),
            OfflineDemRegion(
                id = "japan_dem",
                name = "日本高精度 DEM 离线地形数模 (Japan DEM)",
                demUrl = "https://opentopography.org",
                demType = "GeoTIFF格式 (30米高精密)",
                expectedSizeBytes = 252720000,
                fileName = "japan_dem.tif",
                category = "国家级",
                parentName = "亚洲",
                directoryName = "日本"
            ),
            OfflineDemRegion(
                id = "china_dem",
                name = "中国全境 DEM 离线地形高程图包 (China DEM)",
                demUrl = "https://opentopography.org",
                demType = "GeoTIFF格式 (30米高精密)",
                expectedSizeBytes = 943000000,
                fileName = "china_dem.tif",
                category = "国家级",
                parentName = "亚洲",
                directoryName = "中国"
            ),
            OfflineDemRegion(
                id = "asia_dem",
                name = "亚太高空雷达 SRTM 地形高程模型 (Asia DEM)",
                demUrl = "https://earthdata.nasa.gov",
                demType = "HGT 格式 (30米级雷达测绘)",
                expectedSizeBytes = 1258000000,
                fileName = "asia_dem.tif",
                category = "大洲级",
                parentName = "世界",
                directoryName = "亚洲"
            ),
            OfflineDemRegion(
                id = "liaoning_srtm",
                name = "辽宁局部 SRTM 30米级精细高程雷达芯片 (.hgt格式)",
                demUrl = "https://dds.cr.usgs.gov/srtm/version2_1/SRTM3/Eurasia/",
                demType = "SRTM",
                expectedSizeBytes = 25165824,
                fileName = "liaoning.hgt",
                category = "一级行政区",
                parentName = "中国",
                directoryName = "辽宁"
            )
        )

        val finalDems = mutableListOf<OfflineDemRegion>()
        val processedFileNames = mutableSetOf<String>()

        // Scan actual physical DEMs inside the directory
        try {
            val customFiles = demDir.listFiles { _, name ->
                name.endsWith(".hgt", ignoreCase = true) ||
                name.endsWith(".tif", ignoreCase = true) ||
                name.endsWith(".tiff", ignoreCase = true) ||
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
                            "TIFF" -> "GeoTIFF"
                            "BIL" -> "Copernicus DEM"
                            "IMG" -> "ASTER GDEM"
                            else -> "GeoTIFF"
                        }
                        finalDems.add(
                            OfflineDemRegion(
                                id = file.nameWithoutExtension,
                                name = "本地导入 DEM: ${file.name}",
                                demUrl = "https://opentopography.org",
                                demType = type,
                                expectedSizeBytes = file.length(),
                                fileName = file.name,
                                isDownloaded = true,
                                localPath = file.absolutePath,
                                category = "二级行政区",
                                parentName = "辽宁",
                                directoryName = "自定义导入"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed scanning custom DEMs", e)
        }

        // Add presets not found on disk as download options
        presetDems.forEach { preset ->
            if (!processedFileNames.contains(preset.fileName.lowercase())) {
                preset.isDownloaded = false
                preset.localPath = null
                finalDems.add(preset)
            }
        }

        // Add custom-added github or user DEMs from custom_dems.json
        val customDemsMeta = loadCustomDemsMeta()
        customDemsMeta.forEach { meta ->
            val fileNameLower = meta.fileName.lowercase()
            val isOnDisk = processedFileNames.contains(fileNameLower)
            
            // If already processed as a generic local file, let's update it or just add with proper name!
            if (isOnDisk) {
                // Remove generic local matching first if exists to avoid duplication
                finalDems.removeAll { it.fileName.lowercase() == fileNameLower }
            }

            finalDems.add(
                OfflineDemRegion(
                    id = meta.id,
                    name = "${meta.name} [${meta.coverage}]",
                    demUrl = meta.githubUrl,
                    demType = "${meta.format} (${meta.fileSizeStr})",
                    expectedSizeBytes = if (isOnDisk) File(demDir, meta.fileName).length() else 0L,
                    fileName = meta.fileName,
                    isDownloaded = isOnDisk,
                    localPath = if (isOnDisk) File(demDir, meta.fileName).absolutePath else null,
                    category = meta.category,
                    parentName = meta.parentName,
                    directoryName = meta.directoryName
                )
            )
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
