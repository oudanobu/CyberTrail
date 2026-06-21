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
        if (text == null || text.isEmpty()) {
            Toast.makeText(this, "复制内容为空", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "🚀 " + label + " 极速下载连接已成功复制到剪贴板!", Toast.LENGTH_SHORT).show();
        } catch (e: Exception) {
            Toast.makeText(this, "复制到剪贴板失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            items.add(OfflineTreeItem.Folder("🗺️ 离线地图树状分类浏览器 (按世界->大洲->国家层级下钻)", "🌍", "点击展开树形浏览器配置各层级瓦片包", listOf("世界")))
            items.add(OfflineTreeItem.Folder("🏔️ DEM 地形高程数据资源中心", "🏔️", "SRTM HGT 30m、ASTER Tif高阶数据，支持添加自定义 地形", listOf("DEM数据")))
            items.add(OfflineTreeItem.Folder("⭐️ GitHub 专业开源地图与地形数模生态中心", "⭐️", "聚合编译引擎、点云解算工具及全球三维切片开源贡献源", listOf("GitHub生态")))
            items.add(OfflineTreeItem.Folder("📚 离线高频数据下载源与使用姿势指南", "📚", "提供官方离线包、雷达测绘、ASTER和GitHub镜像下载指南", listOf("使用手册")))
            items.add(OfflineTreeItem.Folder("🛠️ 开发者模式：专业地图转换链与规则源码", "🛠️", "OSM源码、Planetiler编译规则、Joerd高程栅格转换工具及GPKG/SHP底座", listOf("开发者模式")))
            items.add(OfflineTreeItem.Folder("⚙️ 物理数据目录诊断机自我检测", "⚙️", "极速检索Maps和DEM数据目录名及物理数据结构完整性", listOf("诊断系统")))
        } else {
            btnNavigateUp.visibility = View.VISIBLE
            val depthName = navStack.joinToString(" > ")
            tvBreadcrumbs.text = "📍 首页 > $depthName"

            val level = navStack[0]
            when (level) {
                "世界" -> {
                    val currentDir = navStack.last()

                    // 1. Add active parent collapsible headers
                    for (i in 0 until navStack.size) {
                        val pathDir = navStack[i]
                        val displayLabel = if (i == 0) "世界" else "▼ $pathDir"
                        items.add(
                            OfflineTreeItem.Folder(
                                name = displayLabel,
                                icon = "📁",
                                details = "已展开/点击回到 $pathDir 目录下",
                                targetPath = navStack.subList(0, i + 1).toList()
                            )
                        )
                    }

                    // 2. Discover children directory names dynamically under the current directory
                    val childDirs = mutableSetOf<String>()
                    regions.forEach { region ->
                        if (region.parentName?.lowercase() == currentDir.lowercase()) {
                            region.directoryName?.let { childDirs.add(it) }
                        }
                    }

                    childDirs.sorted().forEach { childDir ->
                        val subPath = ArrayList(navStack)
                        subPath.add(childDir)
                        items.add(
                            OfflineTreeItem.Folder(
                                name = "▶ $childDir",
                                icon = "📁",
                                details = "下钻/展开 $childDir 各子区划与地图包",
                                targetPath = subPath
                            )
                        )
                    }

                    // 3. Add actual map files within this directory
                    regions.filter { it.directoryName?.lowercase() == currentDir.lowercase() }.forEach { region ->
                        items.add(OfflineTreeItem.MapFile(region))
                    }
                }
                "DEM数据" -> {
                    dems.forEach {
                        items.add(OfflineTreeItem.DemFile(it))
                    }
                }
                "使用手册" -> {
                    items.add(OfflineTreeItem.HelpManual(
                        "📘 NASA SRTM 30m Global DEM",
                        "推荐级别: 🌟🌟🌟🌟🌟 | 推荐格式: .hgt",
                        "📡 美国太空局雷达地形测绘计划，经典全球30米级多维大底座。\n\n📥 极速下载与导入标准步骤:\n1. 登录 EarthData (点击下面[打开主站])\n2. 高频定位并搜索目标感兴趣区域数据\n3. 导出 SRTMGL1 高程类型数据包\n4. 单击下载对应的 .hgt 格式物理成果文件\n5. 放置入闪存: /CyberTrail/DEM 目录下，或直接点击[导入本地]按钮无感加载！",
                        "https://earthdata.nasa.gov",
                        "https://dds.cr.usgs.gov/srtm/version2_1/SRTM3/Eurasia/",
                        "https://github.com/tilezen/joerd"
                    ))
                    items.add(OfflineTreeItem.HelpManual(
                        "📘 ASTER GDEM 先进遥感物理表面高程",
                        "推荐级别: 🌟🌟🌟🌟 | 推荐格式: GeoTIFF (.tif)",
                        "📡 NASA 与日本林野厅行星联合光学及多波谱解算高精密网幅。\n\n📥 极速下载与导入标准步骤:\n1. 访问 ASTER 地面中心并搜索地图瓦片\n2. 在搜索结果中勾选 ASTER Global DEM 遥感产品\n3. 导出并获取生成的 TIFF (.tif) 数字模型文件\n4. 复制成果并传入大内存卡: /CyberTrail/DEM 物理路径下",
                        "https://asterweb.jpl.nasa.gov",
                        "https://search.earthdata.nasa.gov/search",
                        "https://github.com/bopen/elevation"
                    ))
                    items.add(OfflineTreeItem.HelpManual(
                        "📘 Copernicus Cop-30 欧盟空天高净高程",
                        "推荐级别: 🌟🌟🌟🌟🌟 | 推荐格式: GeoTIFF (.tif)",
                        "📡 欧空局 ESA 与空中客车强力校准，完美解决雷达夹角和阴影斑驳噪声点。\n\n📥 极速下载与导入标准步骤:\n1. 打开 Copernicus 可视化选择接口\n2. 定位大洲或特定省份区域坐标，下载最新发布的 Cop-30 极精地形瓦片\n3. 确认格式为标准 .tif 或 .tiff 多级压缩瓦片\n4. 放入手机闪存 /CyberTrail/DEM 并一键触发刷新生效",
                        "https://earth.esa.int",
                        "https://copernicus-dem-30m.s3.amazonaws.com/",
                        "https://github.com/simonfuhrmann/mve"
                    ))
                    items.add(OfflineTreeItem.HelpManual(
                        "📘 OpenTopography 航天立体地形与点云数据集",
                        "推荐级别: 🌟🌟🌟🌟🌟 | 推荐格式: .tif / .hgt",
                        "📡 极客与学术级高清遥感镜像中心，包含1米级高密度 LiDAR 散点解算。\n\n📥 极速下载与导入标准步骤:\n1. 框选并锁定您的越野探险目标区域\n2. 输出格式选取 .hgt 或者标准的 GeoTIFF\n3. 提交后台执行，下载压缩包并解压\n4. 放置对应的 .tif 或是 .hgt 格式文件入 /CyberTrail/DEM 物理文件夹",
                        "https://opentopography.org",
                        "https://portal.opentopography.org/raster",
                        "https://github.com/geofabrik/openstreetmap-mbtiles-generator"
                    ))
                }
                "GitHub生态" -> {
                    // 地图资源
                    items.add(OfflineTreeItem.HelpManual(
                        "📦 OpenMapTiles 矢量切片核心方案 (Star: 4.8k)",
                        "全球开源矢量航迹地图编译框架",
                        "• 项目地址: https://github.com/openmaptiles/openmaptiles\n• 核心用途: 提供一整套基于 Docker 容器的 OSM 转 MBTiles 工具链。\n• 优点: 支持多国语言、道路拓扑骨架和户外等高线，完美配适 MVT 14级全速渲染！",
                        "https://github.com/openmaptiles/openmaptiles",
                        "https://github.com/openmaptiles/openmaptiles",
                        "https://github.com/openmaptiles/openmaptiles"
                    ))
                    items.add(OfflineTreeItem.HelpManual(
                        "📦 Planetiler 极致多线程矢量切片引擎 (Star: 1.5k)",
                        "Java 极速 MVT 切片生成器",
                        "• 项目地址: https://github.com/onthegomap/planetiler\n• 核心用途: 将全球 OpenStreetMap 的 .pbf 数据编译成标准的 .mbtiles。\n• 优点: 性能奇高，支持任意机器配置，能在短时间内打包全省乃至全国离线图级！",
                        "https://github.com/onthegomap/planetiler",
                        "https://github.com/onthegomap/planetiler",
                        "https://github.com/onthegomap/planetiler"
                    ))
                    items.add(OfflineTreeItem.HelpManual(
                        "📦 Tilemaker 无外部依赖单机切片生成器 (Star: 3.1k)",
                        "C++ 编写的无缝 OSM 到 MBTiles 构建器",
                        "• 项目地址: https://github.com/systemed/tilemaker\n• 核心用途: 脱离任何复杂服务端，可直接单机编译单省/单市地图。\n• 优点: 配合自定义 JSON profile 进行个性化的底图图标提取与配饰渲染！",
                        "https://github.com/systemed/tilemaker",
                        "https://github.com/systemed/tilemaker",
                        "https://github.com/systemed/tilemaker"
                    ))
                    items.add(OfflineTreeItem.HelpManual(
                        "📦 MapTiler Open Data 开源数据集 (Star: 1.2k)",
                        "开源公共地理、地形与地貌切片数据库",
                        "• 项目地址: https://github.com/maptiler\n• 核心用途: 涵盖全球各类开放卫星地图瓦片数据库配置和免费图层。\n• 优点: 提供了与 CyberTrail 完全兼容的离线静态地图样本库！",
                        "https://github.com/maptiler",
                        "https://github.com/maptiler",
                        "https://github.com/maptiler"
                    ))

                    // DEM 资源
                    items.add(OfflineTreeItem.HelpManual(
                        "📐 Tilezen / Joerd 地势高程流解算工具 (Star: 920)",
                        "全球海拔/DEM 数据自动化下载合并套件",
                        "• 项目地址: https://github.com/tilezen/joerd\n• 核心用途: 自适应下载 AWS Mapzen 地物模型，并执行自动拼版及格式转换。\n• 适用: 生成无死角的全球多分辨率2.5D高程遥感格式底图！",
                        "https://github.com/tilezen/joerd",
                        "https://github.com/tilezen/joerd",
                        "https://github.com/tilezen/joerd"
                    ))
                    items.add(OfflineTreeItem.HelpManual(
                        "📐 OpenTopography Tools API 脚本 (Star: 430)",
                        "官方推荐的高清多格式高程批量提取套件",
                        "• 项目地址: https://github.com/opentopography\n• 核心用途: 支持批量、按地标、经纬度边界框调取超清 HGT 及 TIFF 栅格图。\n• 优势: 省去繁琐的网页筛选步骤，极速进行局部越野航路高程获取！",
                        "https://github.com/opentopography",
                        "https://github.com/opentopography",
                        "https://github.com/opentopography"
                    ))
                    items.add(OfflineTreeItem.HelpManual(
                        "📐 GDAL 开源地理空间开发底座 (Star: 4.9k)",
                        "全球 GIS 最权威的地形与切片重采样转换程序",
                        "• 项目地址: https://github.com/OSGeo/gdal\n• 核心用途: 提供了 `gdal_translate`, `gdal_dem` 等极其强悍的命令行格式工具。\n• 作用: 拼接、裁切、重采样多张 ASTER .tif 片段至本地可用 DEM 数据！",
                        "https://github.com/OSGeo/gdal",
                        "https://github.com/OSGeo/gdal",
                        "https://github.com/OSGeo/gdal"
                    ))
                    items.add(OfflineTreeItem.HelpManual(
                        "📐 Rasterio Python 地形栅格操作包 (Star: 1.8k)",
                        "基于 GDAL 的现代 Python 地图处理流",
                        "• 项目地址: https://github.com/rasterio/rasterio\n• 核心用途: 优雅、高性能地读取、过滤、转化山河高程矩阵及像素瓦片。\n• 适用: 自研离线地图转换链条和越野登高高程提取程序的开发者！",
                        "https://github.com/rasterio/rasterio",
                        "https://github.com/rasterio/rasterio",
                        "https://github.com/rasterio/rasterio"
                    ))
                    items.add(OfflineTreeItem.HelpManual(
                        "📐 Terrain Tiles AWS 数据资源 (Star: 760)",
                        "全球瓦片化多阶等高切片汇总描述库",
                        "• 项目地址: https://github.com/tilezen/joerd\n• 核心用途: 多层地形模型合并及高度、山峰阻碍解算。\n• 优点: 提供标准的瓦片化高程资源库说明书及现成下载节点！",
                        "https://github.com/tilezen/joerd",
                        "https://github.com/tilezen/joerd",
                        "https://github.com/tilezen/joerd"
                    ))
                }
                "开发者模式" -> {
                    items.add(OfflineTreeItem.HelpManual(
                        "💻 OSM 原始路网与多格式 PBF 抽取源",
                        "OpenStreetMap 原生 XML/PBF 数据转换链路",
                        "• 简评：包含了全球最完整的地理数据标记和拓扑网。\n• 配适：通过 Osmosis 过滤或 Planetiler 构建，可极限压缩生成户外精简 MBTiles。\n• 功能：可极大缩减大洲级数据，抽取出专门的等高线、森林、越野跑点图层。",
                        "https://openstreetmap.org",
                        "https://download.geofabrik.de",
                        "https://github.com/openstreetmap/osmosis"
                    ))
                    items.add(OfflineTreeItem.HelpManual(
                        "💻 Planetiler 高并发多线程矢量编译引擎 (OSM 源码编译)",
                        "基于 Java 的十亿级矢量切片生成器",
                        "• 简评：在数十核高配置服务器下，仅数十分钟即可转化 100GB 级的原始全球 .pbf 文件。\n• 构建规则：用户自行载入 schema-mapping、自定义户外样式图层配置。\n• 注意：对内存和磁盘IO要求较高，通过它能零误差打包输出标准的 .mbtiles 并由 CyberTrail 瞬间加载！",
                        "https://github.com/onthegomap/planetiler",
                        "https://github.com/onthegomap/planetiler/releases",
                        "https://github.com/onthegomap/planetiler"
                    ))
                    items.add(OfflineTreeItem.HelpManual(
                        "💻 ESRI Shapefile (.shp) 与 GeoPackage (.gpkg) 底座转换",
                        "专业级地理空间多图层矢量数据容器",
                        "• 简评：传统 GIS 软件产出的要素和多图层几何集合。\n• 转换机制：在宿主机端通过 `ogr2ogr` 或是 python `geopandas` 打包成 MVT 瓦片数据库。\n• 说明：这是底层地图学专家生产定制路线必经的离线化流程，转化出的 mbtiles 契合 CyberTrail 加载。",
                        "https://gdal.org",
                        "https://gdal.org/programs/ogr2ogr.html",
                        "https://github.com/OSGeo/gdal"
                    ))
                    items.add(OfflineTreeItem.HelpManual(
                        "💻 Joerd Terrain 自动化高程解算转换套件",
                        "AWS Mapzen 地形瓦片与世界各地高密散碎高程的拼接工具",
                        "• 简评：基于 Python 编写的全球 DEM 合并拼接开源套件。\n• 配适：自适应瓦片地形合并。能够把零散的 HGT 片段自动化拼接，并通过 GDAL 转换为 2.5D 高密度图纸，完美提供三维地形建模基座。",
                        "https://github.com/tilezen/joerd",
                        "https://github.com/tilezen/joerd/archive/refs/heads/master.zip",
                        "https://github.com/tilezen/joerd"
                    ))
                }
                "诊断系统" -> {
                    navStack.clear()
                    startActivity(Intent(this, OfflineDiagnosticActivity::class.java))
                    renderCurrentPath()
                    return
                }
            }
        }

        treeAdapter.updateData(items)
    }

    private fun showCustomImportDialog(sourceUri: Uri, originalFileName: String) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("📥 配置并导入自定义离线地图")

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val tvName = android.widget.TextView(this).apply {
            text = "地图/文件显示名称:"
            textSize = 14f
            setPadding(0, 0, 0, (6 * resources.displayMetrics.density).toInt())
        }
        container.addView(tvName)

        val etName = android.widget.EditText(this).apply {
            val baseName = originalFileName.substringBeforeLast(".")
            setText(baseName)
            hint = "例如: Japan Hiking Map"
        }
        container.addView(etName)

        val tvLevel = android.widget.TextView(this).apply {
            text = "所属地图层级:"
            textSize = 14f
            setPadding(0, (14 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
        }
        container.addView(tvLevel)

        val spinnerLevel = android.widget.Spinner(this)
        val levelsList = listOf("世界", "大洲", "国家", "一级行政区", "二级行政区", "三级行政区")
        val levelAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, levelsList).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerLevel.adapter = levelAdapter
        
        var defaultLevelSelection = 3
        if (navStack.isNotEmpty()) {
            val levelIndex = navStack.size
            if (levelIndex in 1..5) {
                defaultLevelSelection = levelIndex
            }
        }
        spinnerLevel.setSelection(defaultLevelSelection)
        container.addView(spinnerLevel)

        val tvParent = android.widget.TextView(this).apply {
            text = "所属父级节点名称 (请务必在对应父级树下列出):"
            textSize = 14f
            setPadding(0, (14 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
        }
        container.addView(tvParent)

        val etParent = android.widget.EditText(this).apply {
            if (navStack.isNotEmpty()) {
                setText(navStack.last())
            } else {
                setText("世界")
            }
            hint = "例如: 亚洲 或 中国 或 辽宁"
        }
        container.addView(etParent)

        val tvDir = android.widget.TextView(this).apply {
            text = "关联树目录名 (或代表的国家/省份名称):"
            textSize = 14f
            setPadding(0, (14 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
        }
        container.addView(tvDir)

        val etDir = android.widget.EditText(this).apply {
            val baseName = originalFileName.substringBeforeLast(".")
            setText(baseName)
            hint = "留空则默和显示名称一致"
        }
        container.addView(etDir)

        val scrollView = android.widget.ScrollView(this).apply {
            addView(container)
        }
        builder.setView(scrollView)

        builder.setPositiveButton("开始导入") { dialog, _ ->
            val name = etName.text.toString().trim()
            val level = spinnerLevel.selectedItem.toString()
            val parent = etParent.text.toString().trim()
            var dirName = etDir.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "地图名称不能为空", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            if (dirName.isEmpty()) {
                dirName = name
            }

            val category = when (level) {
                "世界" -> "地球级"
                "大洲" -> "大洲级"
                "国家" -> "国家级"
                "一级行政区" -> "一级行政区"
                "二级行政区" -> "二级行政区"
                "三级行政区" -> "三级行政区"
                else -> "一级行政区"
            }

            val id = originalFileName.substringBeforeLast(".").lowercase().replace(" ", "_").replace("-", "_")
            val destFileName = "$id.mbtiles"

            mapManager.saveCustomMapMeta(id, name, category, parent, dirName)
            importMapFile(sourceUri, destFileName) {
                renderCurrentPath()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("取消") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    private fun showCustomDemDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("🏔️ 添加自定义 DEM 高程资源")

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val tvName = android.widget.TextView(this).apply {
            text = "高程资源名称:"
            textSize = 14f
            setPadding(0, 0, 0, (6 * resources.displayMetrics.density).toInt())
        }
        container.addView(tvName)

        val etName = android.widget.EditText(this).apply {
            hint = "例如: Norway High-Precision DEM"
        }
        container.addView(etName)

        val tvCoverage = android.widget.TextView(this).apply {
            text = "覆盖地形范围:"
            textSize = 14f
            setPadding(0, (14 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
        }
        container.addView(tvCoverage)

        val etCoverage = android.widget.EditText(this).apply {
            hint = "例如: 挪威全境 / 30米分辨率"
        }
        container.addView(etCoverage)

        val tvFormat = android.widget.TextView(this).apply {
            text = "高程数据格式:"
            textSize = 14f
            setPadding(0, (14 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
        }
        container.addView(tvFormat)

        val spinnerFormat = android.widget.Spinner(this)
        val formatsList = listOf("GeoTIFF (.tif)", "GeoTIFF (.tiff)", "SRTM (.hgt)", "Copernicus (.bil)")
        val formatAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, formatsList).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerFormat.adapter = formatAdapter
        container.addView(spinnerFormat)

        val tvSize = android.widget.TextView(this).apply {
            text = "文件大小估计 (如: 120MB):"
            textSize = 14f
            setPadding(0, (14 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
        }
        container.addView(tvSize)

        val etSize = android.widget.EditText(this).apply {
            hint = "例如: 240 MB"
        }
        container.addView(etSize)

        val tvUrl = android.widget.TextView(this).apply {
            text = "网页/GitHub Release 下载绝对地址:"
            textSize = 14f
            setPadding(0, (14 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
        }
        container.addView(tvUrl)

        val etUrl = android.widget.EditText(this).apply {
            hint = "https://github.com/osm-no/dem/releases"
        }
        container.addView(etUrl)

        val tvFileName = android.widget.TextView(this).apply {
            text = "指定系统保存文件名 (扩展名与高程文件严格配对):"
            textSize = 14f
            setPadding(0, (14 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
        }
        container.addView(tvFileName)

        val etFileName = android.widget.EditText(this).apply {
            hint = "例如: norway_dem.tif"
        }
        container.addView(etFileName)

        val scrollView = android.widget.ScrollView(this).apply {
            addView(container)
        }
        builder.setView(scrollView)

        builder.setPositiveButton("新增配置") { dialog, _ ->
            val name = etName.text.toString().trim()
            val coverage = etCoverage.text.toString().trim()
            val formatText = spinnerFormat.selectedItem.toString()
            val format = formatText.substringBefore(" (")
            val sizeStr = etSize.text.toString().trim()
            val url = etUrl.text.toString().trim()
            var fileName = etFileName.text.toString().trim()

            if (name.isEmpty() || url.isEmpty()) {
                Toast.makeText(this, "显示名称和下载页链接必填", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            if (fileName.isEmpty()) {
                val ext = if (formatText.contains(".tif")) "tif" else if (formatText.contains(".tiff")) "tiff" else if (formatText.contains(".hgt")) "hgt" else "bil"
                fileName = "custom_dem_${System.currentTimeMillis()}.$ext"
            }

            mapManager.saveCustomDem(name, coverage, format, sizeStr, url, fileName)
            renderCurrentPath()
            dialog.dismiss()
            Toast.makeText(this, "🏔️ 自定义 DEM 数据源新增成功!", Toast.LENGTH_SHORT).show()
        }

        builder.setNegativeButton("取消") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
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
                importDemFile(uri, targetDem.fileName) {
                    renderCurrentPath()
                }
            }
        } else if (requestCode == IMPORT_MAP_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data
            val targetMap = pendingImportMap
            if (uri != null) {
                val originalName = getUriFileName(this, uri) ?: "imported_map_${System.currentTimeMillis()}.mbtiles"
                if (targetMap != null) {
                    val finalName = "${targetMap.id}.mbtiles"
                    importMapFile(uri, finalName) {
                        renderCurrentPath()
                    }
                } else {
                    showCustomImportDialog(uri, originalName)
                }
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

    private fun importDemFile(sourceUri: Uri, destFileName: String, onComplete: () -> Unit) {
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
                    onComplete()
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

    private fun importMapFile(sourceUri: Uri, destFileName: String, onComplete: () -> Unit) {
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
                    onComplete()
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
        menu.add(0, 1001, 1, "➕ 导入自定义地图").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, 1003, 2, "➕ 新增自定义 DEM").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, 1002, 3, "🔍 极速诊断系统").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1001 -> {
                launchFilePickerMap(null)
                return true
            }
            1003 -> {
                showCustomDemDialog()
                return true
            }
            1002 -> {
                startActivity(Intent(this, OfflineDiagnosticActivity::class.java))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
