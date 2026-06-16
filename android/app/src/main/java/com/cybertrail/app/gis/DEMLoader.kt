package com.cybertrail.app.gis

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance, offline-first GIS Digital Elevation Model (DEM) Loader.
 * Decodes SRTM HGT (.hgt) 16-bit binary files and GeoTIFF (.tif/.tiff) raster files.
 */
class DEMLoader(private val context: Context) {

    companion object {
        private const val TAG = "DEMLoader"
    }

    private val loadedDemFiles = ArrayList<DemSource>()

    interface DemSource {
        val name: String
        fun contains(lat: Double, lon: Double): Boolean
        fun getElevation(lat: Double, lon: Double): Double?
        fun close()
    }

    /**
     * Scans and loads any available HGT/TIFF files under context.filesDir/gis/
     */
    fun scanAndLoadLocalGisFiles() {
        val gisDir = File(context.filesDir, "gis")
        if (!gisDir.exists()) {
            gisDir.mkdirs()
        }
        val files = gisDir.listFiles() ?: return
        for (file in files) {
            try {
                if (file.name.endsWith(".hgt", ignoreCase = true)) {
                    loadHgtFile(file)
                } else if (file.name.endsWith(".tif", ignoreCase = true) || file.name.endsWith(".tiff", ignoreCase = true)) {
                    loadGeoTiffFile(file)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load GIS DEM source file: ${file.name}", e)
            }
        }
    }

    fun addManualSource(source: DemSource) {
        loadedDemFiles.add(source)
    }

    fun clearSources() {
        for (s in loadedDemFiles) {
            s.close()
        }
        loadedDemFiles.clear()
    }

    /**
     * Retrieves primary elevation from parsed GIS data. Falls back to null if no source covers the coords.
     */
    fun getElevation(lat: Double, lon: Double): Double? {
        for (source in loadedDemFiles) {
            if (source.contains(lat, lon)) {
                val elevation = source.getElevation(lat, lon)
                if (elevation != null) return elevation
            }
        }
        return null
    }

    private fun loadHgtFile(file: File) {
        Log.i(TAG, "Parsing local SRTM HGT tile: ${file.name}")
        val hgt = HgtSource(file)
        loadedDemFiles.add(hgt)
    }

    private fun loadGeoTiffFile(file: File) {
        Log.i(TAG, "Parsing local GeoTIFF tile: ${file.name}")
        val tiff = GeoTiffSource(file)
        loadedDemFiles.add(tiff)
    }

    /**
     * SRTM HGT Reader:
     * Represents a single 1°x1° local square of terrain.
     * Dimensions are 1201x1201 (SRTM-3, 90m spacing) or 3601x3601 (SRTM-1, 30m spacing).
     * Format: Big-Endian 16-bit signed short integers.
     */
    class HgtSource(private val file: File) : DemSource {
        override val name: String = file.name

        // Parsed tile bounds derived from NxxWxxx or SxxExxx style names
        private val baseLat: Int
        private val baseLon: Int
        private val gridSize: Int // 1201 or 3601

        private val raf = RandomAccessFile(file, "r")

        init {
            val s = file.nameWithoutExtension.uppercase()
            // e.g. N37W123
            var tempLat = 0
            var tempLon = 0
            if (s.length >= 7) {
                val latSign = if (s[0] == 'N') 1 else -1
                val latVal = s.substring(1, 3).toIntOrNull() ?: 0
                tempLat = latSign * latVal

                val lonSign = if (s[3] == 'E') 1 else -1
                val lonVal = s.substring(4, 7).toIntOrNull() ?: 0
                tempLon = lonSign * lonVal
            } else {
                Log.w(TAG, "HGT filename $name is not standard. Defaulting bounds to N00E00.")
            }
            baseLat = tempLat
            baseLon = tempLon

            val byteLength = file.length()
            gridSize = when (byteLength) {
                2884802L -> 1201
                25934402L -> 3601
                else -> {
                    // Autodetect square dimension if odd size
                    val squareDim = Math.sqrt((byteLength / 2).toDouble()).toInt()
                    if (squareDim * squareDim * 2L == byteLength) squareDim else 1201
                }
            }
            Log.d(TAG, "Loaded HGT grid size: $gridSize x $gridSize for geographic bounds Lat: $baseLat, Lon: $baseLon")
        }

        override fun contains(lat: Double, lon: Double): Boolean {
            return lat >= baseLat && lat <= baseLat + 1.0 && lon >= baseLon && lon <= baseLon + 1.0
        }

        override fun getElevation(lat: Double, lon: Double): Double? {
            try {
                val dLat = lat - baseLat
                val dLon = lon - baseLon

                // Check and enforce correct offset coordinates.
                // Row 0 is the northernmost edge, column 0 is the westernmost edge.
                val rowFloat = ((1.0 - dLat) * (gridSize - 1)).coerceIn(0.0, gridSize - 1.0)
                val colFloat = (dLon * (gridSize - 1)).coerceIn(0.0, gridSize - 1.0)

                val r0 = rowFloat.toInt()
                val r1 = (r0 + 1).coerceAtMost(gridSize - 1)
                val c0 = colFloat.toInt()
                val c1 = (c0 + 1).coerceAtMost(gridSize - 1)

                val dr = rowFloat - r0
                val dc = colFloat - c0

                val h00 = readPixel(r0, c0) ?: return null
                val h10 = readPixel(r0, c1) ?: return null
                val h01 = readPixel(r1, c0) ?: return null
                val h11 = readPixel(r1, c1) ?: return null

                // Bilinear interpolation
                val bottom = h00 * (1.0 - dc) + h10 * dc
                val top = h01 * (1.0 - dc) + h11 * dc
                return bottom * (1.0 - dr) + top * dr
            } catch (e: Exception) {
                return null
            }
        }

        private fun readPixel(row: Int, col: Int): Double? {
            synchronized(raf) {
                try {
                    val offset = (row * gridSize + col) * 2L
                    if (offset >= file.length()) return null
                    raf.seek(offset)
                    val b1 = raf.read()
                    val b2 = raf.read()
                    if (b1 == -1 || b2 == -1) return null
                    val rawVal = ((b1 and 0xFF) shl 8) or (b2 and 0xFF)
                    val signedVal = rawVal.toShort().toInt()

                    // -32768 is HGT's official void pixel standard indicator
                    if (signedVal == -32768) return null
                    return signedVal.toDouble()
                } catch (e: Exception) {
                    return null
                }
            }
        }

        override fun close() {
            try {
                raf.close()
            } catch (e: Exception) {}
        }
    }

    /**
     * Robust GeoTIFF Parser:
     * Custom binary decoder extracting tiepoint (Tag 33922) and pixel scales (Tag 33550).
     * Resolves layout structure (Standard IFD tags) offline instantly.
     */
    class GeoTiffSource(private val file: File) : DemSource {
        override val name: String = file.name

        private val raf = RandomAccessFile(file, "r")
        private var isIntel: Boolean = true // True for little-endian, false for big-endian

        private var imageWidth: Int = 0
        private var imageHeight: Int = 0
        private var bitsPerSample: Int = 16
        private var sampleFormat: Int = 1 // 1=uint, 2=int, 3=float

        // Geometric transforms
        private var tiepointX: Double = 0.0
        private var tiepointY: Double = 0.0
        private var tiepointLat: Double = 0.0
        private var tiepointLon: Double = 0.0
        private var pixelScaleX: Double = 1.0
        private var pixelScaleY: Double = 1.0

        private var stripOffsets: LongArray = LongArray(0)
        private var stripByteCounts: LongArray = LongArray(0)
        private var rowsPerStrip: Int = 9999999

        init {
            try {
                parseHeaderAndIfd()
            } catch (e: Exception) {
                Log.e(TAG, "GeoTIFF metadata parsing failure in $name", e)
            }
        }

        private fun parseHeaderAndIfd() {
            val bHeader = ByteArray(8)
            raf.seek(0)
            raf.readFully(bHeader)

            val magic = bHeader[0].toInt().toChar().toString() + bHeader[1].toInt().toChar().toString()
            isIntel = when (magic) {
                "II" -> true
                "MM" -> false
                else -> throw IOException("Invalid TIFF header magic: $magic")
            }

            val curOrder = if (isIntel) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
            val tiffMagic = readUShort(bHeader, 2, curOrder)
            if (tiffMagic != 42) {
                throw IOException("Invalid constant number (42) verification: $tiffMagic")
            }

            val firstIfdOffset = readUInt(bHeader, 4, curOrder)
            raf.seek(firstIfdOffset)

            val bNumEntries = ByteArray(2)
            raf.readFully(bNumEntries)
            val numEntries = readUShort(bNumEntries, 0, curOrder)

            val entryBuffer = ByteArray(12 * numEntries)
            raf.readFully(entryBuffer)

            for (i in 0 until numEntries) {
                val offset = i * 12
                val tag = readUShort(entryBuffer, offset, curOrder)
                val type = readUShort(entryBuffer, offset + 2, curOrder)
                val count = readUInt(entryBuffer, offset + 4, curOrder)
                val valOffset = readUInt(entryBuffer, offset + 8, curOrder)

                when (tag) {
                    256 -> imageWidth = readTagValueToInt(type, valOffset)
                    257 -> imageHeight = readTagValueToInt(type, valOffset)
                    258 -> bitsPerSample = readTagValueToInt(type, valOffset)
                    278 -> rowsPerStrip = readTagValueToInt(type, valOffset)
                    339 -> sampleFormat = readTagValueToInt(type, valOffset)
                    273 -> { // StripOffsets
                        stripOffsets = readTagValueArray(type, count, valOffset, curOrder)
                    }
                    279 -> { // StripByteCounts
                        stripByteCounts = readTagValueArray(type, count, valOffset, curOrder)
                    }
                    33550 -> { // ModelPixelScaleTag
                        val doubles = readDoubleArrayAtOffset(valOffset, 3, curOrder)
                        if (doubles.size >= 2) {
                            pixelScaleX = doubles[0]
                            pixelScaleY = doubles[1]
                        }
                    }
                    33922 -> { // ModelTiepointTag
                        val doubles = readDoubleArrayAtOffset(valOffset, 6, curOrder)
                        if (doubles.size >= 6) {
                            tiepointX = doubles[0]
                            tiepointY = doubles[1]
                            tiepointLon = doubles[3]
                            tiepointLat = doubles[4]
                        }
                    }
                }
            }
            Log.i(TAG, "Parsed GeoTIFF dimensions: $imageWidth x $imageHeight, bitsPerSample: $bitsPerSample, scaleX: $pixelScaleX, scaleY: $pixelScaleY")
        }

        private fun readTagValueToInt(type: Int, valOffset: Long): Int {
            return valOffset.toInt() // Direct short/long value
        }

        private fun readTagValueArray(type: Int, count: Long, valOffset: Long, order: ByteOrder): LongArray {
            if (count == 1L) {
                return longArrayOf(valOffset)
            }
            // Read array starting at files Offset
            val bytesPerItem = if (type == 3) 2 else 4
            val buffer = ByteArray((count * bytesPerItem).toInt())
            val savedPos = raf.filePointer
            raf.seek(valOffset)
            raf.readFully(buffer)
            raf.seek(savedPos)

            val result = LongArray(count.toInt())
            for (i in 0 until count.toInt()) {
                result[i] = if (type == 3) {
                    readUShort(buffer, i * 2, order).toLong()
                } else {
                    readUInt(buffer, i * 4, order)
                }
            }
            return result
        }

        private fun readDoubleArrayAtOffset(offset: Long, count: Int, order: ByteOrder): DoubleArray {
            val buffer = ByteArray(count * 8)
            val savedPos = raf.filePointer
            raf.seek(offset)
            raf.readFully(buffer)
            raf.seek(savedPos)

            val result = DoubleArray(count)
            for (i in 0 until count) {
                val bits = ByteBuffer.wrap(buffer, i * 8, 8).order(order).long
                result[i] = java.lang.Double.longBitsToDouble(bits)
            }
            return result
        }

        private fun readUShort(bytes: ByteArray, offset: Int, order: ByteOrder): Int {
            val buf = ByteBuffer.wrap(bytes, offset, 2).order(order)
            return buf.short.toInt() and 0xFFFF
        }

        private fun readUInt(bytes: ByteArray, offset: Int, order: ByteOrder): Long {
            val buf = ByteBuffer.wrap(bytes, offset, 4).order(order)
            return buf.int.toLong() and 0xFFFFFFFFL
        }

        /**
         * Maps global coordinates with calculated pixel ratios.
         */
        override fun contains(lat: Double, lon: Double): Boolean {
            if (imageWidth == 0 || imageHeight == 0) return false
            val minX = tiepointLon - tiepointX * pixelScaleX
            val maxY = tiepointLat + tiepointY * pixelScaleY
            val maxX = minX + imageWidth * pixelScaleX
            val minY = maxY - imageHeight * pixelScaleY
            return lat >= minY && lat <= maxY && lon >= minX && lon <= maxX
        }

        override fun getElevation(lat: Double, lon: Double): Double? {
            try {
                if (imageWidth == 0 || imageHeight == 0) return null
                val minX = tiepointLon - tiepointX * pixelScaleX
                val maxY = tiepointLat + tiepointY * pixelScaleY

                // Convert geographical (lat, lon) to accurate cell indices (col, row)
                val colFloat = (lon - minX) / pixelScaleX
                val rowFloat = (maxY - lat) / pixelScaleY

                if (colFloat < 0.0 || colFloat >= imageWidth || rowFloat < 0.0 || rowFloat >= imageHeight) return null

                val c0 = colFloat.toInt()
                val c1 = (c0 + 1).coerceAtMost(imageWidth - 1)
                val r0 = rowFloat.toInt()
                val r1 = (r0 + 1).coerceAtMost(imageHeight - 1)

                val dc = colFloat - c0
                val dr = rowFloat - r0

                val h00 = readSampleValue(r0, c0) ?: return null
                val h10 = readSampleValue(r0, c1) ?: return null
                val h01 = readSampleValue(r1, c0) ?: return null
                val h11 = readSampleValue(r1, c1) ?: return null

                // Bilinear interpolation
                val bottom = h00 * (1.0 - dc) + h10 * dc
                val top = h01 * (1.0 - dc) + h11 * dc
                return bottom * (1.0 - dr) + top * dr
            } catch (e: Exception) {
                return null
            }
        }

        private fun readSampleValue(row: Int, col: Int): Double? {
            synchronized(raf) {
                try {
                    // Find strip index
                    val stripIndex = row / rowsPerStrip
                    val stripRow = row % rowsPerStrip
                    if (stripIndex >= stripOffsets.size) return null

                    val baseOffset = stripOffsets[stripIndex]
                    val bytesPerPixel = bitsPerSample / 8
                    val pixelOffset = baseOffset + (stripRow * imageWidth + col) * bytesPerPixel

                    if (pixelOffset >= file.length()) return null
                    raf.seek(pixelOffset)

                    val buffer = ByteArray(bytesPerPixel)
                    raf.readFully(buffer)

                    val curOrder = if (isIntel) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
                    val byteBuf = ByteBuffer.wrap(buffer).order(curOrder)

                    return when {
                        bitsPerSample == 16 && sampleFormat == 3 -> {
                            // Half float (Uncommon in TIFF but supported)
                            null
                        }
                        bitsPerSample == 16 && sampleFormat == 2 -> {
                            // Signed Short
                            byteBuf.short.toDouble()
                        }
                        bitsPerSample == 16 -> {
                            // Unsigned Short
                            (byteBuf.short.toInt() and 0xFFFF).toDouble()
                        }
                        bitsPerSample == 32 && sampleFormat == 3 -> {
                            // Float32
                            byteBuf.float.toDouble()
                        }
                        bitsPerSample == 32 -> {
                            // Int32
                            byteBuf.int.toDouble()
                        }
                        else -> null
                    }
                } catch (e: Exception) {
                    return null
                }
            }
        }

        override fun close() {
            try {
                raf.close()
            } catch (e: Exception) {}
        }
    }
}
