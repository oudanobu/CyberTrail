package com.cybertrail.app.offline

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cybertrail.app.R

class OfflineDemAdapter(
    private val dems: List<OfflineDemRegion>,
    private val onDownloadClick: (OfflineDemRegion) -> Unit,
    private val onDeleteClick: (OfflineDemRegion) -> Unit,
    private val onImportClick: (OfflineDemRegion) -> Unit
) : RecyclerView.Adapter<OfflineDemAdapter.DemViewHolder>() {

    class DemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.nameText)
        val infoText: TextView = view.findViewById(R.id.infoText)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val actionButton: Button = view.findViewById(R.id.actionButton)
        val importButton: Button = view.findViewById(R.id.importButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dem_region, parent, false)
        return DemViewHolder(view)
    }

    override fun onBindViewHolder(holder: DemViewHolder, position: Int) {
        val dem = dems[position]
        holder.nameText.text = dem.name
        
        val sizeMB = dem.expectedSizeBytes / (1024 * 1024)
        val pathInfo = if (dem.isDownloaded) "状态: ✅ 物理已载入\n物理路径: ${dem.localPath}" else "状态: ❌ 未加载 (坡度和坡向无法计算)"
        val info = "物理名称: ${dem.fileName} | 类型: ${dem.demType} | 理想大小: ${sizeMB}MB\n$pathInfo"
        holder.infoText.text = info
        
        holder.importButton.setOnClickListener { onImportClick(dem) }

        if (dem.isDownloaded) {
            holder.actionButton.text = "删除"
            holder.actionButton.isEnabled = true
            holder.actionButton.setOnClickListener { onDeleteClick(dem) }
            holder.progressBar.visibility = View.GONE
        } else if (dem.isDownloading) {
            holder.actionButton.text = "下载中"
            holder.actionButton.isEnabled = false
            holder.progressBar.visibility = View.VISIBLE
            holder.progressBar.progress = dem.progress
        } else {
            holder.actionButton.text = "下载"
            holder.actionButton.isEnabled = true
            holder.actionButton.setOnClickListener { onDownloadClick(dem) }
            holder.progressBar.visibility = View.GONE
        }
    }

    override fun getItemCount() = dems.size
}
