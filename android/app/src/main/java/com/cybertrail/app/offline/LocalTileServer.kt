package com.cybertrail.app.offline

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class LocalTileServer(val mbtilesPath: String) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val TAG = "LocalTileServer"
    private val dbsMap = java.util.concurrent.ConcurrentHashMap<String, SQLiteDatabase>()
    val port = 8080

    var onTileRequest: (() -> Unit)? = null
    var onTileFound: (() -> Unit)? = null
    var onTileNotFound: (() -> Unit)? = null

    // 诊断系统所需变量
    var requestCountTotal = 0
    var requestCountTile = 0
    var lastRequestPath: String? = null
    var lastRequestZ: Int? = null
    var lastRequestX: Int? = null
    var lastRequestY: Int? = null
    var lastRequestTimestamp: String? = null
    var serverStarted = false
    var onRequestLogged: (() -> Unit)? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        thread(start = true, name = "LocalTileServerThread") {
            try {
                val mapsDir = java.io.File(mbtilesPath).parentFile ?: java.io.File("/storage/emulated/0/CyberTrail/Maps")
                if (!mapsDir.exists()) {
                    mapsDir.mkdirs()
                }

                // Open all supported map packages that exist on disk
                val fileNames = listOf("world.mbtiles", "china.mbtiles", "liaoning.mbtiles", "dandong.mbtiles")
                for (fileName in fileNames) {
                    val file = java.io.File(mapsDir, fileName)
                    if (file.exists()) {
                        try {
                            val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                            dbsMap[fileName] = database
                            Log.i(TAG, "MultiMapManager: Loaded offline map pack: $fileName")
                        } catch (e: Exception) {
                            Log.e(TAG, "MultiMapManager: Failed loading map: $fileName", e)
                        }
                    }
                }

                // Automatic Discovery: Scan mapsDir for any other *.mbtiles files
                val otherFiles = mapsDir.listFiles { _, name -> name.endsWith(".mbtiles", ignoreCase = true) }
                if (otherFiles != null) {
                    for (file in otherFiles) {
                        if (!dbsMap.containsKey(file.name)) {
                            try {
                                val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                                dbsMap[file.name] = database
                                Log.i(TAG, "MultiMapManager (Auto-Discovered): Loaded: ${file.name}")
                            } catch (e: Exception) {
                                Log.e(TAG, "MultiMapManager: Failed loading auto-discovered map ${file.name}", e)
                            }
                        }
                    }
                }

                // Fallback: if none of the above are loaded, try loading the primary mbtilesPath itself
                if (dbsMap.isEmpty()) {
                    val primaryFile = java.io.File(mbtilesPath)
                    if (primaryFile.exists()) {
                        try {
                            val database = SQLiteDatabase.openDatabase(primaryFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                            dbsMap[primaryFile.name] = database
                            Log.i(TAG, "MultiMapManager: Loaded fallback primary map: ${primaryFile.name}")
                        } catch (e: Exception) {
                            Log.e(TAG, "MultiMapManager: Failed loading primary map: $mbtilesPath", e)
                        }
                    }
                }

                serverSocket = ServerSocket(port)
                serverStarted = true
                Log.d(TAG, "SERVER_START port=$port")
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept()
                        client?.let { handleRequest(it) }
                    } catch (e: Exception) {
                        if (isRunning) Log.e(TAG, "Accept error", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                isRunning = false
                serverStarted = false
            }
        }
    }

    fun getLoadedMaps(): List<String> {
        return dbsMap.keys.toList().sorted()
    }

    private fun handleRequest(client: Socket) {
        thread {
            Log.d(TAG, "MAP_REQUEST_TILE")
            try {
                val input = client.getInputStream().bufferedReader()
                val line = input.readLine() ?: return@thread
                val parts = line.split(" ")
                if (parts.size >= 2 && parts[0] == "GET") {
                    val path = parts[1]
                    
                    // 记录请求诊断信息
                    requestCountTotal++
                    lastRequestPath = path
                    lastRequestTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
                    
                    if (path == "/test") {
                        sendResponse(client.getOutputStream(), 200, "text/plain", "OK".toByteArray())
                    } else {
                        val pathParts = path.trim('/').split('/')
                        if (pathParts.size >= 3) {
                            val z = pathParts[0].toIntOrNull()
                            val x = pathParts[1].toIntOrNull()
                            val yExt = pathParts[2]
                            val y = yExt.split('.')[0].toIntOrNull()

                            if (z != null && x != null && y != null) {
                                Log.d(TAG, "TILE_REQUEST z=$z x=$x y=$y")
                                
                                // 记录特定瓦片请求参数
                                requestCountTile++
                                lastRequestZ = z
                                lastRequestX = x
                                lastRequestY = y
                                
                                onTileRequest?.invoke()
                                // MBTiles format holds tiles in TMS coordinate format
                                // We need to invert the Y coordinate for standard XYZ requests
                                val tmsY = (1 shl z) - 1 - y
                                
                                val tileData = getTile(z, x, tmsY)
                                if (tileData != null) {
                                    sendResponse(client.getOutputStream(), 200, "image/png", tileData)
                                    onTileFound?.invoke()
                                } else {
                                    send404(client.getOutputStream())
                                    onTileNotFound?.invoke()
                                }
                            } else {
                                lastRequestZ = null
                                lastRequestX = null
                                lastRequestY = null
                                send404(client.getOutputStream())
                                onTileNotFound?.invoke()
                            }
                        } else {
                            lastRequestZ = null
                            lastRequestX = null
                            lastRequestY = null
                            send404(client.getOutputStream())
                            onTileNotFound?.invoke()
                        }
                    }
                    
                    // 触发回调更新 HUD
                    onRequestLogged?.invoke()
                }
            } catch (e: Exception) {
            } finally {
                client.close()
            }
        }
    }

    private fun getTile(z: Int, x: Int, y: Int): ByteArray? {
        Log.d(TAG, "SQL_TILE_QUERY z=$z x=$x y=$y")
        
        // Define candidate offline map files to search, prioritised by appropriate zoom level
        val candidates = when (z) {
            in 0..5 -> listOf("world.mbtiles")
            in 6..8 -> listOf("china.mbtiles", "world.mbtiles")
            in 9..11 -> listOf("liaoning.mbtiles", "china.mbtiles", "world.mbtiles")
            else -> listOf("dandong.mbtiles", "liaoning.mbtiles", "china.mbtiles", "world.mbtiles")
        }

        for (fileName in candidates) {
            val database = dbsMap[fileName]
            if (database != null && database.isOpen) {
                var tileData: ByteArray? = null
                try {
                    val cursor = database.rawQuery(
                        "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                        arrayOf(z.toString(), x.toString(), y.toString())
                    )
                    if (cursor.moveToFirst()) {
                        tileData = cursor.getBlob(0)
                        Log.i(TAG, "TILE_FOUND in $fileName (size=${tileData.size}) for z=$z x=$x y=$y")
                        Log.e("MAP_SWITCH", "Loading MBTiles = ${database.path}")
                    }
                    cursor.close()
                    if (tileData != null) {
                        return tileData
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Query error in $fileName for z=$z: ${e.message}")
                }
            }
        }

        // Search in auto-discovered user imported maps if not found in candidates
        for ((fileName, database) in dbsMap) {
            if (!candidates.contains(fileName) && database.isOpen) {
                var tileData: ByteArray? = null
                try {
                    val cursor = database.rawQuery(
                        "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                        arrayOf(z.toString(), x.toString(), y.toString())
                    )
                    if (cursor.moveToFirst()) {
                        tileData = cursor.getBlob(0)
                        Log.i(TAG, "TILE_FOUND in auto-discovered map $fileName (size=${tileData.size}) for z=$z x=$x y=$y")
                        Log.e("MAP_SWITCH", "Loading MBTiles = ${database.path}")
                    }
                    cursor.close()
                    if (tileData != null) {
                        return tileData
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Query error in auto-discovered $fileName: ${e.message}")
                }
            }
        }

        Log.d(TAG, "TILE_NOT_FOUND in any loaded MBTiles database")
        return null
    }

    private fun sendResponse(out: OutputStream, code: Int, contentType: String, body: ByteArray) {
        val header = "HTTP/1.1 $code OK\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun send404(out: OutputStream) {
        val header = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray())
        out.flush()
    }

    fun stop() {
        isRunning = false
        serverStarted = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        
        // Close all mapped database connections safely
        for ((name, database) in dbsMap) {
            try {
                if (database.isOpen) {
                    database.close()
                    Log.i(TAG, "MultiMapManager: Closed database: $name")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed closing database $name", e)
            }
        }
        dbsMap.clear()
    }
}
