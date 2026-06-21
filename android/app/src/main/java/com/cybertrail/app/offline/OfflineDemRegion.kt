package com.cybertrail.app.offline

data class OfflineDemRegion(
    val id: String,
    val name: String,
    val demUrl: String,
    val demType: String, // "SRTM HGT" or "ASTER GDEM"
    val expectedSizeBytes: Long,
    val fileName: String,
    var isDownloaded: Boolean = false,
    var isDownloading: Boolean = false,
    var progress: Int = 0,
    var localPath: String? = null
)
