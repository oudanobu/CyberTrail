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
        
        val file = dem.localPath?.let { java.io.File(it) }
        val sizeStr = if (file != null && file.exists()) {
            "%.2f MB (物理大小)".format(file.length().toDouble() / (1024 * 1024))
        } else {
            "${dem.expectedSizeBytes / (1024 * 1024)} MB (标准预估)"
        }

        val updateTimeStr = if (file != null && file.exists()) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            sdf.format(java.util.Date(file.lastModified()))
        } else {
            "未获取到本地物理文件"
        }

        val resolution = "高清晰度 1角秒 / 30米 (30m Resolution Grid)"
        val coverage = when {
            dem.id.contains("srtm") -> "全天候航天缝合带 (辽宁 / 丹东及主要自驾测试区)"
            dem.id.contains("aster") -> "高精密星载遥感带 (覆盖辽宁内陆与口岸边境)"
            dem.id.contains("copernicus") -> "欧空局全球高度包 (覆盖辽东半岛 / 连云港 / 朝鲜半岛)"
            else -> "自定义加载扇区 / 设备存储高程瓦片群"
        }

        val source = when {
            dem.id.contains("srtm") -> "SRTM"
            dem.id.contains("aster") -> "ASTER GDEM"
            dem.id.contains("copernicus") -> "Copernicus"
            else -> "USER_SAF_IMPORT"
        }

        val pathInfo = if (dem.isDownloaded) {
            "状态: ✅ 已加载\n" +
            "已加载文件名: ${dem.fileName}\n" +
            "物理路径: ${dem.localPath}\n" +
            "覆盖范围: $coverage\n" +
            "分辨率: 30m\n" +
            "高程来源: $source\n" +
            "更新时间: $updateTimeStr"
        } else {
            "状态: ❌ 未加载 (坡度和坡向无法计算，点击[打开下载页]或[导入外部文件]以载入数据)"
        }
        val info = "高程名称: ${dem.fileName} | 格式类型: ${dem.demType}\n文件大小: $sizeStr\n$pathInfo"
        holder.infoText.text = info
        
        holder.importButton.text = "导入"
        holder.importButton.setOnClickListener { onImportClick(dem) }

        if (dem.isDownloaded) {
            holder.actionButton.text = "删除"
            holder.actionButton.setBackgroundColor(0xFFE53935.toInt()) // Red accent
            holder.actionButton.setTextColor(android.graphics.Color.WHITE)
            holder.actionButton.isEnabled = true
            holder.actionButton.setOnClickListener { onDeleteClick(dem) }
            holder.progressBar.visibility = View.GONE
        } else {
            holder.actionButton.text = "打开下载页"
            holder.actionButton.setBackgroundColor(0xFF1E88E5.toInt()) // Blue accent
            holder.actionButton.setTextColor(android.graphics.Color.WHITE)
            holder.actionButton.isEnabled = true
            holder.actionButton.setOnClickListener { onDownloadClick(dem) }
            holder.progressBar.visibility = View.GONE
        }
    }

    override fun getItemCount() = dems.size
}
