package com.cybertrail.app

import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybertrail.app.db.AppDatabase
import com.cybertrail.app.db.PhotoAnchor
import com.cybertrail.app.db.Track
import com.cybertrail.app.db.TrackDao
import com.cybertrail.app.db.TrackPoint
import com.cybertrail.app.gis.TrackFileHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class TrackManagerActivity : AppCompatActivity() {

    private lateinit var rvTrackList: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var tvTrackCountSummary: TextView
    private lateinit var btnBack: TextView

    private lateinit var trackDao: TrackDao
    private val dbExecutor = Executors.newSingleThreadExecutor()

    private val trackItemsList = mutableListOf<TrackItemData>()
    private lateinit var listAdapter: TrackListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_manager)

        trackDao = AppDatabase.getDatabase(this).trackDao()

        rvTrackList = findViewById(R.id.rv_track_list)
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        tvTrackCountSummary = findViewById(R.id.tv_track_count_summary)
        btnBack = findViewById(R.id.btn_back)

        btnBack.setOnClickListener { finish() }

        rvTrackList.layoutManager = LinearLayoutManager(this)
        listAdapter = TrackListAdapter()
        rvTrackList.adapter = listAdapter

        loadAllTracks()
    }

    private fun loadAllTracks() {
        dbExecutor.execute {
            val allTracks = trackDao.getAllTracks()
            val tempList = mutableListOf<TrackItemData>()

            for (track in allTracks) {
                val points = trackDao.getTrackPoints(track.id)
                val photos = trackDao.getPhotoAnchorsForTrack(track.id)

                val distMeters = calculateTrackDistance(points)
                val durationSec = calculateDurationSeconds(track, points)

                tempList.add(
                    TrackItemData(
                        track = track,
                        points = points,
                        photos = photos,
                        distanceMeters = distMeters,
                        durationSeconds = durationSec
                    )
                )
            }

            runOnUiThread {
                trackItemsList.clear()
                trackItemsList.addAll(tempList)
                listAdapter.notifyDataSetChanged()

                tvTrackCountSummary.text = "共 ${trackItemsList.size} 条"
                if (trackItemsList.isEmpty()) {
                    layoutEmptyState.visibility = View.VISIBLE
                    rvTrackList.visibility = View.GONE
                } else {
                    layoutEmptyState.visibility = View.GONE
                    rvTrackList.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun calculateTrackDistance(points: List<TrackPoint>): Float {
        var dist = 0f
        for (i in 1 until points.size) {
            val results = FloatArray(1)
            Location.distanceBetween(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude,
                results
            )
            dist += results[0]
        }
        return dist
    }

    private fun calculateDurationSeconds(track: Track, points: List<TrackPoint>): Long {
        if (track.endTime != null) {
            val dur = (track.endTime!! - track.startTime) / 1000L
            return if (dur > 0L) dur else 0L
        }
        if (points.isNotEmpty()) {
            val dur = (points.last().timestamp - points.first().timestamp) / 1000L
            return if (dur > 0L) dur else 0L
        }
        return 0L
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    private fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    // Inner Data Class for representing rich details in List
    private data class TrackItemData(
        val track: Track,
        val points: List<TrackPoint>,
        val photos: List<PhotoAnchor>,
        val distanceMeters: Float,
        val durationSeconds: Long
    )

    // RecyclerView Adapter
    private inner class TrackListAdapter : RecyclerView.Adapter<TrackListAdapter.TrackViewHolder>() {

        inner class TrackViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_track_card_name)
            val tvStatus: TextView = view.findViewById(R.id.tv_track_card_status)
            val tvDistance: TextView = view.findViewById(R.id.tv_track_card_distance)
            val tvDuration: TextView = view.findViewById(R.id.tv_track_card_duration)
            val tvPoints: TextView = view.findViewById(R.id.tv_track_card_points)
            val tvStartTime: TextView = view.findViewById(R.id.tv_track_card_start_time)
            val tvEndTime: TextView = view.findViewById(R.id.tv_track_card_end_time)
            val tvPhotosCount: TextView = view.findViewById(R.id.tv_track_card_photos)

            // Buttons
            val btnOpen: TextView = view.findViewById(R.id.btn_action_open)
            val btnRename: TextView = view.findViewById(R.id.btn_action_rename)
            val btnCopy: TextView = view.findViewById(R.id.btn_action_copy)
            val btnExport: TextView = view.findViewById(R.id.btn_action_export)
            val btnShare: TextView = view.findViewById(R.id.btn_action_share)
            val btnDelete: TextView = view.findViewById(R.id.btn_action_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_track_card, parent, false)
            return TrackViewHolder(view)
        }

        override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
            val item = trackItemsList[position]
            val track = item.track

            holder.tvName.text = track.name ?: "未命名轨迹"
            
            // Status UI indicator
            when (track.status) {
                "RECORDING" -> {
                    holder.tvStatus.text = "正在记录"
                    holder.tvStatus.setTextColor(0xFF10B981.toInt()) // Cyber green
                    holder.tvStatus.setBackgroundColor(0x2210B981)
                }
                "PAUSED" -> {
                    holder.tvStatus.text = "已暂停"
                    holder.tvStatus.setTextColor(0xFFF59E0B.toInt()) // Amber
                    holder.tvStatus.setBackgroundColor(0x22F59E0B)
                }
                else -> {
                    holder.tvStatus.text = "已完成"
                    holder.tvStatus.setTextColor(0xFF94A3B8.toInt()) // Slate
                    holder.tvStatus.setBackgroundColor(0x1A94A3B8)
                }
            }

            holder.tvDistance.text = String.format(Locale.US, "%.2f km", item.distanceMeters / 1000f)
            holder.tvDuration.text = formatDuration(item.durationSeconds)
            holder.tvPoints.text = "${item.points.size} 点"
            holder.tvStartTime.text = formatDateTime(track.startTime)
            holder.tvEndTime.text = track.endTime?.let { formatDateTime(it) } ?: "--:--"
            holder.tvPhotosCount.text = "${item.photos.size} 张"

            // Action: Open
            holder.btnOpen.setOnClickListener {
                val intent = Intent(this@TrackManagerActivity, TrackDetailActivity::class.java).apply {
                    putExtra("TRACK_ID", track.id)
                }
                startActivity(intent)
            }

            // Action: Rename
            holder.btnRename.setOnClickListener {
                val input = EditText(this@TrackManagerActivity).apply {
                    setText(track.name ?: "未命名轨迹")
                    setSelection(text.length)
                }
                AlertDialog.Builder(this@TrackManagerActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("重命名轨迹")
                    .setView(input)
                    .setPositiveButton("保存") { _, _ ->
                        val newName = input.text.toString().trim()
                        if (newName.isNotEmpty()) {
                            dbExecutor.execute {
                                track.name = newName
                                trackDao.updateTrack(track)
                                // Resave json on disk
                                TrackFileHelper.saveTrackToJson(track, item.points, item.photos)
                                runOnUiThread {
                                    Toast.makeText(this@TrackManagerActivity, "重命名成功", Toast.LENGTH_SHORT).show()
                                    loadAllTracks()
                                }
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }

            // Action: Delete
            holder.btnDelete.setOnClickListener {
                AlertDialog.Builder(this@TrackManagerActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("确认删除")
                    .setMessage("确定要删除轨迹 [${track.name ?: "未命名"}] 吗？该操作不可逆，且将删除本地对应的数据库与文件缓存。")
                    .setPositiveButton("删除") { _, _ ->
                        dbExecutor.execute {
                            trackDao.deleteTrackById(track.id)
                            TrackFileHelper.deleteTrackJsonFile(track.id)
                            runOnUiThread {
                                Toast.makeText(this@TrackManagerActivity, "轨迹已删除", Toast.LENGTH_SHORT).show()
                                loadAllTracks()
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }

            // Action: Copy
            holder.btnCopy.setOnClickListener {
                Toast.makeText(this@TrackManagerActivity, "正在复制轨迹...", Toast.LENGTH_SHORT).show()
                dbExecutor.execute {
                    val originalTrack = track
                    val originalPoints = item.points
                    val originalPhotos = item.photos

                    val now = System.currentTimeMillis()
                    val copiedTrack = Track(
                        startTime = now,
                        endTime = now + (item.durationSeconds * 1000L),
                        status = "STOPPED",
                        name = "${originalTrack.name ?: "未命名轨迹"} (副本)"
                    )
                    
                    val newTrackId = trackDao.insertTrack(copiedTrack)
                    val finalizedTrack = copiedTrack.copy(id = newTrackId)

                    // Clone and insert Points
                    val copiedPoints = originalPoints.map {
                        TrackPoint(
                            trackId = newTrackId,
                            latitude = it.latitude,
                            longitude = it.longitude,
                            elevation = it.elevation,
                            timestamp = it.timestamp,
                            speed = it.speed,
                            accuracy = it.accuracy
                        )
                    }
                    trackDao.insertTrackPoints(copiedPoints)

                    // Clone and insert photo anchors
                    for (photo in originalPhotos) {
                        val copiedPhoto = PhotoAnchor(
                            id = UUID.randomUUID().toString(),
                            latitude = photo.latitude,
                            longitude = photo.longitude,
                            elevation = photo.elevation,
                            timestamp = photo.timestamp,
                            imagePath = photo.imagePath,
                            thumbnailPath = photo.thumbnailPath,
                            trackId = newTrackId,
                            note = photo.note
                        )
                        trackDao.insertPhotoAnchor(copiedPhoto)
                    }

                    // Save duplicated JSON file to storage
                    TrackFileHelper.saveTrackToJson(finalizedTrack, copiedPoints, originalPhotos)

                    runOnUiThread {
                        Toast.makeText(this@TrackManagerActivity, "轨迹已成功复制", Toast.LENGTH_SHORT).show()
                        loadAllTracks()
                    }
                }
            }

            // Action: Export
            holder.btnExport.setOnClickListener {
                val formats = arrayOf("GPX", "KML", "GeoJSON", "CyberTrail JSON")
                AlertDialog.Builder(this@TrackManagerActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("选择导出格式")
                    .setItems(formats) { _, which ->
                        val format = when (which) {
                            0 -> "GPX"
                            1 -> "KML"
                            2 -> "GEOJSON"
                            else -> "JSON"
                        }
                        dbExecutor.execute {
                            val exportedFile = TrackFileHelper.exportTrack(
                                track,
                                item.points,
                                item.photos,
                                format
                            )
                            runOnUiThread {
                                if (exportedFile != null) {
                                    Toast.makeText(
                                        this@TrackManagerActivity,
                                        "已导出到: ${exportedFile.name}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(this@TrackManagerActivity, "导出失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .show()
            }

            // Action: Share
            holder.btnShare.setOnClickListener {
                Toast.makeText(this@TrackManagerActivity, "正在准备分享...", Toast.LENGTH_SHORT).show()
                dbExecutor.execute {
                    val exportedGpx = TrackFileHelper.exportTrack(
                        track,
                        item.points,
                        item.photos,
                        "GPX"
                    )
                    runOnUiThread {
                        if (exportedGpx != null && exportedGpx.exists()) {
                            try {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    this@TrackManagerActivity,
                                    "com.cybertrail.app.fileprovider",
                                    exportedGpx
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/gpx+xml"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(shareIntent, "分享轨迹 [${track.name}]"))
                            } catch (e: Exception) {
                                Toast.makeText(this@TrackManagerActivity, "分享错误: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@TrackManagerActivity, "无法生成GPX文件进行分享", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        override fun getItemCount(): Int = trackItemsList.size
    }
}
