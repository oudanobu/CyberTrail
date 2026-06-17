package com.cybertrail.app.offline

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class LocalTileServer(private val mbtilesPath: String) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val TAG = "LocalTileServer"
    private var db: SQLiteDatabase? = null
    val port = 8080

    fun start() {
        if (isRunning) return
        try {
            db = SQLiteDatabase.openDatabase(mbtilesPath, null, SQLiteDatabase.OPEN_READONLY)
            serverSocket = ServerSocket(port)
            isRunning = true
            Log.d(TAG, "SERVER_START port=$port")
            thread {
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept()
                        client?.let { handleRequest(it) }
                    } catch (e: Exception) {
                        if (isRunning) Log.e(TAG, "Accept error", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
        }
    }

    private fun handleRequest(client: Socket) {
        thread {
            Log.d(TAG, "MAP_REQUEST_TILE")
            try {
                val input = client.getInputStream().bufferedReader()
                val line = input.readLine() ?: return@thread
                val parts = line.split(" ")
                if (parts.size >= 2 && parts[0] == "GET") {
                    var path = parts[1]
                    val pathParts = path.trim('/').split('/')
                    if (pathParts.size >= 3) {
                        val z = pathParts[0].toIntOrNull()
                        val x = pathParts[1].toIntOrNull()
                        val yExt = pathParts[2]
                        val y = yExt.split('.')[0].toIntOrNull()

                        if (z != null && x != null && y != null) {
                            Log.d(TAG, "TILE_REQUEST z=$z x=$x y=$y")
                            // MBTiles format holds tiles in TMS coordinate format
                            // We need to invert the Y coordinate for standard XYZ requests
                            val tmsY = (1 shl z) - 1 - y
                            
                            val tileData = getTile(z, x, tmsY)
                            if (tileData != null) {
                                sendResponse(client.getOutputStream(), 200, "image/png", tileData)
                            } else {
                                send404(client.getOutputStream())
                            }
                        } else {
                            send404(client.getOutputStream())
                        }
                    } else {
                        send404(client.getOutputStream())
                    }
                }
            } catch (e: Exception) {
            } finally {
                client.close()
            }
        }
    }

    private fun getTile(z: Int, x: Int, y: Int): ByteArray? {
        Log.d(TAG, "SQL_TILE_QUERY z=$z x=$x y=$y")
        db?.let { database ->
            var tileData: ByteArray? = null
            try {
                val cursor = database.rawQuery(
                    "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                    arrayOf(z.toString(), x.toString(), y.toString())
                )
                if (cursor.moveToFirst()) {
                    tileData = cursor.getBlob(0)
                    Log.d(TAG, "TILE_FOUND size=${tileData.size}")
                } else {
                    Log.d(TAG, "TILE_NOT_FOUND")
                }
                cursor.close()
            } catch (e: Exception) {
                Log.d(TAG, "TILE_NOT_FOUND error=${e.message}")
            }
            return tileData
        }
        Log.d(TAG, "TILE_NOT_FOUND db_null")
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
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        try {
            db?.close()
        } catch (e: Exception) {}
    }
}
