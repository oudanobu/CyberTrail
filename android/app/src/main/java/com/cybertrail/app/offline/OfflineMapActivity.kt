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
    private var regions: List<OfflineMapRegion> = emptyList()

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            var verified = false
            var fileUriStr: String? = null
            
            if (id != null && id != -1L) {
                val dm = context?.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                if (dm != null) {
                    val query = DownloadManager.Query().setFilterById(id)
                    val cursor = dm.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        if (statusIdx >= 0 && cursor.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL) {
                            if (uriIdx >= 0) fileUriStr = cursor.getString(uriIdx)
                        }
                        cursor.close()
                    }
                }
            }

            if (fileUriStr != null) {
                try {
                    val file = java.io.File(android.net.Uri.parse(fileUriStr).path ?: "")
                    if (file.exists() && file.length() > 0) {
                        val db = android.database.sqlite.SQLiteDatabase.openDatabase(file.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
                        
                        val metaCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='metadata'", null)
                        val hasMeta = metaCursor.moveToFirst()
                        metaCursor.close()
                        
                        val tilesCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='tiles'", null)
                        val hasTiles = tilesCursor.moveToFirst()
                        tilesCursor.close()
                        
                        db.close()
                        
                        if (hasMeta && hasTiles) {
                            verified = true
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("OfflineMapActivity", "Verification failed", e)
                }
            }
            
            if (verified) {
                Toast.makeText(this@OfflineMapActivity, "地图下载完成且验证成功", Toast.LENGTH_SHORT).show()
                val completeIntent = Intent("com.cybertrail.app.MAP_DOWNLOAD_COMPLETED")
                sendBroadcast(completeIntent)
            } else {
                Toast.makeText(this@OfflineMapActivity, "地图下载验证失败", Toast.LENGTH_SHORT).show()
                // optional: delete bad file
            }
            
            refreshList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_map)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        toolbar.title = "离线地图管理"
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        mapManager = OfflineMapManager(this)
        
        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        refreshList()

        adapter = OfflineMapAdapter(regions, { map ->
            // Download Call
            map.isDownloading = true
            mapManager.startDownload(map)
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "开始下载 ${map.name}...", Toast.LENGTH_SHORT).show()
        }, { map ->
            // Delete Call
            mapManager.deleteMap(map)
            refreshList()
        })
        recyclerView.adapter = adapter
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
        if (needsUpdate && ::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    private fun refreshList() {
        regions = mapManager.getAvailableRegions()
        if (::adapter.isInitialized) {
            // we have to update the adapter's list
            // actually created a new adapter because our lists are static in this simple example
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
        }
    }
}
