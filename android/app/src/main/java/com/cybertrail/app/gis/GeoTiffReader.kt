package com.cybertrail.app.gis

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.io.Closeable

class GeoTiffReader(private val file: File) : Closeable {

    private var raf: RandomAccessFile? = null
    private var isLittleEndian = true

    var imageWidth: Int = 0
        private set
    var imageHeight: Int = 0
        private set
    private var bitsPerSample: Int = 16
    private var sampleFormat: Int = 2 // default to signed int (two's complement)
    private var compression: Int = 1 // default to no compression

    private var rowsPerStrip: Int = 0
    private var stripOffsets = LongArray(0)
    private var stripByteCounts = LongArray(0)

    private var tileWidth: Int = 0
    private var tileLength: Int = 0
    private var tileOffsets = LongArray(0)
    private var tileByteCounts = LongArray(0)

    private var scaleX: Double = 0.0
    private var scaleY: Double = 0.0
    private var tiepointX: Double = 0.0
    private var tiepointY: Double = 0.0

    private var initialized = false

    companion object {
        private const val TAG = "GeoTiffReader"
    }

    init {
        try {
            raf = RandomAccessFile(file, "r")
            parseHeaderAndIfd()
            initialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GeoTIFF reader for ${file.name}", e)
            close()
        }
    }

    @Synchronized
    fun getElevation(lat: Double, lon: Double): Double? {
        if (!initialized || raf == null) return null

        val (px, py) = getPixelCoords(lat, lon)
        val col = px.toInt()
        val row = py.toInt()

        if (col !in 0 until imageWidth || row !in 0 until imageHeight) {
            return null
        }

        return try {
            val rafInstance = raf ?: return null
            val offset = getPixelFileOffset(col, row) ?: return null
            rafInstance.seek(offset)

            val rawVal = when (bitsPerSample) {
                16 -> {
                    val b1 = rafInstance.read()
                    val b2 = rafInstance.read()
                    if (b1 == -1 || b2 == -1) return null
                    val uVal = if (isLittleEndian) {
                        b1 or (b2 shl 8)
                    } else {
                        (b1 shl 8) or b2
                    }
                    if (sampleFormat == 2) {
                        uVal.toShort().toDouble()
                    } else {
                        uVal.toDouble()
                    }
                }
                32 -> {
                    val b1 = rafInstance.read()
                    val b2 = rafInstance.read()
                    val b3 = rafInstance.read()
                    val b4 = rafInstance.read()
                    if (b1 == -1 || b2 == -1 || b3 == -1 || b4 == -1) return null
                    val bits = if (isLittleEndian) {
                        b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
                    } else {
                        (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
                    }
                    if (sampleFormat == 3) {
                        java.lang.Float.intBitsToFloat(bits).toDouble()
                    } else {
                        bits.toDouble()
                    }
                }
                else -> {
                    Log.w(TAG, "Unsupported BitsPerSample: $bitsPerSample")
                    null
                }
            }

            // Filter out common no-data values in DEMs (like -32768, -9999, etc.)
            if (rawVal != null && rawVal > -9000.0 && rawVal < 9000.0) {
                rawVal
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking/reading elevation at ($col, $row)", e)
            null
        }
    }

    private fun getPixelCoords(lat: Double, lon: Double): Pair<Double, Double> {
        // EPSG:4326 Geographic coordinates
        if (tiepointX in -180.1..180.1 && tiepointY in -90.1..90.1) {
            val px = (lon - tiepointX) / scaleX
            val py = (tiepointY - lat) / scaleY
            return Pair(px, py)
        }

        // Web Mercator EPSG:3857 coordinates
        if (Math.abs(tiepointX) > 2000000.0 && Math.abs(tiepointX) < 21000000.0) {
            val mx = lon * 20037508.34 / 180.0
            var my = Math.log(Math.tan((90.0 + lat) * Math.PI / 360.0)) / (Math.PI / 180.0)
            my = my * 20037508.34 / 180.0
            val px = (mx - tiepointX) / scaleX
            val py = (tiepointY - my) / scaleY
            return Pair(px, py)
        }

        // UTM Projection fallback
        val (easting, northing) = latLonToUtm(lat, lon)
        val px = (easting - tiepointX) / scaleX
        val py = (tiepointY - northing) / scaleY
        return Pair(px, py)
    }

    private fun latLonToUtm(lat: Double, lon: Double): Pair<Double, Double> {
        val a = 6378137.0
        val eccSquared = 0.00669437999013
        val k0 = 0.9996

        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)

        val zone = (((lon + 180.0) / 6.0).toInt() + 1).coerceIn(1, 60)
        val lonOrigin = ((zone - 1) * 6 - 180 + 3).toDouble()
        val lonOriginRad = Math.toRadians(lonOrigin)

        val eccPrimeSquared = eccSquared / (1.0 - eccSquared)

        val N = a / Math.sqrt(1.0 - eccSquared * Math.sin(latRad) * Math.sin(latRad))
        val T = Math.tan(latRad) * Math.tan(latRad)
        val C = eccPrimeSquared * Math.cos(latRad) * Math.cos(latRad)
        val A = Math.cos(latRad) * (lonRad - lonOriginRad)

        val M = a * ((1.0 - eccSquared / 4.0 - 3.0 * eccSquared * eccSquared / 64.0 - 5.0 * eccSquared * eccSquared * eccSquared / 256.0) * latRad
                - (3.0 * eccSquared / 8.0 + 3.0 * eccSquared * eccSquared / 32.0 + 45.0 * eccSquared * eccSquared * eccSquared / 1024.0) * Math.sin(2.0 * latRad)
                + (15.0 * eccSquared * eccSquared / 256.0 + 45.0 * eccSquared * eccSquared * eccSquared / 1024.0) * Math.sin(4.0 * latRad)
                - (35.0 * eccSquared * eccSquared * eccSquared / 3072.0) * Math.sin(6.0 * latRad))

        val easting = k0 * N * (A + (1.0 - T + C) * A * A * A / 6.0
                + (5.0 - 18.0 * T + T * T + 72.0 * C - 58.0 * eccPrimeSquared) * A * A * A * A * A / 120.0) + 500000.0

        var northing = k0 * (M + N * Math.tan(latRad) * (A * A / 2.0 + (5.0 - T + 9.0 * C + 4.0 * C * C) * A * A * A * A / 24.0
                + (61.0 - 58.0 * T + T * T + 600.0 * C - 330.0 * eccPrimeSquared) * A * A * A * A * A * A / 720.0))
        if (lat < 0.0) {
            northing += 10000000.0
        }

        return Pair(easting, northing)
    }

    private fun getPixelFileOffset(col: Int, row: Int): Long? {
        val bytesPerPixel = bitsPerSample / 8
        if (compression != 1) {
            Log.w(TAG, "Compressed GeoTIFF is not supported for O(1) random-access queries")
            return null
        }

        if (tileOffsets.isNotEmpty()) {
            val tileCols = (imageWidth + tileWidth - 1) / tileWidth
            val tileCol = col / tileWidth
            val tileRow = row / tileLength
            val tileIdx = tileRow * tileCols + tileCol

            if (tileIdx in tileOffsets.indices) {
                val tileX = col % tileWidth
                val tileY = row % tileLength
                val pixelOffsetInTile = (tileY * tileWidth + tileX) * bytesPerPixel
                return tileOffsets[tileIdx] + pixelOffsetInTile
            }
        } else if (stripOffsets.isNotEmpty()) {
            val actualRowsPerStrip = if (rowsPerStrip <= 0) imageHeight else rowsPerStrip
            val stripIdx = row / actualRowsPerStrip
            val rowInStrip = row % actualRowsPerStrip

            if (stripIdx in stripOffsets.indices) {
                val pixelOffsetInStrip = (rowInStrip * imageWidth + col) * bytesPerPixel
                return stripOffsets[stripIdx] + pixelOffsetInStrip
            }
        }
        return null
    }

    private fun parseHeaderAndIfd() {
        val rafInstance = raf ?: return

        // 1. Read byte order (2 bytes)
        rafInstance.seek(0)
        val bo1 = rafInstance.read()
        val bo2 = rafInstance.read()
        if (bo1 == 'I'.code && bo2 == 'I'.code) {
            isLittleEndian = true
        } else if (bo1 == 'M'.code && bo2 == 'M'.code) {
            isLittleEndian = false
        } else {
            throw IllegalArgumentException("Invalid TIFF byte order")
        }

        // 2. Read magic number (2 bytes)
        val magic = readUShort(rafInstance, isLittleEndian)
        if (magic != 42) {
            throw IllegalArgumentException("Invalid TIFF magic number: $magic")
        }

        // 3. Read first IFD offset (4 bytes)
        val firstIfdOffset = readULong(rafInstance, isLittleEndian)
        if (firstIfdOffset <= 0) {
            throw IllegalArgumentException("Invalid first IFD offset")
        }

        // 4. Parse IFD entries
        rafInstance.seek(firstIfdOffset)
        val entryCount = readUShort(rafInstance, isLittleEndian)

        for (i in 0 until entryCount) {
            val entryOffset = firstIfdOffset + 2 + (i * 12)
            rafInstance.seek(entryOffset)
            val tag = readUShort(rafInstance, isLittleEndian)

            when (tag) {
                256 -> imageWidth = getSingleTagValue(rafInstance, entryOffset).toInt()
                257 -> imageHeight = getSingleTagValue(rafInstance, entryOffset).toInt()
                258 -> bitsPerSample = getSingleTagValue(rafInstance, entryOffset).toInt()
                259 -> compression = getSingleTagValue(rafInstance, entryOffset).toInt()
                273 -> stripOffsets = getTagValuesAsLongArray(rafInstance, entryOffset)
                278 -> rowsPerStrip = getSingleTagValue(rafInstance, entryOffset).toInt()
                279 -> stripByteCounts = getTagValuesAsLongArray(rafInstance, entryOffset)
                322 -> tileWidth = getSingleTagValue(rafInstance, entryOffset).toInt()
                323 -> tileLength = getSingleTagValue(rafInstance, entryOffset).toInt()
                324 -> tileOffsets = getTagValuesAsLongArray(rafInstance, entryOffset)
                325 -> tileByteCounts = getTagValuesAsLongArray(rafInstance, entryOffset)
                339 -> sampleFormat = getSingleTagValue(rafInstance, entryOffset).toInt()
                33550 -> {
                    val doubles = getTagValuesAsDoubleArray(rafInstance, entryOffset)
                    if (doubles.size >= 2) {
                        scaleX = doubles[0]
                        scaleY = doubles[1]
                    }
                }
                33922 -> {
                    val doubles = getTagValuesAsDoubleArray(rafInstance, entryOffset)
                    if (doubles.size >= 6) {
                        // tiepoint: i, j, k, x, y, z. Usually maps pixel (0,0,0) to (lon, lat, height)
                        tiepointX = doubles[3]
                        tiepointY = doubles[4]
                    }
                }
            }
        }

        Log.i(TAG, "Parsed GeoTIFF: Width=$imageWidth, Height=$imageHeight, Bits=$bitsPerSample, SampleFormat=$sampleFormat, Compression=$compression")
        Log.i(TAG, "GeoTIFF Geo-Bounds: ScaleX=$scaleX, ScaleY=$scaleY, TiepointX=$tiepointX, TiepointY=$tiepointY")
    }

    private fun getSingleTagValue(raf: RandomAccessFile, entryOffset: Long): Long {
        raf.seek(entryOffset)
        val tag = readUShort(raf, isLittleEndian)
        val type = readUShort(raf, isLittleEndian)
        val count = readULong(raf, isLittleEndian)

        if (count != 1L) {
            // Read first element only
        }

        raf.seek(entryOffset + 8)
        return when (type) {
            1, 6 -> raf.read().toLong() and 0xFF // BYTE / SBYTE
            3, 8 -> readUShort(raf, isLittleEndian).toLong() // SHORT / SSHORT
            4, 9 -> readULong(raf, isLittleEndian) // LONG / SLONG
            else -> readULong(raf, isLittleEndian)
        }
    }

    private fun getTagValuesAsLongArray(raf: RandomAccessFile, entryOffset: Long): LongArray {
        raf.seek(entryOffset)
        val tag = readUShort(raf, isLittleEndian)
        val type = readUShort(raf, isLittleEndian)
        val count = readULong(raf, isLittleEndian).toInt()

        val typeSize = when (type) {
            3, 8 -> 2
            4, 9 -> 4
            else -> 4
        }

        val totalSize = count * typeSize
        val dataBytes = ByteArray(totalSize)

        if (totalSize <= 4) {
            raf.seek(entryOffset + 8)
            raf.readFully(dataBytes)
        } else {
            raf.seek(entryOffset + 8)
            val valueOffset = readULong(raf, isLittleEndian)
            raf.seek(valueOffset)
            raf.readFully(dataBytes)
        }

        val result = LongArray(count)
        for (i in 0 until count) {
            val off = i * typeSize
            result[i] = when (typeSize) {
                2 -> readUShort(dataBytes, off, isLittleEndian).toLong()
                4 -> readULong(dataBytes, off, isLittleEndian)
                else -> readULong(dataBytes, off, isLittleEndian)
            }
        }
        return result
    }

    private fun getTagValuesAsDoubleArray(raf: RandomAccessFile, entryOffset: Long): DoubleArray {
        raf.seek(entryOffset)
        val tag = readUShort(raf, isLittleEndian)
        val type = readUShort(raf, isLittleEndian)
        val count = readULong(raf, isLittleEndian).toInt()

        val typeSize = when (type) {
            11 -> 4 // FLOAT
            12 -> 8 // DOUBLE
            else -> 8
        }

        val totalSize = count * typeSize
        val dataBytes = ByteArray(totalSize)

        if (totalSize <= 4) {
            raf.seek(entryOffset + 8)
            raf.readFully(dataBytes)
        } else {
            raf.seek(entryOffset + 8)
            val valueOffset = readULong(raf, isLittleEndian)
            raf.seek(valueOffset)
            raf.readFully(dataBytes)
        }

        val result = DoubleArray(count)
        for (i in 0 until count) {
            val off = i * typeSize
            result[i] = if (typeSize == 4) {
                readFloat(dataBytes, off, isLittleEndian).toDouble()
            } else {
                readDouble(dataBytes, off, isLittleEndian)
            }
        }
        return result
    }

    private fun readUShort(raf: RandomAccessFile, littleEndian: Boolean): Int {
        val b1 = raf.read()
        val b2 = raf.read()
        if (b1 == -1 || b2 == -1) throw java.io.EOFException()
        return if (littleEndian) {
            b1 or (b2 shl 8)
        } else {
            (b1 shl 8) or b2
        }
    }

    private fun readULong(raf: RandomAccessFile, littleEndian: Boolean): Long {
        val b1 = raf.read().toLong()
        val b2 = raf.read().toLong()
        val b3 = raf.read().toLong()
        val b4 = raf.read().toLong()
        if (b1 == -1L || b2 == -1L || b3 == -1L || b4 == -1L) throw java.io.EOFException()
        return if (littleEndian) {
            b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
        } else {
            (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
        }
    }

    private fun readUShort(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int {
        val b1 = bytes[offset].toInt() and 0xFF
        val b2 = bytes[offset + 1].toInt() and 0xFF
        return if (littleEndian) {
            b1 or (b2 shl 8)
        } else {
            (b1 shl 8) or b2
        }
    }

    private fun readULong(bytes: ByteArray, offset: Int, littleEndian: Boolean): Long {
        val b1 = bytes[offset].toLong() and 0xFF
        val b2 = bytes[offset + 1].toLong() and 0xFF
        val b3 = bytes[offset + 2].toLong() and 0xFF
        val b4 = bytes[offset + 3].toLong() and 0xFF
        return if (littleEndian) {
            b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
        } else {
            (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
        }
    }

    private fun readFloat(bytes: ByteArray, offset: Int, littleEndian: Boolean): Float {
        val b1 = bytes[offset].toInt() and 0xFF
        val b2 = bytes[offset + 1].toInt() and 0xFF
        val b3 = bytes[offset + 2].toInt() and 0xFF
        val b4 = bytes[offset + 3].toInt() and 0xFF
        val bits = if (littleEndian) {
            b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
        } else {
            (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
        }
        return java.lang.Float.intBitsToFloat(bits)
    }

    private fun readDouble(bytes: ByteArray, offset: Int, littleEndian: Boolean): Double {
        val b1 = bytes[offset].toLong() and 0xFF
        val b2 = bytes[offset + 1].toLong() and 0xFF
        val b3 = bytes[offset + 2].toLong() and 0xFF
        val b4 = bytes[offset + 3].toLong() and 0xFF
        val b5 = bytes[offset + 4].toLong() and 0xFF
        val b6 = bytes[offset + 5].toLong() and 0xFF
        val b7 = bytes[offset + 6].toLong() and 0xFF
        val b8 = bytes[offset + 7].toLong() and 0xFF
        val bits = if (littleEndian) {
            b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24) or
                    (b5 shl 32) or (b6 shl 40) or (b7 shl 48) or (b8 shl 56)
        } else {
            (b1 shl 56) or (b2 shl 48) or (b3 shl 40) or (b4 shl 32) or
                    (b5 shl 24) or (b6 shl 16) or (b7 shl 8) or b8
        }
        return java.lang.Double.longBitsToDouble(bits)
    }

    @Synchronized
    override fun close() {
        try {
            raf?.close()
        } catch (e: Exception) {
            // ignored
        } finally {
            raf = null
        }
    }
}
