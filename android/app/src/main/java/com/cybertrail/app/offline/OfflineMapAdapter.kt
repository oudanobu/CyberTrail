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
    private val onDeleteClick: (OfflineMapRegion) -> Unit
) : RecyclerView.Adapter<OfflineMapAdapter.MapViewHolder>() {

    class MapViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.nameText)
        val infoText: TextView = view.findViewById(R.id.infoText)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val actionButton: Button = view.findViewById(R.id.actionButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MapViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.id.item_map_region, parent, false)
        return MapViewHolder(view)
    }

    override fun onBindViewHolder(holder: MapViewHolder, position: Int) {
        val map = maps[position]
        holder.nameText.text = map.name
        
        val sizeMB = map.expectedSizeBytes / (1024 * 1024)
        val info = "大小: ${sizeMB}MB | 瓦片数: ${map.tileCount}\n范围: ${map.bounds}"
        holder.infoText.text = info
        
        if (map.isDownloaded) {
            holder.actionButton.text = "删除"
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
