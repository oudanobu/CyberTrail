package com.cybertrail.app.gis

import android.util.Log
import java.io.File
import java.io.RandomAccessFile

class SRTMProvider(private val demDirectory: File) : DEMProvider {

    override fun getElevation(lat: Double, lon: Double): Double? {
        val latFloor = Math.floor(lat).toInt()
        val lonFloor = Math.floor(lon).toInt()

        val latPart = if (latFloor >= 0) {
            "N%02d".format(latFloor)
        } else {
            "S%02d".format(-latFloor)
        }

        val lonPart = if (lonFloor >= 0) {
            "E%03d".format(lonFloor)
        } else {
            "W%03d".format(-lonFloor)
        }

        val expectedName = "${latPart}${lonPart}.hgt"
        val file = findFileCaseInsensitive(demDirectory, expectedName) ?: return null

        val fileLength = file.length()
        val size = when (fileLength) {
            2884802L -> 1201
            25934402L -> 3601
            else -> {
                Log.e("SRTMProvider", "Invalid HGT file size for ${file.name}: $fileLength")
                return null
            }
        }

        val dLat = lat - latFloor
        val dLon = lon - lonFloor

        // Row increases Southwards in HGT, column increases Eastwards
        val rowFloat = (1.0 - dLat) * (size - 1)
        val colFloat = dLon * (size - 1)

        return getElevationByPixel(colFloat, rowFloat, lat, lon)
    }

    fun getElevationByPixel(col: Double, row: Double, lat: Double, lon: Double): Double? {
        val latFloor = Math.floor(lat).toInt()
        val lonFloor = Math.floor(lon).toInt()

        val latPart = if (latFloor >= 0) "N%02d".format(latFloor) else "S%02d".format(-latFloor)
        val lonPart = if (lonFloor >= 0) "E%03d".format(lonFloor) else "W%03d".format(-lonFloor)
        val expectedName = "${latPart}${lonPart}.hgt"
        val file = findFileCaseInsensitive(demDirectory, expectedName) ?: return null

        val fileLength = file.length()
        val size = when (fileLength) {
            2884802L -> 1201
            25934402L -> 3601
            else -> return null
        }

        val r0 = Math.floor(row).toInt()
        val c0 = Math.floor(col).toInt()

        if (r0 < 0 || r0 >= size || c0 < 0 || c0 >= size) {
            return null
        }

        var r1 = r0 + 1
        var c1 = c0 + 1

        if (r1 >= size) r1 = r0
        if (c1 >= size) c1 = c0

        val weightRow = (row - r0).coerceIn(0.0, 1.0)
        val weightCol = (col - c0).coerceIn(0.0, 1.0)

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val h00 = readShort(raf, r0, c0, size)
                val h01 = readShort(raf, r0, c1, size)
                val h10 = readShort(raf, r1, c0, size)
                val h11 = readShort(raf, r1, c1, size)

                val q11 = if (h00 == -32768) null else h00.toDouble()
                val q21 = if (h01 == -32768) null else h01.toDouble()
                val q12 = if (h10 == -32768) null else h10.toDouble()
                val q22 = if (h11 == -32768) null else h11.toDouble()

                val w11 = (1.0 - weightCol) * (1.0 - weightRow)
                val w21 = weightCol * (1.0 - weightRow)
                val w12 = (1.0 - weightCol) * weightRow
                val w22 = weightCol * weightRow

                var sumVal = 0.0
                var sumWeight = 0.0

                if (q11 != null) {
                    sumVal += q11 * w11
                    sumWeight += w11
                }
                if (q21 != null) {
                    sumVal += q21 * w21
                    sumWeight += w21
                }
                if (q12 != null) {
                    sumVal += q12 * w12
                    sumWeight += w12
                }
                if (q22 != null) {
                    sumVal += q22 * w22
                    sumWeight += w22
                }

                if (sumWeight > 0.0) sumVal / sumWeight else null
            }
        } catch (e: Exception) {
            Log.e("SRTMProvider", "Error reading elevation by pixel from ${file.name}", e)
            null
        }
    }

    fun getPixelCoords(lat: Double, lon: Double): Pair<Double, Double>? {
        val latFloor = Math.floor(lat).toInt()
        val lonFloor = Math.floor(lon).toInt()

        val latPart = if (latFloor >= 0) "N%02d".format(latFloor) else "S%02d".format(-latFloor)
        val lonPart = if (lonFloor >= 0) "E%03d".format(lonFloor) else "W%03d".format(-lonFloor)
        val expectedName = "${latPart}${lonPart}.hgt"
        val file = findFileCaseInsensitive(demDirectory, expectedName) ?: return null

        val fileLength = file.length()
        val size = when (fileLength) {
            2884802L -> 1201
            25934402L -> 3601
            else -> return null
        }

        val dLat = lat - latFloor
        val dLon = lon - lonFloor

        val rowFloat = (1.0 - dLat) * (size - 1)
        val colFloat = dLon * (size - 1)
        return Pair(colFloat, rowFloat)
    }

    fun getResolutionMeters(lat: Double, lon: Double): Double? {
        val latFloor = Math.floor(lat).toInt()
        val lonFloor = Math.floor(lon).toInt()

        val latPart = if (latFloor >= 0) "N%02d".format(latFloor) else "S%02d".format(-latFloor)
        val lonPart = if (lonFloor >= 0) "E%03d".format(lonFloor) else "W%03d".format(-lonFloor)
        val expectedName = "${latPart}${lonPart}.hgt"
        val file = findFileCaseInsensitive(demDirectory, expectedName) ?: return null

        val fileLength = file.length()
        val size = when (fileLength) {
            2884802L -> 1201
            25934402L -> 3601
            else -> return null
        }
        return if (size == 3601) 30.0 else 90.0
    }

    fun getElevationByPixel(col: Int, row: Int, lat: Double, lon: Double): Double? {
        val latFloor = Math.floor(lat).toInt()
        val lonFloor = Math.floor(lon).toInt()

        val latPart = if (latFloor >= 0) "N%02d".format(latFloor) else "S%02d".format(-latFloor)
        val lonPart = if (lonFloor >= 0) "E%03d".format(lonFloor) else "W%03d".format(-lonFloor)
        val expectedName = "${latPart}${lonPart}.hgt"
        val file = findFileCaseInsensitive(demDirectory, expectedName) ?: return null

        val fileLength = file.length()
        val size = when (fileLength) {
            2884802L -> 1201
            25934402L -> 3601
            else -> return null
        }

        if (col !in 0 until size || row !in 0 until size) {
            return null
        }

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val h = readShort(raf, row, col, size)
                if (h == -32768) null else h.toDouble()
            }
        } catch (e: Exception) {
            Log.e("SRTMProvider", "Error reading elevation by pixel from ${file.name}", e)
            null
        }
    }

    fun getHgtInfo(lat: Double, lon: Double): Pair<Int, String>? {
        val latFloor = Math.floor(lat).toInt()
        val lonFloor = Math.floor(lon).toInt()

        val latPart = if (latFloor >= 0) "N%02d".format(latFloor) else "S%02d".format(-latFloor)
        val lonPart = if (lonFloor >= 0) "E%03d".format(lonFloor) else "W%03d".format(-lonFloor)
        val expectedName = "${latPart}${lonPart}.hgt"
        val file = findFileCaseInsensitive(demDirectory, expectedName) ?: return null

        val fileLength = file.length()
        val size = when (fileLength) {
            2884802L -> 1201
            25934402L -> 3601
            else -> return null
        }
        return Pair(size, "EPSG:4326")
    }

    private fun findFileCaseInsensitive(dir: File, name: String): File? {
        if (!dir.exists()) return null
        val files = dir.listFiles() ?: return null
        for (f in files) {
            if (f.name.equals(name, ignoreCase = true)) {
                return f
            }
        }
        return null
    }

    private fun readShort(raf: RandomAccessFile, r: Int, c: Int, size: Int): Int {
        val offset = (r * size + c) * 2L
        raf.seek(offset)
        val b1 = raf.read()
        val b2 = raf.read()
        if (b1 == -1 || b2 == -1) return -32768
        return (((b1 and 0xFF) shl 8) or (b2 and 0xFF)).toShort().toInt()
    }
}
