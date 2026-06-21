package com.cybertrail.app.offline

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cybertrail.app.R

class OfflineMapAdapter(
    private val maps: List<OfflineMapRegion>,
    private val onDownloadClick: (OfflineMapRegion) -> Unit,
    private val onDeleteClick: (OfflineMapRegion) -> Unit,
    private val onImportClick: (OfflineMapRegion) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerText: TextView = view as TextView
    }

    class MapViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.nameText)
        val infoText: TextView = view.findViewById(R.id.infoText)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val actionButton: Button = view.findViewById(R.id.actionButton)
        val importButton: Button = view.findViewById(R.id.importButton)
    }

    override fun getItemViewType(position: Int): Int {
        return if (maps[position].id.startsWith("header_")) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == VIEW_TYPE_HEADER) {
            val textView = TextView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16, 24, 16, 8)
                }
                textSize = 15spToPx(parent.context)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFF388E3C.toInt()) // Forest green accent for category header
                gravity = Gravity.START
            }
            return HeaderViewHolder(textView)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_map_region, parent, false)
            return MapViewHolder(view)
        }
    }

    private fun 15spToPx(context: android.content.Context): Float {
        return 15f // fallbacks or direct text size in sp
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val map = maps[position]
        
        if (holder is HeaderViewHolder) {
            holder.headerText.text = map.name
            holder.headerText.textSize = 15f
            return
        }

        val itemHolder = holder as MapViewHolder
        itemHolder.nameText.text = map.name
        
        val file = map.localPath?.let { java.io.File(it) }
        val sizeStr = if (file != null && file.exists()) {
            "%.2f MB (物理大小)".format(file.length().toDouble() / (1024 * 1024))
        } else {
            "${map.expectedSizeBytes / (1024 * 1024)} MB (标准预估)"
        }

        val updateTimeStr = if (file != null && file.exists()) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            sdf.format(java.util.Date(file.lastModified()))
        } else {
            "未获取到本地物理文件"
        }

        val pathInfo = if (map.isDownloaded) {
            "状态: ✅ 物理已载入\n" +
            "物理路径: ${map.localPath}\n" +
            "更新时间: $updateTimeStr"
        } else {
            "状态: ❌ 未加载 (各层级地图均未加载，点击[导入本本地]或点击详情以启用)"
        }
        
        val info = "所属分类: ${map.category} | 推荐层级: zoom ${map.minZoom}~${map.maxZoom}\n" +
                "文件大小: $sizeStr | 地理范围: ${map.bounds}\n" +
                "$pathInfo"
        itemHolder.infoText.text = info
        
        itemHolder.importButton.text = "导入"
        itemHolder.importButton.setOnClickListener { onImportClick(map) }

        // Setup button action behaviors based on user request details
        if (map.isDownloaded) {
            itemHolder.actionButton.visibility = View.VISIBLE
            itemHolder.actionButton.text = "删除"
            itemHolder.actionButton.setBackgroundColor(0xFFE53935.toInt()) // Red accent
            itemHolder.actionButton.setTextColor(android.graphics.Color.WHITE)
            itemHolder.actionButton.setOnClickListener { onDeleteClick(map) }
            itemHolder.progressBar.visibility = View.GONE
        } else {
            // Unloaded presets: show download link if 大洲级 or 国家级
            if (map.category == "大洲级" || map.category == "国家级") {
                itemHolder.actionButton.visibility = View.VISIBLE
                itemHolder.actionButton.text = "打开下载页"
                itemHolder.actionButton.setBackgroundColor(0xFF1E88E5.toInt()) // Blue accent
                itemHolder.actionButton.setTextColor(android.graphics.Color.WHITE)
                itemHolder.actionButton.setOnClickListener { onDownloadClick(map) }
            } else {
                // Hide actionButton for other categories when not loaded
                itemHolder.actionButton.visibility = View.GONE
            }
            itemHolder.progressBar.visibility = View.GONE
        }
    }

    override fun getItemCount() = maps.size
}

