package com.cybertrail.app.offline

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
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

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Toast.makeText(this@OfflineMapActivity, "离线产品下载已在后台全部处理完成", Toast.LENGTH_SHORT).show()
            val completeIntent = Intent("com.cybertrail.app.MAP_DOWNLOAD_COMPLETED")
            sendBroadcast(completeIntent)
            refreshList()
        }
    }

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

        // Bind map package adapter with download, delete, and import callback handlers
        adapter = OfflineMapAdapter(regions, { map ->
            try {
                if (map.mbtilesUrl.isNullOrEmpty()) {
                    Toast.makeText(this, "该地图包下载链接未配置或为本地自定义加载包", Toast.LENGTH_SHORT).show()
                    return@OfflineMapAdapter
                }
                map.isDownloading = true
                val downloadId = mapManager.startDownload(map)
                if (downloadId == -1L) {
                    map.isDownloading = false
                    android.util.Log.e("OfflineMapActivity", "Failed to start download for ${map.id} (returned -1)")
                    Toast.makeText(this, "启动下载失败：请确认系统 DownloadManager 已经启用，并在应用设置中授予相关存储权限", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "开始下载 ${map.name}，任务编号: $downloadId", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                map.isDownloading = false
                android.util.Log.e("OfflineMapActivity", "Failed to start download for ${map.id}", e)
                Toast.makeText(this, "意外异常: ${e.message}。已记录崩溃日志，防止闪退！", Toast.LENGTH_LONG).show()
            }
            adapter.notifyDataSetChanged()
        }, { map ->
            mapManager.deleteMap(map)
            refreshList()
        }, { map ->
            launchFilePickerMap(map)
        })
        recyclerView.adapter = adapter

        // Bind DEM layout adapter with download, delete, and import handler callbacks
        demAdapter = OfflineDemAdapter(dems, { dem ->
            try {
                if (dem.demUrl.isNullOrEmpty()) {
                    Toast.makeText(this, "该高程下载链接未配置或为本地自定义高程包", Toast.LENGTH_SHORT).show()
                    return@OfflineDemAdapter
                }
                dem.isDownloading = true
                val downloadId = mapManager.startDemDownload(dem)
                if (downloadId == -1L) {
                    dem.isDownloading = false
                    android.util.Log.e("OfflineMapActivity", "Failed to start download for DEM ${dem.id} (returned -1)")
                    Toast.makeText(this, "启动高程下载失败：请确认系统 DownloadManager 已经启用，并在应用设置中授予相关存储权限", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "开始下载高程: ${dem.name} (任务编号: $downloadId)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                dem.isDownloading = false
                android.util.Log.e("OfflineMapActivity", "Failed to start DEM download for ${dem.id}", e)
                Toast.makeText(this, "意外异常: ${e.message}。已记录崩溃日志，防止闪退！", Toast.LENGTH_LONG).show()
            }
            demAdapter.notifyDataSetChanged()
        }, { dem ->
            mapManager.deleteDem(dem)
            refreshList()
            Toast.makeText(this, "已彻底删除高程: ${dem.fileName}", Toast.LENGTH_SHORT).show()
        }, { dem ->
            launchFilePickerDem(dem)
        })
        recyclerViewDems.adapter = demAdapter
    }

    private fun launchFilePickerDem(dem: OfflineDemRegion) {
        pendingImportDem = dem
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "选择高程瓦片数据 (.hgt, .bil, .tif, .img)"), IMPORT_DEM_REQUEST_CODE)
    }

    private fun launchFilePickerMap(map: OfflineMapRegion) {
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
            if (uri != null && targetMap != null) {
                importMapFile(uri, "${targetMap.id}.mbtiles")
                refreshList()
            }
        }
    }

    private fun importDemFile(sourceUri: android.net.Uri, destFileName: String) {
        val destFile = java.io.File(mapManager.demDir, destFileName)
        try {
            contentResolver.openInputStream(sourceUri).use { inputStream ->
                if (inputStream != null) {
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    Toast.makeText(this, "高程文件成功导入! 已命名为: ${destFileName}", Toast.LENGTH_SHORT).show()
                    
                    val completeIntent = Intent("com.cybertrail.app.MAP_DOWNLOAD_COMPLETED")
                    sendBroadcast(completeIntent)
                } else {
                    Toast.makeText(this, "打开高程源文件失败", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("OfflineMapActivity", "Import DEM failed", e)
            Toast.makeText(this, "外部高程导入失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importMapFile(sourceUri: android.net.Uri, destFileName: String) {
        val destFile = java.io.File(mapManager.mapsDir, destFileName)
        try {
            contentResolver.openInputStream(sourceUri).use { inputStream ->
                if (inputStream != null) {
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    Toast.makeText(this, "离线地图包成功导入! 已命名为: ${destFileName}", Toast.LENGTH_SHORT).show()
                    
                    val completeIntent = Intent("com.cybertrail.app.MAP_DOWNLOAD_COMPLETED")
                    sendBroadcast(completeIntent)
                } else {
                    Toast.makeText(this, "打开地图源文件失败", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("OfflineMapActivity", "Import Map failed", e)
            Toast.makeText(this, "外部地图导入失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        } catch (e: Exception) {
            android.util.Log.e("OfflineMapActivity", "Register download receiver error", e)
        }
        refreshList()
        startProgressChecker()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            android.util.Log.d("OfflineMapActivity", "Download receiver already unregistered")
        }
        stopProgressChecker()
    }

    private var progressThread: Thread? = null
    private var isCheckingProgress = false

    private fun startProgressChecker() {
        isCheckingProgress = true
        progressThread = Thread {
            while (isCheckingProgress) {
                try {
                    Thread.sleep(1500)
                    runOnUiThread {
                        checkDownloadProgress()
                    }
                } catch (e: Exception) {
                }
            }
        }
        progressThread?.start()
    }

    private fun stopProgressChecker() {
        isCheckingProgress = false
        progressThread?.interrupt()
        progressThread = null
    }

    private fun checkDownloadProgress() {
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var needsUpdate = false
        
        // Match maps status
        regions.forEach { region ->
            if (region.isDownloading) {
                // Here we would query downloadManager download status dynamically
                needsUpdate = true
            }
        }
        
        // Match dems status
        dems.forEach { dem ->
            if (dem.isDownloading) {
                needsUpdate = true
            }
        }

        if (needsUpdate) {
            if (::adapter.isInitialized) {
                adapter.notifyDataSetChanged()
            }
            if (::demAdapter.isInitialized) {
                demAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun refreshList() {
        regions = mapManager.getAvailableRegions()
        dems = mapManager.getAvailableDems()
        
        if (::adapter.isInitialized && ::demAdapter.isInitialized) {
            adapter = OfflineMapAdapter(regions, { map ->
                try {
                    if (map.mbtilesUrl.isNullOrEmpty()) {
                        Toast.makeText(this, "该地图包下载链接未配置或为本地自定义加载包", Toast.LENGTH_SHORT).show()
                        return@OfflineMapAdapter
                    }
                    map.isDownloading = true
                    val downloadId = mapManager.startDownload(map)
                    if (downloadId == -1L) {
                        map.isDownloading = false
                        Toast.makeText(this, "启动下载失败", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "开始下载 ${map.name}...", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    map.isDownloading = false
                    Toast.makeText(this, "下载启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
                adapter.notifyDataSetChanged()
            }, { map ->
                mapManager.deleteMap(map)
                refreshList()
            }, { map ->
                launchFilePickerMap(map)
            })
            findViewById<RecyclerView>(R.id.recyclerView).adapter = adapter

            demAdapter = OfflineDemAdapter(dems, { dem ->
                try {
                    if (dem.demUrl.isNullOrEmpty()) {
                        Toast.makeText(this, "该高程下载链接未配置或为本地自定义高程包", Toast.LENGTH_SHORT).show()
                        return@OfflineDemAdapter
                    }
                    dem.isDownloading = true
                    val downloadId = mapManager.startDemDownload(dem)
                    if (downloadId == -1L) {
                        dem.isDownloading = false
                        Toast.makeText(this, "启动高程下载失败", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "开始下载高程: ${dem.name}...", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    dem.isDownloading = false
                    Toast.makeText(this, "下载高程启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
                demAdapter.notifyDataSetChanged()
            }, { dem ->
                mapManager.deleteDem(dem)
                refreshList()
            }, { dem ->
                launchFilePickerDem(dem)
            })
            findViewById<RecyclerView>(R.id.recyclerViewDems).adapter = demAdapter
        }
    }
}
