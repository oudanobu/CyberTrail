package com.cybertrail.app.offline

sealed class OfflineTreeItem {
    
    data class Folder(
        val name: String,
        val icon: String,
        val details: String,
        val targetPath: List<String>
    ) : OfflineTreeItem()

    data class MapFile(
        val region: OfflineMapRegion
    ) : OfflineTreeItem()

    data class DemFile(
        val dem: OfflineDemRegion
    ) : OfflineTreeItem()

    data class HelpManual(
        val title: String,
        val subtitle: String,
        val desc: String,
        val webUrl: String,
        val downloadUrl: String,
        val githubUrl: String
    ) : OfflineTreeItem()
}
