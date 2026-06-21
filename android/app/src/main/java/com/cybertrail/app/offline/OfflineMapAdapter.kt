package com.cybertrail.app.offline

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
) : RecyclerView.Adapter<OfflineMapAdapter.MapViewHolder>() {

    class MapViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.nameText)
        val infoText: TextView = view.findViewById(R.id.infoText)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val actionButton: Button = view.findViewById(R.id.actionButton)
        val importButton: Button = view.findViewById(R.id.importButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MapViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_map_region, parent, false)
        return MapViewHolder(view)
    }

    override fun onBindViewHolder(holder: MapViewHolder, position: Int) {
        val map = maps[position]
        holder.nameText.text = map.name
        
        val sizeMB = map.expectedSizeBytes / (1024 * 1024)
        val pathInfo = if (map.isDownloaded) "状态: ✅ 已载入\n物理路径: ${map.localPath}" else "状态: ❌ 未加载"
        val info = "理想大小: ${sizeMB}MB | 预估瓦片数: ${map.tileCount}\n范围: ${map.bounds}\n$pathInfo"
        holder.infoText.text = info
        
        holder.importButton.setOnClickListener { onImportClick(map) }

        if (map.isDownloaded) {
            holder.actionButton.text = "删除"
            holder.actionButton.isEnabled = true
            holder.actionButton.setOnClickListener { onDeleteClick(map) }
            holder.progressBar.visibility = View.GONE
        } else if (map.isDownloading) {
            holder.actionButton.text = "下载中"
            holder.actionButton.isEnabled = false
            holder.progressBar.visibility = View.VISIBLE
            holder.progressBar.progress = map.progress
        } else {
            holder.actionButton.text = "下载"
            holder.actionButton.isEnabled = true
            holder.actionButton.setOnClickListener { onDownloadClick(map) }
            holder.progressBar.visibility = View.GONE
        }
    }

    override fun getItemCount() = maps.size
}
