package com.cybertrail.app

import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cybertrail.app.db.AppDatabase
import com.cybertrail.app.db.RouteDao
import com.cybertrail.app.db.RouteEntity
import com.cybertrail.app.db.WaypointDao
import com.cybertrail.app.db.WaypointEntity
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class RouteCreateActivity : AppCompatActivity() {

    private lateinit var etRouteName: EditText
    private lateinit var etRouteDesc: EditText
    private lateinit var tvStatsDistance: TextView
    private lateinit var tvStatsTime: TextView
    private lateinit var tvStatsCount: TextView
    private lateinit var layoutSelectedSequence: LinearLayout
    private lateinit var etSearchWaypoints: EditText
    private lateinit var rvAvailableWaypoints: RecyclerView
    private lateinit var layoutEmptyWaypoints: View
    private lateinit var tvTitle: TextView

    private lateinit var waypointDao: WaypointDao
    private lateinit var routeDao: RouteDao
    private val dbExecutor = Executors.newSingleThreadExecutor()

    private val allWaypoints = mutableListOf<WaypointEntity>()
    private val displayedWaypoints = mutableListOf<WaypointEntity>()
    private val selectedSequence = mutableListOf<WaypointEntity>()

    private var editingRouteId: String? = null
    private var searchQuery = ""
    private lateinit var availableAdapter: AvailableWaypointsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_create)

        val db = AppDatabase.getDatabase(this)
        waypointDao = db.waypointDao()
        routeDao = db.routeDao()

        etRouteName = findViewById(R.id.et_route_name)
        etRouteDesc = findViewById(R.id.et_route_desc)
        tvStatsDistance = findViewById(R.id.tv_stats_distance)
        tvStatsTime = findViewById(R.id.tv_stats_time)
        tvStatsCount = findViewById(R.id.tv_stats_count)
        layoutSelectedSequence = findViewById(R.id.layout_selected_sequence)
        etSearchWaypoints = findViewById(R.id.et_search_waypoints)
        rvAvailableWaypoints = findViewById(R.id.rv_available_waypoints)
        layoutEmptyWaypoints = findViewById(R.id.layout_empty_waypoints)
        tvTitle = findViewById(R.id.tv_title)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_save).setOnClickListener { saveRoute() }

        etSearchWaypoints.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                filterAvailableWaypoints()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        rvAvailableWaypoints.layoutManager = LinearLayoutManager(this)
        availableAdapter = AvailableWaypointsAdapter()
        rvAvailableWaypoints.adapter = availableAdapter

        editingRouteId = intent.getStringExtra("route_id")
        loadData()
    }

    private fun loadData() {
        dbExecutor.execute {
            val waypoints = waypointDao.getAllWaypoints()
            val existingRoute = editingRouteId?.let { routeDao.getRouteById(it) }

            runOnUiThread {
                allWaypoints.clear()
                allWaypoints.addAll(waypoints)

                if (existingRoute != null) {
                    tvTitle.text = "✏ 编辑路线"
                    etRouteName.setText(existingRoute.name)
                    etRouteDesc.setText(existingRoute.description ?: "")

                    val wpIds = existingRoute.getWaypointIdList()
                    selectedSequence.clear()
                    for (id in wpIds) {
                        val found = allWaypoints.find { it.id == id }
                        if (found != null) {
                            selectedSequence.add(found)
                        }
                    }
                } else {
                    tvTitle.text = "➕ 新建路线"
                }

                updateSelectedSequenceUI()
                filterAvailableWaypoints()
            }
        }
    }

    private fun filterAvailableWaypoints() {
        displayedWaypoints.clear()
        val filtered = if (searchQuery.isEmpty()) {
            allWaypoints
        } else {
            allWaypoints.filter {
                it.name.contains(searchQuery, ignoreCase = true) || 
                (it.description != null && it.description.contains(searchQuery, ignoreCase = true))
            }
        }
        displayedWaypoints.addAll(filtered)

        if (displayedWaypoints.isEmpty()) {
            layoutEmptyWaypoints.visibility = View.VISIBLE
            rvAvailableWaypoints.visibility = View.GONE
        } else {
            layoutEmptyWaypoints.visibility = View.GONE
            rvAvailableWaypoints.visibility = View.VISIBLE
        }
        availableAdapter.notifyDataSetChanged()
    }

    private fun updateSelectedSequenceUI() {
        layoutSelectedSequence.removeAllViews()

        var totalDistance = 0.0
        for (i in 0 until selectedSequence.size) {
            val wp = selectedSequence[i]
            val chipView = LayoutInflater.from(this).inflate(R.layout.item_selected_waypoint_chip, layoutSelectedSequence, false)
            
            val tvEmoji: TextView = chipView.findViewById(R.id.tv_chip_emoji)
            val tvName: TextView = chipView.findViewById(R.id.tv_chip_name)
            val btnRemove: View = chipView.findViewById(R.id.btn_remove_chip)
            val tvArrow: TextView = chipView.findViewById(R.id.tv_chip_arrow)

            tvEmoji.text = getEmojiForIconType(wp.iconType)
            tvName.text = wp.name
            btnRemove.setOnClickListener {
                selectedSequence.removeAt(i)
                updateSelectedSequenceUI()
            }

            if (i == selectedSequence.size - 1) {
                tvArrow.visibility = View.GONE
            } else {
                tvArrow.visibility = View.VISIBLE
                
                // Calculate distance to next
                val nextWp = selectedSequence[i + 1]
                val results = FloatArray(1)
                Location.distanceBetween(wp.latitude, wp.longitude, nextWp.latitude, nextWp.longitude, results)
                totalDistance += results[0]
            }

            layoutSelectedSequence.addView(chipView)
        }

        // Update Stats
        val distKm = totalDistance / 1000.0
        tvStatsDistance.text = String.format(Locale.US, "总长: %.2f km", distKm)
        
        // Estimated hiking speed: 4 km/h (4000 meters / hour = 66.67 meters / minute)
        val estimatedTimeMinutes = if (totalDistance > 0) {
            (totalDistance / 4000.0 * 60.0).toInt()
        } else {
            0
        }

        tvStatsTime.text = if (estimatedTimeMinutes >= 60) {
            val h = estimatedTimeMinutes / 60
            val m = estimatedTimeMinutes % 60
            "预计时间: ${h}小时${m}分钟"
        } else {
            "预计时间: ${estimatedTimeMinutes}分钟"
        }

        tvStatsCount.text = "已选: ${selectedSequence.size} 个点"
    }

    private fun saveRoute() {
        val name = etRouteName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入路线名称！", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedSequence.size < 2) {
            Toast.makeText(this, "路线至少需要包含2个航点！", Toast.LENGTH_SHORT).show()
            return
        }

        val desc = etRouteDesc.text.toString().trim().ifEmpty { null }
        
        // Calculate final specs
        var totalDistance = 0.0
        for (i in 0 until selectedSequence.size - 1) {
            val wp1 = selectedSequence[i]
            val wp2 = selectedSequence[i + 1]
            val results = FloatArray(1)
            Location.distanceBetween(wp1.latitude, wp1.longitude, wp2.latitude, wp2.longitude, results)
            totalDistance += results[0]
        }
        val estimatedTimeMinutes = (totalDistance / 4000.0 * 60.0).toInt()

        val wpIdsString = selectedSequence.joinToString(",") { it.id }

        dbExecutor.execute {
            val id = editingRouteId ?: UUID.randomUUID().toString()
            val favorite = editingRouteId?.let { routeDao.getRouteById(it)?.favorite } ?: false
            val createTime = editingRouteId?.let { routeDao.getRouteById(it)?.createTime } ?: System.currentTimeMillis()

            val route = RouteEntity(
                id = id,
                name = name,
                description = desc,
                createTime = createTime,
                favorite = favorite,
                distanceMeters = totalDistance,
                estimatedTimeMinutes = estimatedTimeMinutes,
                waypointIds = wpIdsString
            )

            routeDao.insertRoute(route)

            runOnUiThread {
                Toast.makeText(this@RouteCreateActivity, "路线保存成功！", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
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

    inner class AvailableWaypointsAdapter : RecyclerView.Adapter<AvailableWaypointsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvEmoji: TextView = view.findViewById(R.id.tv_waypoint_emoji)
            val tvName: TextView = view.findViewById(R.id.tv_waypoint_name)
            val tvInfo: TextView = view.findViewById(R.id.tv_waypoint_info)
            val btnAdd: View = view.findViewById(R.id.btn_add_to_sequence)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_waypoint_select_row, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val wp = displayedWaypoints[position]
            holder.tvEmoji.text = getEmojiForIconType(wp.iconType)
            holder.tvName.text = wp.name
            
            val elevStr = wp.elevation?.let { String.format(Locale.US, "%.1fm", it) } ?: "--m"
            holder.tvInfo.text = String.format(Locale.US, "海拔: %s | 坐标: %.4f, %.4f", elevStr, wp.latitude, wp.longitude)

            holder.btnAdd.setOnClickListener {
                selectedSequence.add(wp)
                updateSelectedSequenceUI()
            }

            holder.itemView.setOnClickListener {
                selectedSequence.add(wp)
                updateSelectedSequenceUI()
            }
        }

        override fun getItemCount(): Int = displayedWaypoints.size
    }
}
