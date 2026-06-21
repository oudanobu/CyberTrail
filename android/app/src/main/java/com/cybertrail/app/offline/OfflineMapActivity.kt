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
    private val IMPORT_REQUEST_CODE = 404

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

        // Bind map package adapter
        adapter = OfflineMapAdapter(regions, { map ->
            map.isDownloading = true
            mapManager.startDownload(map)
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "开始下载 ${map.name}...", Toast.LENGTH_SHORT).show()
        }, { map ->
            mapManager.deleteMap(map)
            refreshList()
        })
        recyclerView.adapter = adapter

        // Bind DEM layout adapter
        demAdapter = OfflineDemAdapter(dems, { dem ->
            dem.isDownloading = true
            mapManager.startDemDownload(dem)
            demAdapter.notifyDataSetChanged()
            Toast.makeText(this, "开始下载 ${dem.name}...", Toast.LENGTH_SHORT).show()
        }, { dem ->
            mapManager.deleteDem(dem)
            refreshList()
            Toast.makeText(this, "已彻底删除: ${dem.fileName}", Toast.LENGTH_SHORT).show()
        }, { dem ->
            launchFilePicker(dem)
        })
        recyclerViewDems.adapter = demAdapter
    }

    private fun launchFilePicker(dem: OfflineDemRegion) {
        pendingImportDem = dem
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "选择高程瓦片数据 (.hgt, .bil, .tif)"), IMPORT_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data
            val targetDem = pendingImportDem
            if (uri != null && targetDem != null) {
                importFile(uri, targetDem.fileName)
                refreshList()
            }
        }
    }

    private fun importFile(sourceUri: android.net.Uri, destFileName: String) {
        val destFile = java.io.File(mapManager.demDir, destFileName)
        try {
            contentResolver.openInputStream(sourceUri).use { inputStream ->
                if (inputStream != null) {
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    Toast.makeText(this, "高程文件成功导入! 已命名为: ${destFileName}", Toast.LENGTH_SHORT).show()
                    
                    // Broadcast to trigger map refresh on background terrain scanners
                    val completeIntent = Intent("com.cybertrail.app.MAP_DOWNLOAD_COMPLETED")
                    sendBroadcast(completeIntent)
                } else {
                    Toast.makeText(this, "打开文件源失败", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("OfflineMapActivity", "Import failed", e)
            Toast.makeText(this, "外部文件导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
        refreshList()
        startProgressChecker()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(downloadReceiver)
        stopProgressChecker()
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 1000)
        }
    }

    private fun startProgressChecker() {
        handler.post(progressRunnable)
    }

    private fun stopProgressChecker() {
        handler.removeCallbacks(progressRunnable)
    }

    private fun updateProgress() {
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var needsUpdate = false
        
        regions.filter { it.isDownloading }.forEach { map ->
            val query = DownloadManager.Query()
            val cursor = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIndex)
                if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                    map.isDownloading = false
                    needsUpdate = true
                } else if (status == DownloadManager.STATUS_RUNNING) {
                    val bytesDownIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    if (bytesDownIndex >= 0 && bytesTotalIndex >= 0) {
                        val downloaded = cursor.getLong(bytesDownIndex)
                        val total = cursor.getLong(bytesTotalIndex)
                        if (total > 0) {
                            val progress = ((downloaded * 100) / total).toInt()
                            if (map.progress != progress) {
                                map.progress = progress
                                needsUpdate = true
                            }
                        }
                    }
                }
            }
            cursor?.close()
        }

        dems.filter { it.isDownloading }.forEach { dem ->
            val query = DownloadManager.Query()
            val cursor = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIndex)
                if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                    dem.isDownloading = false
                    needsUpdate = true
                } else if (status == DownloadManager.STATUS_RUNNING) {
                    val bytesDownIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    if (bytesDownIndex >= 0 && bytesTotalIndex >= 0) {
                        val downloaded = cursor.getLong(bytesDownIndex)
                        val total = cursor.getLong(bytesTotalIndex)
                        if (total > 0) {
                            val progress = ((downloaded * 100) / total).toInt()
                            if (dem.progress != progress) {
                                dem.progress = progress
                                needsUpdate = true
                            }
                        }
                    }
                }
            }
            cursor?.close()
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
                map.isDownloading = true
                mapManager.startDownload(map)
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "开始下载 ${map.name}...", Toast.LENGTH_SHORT).show()
            }, { map ->
                mapManager.deleteMap(map)
                refreshList()
            })
            findViewById<RecyclerView>(R.id.recyclerView).adapter = adapter

            demAdapter = OfflineDemAdapter(dems, { dem ->
                dem.isDownloading = true
                mapManager.startDemDownload(dem)
                demAdapter.notifyDataSetChanged()
                Toast.makeText(this, "开始下载 ${dem.name}...", Toast.LENGTH_SHORT).show()
            }, { dem ->
                mapManager.deleteDem(dem)
                refreshList()
            }, { dem ->
                launchFilePicker(dem)
            })
            findViewById<RecyclerView>(R.id.recyclerViewDems).adapter = demAdapter
        }
    }
}
