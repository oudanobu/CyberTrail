package com.cybertrail.app

import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybertrail.app.db.AppDatabase
import com.cybertrail.app.db.WaypointDao
import com.cybertrail.app.db.WaypointEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class WaypointManagerActivity : AppCompatActivity() {

    private lateinit var rvWaypointList: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var tvWaypointCountSummary: TextView
    private lateinit var btnBack: TextView
    private lateinit var btnExportAllGpx: TextView

    private lateinit var etSearchWaypoint: EditText
    
    // Batch select bar
    private lateinit var panelBatchActions: View
    private lateinit var tvSelectedCount: TextView
    private lateinit var btnBatchDelete: TextView
    private lateinit var btnBatchCancel: TextView

    private lateinit var waypointDao: WaypointDao
    private val dbExecutor = Executors.newSingleThreadExecutor()

    private val allWaypoints = mutableListOf<WaypointEntity>()
    private val displayedWaypoints = mutableListOf<WaypointEntity>()
    private lateinit var listAdapter: WaypointListAdapter

    private var searchQuery = ""
    private var isMultiSelectMode = false
    private val selectedWaypointIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_waypoint_manager)

        waypointDao = AppDatabase.getDatabase(this).waypointDao()

        rvWaypointList = findViewById(R.id.rv_waypoint_list)
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        tvWaypointCountSummary = findViewById(R.id.tv_waypoint_count_summary)
        btnBack = findViewById(R.id.btn_back)
        btnExportAllGpx = findViewById(R.id.btn_export_all_gpx)

        etSearchWaypoint = findViewById(R.id.et_search_waypoint)
        
        panelBatchActions = findViewById(R.id.panel_batch_actions)
        tvSelectedCount = findViewById(R.id.tv_selected_count)
        btnBatchDelete = findViewById(R.id.btn_batch_delete)
        btnBatchCancel = findViewById(R.id.btn_batch_cancel)

        btnBack.setOnClickListener { finish() }

        btnExportAllGpx.setOnClickListener {
            exportAllWaypointsToGPX(displayedWaypoints)
        }

        btnBatchCancel.setOnClickListener {
            exitMultiSelectMode()
        }

        btnBatchDelete.setOnClickListener {
            if (selectedWaypointIds.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("确认批量删除")
                .setMessage("确定要删除选中的 ${selectedWaypointIds.size} 个航点吗？")
                .setPositiveButton("删除") { _, _ ->
                    val idsToDelete = selectedWaypointIds.toList()
                    dbExecutor.execute {
                        for (id in idsToDelete) {
                            waypointDao.deleteWaypointById(id)
                        }
                        runOnUiThread {
                            Toast.makeText(this@WaypointManagerActivity, "批量删除成功！", Toast.LENGTH_SHORT).show()
                            exitMultiSelectMode()
                            loadWaypoints()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        etSearchWaypoint.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilterAndSort()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        rvWaypointList.layoutManager = LinearLayoutManager(this)
        listAdapter = WaypointListAdapter()
        rvWaypointList.adapter = listAdapter

        loadWaypoints()
    }

    override fun onResume() {
        super.onResume()
        loadWaypoints()
    }

    private fun loadWaypoints() {
        dbExecutor.execute {
            val list = waypointDao.getAllWaypoints()
            runOnUiThread {
                allWaypoints.clear()
                allWaypoints.addAll(list)
                applyFilterAndSort()
            }
        }
    }

    private fun applyFilterAndSort() {
        displayedWaypoints.clear()
        
        // Filter
        val filtered = if (searchQuery.isEmpty()) {
            allWaypoints
        } else {
            allWaypoints.filter {
                it.name.contains(searchQuery, ignoreCase = true) || 
                (it.description != null && it.description.contains(searchQuery, ignoreCase = true))
            }
        }

        // Sort: Favorite prioritized first, then createTime descending
        val sorted = filtered.sortedWith(compareByDescending<WaypointEntity> { it.favorite }.thenByDescending { it.createTime })
        
        displayedWaypoints.addAll(sorted)

        // Summary Count
        tvWaypointCountSummary.text = "共 ${displayedWaypoints.size} 个"

        // Empty state visibility
        if (displayedWaypoints.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            rvWaypointList.visibility = View.GONE
        } else {
            layoutEmptyState.visibility = View.GONE
            rvWaypointList.visibility = View.VISIBLE
        }

        listAdapter.notifyDataSetChanged()
    }

    private fun enterMultiSelectMode(initialWaypoint: WaypointEntity) {
        isMultiSelectMode = true
        selectedWaypointIds.clear()
        selectedWaypointIds.add(initialWaypoint.id)
        panelBatchActions.visibility = View.VISIBLE
        updateSelectedCountText()
        listAdapter.notifyDataSetChanged()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedWaypointIds.clear()
        panelBatchActions.visibility = View.GONE
        listAdapter.notifyDataSetChanged()
    }

    private fun updateSelectedCountText() {
        tvSelectedCount.text = "已选择 ${selectedWaypointIds.size} 项"
    }

    private fun getLastKnownLocation(): Location? {
        val lm = getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        var bestLocation: Location? = null
        try {
            val providers = lm.getProviders(true)
            for (provider in providers) {
                val l = lm.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                    bestLocation = l
                }
            }
        } catch (e: SecurityException) {
            // No permission or disabled
        }
        return bestLocation
    }

    private fun getEmojiForIconType(iconType: String): String {
        return when (iconType) {
            "NORMAL" -> "📍"
            "CAMP" -> "⛺"
            "WATER" -> "🚰"
            "SUMMIT" -> "🏔"
            "DANGER" -> "⚠"
            "PHOTO" -> "📷"
            "PARKING" -> "🚗"
            else -> "📍"
        }
    }

    private fun showEditWaypointDialog(waypoint: WaypointEntity) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_waypoint, null)
        val tvTitle: TextView = dialogView.findViewById(R.id.tv_waypoint_coords)
        val etName: EditText = dialogView.findViewById(R.id.et_waypoint_name)
        val etDesc: EditText = dialogView.findViewById(R.id.et_waypoint_desc)
        val spIcon: android.widget.Spinner = dialogView.findViewById(R.id.sp_waypoint_icon)

        // Setup title
        (dialogView.getChildAt(0) as? TextView)?.text = "✏ 编辑航点"
        tvTitle.text = String.format(
            Locale.US,
            "纬度: %.6f | 经度: %.6f\n估算海拔: %.1f m",
            waypoint.latitude,
            waypoint.longitude,
            waypoint.elevation ?: 0.0
        )

        etName.setText(waypoint.name)
        etDesc.setText(waypoint.description ?: "")

        val spinnerItems = listOf(
            "📍 默认 (NORMAL)",
            "⛺ 营地 (CAMP)",
            "🚰 水源 (WATER)",
            "🏔 山峰 (SUMMIT)",
            "⚠ 危险 (DANGER)",
            "📷 照片 (PHOTO)",
            "🚗 车位 (PARKING)"
        )
        val iconTypes = listOf("NORMAL", "CAMP", "WATER", "SUMMIT", "DANGER", "PHOTO", "PARKING")

        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            spinnerItems
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spIcon.adapter = adapter

        val idx = iconTypes.indexOf(waypoint.iconType).coerceAtLeast(0)
        spIcon.setSelection(idx)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .setPositiveButton("保存") { dialog, _ ->
                val name = etName.text.toString().trim().ifEmpty { "未命名航点" }
                val desc = etDesc.text.toString().trim().ifEmpty { null }
                val iconType = iconTypes[spIcon.selectedItemPosition]

                val updatedWaypoint = waypoint.copy(
                    name = name,
                    description = desc,
                    iconType = iconType
                )

                dbExecutor.execute {
                    waypointDao.updateWaypoint(updatedWaypoint)
                    runOnUiThread {
                        Toast.makeText(this@WaypointManagerActivity, "航点已更新！", Toast.LENGTH_SHORT).show()
                        loadWaypoints()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun exportAllWaypointsToGPX(waypoints: List<WaypointEntity>) {
        if (waypoints.isEmpty()) {
            Toast.makeText(this, "无航点可导出", Toast.LENGTH_SHORT).show()
            return
        }
        val baseDir = File(android.os.Environment.getExternalStorageDirectory(), "CyberTrail")
        val waypointsDir = File(baseDir, "Waypoints")
        if (!waypointsDir.exists()) {
            waypointsDir.mkdirs()
        }
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "waypoints_export_${sdf.format(Date())}.gpx"
        val file = File(waypointsDir, fileName)

        try {
            file.printWriter().use { out ->
                out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                out.println("<gpx version=\"1.1\" creator=\"CyberTrail\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
                for (wp in waypoints) {
                    out.println("  <wpt lat=\"${wp.latitude}\" lon=\"${wp.longitude}\">")
                    wp.elevation?.let { out.println("    <ele>${it}</ele>") }
                    val dateStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date(wp.createTime))
                    out.println("    <time>${dateStr}</time>")
                    out.println("    <name>${wp.name}</name>")
                    wp.description?.let { out.println("    <desc>${it}</desc>") }
                    out.println("    <sym>${wp.iconType}</sym>")
                    out.println("  </wpt>")
                }
                out.println("</gpx>")
            }
            Toast.makeText(this, "导出成功！保存在:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Inner Adapter Class
    inner class WaypointListAdapter : RecyclerView.Adapter<WaypointListAdapter.WaypointViewHolder>() {

        inner class WaypointViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cbSelect: CheckBox = view.findViewById(R.id.cb_select_waypoint)
            val tvEmoji: TextView = view.findViewById(R.id.tv_waypoint_emoji)
            val tvName: TextView = view.findViewById(R.id.tv_waypoint_name)
            val btnFavStar: TextView = view.findViewById(R.id.btn_waypoint_fav_star)
            val tvDesc: TextView = view.findViewById(R.id.tv_waypoint_desc)
            val tvCoords: TextView = view.findViewById(R.id.tv_waypoint_coords)
            val tvElevation: TextView = view.findViewById(R.id.tv_waypoint_elevation)
            val tvDistance: TextView = view.findViewById(R.id.tv_waypoint_distance)
            
            val btnLocate: TextView = view.findViewById(R.id.btn_action_locate)
            val btnEdit: TextView = view.findViewById(R.id.btn_action_edit)
            val btnDelete: TextView = view.findViewById(R.id.btn_action_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WaypointViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_waypoint_card, parent, false)
            return WaypointViewHolder(view)
        }

        override fun onBindViewHolder(holder: WaypointViewHolder, position: Int) {
            val wp = displayedWaypoints[position]

            // Selection state
            if (isMultiSelectMode) {
                holder.cbSelect.visibility = View.VISIBLE
                holder.cbSelect.isChecked = selectedWaypointIds.contains(wp.id)
                holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedWaypointIds.add(wp.id)
                    } else {
                        selectedWaypointIds.remove(wp.id)
                    }
                    updateSelectedCountText()
                }
            } else {
                holder.cbSelect.visibility = View.GONE
            }

            holder.tvEmoji.text = getEmojiForIconType(wp.iconType)
            holder.tvName.text = wp.name
            holder.tvDesc.text = wp.description ?: "无备注"
            holder.tvCoords.text = String.format(Locale.US, "%.5f, %.5f", wp.latitude, wp.longitude)
            holder.tvElevation.text = wp.elevation?.let { String.format(Locale.US, "%.1f m", it) } ?: "-- m"

            // Calculate distance
            val lastLoc = getLastKnownLocation()
            if (lastLoc != null) {
                val results = FloatArray(1)
                Location.distanceBetween(lastLoc.latitude, lastLoc.longitude, wp.latitude, wp.longitude, results)
                val distMeters = results[0]
                holder.tvDistance.text = if (distMeters < 1000f) {
                    String.format(Locale.US, "%.0fm", distMeters)
                } else {
                    String.format(Locale.US, "%.2fkm", distMeters / 1000f)
                }
            } else {
                holder.tvDistance.text = "--"
            }

            // Favorite star
            holder.btnFavStar.text = if (wp.favorite) "★" else "☆"
            holder.btnFavStar.setOnClickListener {
                wp.favorite = !wp.favorite
                holder.btnFavStar.text = if (wp.favorite) "★" else "☆"
                dbExecutor.execute {
                    waypointDao.updateWaypoint(wp)
                    runOnUiThread {
                        loadWaypoints()
                    }
                }
            }

            // Long click triggers MultiSelect mode
            holder.itemView.setOnLongClickListener {
                if (!isMultiSelectMode) {
                    enterMultiSelectMode(wp)
                }
                true
            }

            holder.itemView.setOnClickListener {
                if (isMultiSelectMode) {
                    holder.cbSelect.toggle()
                }
            }

            // Action Buttons
            holder.btnLocate.setOnClickListener {
                // Back to map with extra coordinate so MapActivity centers there!
                val intent = Intent(this@WaypointManagerActivity, MapActivity::class.java).apply {
                    putExtra("center_latitude", wp.latitude)
                    putExtra("center_longitude", wp.longitude)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            }

            holder.btnEdit.setOnClickListener {
                showEditWaypointDialog(wp)
            }

            holder.btnDelete.setOnClickListener {
                AlertDialog.Builder(this@WaypointManagerActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("确认删除")
                    .setMessage("确定要删除航点 \"${wp.name}\" 吗？")
                    .setPositiveButton("删除") { _, _ ->
                        dbExecutor.execute {
                            waypointDao.deleteWaypointById(wp.id)
                            runOnUiThread {
                                Toast.makeText(this@WaypointManagerActivity, "已删除", Toast.LENGTH_SHORT).show()
                                loadWaypoints()
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }

        override fun getItemCount(): Int = displayedWaypoints.size
    }
}
