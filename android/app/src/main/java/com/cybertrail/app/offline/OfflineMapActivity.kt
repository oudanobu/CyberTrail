package com.cybertrail.app.offline

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybertrail.app.R
import java.io.File

class OfflineMapActivity : AppCompatActivity() {

    private lateinit var mapManager: OfflineMapManager
    private lateinit var treeAdapter: OfflineTreeAdapter
    
    // Path navigation stack
    private val navStack = ArrayList<String>()

    private var regions: List<OfflineMapRegion> = emptyList()
    private var dems: List<OfflineDemRegion> = emptyList()

    private var pendingImportDem: OfflineDemRegion? = null
    private var pendingImportMap: OfflineMapRegion? = null
    
    private val IMPORT_DEM_REQUEST_CODE = 404
    private val IMPORT_MAP_REQUEST_CODE = 405

    private lateinit var tvBreadcrumbs: TextView
    private lateinit var btnNavigateUp: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_map)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        toolbar.title = "数字航迹离线数据浏览器"
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { 
            handleBackNavigation()
        }

        mapManager = OfflineMapManager(this)
        tvBreadcrumbs = findViewById(R.id.tv_breadcrumbs)
        btnNavigateUp = findViewById(R.id.btn_navigate_up)

        btnNavigateUp.setOnClickListener {
            handleBackNavigation()
        }

        // Initialize Tree List Recycler
        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        treeAdapter = OfflineTreeAdapter(
            items = emptyList(),
            onFolderClick = { folder ->
                // Navigate into child folder path
                navStack.clear()
                navStack.addAll(folder.targetPath)
                renderCurrentPath()
            },
            onMapOpenWeb = { region ->
                openBrowser(region.mbtilesUrl)
            },
            onMapCopyUrl = { region ->
                copyToClipboard("地图包 - " + region.name, region.mbtilesUrl)
            },
            onMapImportLocal = { region ->
                launchFilePickerMap(region)
            },
            onMapDelete = { region ->
                mapManager.deleteMap(region)
                renderCurrentPath()
                Toast.makeText(this, "已彻底删除本地瓦片: ${region.id}.mbtiles", Toast.LENGTH_SHORT).show()
            },
            onDemOpenWeb = { dem ->
                openBrowser(dem.demUrl)
            },
            onDemCopyUrl = { dem ->
                copyToClipboard("DEM高程 - " + dem.name, dem.demUrl)
            },
            onDemImportLocal = { dem ->
                launchFilePickerDem(dem)
            },
            onDemDelete = { dem ->
                mapManager.deleteDem(dem)
                renderCurrentPath()
                Toast.makeText(this, "已彻底卸载本地高程: ${dem.fileName}", Toast.LENGTH_SHORT).show()
            },
            onHelpOpenWeb = { url ->
                openBrowser(url)
            },
            onHelpCopyUrl = { url ->
                copyToClipboard("数据服务", url)
            },
            onHelpOpenGithub = { url ->
                openBrowser(url)
            }
        )
        recyclerView.adapter = treeAdapter

        renderCurrentPath()
    }

    private fun handleBackNavigation() {
        if (navStack.isNotEmpty()) {
            navStack.removeAt(navStack.size - 1)
            renderCurrentPath()
        } else {
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (navStack.isNotEmpty()) {
            navStack.removeAt(navStack.size - 1)
            renderCurrentPath()
        } else {
            super.onBackPressed()
        }
    }

    private fun openBrowser(url: String?) {
        if (url.isNullOrEmpty()) {
            Toast.makeText(this, "该数据项没有关联的联机下载地址", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法调用设备外部浏览器: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(label: String, text: String?) {
        if (text.isNullOrEmpty()) {
            Toast.makeText(this, "复制内容为空", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "🚀 $label 极速下载连接已成功复制到剪贴板！", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "剪贴板拒绝访问: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderCurrentPath() {
        // Refresh available items from manager
        regions = mapManager.getAvailableRegions()
        dems = mapManager.getAvailableDems()

        val items = ArrayList<OfflineTreeItem>()
        
        // Build crumbs presentation
        if (navStack.isEmpty()) {
            tvBreadcrumbs.text = "📍 首页"
            btnNavigateUp.visibility = View.GONE

            // Categories list
            items.add(OfflineTreeItem.Folder("🌍 地球级：全球高亮底图与简易越野覆盖", "🌍", "点击下钻全球基础低分辨率MBTiles离线大图", listOf("地球级")))
            items.add(OfflineTreeItem.Folder("🌏 大洲级：区域山脉骑行/徒步混合网格", "🌏", "包含亚洲、欧洲、北美洲、南美洲、非洲、大洋洲等", listOf("大洲级")))
            items.add(OfflineTreeItem.Folder("🗾 国家级及省市行政行政细分瓦片", "🗾", "精细等高高线层级：中国、日本、美国、德国各省市及徒步特区", listOf("国家级")))
            items.add(OfflineTreeItem.Folder("🏔 离线地形：DEM高程数模立体控制核心", "🏔", "SRTM HGT 30m、ASTER Tif高阶数据，支持导入本地自定义地形", listOf("DEM数据")))
            items.add(OfflineTreeItem.Folder("📚 离线高频数据下载源与使用姿势指南", "📚", "提供NASA雷达测绘、ASTER、Copernicus和GitHub镜像直接连接", listOf("使用手册")))
            items.add(OfflineTreeItem.Folder("⚙ 物理数据目录诊断机自我检测", "⚙", "快捷扫描Maps和DEM文件后缀名与元数据校验，发现格式损坏", listOf("诊断系统")))
        } else {
            btnNavigateUp.visibility = View.VISIBLE
            val depthName = navStack.joinToString(" > ")
            tvBreadcrumbs.text = "📍 首页 > $depthName"

            val level = navStack[0]
            when (level) {
                "地球级" -> {
                    regions.filter { it.category == "地球级" }.forEach {
                        items.add(OfflineTreeItem.MapFile(it))
                    }
                }
                "大洲级" -> {
                    regions.filter { it.category == "大洲级" }.forEach {
                        items.add(OfflineTreeItem.MapFile(it))
                    }
                }
                "国家级" -> {
                    if (navStack.size == 1) {
                        // Country Root Folder level
                        items.add(OfflineTreeItem.Folder("📁 中国 (China) 省市详细高线特区", "📁", "可深入钻取：辽宁山脉自驾精细瓦片、丹东遥感等", listOf("国家级", "中国")))
                        regions.find { it.id == "china" }?.let { items.add(OfflineTreeItem.MapFile(it)) }

                        items.add(OfflineTreeItem.Folder("📁 日本 (Japan) 县府精细骑行极图", "📁", "可深入钻取：东京都关东片区、北海道雪线自驾、新宿、富士山等", listOf("国家级", "日本")))
                        regions.find { it.id == "japan" }?.let { items.add(OfflineTreeItem.MapFile(it)) }

                        items.add(OfflineTreeItem.Folder("📁 美国 (USA) 各州国家公园细化层", "📁", "可深入钻取：加利福尼亚野径及露营地、洛杉矶越野等", listOf("国家级", "美国")))
                        regions.find { it.id == "usa" }?.let { items.add(OfflineTreeItem.MapFile(it)) }

                        items.add(OfflineTreeItem.Folder("📁 德国 (Germany) 联邦阿尔卑斯山区", "📁", "可深入钻取：巴伐利亚深林古堡与徒步、高密度等高", listOf("国家级", "德国")))
                        regions.find { it.id == "germany" }?.let { items.add(OfflineTreeItem.MapFile(it)) }

                        // Standalone country packages with no sub-folders declared
                        regions.filter { it.category == "国家级" && it.id !in listOf("china", "japan", "usa", "germany") }.forEach {
                            items.add(OfflineTreeItem.MapFile(it))
                        }
                    } else if (navStack.size == 2) {
                        val country = navStack[1]
                        when (country) {
                            "中国" -> {
                                regions.find { it.id == "liaoning" }?.let { items.add(OfflineTreeItem.MapFile(it)) }
                                items.add(OfflineTreeItem.Folder("📁 辽宁省 (Liaoning) 省内二级市级瓦片", "📁", "可钻取：丹东高精度卫星影像及徒步网格", listOf("国家级", "中国", "辽宁")))
                            }
                            "日本" -> {
                                regions.find { it.id == "tokyo" }?.let { items.add(OfflineTreeItem.MapFile(it)) }
                                regions.find { it.id == "hokkaido" }?.let { items.add(OfflineTreeItem.MapFile(it)) }
                                items.add(OfflineTreeItem.Folder("📁 东京都特区细划、徒步登山线", "📁", "可下钻：新宿骑行越野图、富士山等高攀登", listOf("国家级", "日本", "东京都")))
                                items.add(OfflineTreeItem.Folder("📁 北海道二级市府与滑雪特指线", "📁", "可下钻：札幌雪道越野与自驾", listOf("国家级", "日本", "北海道")))
                            }
                            "美国" -> {
                                regions.find { it.id == "california" }?.let { items.add(OfflineTreeItem.MapFile(it)) }
                                items.add(OfflineTreeItem.Folder("📁 加利福尼亚县级行政区划细节", "📁", "可钻取：洛杉矶县野外自驾探险骑行地图", listOf("国家级", "美国", "加州")))
                            }
                            "德国" -> {
                                regions.find { it.id == "bavaria" }?.let { items.add(OfflineTreeItem.MapFile(it)) }
                            }
                        }
                    } else if (navStack.size == 3) {
                        val subCategory = navStack[2]
                        when (subCategory) {
                            "辽宁" -> {
                                regions.find { it.id == "dandong" }?.let { items.add(OfflineTreeItem.MapFile(it)) }
                            }
                            "东京都" -> {
                                regions.find { it.id == "shinjuku" }?.let { items.add(OfflineTreeItem.MapFile(it)) }
                                regions.find { it.id == "mount_fuji" }?.let { items.add(OfflineTreeItem.MapFile(it)) }
                            }
                            "北海道" -> {
                                regions.find { it.id == "sapporo" }?.let { items.add(OfflineTreeItem.MapFile(it)) }
                            }
                            "加州" -> {
                                regions.find { it.id == "los_angeles" }?.let { items.add(OfflineTreeItem.MapFile(it)) }
                            }
                        }
                    }
                }
                "DEM数据" -> {
                    dems.forEach {
                        items.add(OfflineTreeItem.DemFile(it))
                    }
                }
                "使用手册" -> {
                    // Populate educational manuals with beautiful content
                    items.add(OfflineTreeItem.HelpManual(
                        "📘 NASA SRTM 30m Global DEM",
                        "来自美国太空局/地质局雷达测绘计划 (.hgt 格式)",
                        "• 简评：最经典的30米雷达地面高程数值，无损测试极其优异。\n• 配适：由于是30m高密度，非常适合攀岩、登山、山脊越野。\n• 导入说明：可直接将 .hgt 文件放入 /CyberTrail/DEM 目录下，系统即可无感自动加载并动态生成任意航线高低图表。",
                        "https://earthdata.nasa.gov",
                        "https://dds.cr.usgs.gov/srtm/version2_1/SRTM3/Eurasia/",
                        "https://github.com/tilezen/joerd"
                    ))
                    items.add(OfflineTreeItem.HelpManual(
                        "📘 ASTER GDEM 先进遥感地形表面",
                        "来自 NASA 与日本林野厅(METI) 联合遥感测量 (.tif 格式)",
                        "• 简评：全球1弧秒（相当于30m）超清三维表面高程，数字水文条件完美匹配。\n• 配适：高寒冻海、落叶丛林、陡峭悬崖的三维阴影与实时坡向解算精度极具说服力。\n• 提示：系统支持将 .tif 遥感图拖入，自动解压渲染，坡度偏差在3%以内。",
                        "https://asterweb.jpl.nasa.gov",
                        "https://search.earthdata.nasa.gov/search",
                        "https://github.com/bopen/elevation"
                    ))
                    items.add(OfflineTreeItem.HelpManual(
                        "📘 Copernicus Cop-30 极地无死角数模",
                        "欧空局 ESA 与空中客车提供 (.tif /.tiff 格式)",
                        "• 简评：去除了阴影斑驳和雷达死角填补的超精品三维地球模型。\n• 特点：对阿尔卑斯、喜马拉雅等极端高度地带做了完全重校准解密。\n• 提示：支持导入本客户端的物理 DEM 目录下，直接解算瞬间爬坡率及垂直陡度。",
                        "https://earth.esa.int",
                        "https://copernicus-dem-30m.s3.amazonaws.com/",
                        "https://github.com/simonfuhrmann/mve"
                    ))
                    items.add(OfflineTreeItem.HelpManual(
                        "📘 OpenTopography 航天立体地形与点云数据集",
                        "世界级开源高精确山体三模资源库",
                        "• 简评：不仅有 30 米 SRTM，还有 1 米 级 LiDAR 精密等高线文件可做局部极限越野徒步实验。\n• 下载说明：选择目标边界区域，输出格式选 .hgt 或是 GeoTIFF，极力推荐在 GitHub 的 OpenTopography 镜像脚本直接批量打包拉取！",
                        "https://opentopography.org",
                        "https://portal.opentopography.org/raster",
                        "https://github.com/geofabrik/openstreetmap-mbtiles-generator"
                    ))
                }
                "诊断系统" -> {
                    // Navigate to diagnostics and reset path so it pops elegantly
                    navStack.clear()
                    startActivity(Intent(this, OfflineDiagnosticActivity::class.java))
                    renderCurrentPath()
                    return
                }
            }
        }

        treeAdapter.updateData(items)
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_DEM_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data
            val targetDem = pendingImportDem
            if (uri != null && targetDem != null) {
                importDemFile(uri, targetDem.fileName)
                renderCurrentPath()
            }
        } else if (requestCode == IMPORT_MAP_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data
            val targetMap = pendingImportMap
            if (uri != null) {
                val originalName = getUriFileName(this, uri) ?: "imported_map_${System.currentTimeMillis()}.mbtiles"
                val finalName = if (targetMap != null) {
                    "${targetMap.id}.mbtiles"
                } else {
                    originalName
                }
                importMapFile(uri, finalName)
                renderCurrentPath()
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
            result = uri.path?.substringAfterLast('/')
        }
        return result
    }

    private fun importDemFile(sourceUri: Uri, destFileName: String) {
        val destFile = File(mapManager.demDir, destFileName)
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
        val destFile = File(mapManager.mapsDir, destFileName)
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
        renderCurrentPath()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1001, 1, "导入任意离线地图").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, 1002, 2, "数据诊断系统").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1001) {
            launchFilePickerMap(null)
            return true
        } else if (item.itemId == 1002) {
            startActivity(Intent(this, OfflineDiagnosticActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
