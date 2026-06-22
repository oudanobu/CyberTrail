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
    var localPath: String? = null,
    var downloadId: Long = -1L,
    var category: String = "二级行政区",
    var parentName: String? = null,
    var directoryName: String? = null
)
