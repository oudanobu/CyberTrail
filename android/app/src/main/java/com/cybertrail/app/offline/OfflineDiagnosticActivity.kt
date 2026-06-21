package com.cybertrail.app.offline

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import com.cybertrail.app.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OfflineDiagnosticActivity : AppCompatActivity() {

    private lateinit var mapManager: OfflineMapManager
    private lateinit var container: LinearLayout
    private lateinit var tvMapsPath: TextView
    private lateinit var tvMapsStatus: TextView
    private lateinit var tvDemPath: TextView
    private lateinit var tvDemStatus: TextView
    private lateinit var tvEmpty: TextView

    private val IMPORT_MAP_REQUEST_CODE = 501
    private val IMPORT_DEM_REQUEST_CODE = 502

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_diagnostic)

        val toolbar: Toolbar = findViewById(R.id.diagnostic_toolbar)
        toolbar.title = "数字地图离线诊断"
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        mapManager = OfflineMapManager(this)
        container = findViewById(R.id.diagnostic_items_container)
        tvMapsPath = findViewById(R.id.tv_maps_path)
        tvMapsStatus = findViewById(R.id.tv_maps_status)
        tvDemPath = findViewById(R.id.tv_dem_path)
        tvDemStatus = findViewById(R.id.tv_dem_status)
        tvEmpty = findViewById(R.id.tv_empty_diagnostic)

        // Bind global actions
        findViewById<Button>(R.id.btn_rescan).setOnClickListener {
            runDiagnostic()
            Toast.makeText(this, "🔄 重新扫描完成：已刷新全部路径和状态！", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_import_map).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(intent, "选择离线瓦片地图数据 (.mbtiles)"), IMPORT_MAP_REQUEST_CODE)
        }

        findViewById<Button>(R.id.btn_import_dem).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(intent, "选择高程瓦片数据 (.hgt, .bil, .tif, .img)"), IMPORT_DEM_REQUEST_CODE)
        }

        runDiagnostic()
    }

    private fun runDiagnostic() {
        container.removeAllViews()

        val mapsDir = mapManager.mapsDir
        val demDir = mapManager.demDir

        tvMapsPath.text = mapsDir.absolutePath
        tvDemPath.text = demDir.absolutePath

        val mapFiles = mapsDir.listFiles { _, name -> name.endsWith(".mbtiles", ignoreCase = true) } ?: emptyArray()
        val demFiles = demDir.listFiles { _, name ->
            name.endsWith(".hgt", ignoreCase = true) ||
            name.endsWith(".tif", ignoreCase = true) ||
            name.endsWith(".tiff", ignoreCase = true) ||
            name.endsWith(".bil", ignoreCase = true) ||
            name.endsWith(".img", ignoreCase = true) ||
            name.endsWith(".asc", ignoreCase = true)
        } ?: emptyArray()

        tvMapsStatus.text = "检测状态: 发现 ${mapFiles.size} 个离线地图文件"
        tvDemStatus.text = "检测状态: 发现 ${demFiles.size} 个高程数据文件"

        if (mapFiles.isEmpty() && demFiles.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            return
        } else {
            tvEmpty.visibility = View.GONE
        }

        // 1. Process Maps Diagnostic
        for (file in mapFiles) {
            val card = createDiagnosticCard(file, isMap = true)
            container.addView(card)
        }

        // 2. Process DEM Diagnostic
        for (file in demFiles) {
            val card = createDiagnosticCard(file, isMap = false)
            container.addView(card)
        }
    }

    private fun createDiagnosticCard(file: File, isMap: Boolean): CardView {
        val context = this
        val card = CardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            radius = 8f * resources.displayMetrics.density
            cardElevation = 2f * resources.displayMetrics.density
            setCardBackgroundColor(Color.WHITE)
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Group Title and Badge layout
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val typeText = if (isMap) "🗺️ MAP: " else "🏔️ DEM: "
        val titleText = TextView(context).apply {
            text = "$typeText${file.name}"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#222222"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        headerLayout.addView(titleText)

        // Perform Diagnostics
        var isRecognized = false
        var isDamaged = false
        var detailsText = ""

        val sizeMB = file.length().toDouble() / (1024 * 1024)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val updateTime = sdf.format(Date(file.lastModified()))

        if (isMap) {
            var db: SQLiteDatabase? = null
            try {
                if (file.length() == 0L) {
                    isDamaged = true
                } else {
                    db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                    
                    val metadataExists = hasTable(db, "metadata")
                    val tilesExists = hasTable(db, "tiles")

                    if (!metadataExists && !tilesExists) {
                        isRecognized = false
                    } else {
                        isRecognized = true
                        var name = file.nameWithoutExtension
                        var minzoom = "未设定"
                        var maxzoom = "未设定"
                        var bounds = "未设定"

                        if (metadataExists) {
                            val c = db.rawQuery("SELECT name, value FROM metadata", null)
                            while (c.moveToNext()) {
                                val k = c.getString(0)?.lowercase() ?: ""
                                val v = c.getString(1) ?: ""
                                when (k) {
                                    "name" -> name = v
                                    "minzoom" -> minzoom = v
                                    "maxzoom" -> maxzoom = v
                                    "bounds" -> bounds = v
                                }
                            }
                            c.close()
                        }
                        
                        // Classify Map level visually
                        val levelText = when (maxzoom.toIntOrNull() ?: 12) {
                            in 0..6 -> "地球级"
                            in 7..8 -> "大洲级"
                            in 9..10 -> "国家级"
                            in 11..12 -> "省一级"
                            in 13..14 -> "市二级"
                            else -> "区县三级"
                        }
                        detailsText = "识别地图名: $name\n地图量级: $levelText (缩放级别 $minzoom ~ $maxzoom)\n地理范围: $bounds"
                    }
                }
            } catch (e: Exception) {
                Log.e("Diagnostic", "Sqlite reading error on file: ${file.name}", e)
                isDamaged = true
            } finally {
                db?.close()
            }
        } else {
            // DEM
            val ext = file.extension.lowercase()
            if (file.length() == 0L) {
                isDamaged = true
            } else {
                isRecognized = true
                val source = when (ext) {
                    "hgt" -> {
                        val len = file.length()
                        if (len != 2884802L && len != 25934402L) {
                            "SRTM 标准 HGT (大小异常)"
                        } else {
                            "SRTM 特斯拉 HGT"
                        }
                    }
                    "tif", "tiff" -> "GeoTIFF 高精密等高解算芯片"
                    "bil" -> "BIL 像素网格高程"
                    "img" -> "IMG 高程矢量模型"
                    else -> "等高数据 (格式.${ext.uppercase()})"
                }
                detailsText = "地形高程格式: $source\n精度标准: 1-弧秒 (约30米高密格网)\n数据有效性: 文件解压正常"
            }
        }

        val badgeText = TextView(context).apply {
            setPadding(16, 6, 16, 6)
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }

        when {
            isDamaged -> {
                badgeText.text = "损坏 / 异常"
                badgeText.setBackgroundColor(Color.parseColor("#D32F2F"))
            }
            isRecognized -> {
                badgeText.text = "已识别"
                badgeText.setBackgroundColor(Color.parseColor("#388E3C"))
            }
            else -> {
                badgeText.text = "未识别"
                badgeText.setBackgroundColor(Color.parseColor("#F57C00"))
            }
        }
        headerLayout.addView(badgeText)
        contentLayout.addView(headerLayout)

        // General file metrics
        val metricsText = TextView(context).apply {
            text = "文件大小: %.2f MB  |  更新时间: %s\n物理路径: %s\n%s".format(sizeMB, updateTime, file.absolutePath, detailsText)
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(2f, 1.1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }
        }
        contentLayout.addView(metricsText)

        // Action button strip at card bottom
        val buttonStrip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12
            }
        }

        val btnRename = Button(context).apply {
            text = "🖊️ 重命名"
            textSize = 11sp
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#546E7A"))
            layoutParams = LinearLayout.LayoutParams(0, (36 * resources.displayMetrics.density).toInt(), 1f).apply {
                rightMargin = 8
            }
            setOnClickListener {
                showRenameDialog(file)
            }
        }

        val btnDelete = Button(context).apply {
            text = "❌ 物理删除"
            textSize = 11sp
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828"))
            layoutParams = LinearLayout.LayoutParams(0, (36 * resources.displayMetrics.density).toInt(), 1f)
            setOnClickListener {
                showDeleteConfirmDialog(file)
            }
        }

        buttonStrip.addView(btnRename)
        buttonStrip.addView(btnDelete)
        contentLayout.addView(buttonStrip)

        card.addView(contentLayout)
        return card
    }

    private fun showRenameDialog(file: File) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("🖊️ 输入新的物理文件名")
        
        val input = EditText(this).apply {
            setText(file.name)
            setSelection(file.name.length)
            setSingleLine(true)
        }
        
        val containerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            addView(input)
        }
        
        builder.setView(containerLayout)
        builder.setPositiveButton("确同修改") { dialog, _ ->
            val newName = input.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(this, "文件名不能为空", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            if (newName == file.name) {
                dialog.dismiss()
                return@setPositiveButton
            }
            
            val targetFile = File(file.parentFile, newName)
            if (targetFile.exists()) {
                Toast.makeText(this, "同名目标文件已存在，请重新输入", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            
            try {
                if (file.renameTo(targetFile)) {
                    Toast.makeText(this, "成功重命名为: $newName", Toast.LENGTH_SHORT).show()
                    val completeIntent = Intent("com.cybertrail.app.MAP_DOWNLOAD_COMPLETED")
                    sendBroadcast(completeIntent)
                    runDiagnostic()
                } else {
                    Toast.makeText(this, "无法重命名，操作系统正在锁定此文件", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "操作发生异常: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun showDeleteConfirmDialog(file: File) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ 彻底丢弃此离线数据包?")
            .setMessage("确定从设备磁盘完全移除：[ ${file.name} ]？\n\n该操作会立即回收物理存储空间，但一旦删除将无法找回该瓦片或高程。您确定继续删除核销嘛？")
            .setPositiveButton("极其确定物理删除") { dialog, _ ->
                try {
                    if (file.delete()) {
                        Toast.makeText(this, "🗑️ 物理数据已安全销毁，存储空间已成功释放！", Toast.LENGTH_SHORT).show()
                        val completeIntent = Intent("com.cybertrail.app.MAP_DOWNLOAD_COMPLETED")
                        sendBroadcast(completeIntent)
                        runDiagnostic()
                    } else {
                        Toast.makeText(this, "删除失败，系统读写锁在占用中", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "操作异常: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("撤销保留") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun hasTable(db: SQLiteDatabase, tableName: String): Boolean {
        var cursor: android.database.Cursor? = null
        return try {
            cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName))
            val exists = cursor.count > 0
            cursor.close()
            exists
        } catch (e: Exception) {
            false
        } finally {
            cursor?.close()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_MAP_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                val originalName = getUriFileName(this, uri) ?: "imported_map_${System.currentTimeMillis()}.mbtiles"
                importMapFile(uri, originalName)
            }
        } else if (requestCode == IMPORT_DEM_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                val originalName = getUriFileName(this, uri) ?: "imported_dem_${System.currentTimeMillis()}.hgt"
                importDemFile(uri, originalName)
            }
        }
    }

    private fun getUriFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                Log.e("OfflineDiagnostic", "Error getting Uri filename", e)
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        return result
    }

    private fun importMapFile(sourceUri: Uri, destFileName: String) {
        val destFile = File(mapManager.mapsDir, destFileName)
        try {
            contentResolver.openInputStream(sourceUri).use { inputStream ->
                if (inputStream != null) {
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    Log.d("MAP_DEBUG", "ImportSuccess: Map $destFileName imported successfully")
                    Toast.makeText(this, "🗺️ 离线地图包成功导入! 已命名为: ${destFileName}", Toast.LENGTH_SHORT).show()
                    
                    val completeIntent = Intent("com.cybertrail.app.MAP_DOWNLOAD_COMPLETED")
                    sendBroadcast(completeIntent)
                    runDiagnostic()
                } else {
                    Toast.makeText(this, "打开地图源文件失败", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "外部地图导入失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importDemFile(sourceUri: Uri, destFileName: String) {
        val destFile = File(mapManager.demDir, destFileName)
        try {
            contentResolver.openInputStream(sourceUri).use { inputStream ->
                if (inputStream != null) {
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    Log.d("MAP_DEBUG", "ImportSuccess: DEM $destFileName imported successfully")
                    Toast.makeText(this, "🏔️ 高程数据成功导入! 已命名为: ${destFileName}", Toast.LENGTH_SHORT).show()
                    
                    val completeIntent = Intent("com.cybertrail.app.MAP_DOWNLOAD_COMPLETED")
                    sendBroadcast(completeIntent)
                    runDiagnostic()
                } else {
                    Toast.makeText(this, "打开高程源文件失败", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "外部高程导入失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
