package com.graphro.core.project

import com.graphro.core.chart.AxisMode
import com.graphro.core.chart.ChartSpec
import com.graphro.core.chart.ChartType
import com.graphro.core.chart.HighlightMode
import com.graphro.core.chart.SortMode
import com.graphro.core.json.Json
import com.graphro.core.parse.TableSource
import com.graphro.core.parse.UnitSpec

/** A saved piece of work: the table source (rebuildable), the chart settings and the original text. */
data class Project(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val source: TableSource,
    val spec: ChartSpec,
    val rawText: String? = null,
    val origin: String = "",
)

object ProjectCodec {
    const val VERSION = 1

    fun encode(p: Project): String = Json.stringify(
        mapOf(
            "v" to VERSION,
            "id" to p.id,
            "name" to p.name,
            "createdAt" to p.createdAt,
            "updatedAt" to p.updatedAt,
            "origin" to p.origin,
            "rawText" to p.rawText,
            "source" to encodeSource(p.source),
            "spec" to encodeSpec(p.spec),
        )
    )

    fun decode(text: String): Project {
        val m = Json.parse(text) as Map<*, *>
        return Project(
            id = m["id"] as String,
            name = m["name"] as? String ?: "차트",
            createdAt = (m["createdAt"] as? Double)?.toLong() ?: 0L,
            updatedAt = (m["updatedAt"] as? Double)?.toLong() ?: 0L,
            source = decodeSource(m["source"] as Map<*, *>),
            spec = decodeSpec(m["spec"] as Map<*, *>),
            rawText = m["rawText"] as? String,
            origin = m["origin"] as? String ?: "",
        )
    }

    fun encodeSource(s: TableSource): Map<String, Any?> = mapOf(
        "title" to s.title,
        "header" to s.header,
        "rows" to s.rows,
        "unitFactor" to s.unitHint?.factor,
        "unit" to s.unitHint?.unit,
        "notes" to s.notes,
        "report" to s.report,
        "forceInclude" to s.forceInclude.toList(),
        "keepRaw" to s.keepRaw.map { listOf(it.first, it.second) },
    )

    @Suppress("UNCHECKED_CAST")
    fun decodeSource(m: Map<*, *>): TableSource {
        val factor = m["unitFactor"] as? Double
        return TableSource(
            title = m["title"] as? String,
            header = (m["header"] as? List<*>)?.map { it?.toString() ?: "" } ?: emptyList(),
            rows = (m["rows"] as? List<*>)?.map { r -> (r as List<*>).map { it?.toString() ?: "" } } ?: emptyList(),
            unitHint = if (factor != null || m["unit"] != null) UnitSpec(factor ?: 1.0, m["unit"] as? String) else null,
            notes = (m["notes"] as? List<*>)?.map { it.toString() } ?: emptyList(),
            report = (m["report"] as? List<*>)?.map { it.toString() } ?: emptyList(),
            forceInclude = (m["forceInclude"] as? List<*>)?.mapNotNull { (it as? Double)?.toInt() }?.toSet() ?: emptySet(),
            keepRaw = (m["keepRaw"] as? List<*>)?.mapNotNull { e -> (e as? List<*>)?.let { ((it[0] as Double).toInt()) to ((it[1] as Double).toInt()) } }?.toSet() ?: emptySet(),
        )
    }

    fun encodeSpec(s: ChartSpec): Map<String, Any?> = mapOf(
        "type" to s.type.name,
        "title" to s.title,
        "categoryCol" to s.categoryCol,
        "seriesCols" to s.seriesCols,
        "highlightMode" to s.highlightMode.name,
        "highlightRow" to s.highlightRow,
        "highlightColor" to s.highlightColor.toLong(),
        "axisMode" to s.axisMode.name,
        "axisMin" to s.axisMin,
        "axisMax" to s.axisMax,
        "showValues" to s.showValues,
        "showCaption" to s.showCaption,
        "customCaption" to s.customCaption,
        "sort" to s.sort.name,
        "target" to s.target,
        "showAverage" to s.showAverage,
        "note" to s.note,
        "source" to s.source,
    )

    fun decodeSpec(m: Map<*, *>): ChartSpec {
        fun int(k: String, d: Int) = (m[k] as? Double)?.toInt() ?: d
        return ChartSpec(
            type = runCatching { ChartType.valueOf(m["type"] as String) }.getOrDefault(ChartType.BAR),
            title = m["title"] as? String ?: "",
            categoryCol = int("categoryCol", 0),
            seriesCols = (m["seriesCols"] as? List<*>)?.mapNotNull { (it as? Double)?.toInt() } ?: emptyList(),
            highlightMode = runCatching { HighlightMode.valueOf(m["highlightMode"] as String) }.getOrDefault(HighlightMode.NONE),
            highlightRow = int("highlightRow", -1),
            highlightColor = (m["highlightColor"] as? Double)?.toLong()?.toInt() ?: com.graphro.core.chart.Colors.SIGNAL,
            axisMode = runCatching { AxisMode.valueOf(m["axisMode"] as String) }.getOrDefault(AxisMode.AUTO),
            axisMin = m["axisMin"] as? Double,
            axisMax = m["axisMax"] as? Double,
            showValues = m["showValues"] as? Boolean ?: false,
            showCaption = m["showCaption"] as? Boolean ?: true,
            customCaption = m["customCaption"] as? String,
            sort = runCatching { SortMode.valueOf(m["sort"] as String) }.getOrDefault(SortMode.NONE),
            target = m["target"] as? Double,
            showAverage = m["showAverage"] as? Boolean ?: false,
            note = m["note"] as? String,
            source = m["source"] as? String,
        )
    }
}
