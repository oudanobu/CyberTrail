package com.cybertrail.app.offline

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybertrail.app.R

class OfflineMapActivity : AppCompatActivity() {

    private lateinit var mapManager: OfflineMapManager
    private lateinit var adapter: OfflineMapAdapter
    private lateinit var demAdapter: OfflineDemAdapter
    
    private var regions: List<OfflineMapRegion> = emptyList()
    private var dems: List<OfflineDemRegion> = emptyList()

    private var pendingImportDem: OfflineDemRegion? = null
    private var pendingImportMap: OfflineMapRegion? = null
    
    private val IMPORT_DEM_REQUEST_CODE = 404
    private val IMPORT_MAP_REQUEST_CODE = 405

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_map)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        toolbar.title = "离线数据影像与 DEM 管理"
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        mapManager = OfflineMapManager(this)
        
        // Register map list recycler
        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Register DEM list recycler
        val recyclerViewDems: RecyclerView = findViewById(R.id.recyclerViewDems)
        recyclerViewDems.layoutManager = LinearLayoutManager(this)
        
        refreshList()
    }

    private fun launchFilePickerDem(dem: OfflineDemRegion) {
        pendingImportDem = dem
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "选择高程瓦片数据 (.hgt, .bil, .tif, .img)"), IMPORT_DEM_REQUEST_CODE)
    }

    private fun launchFilePickerMap(map: OfflineMapRegion?) {
        pendingImportMap = map
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "选择离线瓦片地图数据 (.mbtiles)"), IMPORT_MAP_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_DEM_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data
            val targetDem = pendingImportDem
            if (uri != null && targetDem != null) {
                importDemFile(uri, targetDem.fileName)
                refreshList()
            }
        } else if (requestCode == IMPORT_MAP_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data
            val targetMap = pendingImportMap
            if (uri != null) {
                val originalName = getUriFileName(this, uri) ?: "imported_map_${System.currentTimeMillis()}.mbtiles"
                val finalName = if (targetMap != null && !targetMap.id.startsWith("header_")) {
                    "${targetMap.id}.mbtiles"
                } else {
                    originalName
                }
                importMapFile(uri, finalName)
                refreshList()
            }
        }
    }

    private fun getUriFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                Log.e("OfflineMapActivity", "Error getting Uri filename", e)
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result
    }

    private fun importDemFile(sourceUri: Uri, destFileName: String) {
        val destFile = java.io.File(mapManager.demDir, destFileName)
        try {
            contentResolver.openInputStream(sourceUri).use { inputStream ->
                if (inputStream != null) {
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    Log.d("MAP_DEBUG", "ImportSuccess: DEM $destFileName imported successfully")
                    Toast.makeText(this, "高程文件成功导入! 已命名为: ${destFileName}", Toast.LENGTH_SHORT).show()
                    
                    val completeIntent = Intent("com.cybertrail.app.MAP_DOWNLOAD_COMPLETED")
                    sendBroadcast(completeIntent)
                } else {
                    Log.e("MAP_DEBUG", "ImportFailed: opening Stream for DEM $destFileName failed")
                    Toast.makeText(this, "打开高程源文件失败", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MAP_DEBUG", "ImportFailed: Stream error for DEM $destFileName", e)
            Log.e("OfflineMapActivity", "Import DEM failed", e)
            Toast.makeText(this, "外部高程导入失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importMapFile(sourceUri: Uri, destFileName: String) {
        val destFile = java.io.File(mapManager.mapsDir, destFileName)
        try {
            contentResolver.openInputStream(sourceUri).use { inputStream ->
                if (inputStream != null) {
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    Log.d("MAP_DEBUG", "ImportSuccess: Map $destFileName imported successfully")
                    Toast.makeText(this, "离线地图包成功导入! 已命名为: ${destFileName}", Toast.LENGTH_SHORT).show()
                    
                    val completeIntent = Intent("com.cybertrail.app.MAP_DOWNLOAD_COMPLETED")
                    sendBroadcast(completeIntent)
                } else {
                    Log.e("MAP_DEBUG", "ImportFailed: opening Stream for Map $destFileName failed")
                    Toast.makeText(this, "打开地图源文件失败", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MAP_DEBUG", "ImportFailed: Stream error for Map $destFileName", e)
            Log.e("OfflineMapActivity", "Import Map failed", e)
            Toast.makeText(this, "外部地图导入失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1001, 1, "导入任意离线地图").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1001) {
            launchFilePickerMap(null)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun buildCategorizedList(rawList: List<OfflineMapRegion>): List<OfflineMapRegion> {
        val categories = listOf("世界级", "大洲级", "国家级", "一级行政区", "二级行政区", "三级行政区")
        val result = mutableListOf<OfflineMapRegion>()
        
        categories.forEach { cat ->
            val catItems = rawList.filter { it.category == cat }
            if (catItems.isNotEmpty()) {
                val displayName = when(cat) {
                    "世界级" -> "世界级 (World Map) [显示推荐: zoom 0~6]"
                    "大洲级" -> "大洲级 (Continent Map) [显示推荐: zoom 4~8]"
                    "国家级" -> "国家级 (National Map) [显示推荐: zoom 6~10]"
                    "一级行政区" -> "一级行政区 (Provincial / Level 1) [显示推荐: zoom 9~12]"
                    "二级行政区" -> "二级行政区 (Municipal / Level 2) [显示推荐: zoom 12~14]"
                    "三级行政区" -> "三级行政区 (District / Level 3) [显示推荐: zoom 14~16+]"
                    else -> cat
                }
                result.add(
                    OfflineMapRegion(
                        id = "header_$cat",
                        name = displayName,
                        mbtilesUrl = null,
                        demUrl = null,
                        expectedSizeBytes = 0,
                        tileCount = 0,
                        bounds = "",
                        category = cat,
                        isDownloaded = false
                    )
                )
                result.addAll(catItems)
            }
        }
        return result
    }

    private fun refreshList() {
        // Direct scanning without background worker constraints or mock status loops
        val rawRegions = mapManager.getAvailableRegions()
        regions = buildCategorizedList(rawRegions)
        dems = mapManager.getAvailableDems()
        
        adapter = OfflineMapAdapter(regions, { map ->
            if (map.mbtilesUrl.isNullOrEmpty()) {
                Toast.makeText(this, "该地图包下载链接未配置", Toast.LENGTH_SHORT).show()
                return@OfflineMapAdapter
            }
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(map.mbtilesUrl))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开浏览器: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, { map ->
            mapManager.deleteMap(map)
            refreshList()
        }, { map ->
            launchFilePickerMap(map)
        })
        findViewById<RecyclerView>(R.id.recyclerView).adapter = adapter

        demAdapter = OfflineDemAdapter(dems, { dem ->
            if (dem.demUrl.isNullOrEmpty()) {
                Toast.makeText(this, "该高程下载链接未配置或为本地自定义包", Toast.LENGTH_SHORT).show()
                return@OfflineDemAdapter
            }
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(dem.demUrl))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开浏览器: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, { dem ->
            mapManager.deleteDem(dem)
            refreshList()
            Toast.makeText(this, "已彻底删除高程: ${dem.fileName}", Toast.LENGTH_SHORT).show()
        }, { dem ->
            launchFilePickerDem(dem)
        })
        findViewById<RecyclerView>(R.id.recyclerViewDems).adapter = demAdapter
    }
}
