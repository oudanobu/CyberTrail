package com.cybertrail.app.offline

data class OfflineMapRegion(
    val id: String,
    val name: String,
    val mbtilesUrl: String?,
    val demUrl: String?,
    val expectedSizeBytes: Long,
    val tileCount: Int,
    var bounds: String,
    var isDownloaded: Boolean = false,
    var isDownloading: Boolean = false,
    var progress: Int = 0,
    var localPath: String? = null,
    var downloadId: Long = -1L,
    var category: String = "一级行政区",
    var minZoom: Int = 0,
    var maxZoom: Int = 22,
    var parentName: String? = null,
    var directoryName: String? = null
)

