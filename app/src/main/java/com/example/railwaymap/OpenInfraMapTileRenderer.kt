package com.example.railwaymap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

object OpenInfraMapTileRenderer {

    enum class GeometryType {
        UNKNOWN, POINT, LINESTRING, POLYGON
    }

    data class Feature(
        val type: GeometryType,
        val paths: List<List<Pair<Float, Float>>>,
        val tags: Map<String, String>
    )

    data class Layer(
        val name: String,
        val extent: Int,
        val keys: List<String>,
        val values: List<String>,
        val features: List<Feature>
    )

    fun decodeAndRender(
        rawBytes: ByteArray,
        layerCategory: String,
        targetSize: Int = 512,
        zoom: Int = 14
    ): Bitmap? {
        if (rawBytes.isEmpty()) return null

        val pbfBytes = if (rawBytes.size >= 2 && rawBytes[0] == 0x1F.toByte() && rawBytes[1] == 0x8B.toByte()) {
            try {
                GZIPInputStream(ByteArrayInputStream(rawBytes)).use { it.readBytes() }
            } catch (_: Exception) {
                rawBytes
            }
        } else {
            rawBytes
        }

        val layers = parsePbfTile(pbfBytes)
        if (layers.isEmpty()) return null

        var hasAnyFeatures = false
        for (layer in layers) {
            if (layer.features.isNotEmpty()) {
                hasAnyFeatures = true
                break
            }
        }
        if (!hasAnyFeatures) return null

        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val zoomFactor = when {
            zoom <= 8 -> 0.45f
            zoom in 9..11 -> 0.75f
            else -> 1.0f
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val pointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = 2.5f * zoomFactor
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val textOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#E0FFFFFF")
            strokeWidth = 5f
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val textFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#111111")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val dashedEffect = DashPathEffect(floatArrayOf(14f * zoomFactor, 8f * zoomFactor), 0f)

        for (layer in layers) {
            val scale = targetSize.toFloat() / layer.extent.toFloat()

            for (feature in layer.features) {
                configurePaintsForFeature(
                    layerCategory,
                    feature.tags,
                    linePaint,
                    pointPaint,
                    fillPaint,
                    textFillPaint,
                    dashedEffect,
                    zoomFactor
                )

                when (feature.type) {
                    GeometryType.LINESTRING -> {
                        for (pathPoints in feature.paths) {
                            if (pathPoints.size < 2) continue
                            val path = Path()
                            val p0 = pathPoints[0]
                            path.moveTo(p0.first * scale, p0.second * scale)
                            for (i in 1 until pathPoints.size) {
                                val pt = pathPoints[i]
                                path.lineTo(pt.first * scale, pt.second * scale)
                            }
                            canvas.drawPath(path, linePaint)
                        }

                        // Render text label ONLY ONCE per feature on its single longest line segment if zoom >= 13
                        if (zoom >= 13) {
                            val label = getLineLabel(feature.tags)
                            if (label != null) {
                                var longestLenSq = 0f
                                var bestMidX = 0f
                                var bestMidY = 0f

                                for (pathPoints in feature.paths) {
                                    for (i in 0 until pathPoints.size - 1) {
                                        val pA = pathPoints[i]
                                        val pB = pathPoints[i + 1]
                                        val dx = (pB.first - pA.first) * scale
                                        val dy = (pB.second - pA.second) * scale
                                        val lenSq = dx * dx + dy * dy
                                        if (lenSq > longestLenSq) {
                                            longestLenSq = lenSq
                                            bestMidX = (pA.first + pB.first) / 2f * scale
                                            bestMidY = (pA.second + pB.second) / 2f * scale
                                        }
                                    }
                                }

                                // Only draw if longest segment is at least 45px long on screen
                                if (longestLenSq >= 2025f) {
                                    canvas.drawText(label, bestMidX, bestMidY, textOutlinePaint)
                                    canvas.drawText(label, bestMidX, bestMidY, textFillPaint)
                                }
                            }
                        }
                    }
                    GeometryType.POLYGON -> {
                        for (pathPoints in feature.paths) {
                            if (pathPoints.size < 3) continue
                            val path = Path()
                            val p0 = pathPoints[0]
                            path.moveTo(p0.first * scale, p0.second * scale)
                            for (i in 1 until pathPoints.size) {
                                val pt = pathPoints[i]
                                path.lineTo(pt.first * scale, pt.second * scale)
                            }
                            path.close()
                            canvas.drawPath(path, fillPaint)
                            canvas.drawPath(path, linePaint)

                            if (zoom >= 12) {
                                val label = feature.tags["name"]
                                if (label != null) {
                                    val cx = pathPoints.map { it.first }.average().toFloat() * scale
                                    val cy = pathPoints.map { it.second }.average().toFloat() * scale
                                    canvas.drawText(label, cx, cy, textOutlinePaint)
                                    canvas.drawText(label, cx, cy, textFillPaint)
                                }
                            }
                        }
                    }
                    GeometryType.POINT -> {
                        val radius = when {
                            zoom <= 8 -> 2.5f
                            zoom in 9..11 -> 4.5f
                            else -> 6.5f
                        }

                        for (pathPoints in feature.paths) {
                            for (pt in pathPoints) {
                                val cx = pt.first * scale
                                val cy = pt.second * scale
                                canvas.drawCircle(cx, cy, radius, pointPaint)
                                canvas.drawCircle(cx, cy, radius, pointStrokePaint)

                                if (zoom >= 12) {
                                    val label = getPointLabel(feature.tags)
                                    if (label != null) {
                                        val lx = cx + radius + 4f
                                        val ly = cy + 4f
                                        canvas.drawText(label, lx, ly, textOutlinePaint)
                                        canvas.drawText(label, lx, ly, textFillPaint)
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        return bitmap
    }

    private fun configurePaintsForFeature(
        category: String,
        tags: Map<String, String>,
        linePaint: Paint,
        pointPaint: Paint,
        fillPaint: Paint,
        textFillPaint: Paint,
        dashedEffect: DashPathEffect,
        zoomFactor: Float
    ) {
        val isUnderground = tags["location"] == "underground" ||
                tags["tunnel"] == "1" ||
                tags["type"] == "cable" ||
                tags["line"] == "cable"
        linePaint.pathEffect = if (isUnderground) dashedEffect else null

        when (category) {
            "power" -> {
                val voltagekV = parseVoltagekV(tags["voltage"])
                val (lineColorStr, baseWidth) = when {
                    voltagekV >= 450f -> Pair("#7B1FA2", 5.5f) // Deep Purple (500kV+)
                    voltagekV >= 300f -> Pair("#D32F2F", 4.5f) // Red (300kV - 400kV)
                    voltagekV >= 200f -> Pair("#F57C00", 4.0f) // Orange (200kV - 290kV)
                    voltagekV >= 100f -> Pair("#FBC02D", 3.5f) // Yellow (100kV - 170kV)
                    voltagekV > 0f    -> Pair("#388E3C", 3.0f) // Green (<100kV)
                    else              -> Pair("#E53935", 3.5f) // Default Red
                }
                val color = Color.parseColor(lineColorStr)
                linePaint.color = color
                linePaint.strokeWidth = baseWidth * zoomFactor
                pointPaint.color = Color.parseColor("#FFB300")
                fillPaint.color = (color and 0x00FFFFFF) or 0x33000000
                textFillPaint.color = color
            }
            "telecoms" -> {
                val color = Color.parseColor("#00ACC1")
                linePaint.color = color
                linePaint.strokeWidth = 3.5f * zoomFactor
                pointPaint.color = Color.parseColor("#00897B")
                fillPaint.color = Color.parseColor("#3300ACC1")
                textFillPaint.color = color
            }
            "petroleum" -> {
                val color = Color.parseColor("#E65100")
                linePaint.color = color
                linePaint.strokeWidth = 4.5f * zoomFactor
                pointPaint.color = Color.parseColor("#6D4C41")
                fillPaint.color = Color.parseColor("#33E65100")
                textFillPaint.color = color
            }
            "water" -> {
                val color = Color.parseColor("#1976D2")
                linePaint.color = color
                linePaint.strokeWidth = 4.0f * zoomFactor
                pointPaint.color = Color.parseColor("#039BE5")
                fillPaint.color = Color.parseColor("#331976D2")
                textFillPaint.color = color
            }
            else -> {
                val color = Color.parseColor("#FF9800")
                linePaint.color = color
                linePaint.strokeWidth = 3.5f * zoomFactor
                pointPaint.color = Color.parseColor("#FFC107")
                fillPaint.color = Color.parseColor("#33FF9800")
                textFillPaint.color = color
            }
        }
    }

    private fun parseVoltagekV(voltageStr: String?): Float {
        if (voltageStr.isNullOrEmpty()) return 0f
        val firstPart = voltageStr.split(";").firstOrNull()?.trim() ?: return 0f
        val rawNum = firstPart.toFloatOrNull() ?: return 0f
        return if (rawNum >= 1000f) rawNum / 1000f else rawNum
    }

    private fun getLineLabel(tags: Map<String, String>): String? {
        val name = tags["name"]
        if (!name.isNullOrEmpty()) return name
        val v = parseVoltagekV(tags["voltage"])
        if (v > 0f) return "${v.toInt()} kV"
        return null
    }

    private fun getPointLabel(tags: Map<String, String>): String? {
        val name = tags["name"]
        if (!name.isNullOrEmpty()) return name
        val v = parseVoltagekV(tags["voltage"])
        if (v > 0f) return "${v.toInt()} kV"
        val sub = tags["substation"]
        if (!sub.isNullOrEmpty()) return sub.replace("_", " ")
        return null
    }

    private fun parsePbfTile(bytes: ByteArray): List<Layer> {
        val layers = mutableListOf<Layer>()
        val stream = ByteArrayInputStream(bytes)

        while (stream.available() > 0) {
            val tag = readVarint(stream)
            if (tag == 0L) break
            val fieldNum = tag ushr 3
            val wireType = (tag and 0x7).toInt()

            if (wireType == 2 && fieldNum == 3L) {
                val length = readVarint(stream).toInt()
                val layerData = readBytes(stream, length)
                val layer = parseLayer(layerData)
                if (layer != null) {
                    layers.add(layer)
                }
            } else {
                skipField(stream, wireType)
            }
        }
        return layers
    }

    private fun parseLayer(data: ByteArray): Layer? {
        val stream = ByteArrayInputStream(data)
        var name = ""
        var extent = 4096
        val keys = mutableListOf<String>()
        val values = mutableListOf<String>()
        val featureBytesList = mutableListOf<ByteArray>()

        while (stream.available() > 0) {
            val tag = readVarint(stream)
            if (tag == 0L) break
            val fieldNum = tag ushr 3
            val wireType = (tag and 0x7).toInt()

            when {
                fieldNum == 1L && wireType == 2 -> {
                    val len = readVarint(stream).toInt()
                    val b = readBytes(stream, len)
                    name = String(b, Charsets.UTF_8)
                }
                fieldNum == 2L && wireType == 2 -> {
                    val len = readVarint(stream).toInt()
                    val b = readBytes(stream, len)
                    featureBytesList.add(b)
                }
                fieldNum == 3L && wireType == 2 -> {
                    val len = readVarint(stream).toInt()
                    val b = readBytes(stream, len)
                    keys.add(String(b, Charsets.UTF_8))
                }
                fieldNum == 4L && wireType == 2 -> {
                    val len = readVarint(stream).toInt()
                    val b = readBytes(stream, len)
                    values.add(parseValue(b))
                }
                fieldNum == 5L && wireType == 0 -> {
                    extent = readVarint(stream).toInt()
                }
                else -> {
                    skipField(stream, wireType)
                }
            }
        }

        val features = mutableListOf<Feature>()
        for (fb in featureBytesList) {
            val feature = parseFeature(fb, keys, values)
            if (feature != null) {
                features.add(feature)
            }
        }

        return if (name.isNotEmpty()) Layer(name, extent, keys, values, features) else null
    }

    private fun parseValue(data: ByteArray): String {
        val stream = ByteArrayInputStream(data)
        while (stream.available() > 0) {
            val tag = readVarint(stream)
            if (tag == 0L) break
            val fieldNum = tag ushr 3
            val wireType = (tag and 0x7).toInt()

            when {
                fieldNum == 1L && wireType == 2 -> {
                    val len = readVarint(stream).toInt()
                    val b = readBytes(stream, len)
                    return String(b, Charsets.UTF_8)
                }
                fieldNum in 2L..6L -> {
                    val v = readVarint(stream)
                    return v.toString()
                }
                fieldNum == 7L && wireType == 0 -> {
                    val b = readVarint(stream)
                    return if (b != 0L) "true" else "false"
                }
                else -> skipField(stream, wireType)
            }
        }
        return ""
    }

    private fun parseFeature(data: ByteArray, keys: List<String>, values: List<String>): Feature? {
        val stream = ByteArrayInputStream(data)
        var geomType = GeometryType.UNKNOWN
        var geometryData: IntArray? = null
        val tagList = mutableListOf<Int>()

        while (stream.available() > 0) {
            val tag = readVarint(stream)
            if (tag == 0L) break
            val fieldNum = tag ushr 3
            val wireType = (tag and 0x7).toInt()

            when {
                fieldNum == 2L && wireType == 2 -> {
                    val len = readVarint(stream).toInt()
                    val packedBytes = readBytes(stream, len)
                    val packedStream = ByteArrayInputStream(packedBytes)
                    while (packedStream.available() > 0) {
                        tagList.add(readVarint(packedStream).toInt())
                    }
                }
                fieldNum == 3L && wireType == 0 -> {
                    val typeVal = readVarint(stream).toInt()
                    geomType = when (typeVal) {
                        1 -> GeometryType.POINT
                        2 -> GeometryType.LINESTRING
                        3 -> GeometryType.POLYGON
                        else -> GeometryType.UNKNOWN
                    }
                }
                fieldNum == 4L && wireType == 2 -> {
                    val len = readVarint(stream).toInt()
                    val packedBytes = readBytes(stream, len)
                    val packedStream = ByteArrayInputStream(packedBytes)
                    val intList = mutableListOf<Int>()
                    while (packedStream.available() > 0) {
                        intList.add(readVarint(packedStream).toInt())
                    }
                    geometryData = intList.toIntArray()
                }
                else -> {
                    skipField(stream, wireType)
                }
            }
        }

        if (geomType == GeometryType.UNKNOWN || geometryData == null) return null

        val tagMap = mutableMapOf<String, String>()
        for (i in 0 until tagList.size - 1 step 2) {
            val kIdx = tagList[i]
            val vIdx = tagList[i + 1]
            if (kIdx in keys.indices && vIdx in values.indices) {
                tagMap[keys[kIdx]] = values[vIdx]
            }
        }

        val paths = decodeGeometry(geometryData)
        return Feature(geomType, paths, tagMap)
    }

    private fun decodeGeometry(geomData: IntArray): List<List<Pair<Float, Float>>> {
        val paths = mutableListOf<List<Pair<Float, Float>>>()
        var currentPath = mutableListOf<Pair<Float, Float>>()
        var idx = 0
        var curX = 0
        var curY = 0

        while (idx < geomData.size) {
            val cmd = geomData[idx++]
            val cmdId = cmd and 0x7
            val cmdCount = cmd ushr 3

            when (cmdId) {
                1 -> { // MoveTo
                    for (i in 0 until cmdCount) {
                        if (currentPath.isNotEmpty()) {
                            paths.add(currentPath)
                            currentPath = mutableListOf()
                        }
                        val dx = zigzagDecode(geomData[idx++])
                        val dy = zigzagDecode(geomData[idx++])
                        curX += dx
                        curY += dy
                        currentPath.add(Pair(curX.toFloat(), curY.toFloat()))
                    }
                }
                2 -> { // LineTo
                    for (i in 0 until cmdCount) {
                        val dx = zigzagDecode(geomData[idx++])
                        val dy = zigzagDecode(geomData[idx++])
                        curX += dx
                        curY += dy
                        currentPath.add(Pair(curX.toFloat(), curY.toFloat()))
                    }
                }
                7 -> { // ClosePath
                    if (currentPath.isNotEmpty()) {
                        currentPath.add(currentPath.first())
                        paths.add(currentPath)
                        currentPath = mutableListOf()
                    }
                }
            }
        }
        if (currentPath.isNotEmpty()) {
            paths.add(currentPath)
        }
        return paths
    }

    private fun zigzagDecode(n: Int): Int {
        return (n ushr 1) xor -(n and 1)
    }

    private fun readVarint(stream: ByteArrayInputStream): Long {
        var res = 0L
        var shift = 0
        while (true) {
            val b = stream.read()
            if (b == -1) break
            res = res or ((b.toLong() and 0x7FL) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
            if (shift >= 64) break
        }
        return res
    }

    private fun readBytes(stream: ByteArrayInputStream, length: Int): ByteArray {
        val b = ByteArray(length)
        var readTotal = 0
        while (readTotal < length) {
            val count = stream.read(b, readTotal, length - readTotal)
            if (count <= 0) break
            readTotal += count
        }
        return b
    }

    private fun skipField(stream: ByteArrayInputStream, wireType: Int) {
        when (wireType) {
            0 -> readVarint(stream)
            1 -> stream.skip(8)
            2 -> {
                val len = readVarint(stream).toInt()
                stream.skip(len.toLong())
            }
            5 -> stream.skip(4)
        }
    }
}
