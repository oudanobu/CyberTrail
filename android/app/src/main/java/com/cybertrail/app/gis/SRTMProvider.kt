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

        val r0 = rowFloat.toInt().coerceIn(0, size - 2)
        val r1 = r0 + 1
        val c0 = colFloat.toInt().coerceIn(0, size - 2)
        val c1 = c0 + 1

        val weightRow = rowFloat - r0
        val weightCol = colFloat - c0

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val h00 = readShort(raf, r0, c0, size)
                val h01 = readShort(raf, r0, c1, size)
                val h10 = readShort(raf, r1, c0, size)
                val h11 = readShort(raf, r1, c1, size)

                // -32768 is standard SRTM void value
                if (h00 == -32768 || h01 == -32768 || h10 == -32768 || h11 == -32768) {
                    val valid = listOf(h00, h01, h10, h11).filter { it != -32768 }
                    if (valid.isEmpty()) null else valid.average()
                } else {
                    val top = h00 * (1.0 - weightCol) + h01 * weightCol
                    val bottom = h10 * (1.0 - weightCol) + h11 * weightCol
                    top * (1.0 - weightRow) + bottom * weightRow
                }
            }
        } catch (e: Exception) {
            Log.e("SRTMProvider", "Error reading elevation from ${file.name}", e)
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
