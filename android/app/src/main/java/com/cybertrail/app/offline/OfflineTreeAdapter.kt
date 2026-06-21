package com.cybertrail.app.offline

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cybertrail.app.R

class OfflineTreeAdapter(
    private var items: List<OfflineTreeItem>,
    private val onFolderClick: (OfflineTreeItem.Folder) -> Unit,
    // Map actions
    private val onMapOpenWeb: (OfflineMapRegion) -> Unit,
    private val onMapCopyUrl: (OfflineMapRegion) -> Unit,
    private val onMapImportLocal: (OfflineMapRegion) -> Unit,
    private val onMapDelete: (OfflineMapRegion) -> Unit,
    // DEM actions
    private val onDemOpenWeb: (OfflineDemRegion) -> Unit,
    private val onDemCopyUrl: (OfflineDemRegion) -> Unit,
    private val onDemImportLocal: (OfflineDemRegion) -> Unit,
    private val onDemDelete: (OfflineDemRegion) -> Unit,
    // Help actions
    private val onHelpOpenWeb: (String) -> Unit,
    private val onHelpCopyUrl: (String) -> Unit,
    private val onHelpOpenGithub: (String) -> Unit
) : RecyclerView.Adapter<OfflineTreeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView = view.findViewById(R.id.tv_icon_indicator)
        val tvTitle: TextView = view.findViewById(R.id.tv_title)
        val tvSubtitle: TextView = view.findViewById(R.id.tv_subtitle)
        val tvStatusBadge: TextView = view.findViewById(R.id.tv_status_badge)
        val tvDetails: TextView = view.findViewById(R.id.tv_details)
        val progressBar: ProgressBar = view.findViewById(R.id.progress_bar)
        
        val layoutActions: View = view.findViewById(R.id.layout_actions)
        val btnOpenPage: Button = view.findViewById(R.id.btn_open_page)
        val btnCopyUrl: Button = view.findViewById(R.id.btn_copy_url)
        val btnGithub: Button = view.findViewById(R.id.btn_github)
        val btnImport: Button = view.findViewById(R.id.btn_import)
        val btnDelete: Button = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_offline_tree, parent, false)
        return ViewHolder(view)
    }

    fun updateData(newItems: List<OfflineTreeItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        // Reset defaults
        holder.itemView.setOnClickListener(null)
        holder.tvDetails.visibility = View.GONE
        holder.layoutActions.visibility = View.GONE
        holder.btnDelete.visibility = View.GONE
        holder.btnImport.visibility = View.VISIBLE
        holder.btnGithub.visibility = View.VISIBLE
        holder.progressBar.visibility = View.GONE

        when (item) {
            is OfflineTreeItem.Folder -> {
                holder.tvIcon.text = item.icon
                holder.tvTitle.text = item.name
                holder.tvSubtitle.text = item.details
                
                holder.tvStatusBadge.text = "文件夹"
                holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#00897B")) // Teal
                
                holder.itemView.setOnClickListener {
                    onFolderClick(item)
                }
            }

            is OfflineTreeItem.MapFile -> {
                val region = item.region
                holder.tvIcon.text = "🗺️"
                holder.tvTitle.text = region.name
                
                val sizeMB = region.expectedSizeBytes.toDouble() / (1024 * 1024)
                holder.tvSubtitle.text = "预计大小: %.1f MB  |  层级范围: 缩放 %d ~ %d 级".format(sizeMB, region.minZoom, region.maxZoom)
                
                if (region.isDownloaded) {
                    holder.tvStatusBadge.text = "已启用"
                    holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#2E7D32")) // Green
                    holder.btnDelete.visibility = View.VISIBLE
                } else {
                    holder.tvStatusBadge.text = "未载入"
                    holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#757575")) // Gray
                }

                holder.tvDetails.visibility = View.VISIBLE
                holder.tvDetails.text = "🌐 目标文件: ${region.id}.mbtiles\n📍 推荐配适地理范围:\n[ ${region.bounds} ]\n📦 切片估算总数: ${region.tileCount}张瓦片"

                holder.layoutActions.visibility = View.VISIBLE
                
                // Button 1: Web Link
                holder.btnOpenPage.text = "极速下载"
                holder.btnOpenPage.setOnClickListener { onMapOpenWeb(region) }

                // Button 2: Copy Download Address
                holder.btnCopyUrl.text = "复制地址"
                holder.btnCopyUrl.setOnClickListener { onMapCopyUrl(region) }

                // Button 3: Geofabrik Index / Github Resources
                holder.btnGithub.text = "Geofabrik"
                holder.btnGithub.setOnClickListener { onHelpOpenWeb("https://download.geofabrik.de") }

                // Button 4: Import locally
                holder.btnImport.text = "导入本地"
                holder.btnImport.setOnClickListener { onMapImportLocal(region) }

                // Button 5: Delete locally
                holder.btnDelete.setOnClickListener { onMapDelete(region) }
            }

            is OfflineTreeItem.DemFile -> {
                val dem = item.dem
                holder.tvIcon.text = "🏔️"
                holder.tvTitle.text = dem.name
                
                val sizeMB = dem.expectedSizeBytes.toDouble() / (1024 * 1024)
                holder.tvSubtitle.text = "格式: %s  |  预计大小: %.1f MB".format(dem.demType, sizeMB)
                
                if (dem.isDownloaded) {
                    holder.tvStatusBadge.text = "已就绪"
                    holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#2E7D32")) // Green
                    holder.btnDelete.visibility = View.VISIBLE
                } else {
                    holder.tvStatusBadge.text = "未加载"
                    holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#9E9E9E")) // Light Gray
                }

                holder.tvDetails.visibility = View.VISIBLE
                val coverageString = when (dem.id) {
                    "world_dem" -> "🌍 全球主要大陆架 / 极地无缝覆盖"
                    "japan_dem" -> "🇯🇵 日本列岛全域 / 包括阿尔卑斯户外核心山体"
                    "china_dem" -> "🇨🇳 中国内陆版图、周边及边缘海岛陆基范围"
                    "asia_dem" -> "🌏 亚洲高海拔高山地带、喜马拉雅等密集探险极地"
                    "liaoning_srtm" -> "📍 中国辽宁省全域及丹东、沈阳等核心山区"
                    else -> "📍 自定义导入高密覆照网幅"
                }
                holder.tvDetails.text = "🛰️ 覆盖区域: %s\n📁 系统指定命名: %s\n⚙️ 格式规格: %s\n🏃‍♂️ 适用: CyberTrail 野外爬升/瞬时坡度/地形高低自动建模".format(
                    coverageString, dem.fileName, dem.demType
                )

                holder.layoutActions.visibility = View.VISIBLE

                // Button 1: Web Link
                holder.btnOpenPage.text = "极速下载"
                holder.btnOpenPage.setOnClickListener { onDemOpenWeb(dem) }

                // Button 2: Copy link
                holder.btnCopyUrl.text = "复制地址"
                holder.btnCopyUrl.setOnClickListener { onDemCopyUrl(dem) }

                // Button 3: OpenTopography Mirror
                holder.btnGithub.text = "数据镜像"
                holder.btnGithub.setOnClickListener { onHelpOpenWeb("https://opentopography.org") }

                // Button 4: Import
                holder.btnImport.text = "导入本地"
                holder.btnImport.setOnClickListener { onDemImportLocal(dem) }

                // Button 5: Delete
                holder.btnDelete.setOnClickListener { onDemDelete(dem) }
            }

            is OfflineTreeItem.HelpManual -> {
                holder.tvIcon.text = "📚"
                holder.tvTitle.text = item.title
                holder.tvSubtitle.text = item.subtitle
                
                holder.tvStatusBadge.text = "标准源"
                holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#3F51B5")) // Indigo

                holder.tvDetails.visibility = View.VISIBLE
                holder.tvDetails.text = item.desc

                holder.layoutActions.visibility = View.VISIBLE
                
                // Hide unnecessary buttons for manuals
                holder.btnImport.visibility = View.GONE
                
                // Button 1: Web link
                holder.btnOpenPage.text = "打开主站"
                holder.btnOpenPage.setOnClickListener { onHelpOpenWeb(item.webUrl) }

                // Button 2: Copy mirror url
                holder.btnCopyUrl.text = "复制源链接"
                holder.btnCopyUrl.setOnClickListener { onHelpCopyUrl(item.downloadUrl) }

                // Button 3: Github resource
                holder.btnGithub.text = "GitHub资源"
                holder.btnGithub.setOnClickListener { onHelpOpenGithub(item.githubUrl) }
            }
        }
    }
}
