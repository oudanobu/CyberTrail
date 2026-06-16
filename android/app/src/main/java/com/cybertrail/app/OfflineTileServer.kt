package com.cybertrail.app

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.Executors

class OfflineTileServer(private val mbtilesFile: File) {
    private var server: HttpServer? = null
    val port: Int = 8085

    fun start() {
        Log.i("OfflineTileServer", "Attempting to boot offline GIS server. Path: ${mbtilesFile.absolutePath}")
        try {
            server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0).apply {
                createContext("/style.json", StyleHandler(port))
                createContext("/tile", TileHandler(mbtilesFile))
                executor = Executors.newSingleThreadExecutor()
                start()
            }
            Log.i("OfflineTileServer", "Offline Map Tile server booted on http://127.0.0.1:$port")
        } catch (e: Exception) {
            Log.e("OfflineTileServer", "Failed to start local tile server daemon", e)
        }
    }

    fun stop() {
        try {
            server?.stop(0)
            Log.i("OfflineTileServer", "Offline Map Tile server stopped successfully.")
        } catch (e: Exception) {
            Log.e("OfflineTileServer", "Exception halting tile server", e)
        }
    }

    private class StyleHandler(private val port: Int) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val styleJson = """
                {
                  "version": 8,
                  "name": "CyberTrail Offline Style",
                  "sources": {
                    "offline-radar": {
                      "type": "raster",
                      "tiles": [
                        "http://127.0.0.1:$port/tile/{z}/{x}/{y}"
                      ],
                      "tileSize": 256,
                      "minzoom": 0,
                      "maxzoom": 18
                    }
                  },
                  "layers": [
                    {
                      "id": "background",
                      "type": "background",
                      "paint": {
                        "background-color": "#0D1117"
                      }
                    },
                    {
                      "id": "offline-tiles",
                      "type": "raster",
                      "source": "offline-radar",
                      "paint": {
                        "raster-opacity": 1.0,
                        "raster-fade-duration": 100
                      }
                    }
                  ]
                }
            """.trimIndent()

            val bytes = styleJson.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
            exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private class TileHandler(private val mbtilesFile: File) : HttpHandler {
        private var database: SQLiteDatabase? = null

        @Synchronized
        private fun getDatabase(): SQLiteDatabase? {
            if (database == null && mbtilesFile.exists()) {
                try {
                    database = SQLiteDatabase.openDatabase(
                        mbtilesFile.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                    )
                    Log.i("OfflineTileServer", "Successfully loaded MBTiles SQLite layer database from: ${mbtilesFile.name}")
                } catch (e: Exception) {
                    Log.e("OfflineTileServer", "Error opening offline MBTiles database file direct", e)
                }
            }
            return database
        }

        override fun handle(exchange: HttpExchange) {
            val path = exchange.requestURI.path // e.g. /tile/z/x/y
            val parts = path.split("/").filter { it.isNotEmpty() }
            
            if (parts.size < 4) {
                sendFallbackTile(exchange, 0, 0, 0)
                return
            }

            try {
                val z = parts[1].toInt()
                val x = parts[2].toInt()
                val yRaw = parts[3].substringBefore(".").toInt()

                // Standard TMS inversion:
                // MBTiles is TMS (Y coordinates go bottom-to-top), XYZ goes top-to-bottom.
                val yTms = (1 shl z) - 1 - yRaw

                val db = getDatabase()
                if (db != null) {
                    var tileBytes: ByteArray? = null
                    val cursor = db.rawQuery(
                        "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                        arrayOf(z.toString(), x.toString(), yTms.toString())
                    )
                    if (cursor.moveToFirst()) {
                        tileBytes = cursor.getBlob(0)
                    }
                    cursor.close()

                    if (tileBytes != null && tileBytes.isNotEmpty()) {
                        exchange.responseHeaders.set("Content-Type", "image/png")
                        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
                        exchange.sendResponseHeaders(200, tileBytes.size.toLong())
                        exchange.responseBody.use { it.write(tileBytes) }
                        return
                    }
                }
                
                // If tile not found or database missing, draw programmatical tactical topo-grid
                sendFallbackTile(exchange, z, x, yRaw)

            } catch (e: Exception) {
                Log.e("OfflineTileServer", "Error resolving offline GIS coordinate: $path", e)
                sendFallbackTile(exchange, 0, 0, 0)
            }
        }

        private fun sendFallbackTile(exchange: HttpExchange, z: Int, x: Int, y: Int) {
            val tileBytes = generateGridTile(z, x, y)
            exchange.responseHeaders.set("Content-Type", "image/png")
            exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
            exchange.sendResponseHeaders(200, tileBytes.size.toLong())
            exchange.responseBody.use { it.write(tileBytes) }
        }

        private fun generateGridTile(z: Int, x: Int, y: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Cyberpunk Dark Background
            canvas.drawColor(0xFF0D1117.toInt())

            // Aesthetic cyan border representing tile bounds
            val borderPaint = Paint().apply {
                color = 0x221F6FEB.toInt() // dark tactical blue-gray borders
                strokeWidth = 1.0f
                style = Paint.Style.STROKE
            }
            canvas.drawRect(0f, 0f, 256f, 256f, borderPaint)

            // Dynamic grid laser lines
            val gridPaint = Paint().apply {
                color = 0x1158A6FF.toInt() // semi-opaque HUD cyan
                strokeWidth = 0.5f
            }
            canvas.drawLine(128f, 0f, 128f, 256f, gridPaint)
            canvas.drawLine(0f, 128f, 256f, 128f, gridPaint)

            // Dynamic laser radar circles
            val circlePaint = Paint().apply {
                color = 0x15238636.toInt() // tech cyber green rings
                strokeWidth = 1.0f
                style = Paint.Style.STROKE
            }
            canvas.drawCircle(128f, 128f, 64f, circlePaint)
            canvas.drawCircle(128f, 128f, 120f, circlePaint)

            // Contour laser waves simulating contour topography patterns
            val contourPaint = Paint().apply {
                color = 0x0C238636.toInt() // extremely subtle topographic waves
                strokeWidth = 0.75f
                style = Paint.Style.STROKE
            }
            val frequency = 40.0
            val scaleX = x.toDouble() * 256.0
            val scaleY = y.toDouble() * 256.0
            for (radius in listOf(20f, 45f, 85f, 150f, 200f)) {
                // Introduce slight fractal/noise warping using sin/cos of coordinates to make them look like real topographic contour elevations!
                val warp = Math.sin((scaleX + radius) / 50000.0) * 15.0 + Math.cos((scaleY + radius) / 50000.0) * 15.0
                canvas.drawCircle(128f + warp.toFloat(), 128f + warp.toFloat(), radius, contourPaint)
            }

            // Monospace telemetry labeling texts
            val fontPaint = Paint().apply {
                color = 0x888B949E.toInt() // clean slate gray
                textSize = 8f
                isAntiAlias = true
                typeface = android.graphics.Typeface.MONOSPACE
            }
            canvas.drawText("GRID INDEX [Z:$z, X:$x, Y:$y]", 10f, 24f, fontPaint)
            canvas.drawText("CYBERTRAIL TACTICAL GIS", 10f, 246f, fontPaint)

            // Add directional grid reference markers inside the tile margin
            canvas.drawText("+5m INTERVALS", 170f, 246f, fontPaint)

            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
            val bytes = outputStream.toByteArray()
            bitmap.recycle()
            return bytes
        }
    }
}
