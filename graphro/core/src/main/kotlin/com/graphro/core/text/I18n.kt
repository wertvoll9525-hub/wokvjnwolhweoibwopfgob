package com.graphro.core.text

enum class Lang { KO, EN, JA;
    companion object {
        fun of(tag: String?): Lang = when (tag?.lowercase()?.take(2)) {
            "ko" -> KO
            "ja" -> JA
            else -> EN
        }
    }
}

/**
 * The language the engine writes its messages in. The app sets it from the system language;
 * tables and captions are rebuilt on change, so a single global setting is enough.
 */
object I18n {
    @Volatile var lang: Lang = Lang.KO

    fun tr(ko: String, en: String, ja: String): String = when (lang) {
        Lang.KO -> ko
        Lang.EN -> en
        Lang.JA -> ja
    }

    /** Default PowerPoint/Excel font that recipients of each language usually have. */
    val officeFont: String get() = tr("맑은 고딕", "Calibri", "Yu Gothic")

    // Generic names the engine gives to columns, in every language, so logic can recognise them.
    val periodNames = setOf("기간", "Period", "期間")
    val itemNames = setOf("항목", "구분", "Item", "項目", "区分")
    fun period() = tr("기간", "Period", "期間")
    fun item() = tr("항목", "Item", "項目")
    fun value() = tr("값", "Value", "値")
    fun valueN(n: Int) = tr("값 $n", "Value $n", "値$n")
    fun columnN(n: Int) = tr("열 $n", "Column $n", "列$n")
    fun isGenericName(s: String) = s in periodNames || s in itemNames ||
        Regex("^(열|값|Column|Value|列|値) ?\\d+$").matches(s) || s == "값" || s == "Value" || s == "値"
}
