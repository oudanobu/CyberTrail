package com.cybertrail.app

import android.content.Intent
import android.os.Bundle
import android.os.Environment
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
import com.cybertrail.app.db.RouteDao
import com.cybertrail.app.db.RouteEntity
import com.cybertrail.app.db.WaypointDao
import com.cybertrail.app.db.WaypointEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class RouteManagerActivity : AppCompatActivity() {

    private lateinit var rvRouteList: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var tvRouteCountSummary: TextView
    private lateinit var etSearchRoute: EditText
    private lateinit var btnCreateRoute: View

    private lateinit var routeDao: RouteDao
    private lateinit var waypointDao: WaypointDao
    private val dbExecutor = Executors.newSingleThreadExecutor()

    private val allRoutes = mutableListOf<RouteEntity>()
    private val displayedRoutes = mutableListOf<RouteEntity>()
    private val allWaypoints = mutableMapOf<String, WaypointEntity>()
    private lateinit var listAdapter: RouteListAdapter

    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_manager)

        val db = AppDatabase.getDatabase(this)
        routeDao = db.routeDao()
        waypointDao = db.waypointDao()

        rvRouteList = findViewById(R.id.rv_route_list)
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        tvRouteCountSummary = findViewById(R.id.tv_route_count_summary)
        etSearchRoute = findViewById(R.id.et_search_route)
        btnCreateRoute = findViewById(R.id.btn_create_route)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        btnCreateRoute.setOnClickListener {
            // Check if we have at least 2 waypoints before allowing creation
            dbExecutor.execute {
                val waypointsCount = waypointDao.getAllWaypoints().size
                runOnUiThread {
                    if (waypointsCount < 2) {
                        Toast.makeText(this, "需要至少创建2个航点才能进行路线规划！", Toast.LENGTH_LONG).show()
                    } else {
                        val intent = Intent(this@RouteManagerActivity, RouteCreateActivity::class.java)
                        startActivity(intent)
                    }
                }
            }
        }

        etSearchRoute.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilterAndSort()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        rvRouteList.layoutManager = LinearLayoutManager(this)
        listAdapter = RouteListAdapter()
        rvRouteList.adapter = listAdapter
    }

    override fun onResume() {
        super.onResume()
        loadRoutesAndWaypoints()
    }

    private fun loadRoutesAndWaypoints() {
        dbExecutor.execute {
            val routesList = routeDao.getAllRoutes()
            val waypointsList = waypointDao.getAllWaypoints()
            
            runOnUiThread {
                allWaypoints.clear()
                for (wp in waypointsList) {
                    allWaypoints[wp.id] = wp
                }

                allRoutes.clear()
                allRoutes.addAll(routesList)
                applyFilterAndSort()
            }
        }
    }

    private fun applyFilterAndSort() {
        displayedRoutes.clear()
        val filtered = if (searchQuery.isEmpty()) {
            allRoutes
        } else {
            allRoutes.filter {
                it.name.contains(searchQuery, ignoreCase = true) || 
                (it.description != null && it.description.contains(searchQuery, ignoreCase = true))
            }
        }

        // Sort: Favorites first, then creation time descending
        val sorted = filtered.sortedWith(compareByDescending<RouteEntity> { it.favorite }.thenByDescending { it.createTime })
        displayedRoutes.addAll(sorted)

        tvRouteCountSummary.text = "共 ${displayedRoutes.size} 条"

        if (displayedRoutes.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            rvRouteList.visibility = View.GONE
        } else {
            layoutEmptyState.visibility = View.GONE
            rvRouteList.visibility = View.VISIBLE
        }

        listAdapter.notifyDataSetChanged()
    }

    private fun showExportOptionsDialog(route: RouteEntity) {
        val formats = arrayOf("GPX Route (路线)", "KML (图层)", "GeoJSON (数据)")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("选择导出格式")
            .setItems(formats) { _, which ->
                dbExecutor.execute {
                    val wpList = mutableListOf<WaypointEntity>()
                    for (id in route.getWaypointIdList()) {
                        val wp = waypointDao.getWaypointById(id)
                        if (wp != null) {
                            wpList.add(wp)
                        }
                    }
                    runOnUiThread {
                        when (which) {
                            0 -> exportRouteGPX(route, wpList)
                            1 -> exportRouteKML(route, wpList)
                            2 -> exportRouteGeoJSON(route, wpList)
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun getExportsDir(): File {
        val baseDir = File(Environment.getExternalStorageDirectory(), "CyberTrail")
        val exportsDir = File(baseDir, "Exports")
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        }
        return exportsDir
    }

    private fun exportRouteGPX(route: RouteEntity, waypoints: List<WaypointEntity>) {
        try {
            val dir = getExportsDir()
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "route_${route.name}_${sdf.format(Date())}.gpx"
            val file = File(dir, fileName)

            file.printWriter().use { out ->
                out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                out.println("<gpx version=\"1.1\" creator=\"CyberTrail\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
                out.println("  <rte>")
                out.println("    <name>${route.name}</name>")
                route.description?.let { out.println("    <desc>${it}</desc>") }
                
                for (wp in waypoints) {
                    out.println("    <rtept lat=\"${wp.latitude}\" lon=\"${wp.longitude}\">")
                    wp.elevation?.let { out.println("      <ele>${it}</ele>") }
                    out.println("      <name>${wp.name}</name>")
                    wp.description?.let { out.println("      <desc>${it}</desc>") }
                    out.println("      <sym>${wp.iconType}</sym>")
                    out.println("    </rtept>")
                }
                
                out.println("  </rte>")
                out.println("</gpx>")
            }
            Toast.makeText(this, "GPX导出成功！保存在:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportRouteKML(route: RouteEntity, waypoints: List<WaypointEntity>) {
        try {
            val dir = getExportsDir()
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "route_${route.name}_${sdf.format(Date())}.kml"
            val file = File(dir, fileName)

            file.printWriter().use { out ->
                out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                out.println("<kml xmlns=\"http://www.opengis.net/kml/2.2\">")
                out.println("  <Document>")
                out.println("    <name>${route.name}</name>")
                route.description?.let { out.println("    <description>${it}</description>") }
                
                // Add Placemark for LineString (the route path)
                out.println("    <Placemark>")
                out.println("      <name>${route.name} - 路线轨迹</name>")
                out.println("      <LineString>")
                out.println("        <tessellate>1</tessellate>")
                out.println("        <coordinates>")
                val coordString = waypoints.joinToString(" ") { "${it.longitude},${it.latitude},${it.elevation ?: 0.0}" }
                out.println("          $coordString")
                out.println("        </coordinates>")
                out.println("      </LineString>")
                out.println("    </Placemark>")

                // Add Placemarks for each Waypoint
                for (wp in waypoints) {
                    out.println("    <Placemark>")
                    out.println("      <name>${wp.name}</name>")
                    wp.description?.let { out.println("      <description>${it}</description>") }
                    out.println("      <Point>")
                    out.println("        <coordinates>${wp.longitude},${wp.latitude},${wp.elevation ?: 0.0}</coordinates>")
                    out.println("      </Point>")
                    out.println("    </Placemark>")
                }

                out.println("  </Document>")
                out.println("</kml>")
            }
            Toast.makeText(this, "KML导出成功！保存在:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportRouteGeoJSON(route: RouteEntity, waypoints: List<WaypointEntity>) {
        try {
            val dir = getExportsDir()
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "route_${route.name}_${sdf.format(Date())}.geojson"
            val file = File(dir, fileName)

            file.printWriter().use { out ->
                out.println("{")
                out.println("  \"type\": \"FeatureCollection\",")
                out.println("  \"features\": [")
                
                // 1. LineString feature
                out.println("    {")
                out.println("      \"type\": \"Feature\",")
                out.println("      \"properties\": {")
                out.println("        \"name\": \"${route.name}\",")
                out.println("        \"description\": \"${route.description ?: ""}\",")
                out.println("        \"type\": \"route_line\"")
                out.println("      },")
                out.println("      \"geometry\": {")
                out.println("        \"type\": \"LineString\",")
                out.println("        \"coordinates\": [")
                val lineCoords = waypoints.joinToString(",\n") { "          [${it.longitude}, ${it.latitude}, ${it.elevation ?: 0.0}]" }
                out.println(lineCoords)
                out.println("        ]")
                out.println("      }")
                out.println("    }")

                // 2. Individual Waypoints features
                for (wp in waypoints) {
                    out.println("    ,")
                    out.println("    {")
                    out.println("      \"type\": \"Feature\",")
                    out.println("      \"properties\": {")
                    out.println("        \"name\": \"${wp.name}\",")
                    out.println("        \"description\": \"${wp.description ?: ""}\",")
                    out.println("        \"iconType\": \"${wp.iconType}\",")
                    out.println("        \"type\": \"route_waypoint\"")
                    out.println("      },")
                    out.println("      \"geometry\": {")
                    out.println("        \"type\": \"Point\",")
                    out.println("        \"coordinates\": [${wp.longitude}, ${wp.latitude}, ${wp.elevation ?: 0.0}]")
                    out.println("      }")
                    out.println("    }")
                }
                
                out.println("  ]")
                out.println("}")
            }
            Toast.makeText(this, "GeoJSON导出成功！保存在:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
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

    inner class RouteListAdapter : RecyclerView.Adapter<RouteListAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvEmoji: TextView = view.findViewById(R.id.tv_route_emoji)
            val tvName: TextView = view.findViewById(R.id.tv_route_name)
            val btnFavStar: TextView = view.findViewById(R.id.btn_route_fav_star)
            val tvDesc: TextView = view.findViewById(R.id.tv_route_desc)
            val tvDistance: TextView = view.findViewById(R.id.tv_route_distance)
            val tvTime: TextView = view.findViewById(R.id.tv_route_time)
            val tvWaypointsCount: TextView = view.findViewById(R.id.tv_route_waypoints_count)
            val tvRouteSequence: TextView = view.findViewById(R.id.tv_route_sequence)

            val btnActionMap: View = view.findViewById(R.id.btn_action_map)
            val btnActionExport: View = view.findViewById(R.id.btn_action_export)
            val btnActionEdit: View = view.findViewById(R.id.btn_action_edit)
            val btnActionDelete: View = view.findViewById(R.id.btn_action_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_route_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val route = displayedRoutes[position]

            holder.tvEmoji.text = "🛣️"
            holder.tvName.text = route.name
            holder.tvDesc.text = route.description ?: "无路线备注"
            
            val distKm = route.distanceMeters / 1000.0
            holder.tvDistance.text = String.format(Locale.US, "%.2f km", distKm)

            val mins = route.estimatedTimeMinutes
            holder.tvTime.text = if (mins >= 60) {
                val h = mins / 60
                val m = mins % 60
                "${h}h${m}m"
            } else {
                "${mins}m"
            }

            val wpIds = route.getWaypointIdList()
            holder.tvWaypointsCount.text = "${wpIds.size} 个"

            // Construct readable sequence text (e.g. "营地 ➔ 水源 ➔ 终点")
            val nameSequence = wpIds.mapNotNull { allWaypoints[it]?.name }
            if (nameSequence.isNotEmpty()) {
                holder.tvRouteSequence.text = nameSequence.joinToString(" ➔ ")
            } else {
                holder.tvRouteSequence.text = "暂无经过航点"
            }

            // Favorite star state
            holder.btnFavStar.text = if (route.favorite) "★" else "☆"
            holder.btnFavStar.setOnClickListener {
                route.favorite = !route.favorite
                holder.btnFavStar.text = if (route.favorite) "★" else "☆"
                dbExecutor.execute {
                    routeDao.updateRoute(route)
                    runOnUiThread {
                        loadRoutesAndWaypoints()
                    }
                }
            }

            // Edit
            holder.btnActionEdit.setOnClickListener {
                val intent = Intent(this@RouteManagerActivity, RouteCreateActivity::class.java).apply {
                    putExtra("route_id", route.id)
                }
                startActivity(intent)
            }

            // Delete
            holder.btnActionDelete.setOnClickListener {
                AlertDialog.Builder(this@RouteManagerActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("确认删除路线")
                    .setMessage("确定要删除 \"${route.name}\" 吗？")
                    .setPositiveButton("删除") { _, _ ->
                        dbExecutor.execute {
                            routeDao.deleteRouteById(route.id)
                            runOnUiThread {
                                Toast.makeText(this@RouteManagerActivity, "路线已删除", Toast.LENGTH_SHORT).show()
                                loadRoutesAndWaypoints()
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }

            // Export options
            holder.btnActionExport.setOnClickListener {
                showExportOptionsDialog(route)
            }

            // Plot/View on Map and start navigation option
            holder.btnActionMap.setOnClickListener {
                val intent = Intent(this@RouteManagerActivity, MapActivity::class.java).apply {
                    putExtra("plot_route_id", route.id)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            }
        }

        override fun getItemCount(): Int = displayedRoutes.size
    }
}
