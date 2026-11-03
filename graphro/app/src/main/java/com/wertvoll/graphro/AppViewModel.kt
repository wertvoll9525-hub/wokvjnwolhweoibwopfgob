package com.wertvoll.graphro

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.graphro.core.chart.ChartData
import com.graphro.core.chart.ChartSpec
import com.graphro.core.chart.Recommender
import com.graphro.core.model.ColumnType
import com.graphro.core.model.DataTable
import com.graphro.core.parse.Candidate
import com.graphro.core.parse.Importer
import com.graphro.core.parse.TableBuilder
import com.graphro.core.parse.TableSource
import com.graphro.core.parse.TextDecode
import com.graphro.core.project.Project
import com.wertvoll.graphro.data.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Screen { HOME, PASTE, DATA, EDITOR, PROJECTS, SETTINGS, PRIVACY, LICENSES }

/** A table being reviewed before (or while) charting. */
data class ImportSession(
    val candidates: List<Candidate>,
    val selected: Int,
    val source: TableSource,
    val table: DataTable,
    val rawText: String?,
    val origin: String,
    /** Set when reviewing the data of an existing project from the editor. */
    val editingProjectId: String? = null,
)

data class ProjectItem(val project: Project, val table: DataTable) {
    val data: ChartData by lazy { ChartData.build(table, project.spec) }
}

data class EditorSession(
    val project: Project,
    val table: DataTable,
    val undo: List<ChartSpec> = emptyList(),
    val redo: List<ChartSpec> = emptyList(),
) {
    val spec get() = project.spec
    val data: ChartData by lazy { ChartData.build(table, project.spec) }
}

const val SAMPLE = "월\t매출\t방문자(명)\n1월\t320만\t1,240\n2월\t280만원\t1,105\n3월\t410만\t1,630\n4월\t395\t1,580\n5월\t452만\t1,712\n6월\t4,380,000원\t1,690\n합계\t2,295만\t8,957"

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProjectStore(app)

    var screen by mutableStateOf(Screen.HOME); private set
    private val back = ArrayDeque<Screen>()
    var projects by mutableStateOf<List<ProjectItem>>(emptyList()); private set
    var importSession by mutableStateOf<ImportSession?>(null); private set
    var editor by mutableStateOf<EditorSession?>(null); private set
    var toast by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false); private set
    var clipboardHasText by mutableStateOf(false); private set
    var pasteDraft by mutableStateOf("")

    private var saveJob: Job? = null

    init { refreshProjects() }

    fun go(s: Screen) {
        if (s == screen) return
        back.addLast(screen)
        screen = s
    }

    /** Returns false when there is nowhere to go back to. */
    fun goBack(): Boolean {
        val prev = back.removeLastOrNull() ?: return false
        if (screen == Screen.EDITOR) flushSave()
        screen = prev
        if (screen == Screen.HOME) back.clear()
        return true
    }

    private fun goHomeThen(s: Screen) {
        back.clear()
        if (s == Screen.HOME) { screen = Screen.HOME; return }
        back.addLast(Screen.HOME)
        screen = s
    }

    fun refreshProjects() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                store.list().mapNotNull { p -> runCatching { ProjectItem(p, TableBuilder.build(p.source)) }.getOrNull() }
            }
            projects = list
        }
    }

    fun checkClipboard(ctx: Context) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        // Only the description is read here, so no clipboard-access notice is shown.
        val desc = runCatching { cm.primaryClipDescription }.getOrNull()
        clipboardHasText = desc != null && (desc.hasMimeType("text/*"))
    }

    fun readClipboard(ctx: Context): String? {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = runCatching { cm.primaryClip }.getOrNull() ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(ctx)?.toString()
    }

    // ---------- import ----------

    fun importText(text: String, origin: String) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.Default) { runCatching { Importer.fromText(text) }.getOrNull() }
            busy = false
            if (r == null || r.candidates.isEmpty()) { toast = r?.error ?: "표를 찾지 못했어요"; return@launch }
            startReview(r.candidates, text, origin)
        }
    }

    fun importUri(ctx: Context, uri: Uri) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val size = c.getLong(1)
                            if (size > Importer.MAX_BYTES) return@runCatching Triple(null, "파일이 너무 커요 (30MB까지)", "")
                            c.getString(0)
                        } else null
                    } ?: uri.lastPathSegment ?: "파일"
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            total += n
                            if (total > Importer.MAX_BYTES) return@runCatching Triple(null, "파일이 너무 커요 (30MB까지)", name)
                            out.write(buf, 0, n)
                        }
                        out.toByteArray()
                    } ?: return@runCatching Triple(null, "파일을 열 수 없어요", name)
                    val r = Importer.fromFile(name, bytes)
                    val isText = !(bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte())
                    val raw = if (isText && bytes.size < 2_000_000) TextDecode.decode(bytes) else null
                    Triple(r to raw, r.error, name)
                }.getOrElse { Triple(null, "파일을 읽는 중 문제가 생겼어요", "") }
            }
            busy = false
            val (pair, error, name) = result
            val r = pair?.first
            if (r == null || r.candidates.isEmpty()) { toast = error ?: "표를 찾지 못했어요"; return@launch }
            startReview(r.candidates, pair.second, name)
        }
    }

    private fun startReview(candidates: List<Candidate>, raw: String?, origin: String) {
        val best = candidates.indices.maxByOrNull { candidates[it].score } ?: 0
        val c = candidates[best]
        importSession = ImportSession(candidates, best, c.source, c.table, raw, origin)
        pasteDraft = ""
        goHomeThen(Screen.DATA)
    }

    fun selectCandidate(i: Int) {
        val s = importSession ?: return
        val c = s.candidates.getOrNull(i) ?: return
        importSession = s.copy(selected = i, source = c.source, table = c.table)
    }

    private fun updateSource(f: (TableSource) -> TableSource) {
        val s = importSession ?: return
        val src = f(s.source)
        importSession = s.copy(source = src, table = TableBuilder.build(src))
    }

    fun transpose() = updateSource { it.transposed() }
    fun includeRow(r: Int) = updateSource { it.copy(forceInclude = it.forceInclude + r) }
    fun keepRaw(r: Int, c: Int) = updateSource { it.copy(keepRaw = it.keepRaw + (r to c)) }
    fun editCell(r: Int, c: Int, text: String) = updateSource { it.withCell(r, c, text) }
    fun renameColumn(c: Int, name: String) = updateSource { s ->
        s.copy(header = List(maxOf(s.header.size, c + 1)) { i -> if (i == c) name else s.header.getOrElse(i) { "" } })
    }

    /** Create the chart (or apply the reviewed data back to the project being edited). */
    fun confirmData() {
        val s = importSession ?: return
        if (s.table.numberColumns.isEmpty()) { toast = "숫자 열이 없어서 차트를 그릴 수 없어요"; return }
        val existing = s.editingProjectId?.let { id -> editor?.project?.takeIf { it.id == id } }
        val now = System.currentTimeMillis()
        if (existing != null) {
            val spec = fitSpec(existing.spec, s.table)
            val p = existing.copy(source = s.source, spec = spec, updatedAt = now)
            editor = EditorSession(p, s.table)
            store.save(p)
            importSession = null
            refreshProjects()
            goBack()
            return
        }
        val spec = Recommender.defaultSpec(s.table)
        val p = Project(UUID.randomUUID().toString(), spec.title, now, now, s.source, spec, s.rawText, s.origin)
        store.save(p)
        editor = EditorSession(p, s.table)
        importSession = null
        refreshProjects()
        goHomeThen(Screen.EDITOR)
    }

    /** Keeps a spec valid after the table changed shape. */
    private fun fitSpec(spec: ChartSpec, t: DataTable): ChartSpec {
        val numeric = t.numberColumns.toSet()
        val series = spec.seriesCols.filter { it in numeric }.ifEmpty { Recommender.defaultSeries(t) }
        val catOk = spec.categoryCol in t.columns.indices && t.columns[spec.categoryCol].type != ColumnType.NUMBER
        return spec.copy(seriesCols = series, categoryCol = if (catOk) spec.categoryCol else Recommender.categoryColumn(t))
    }

    fun reviewProjectData() {
        val e = editor ?: return
        val table = TableBuilder.build(e.project.source)
        importSession = ImportSession(
            listOf(Candidate(e.project.origin, e.project.source, table, 1.0)), 0, e.project.source, table,
            e.project.rawText, e.project.origin, editingProjectId = e.project.id,
        )
        go(Screen.DATA)
    }

    fun cancelImport() {
        importSession = null
        goBack().also { if (!it) screen = Screen.HOME }
    }

    // ---------- projects ----------

    fun open(item: ProjectItem) {
        editor = EditorSession(item.project, item.table)
        go(Screen.EDITOR)
    }

    fun delete(item: ProjectItem) {
        store.delete(item.project.id)
        projects = projects.filterNot { it.project.id == item.project.id }
        toast = "삭제했어요"
    }

    fun rename(item: ProjectItem, name: String) {
        val p = item.project.copy(name = name, spec = item.project.spec.copy(title = name), updatedAt = System.currentTimeMillis())
        store.save(p)
        refreshProjects()
    }

    fun duplicate(item: ProjectItem) {
        val now = System.currentTimeMillis()
        val p = item.project.copy(id = UUID.randomUUID().toString(), name = item.project.name + " 사본", spec = item.project.spec.copy(title = item.project.spec.title + " 사본"), createdAt = now, updatedAt = now)
        store.save(p)
        refreshProjects()
        toast = "사본을 만들었어요"
    }

    fun deleteAll() {
        store.deleteAll()
        projects = emptyList()
        editor = null
        toast = "모든 작업을 지웠어요"
    }

    fun sample() = importText(SAMPLE, "예제")

    // ---------- editor ----------

    fun updateSpec(record: Boolean = true, f: (ChartSpec) -> ChartSpec) {
        val e = editor ?: return
        val newSpec = f(e.spec)
        if (newSpec == e.spec) return
        val p = e.project.copy(spec = newSpec, name = newSpec.title.ifBlank { e.project.name }, updatedAt = System.currentTimeMillis())
        editor = if (record) e.copy(project = p, undo = (e.undo + e.spec).takeLast(60), redo = emptyList()) else e.copy(project = p)
        scheduleSave()
    }

    fun undo() {
        val e = editor ?: return
        val prev = e.undo.lastOrNull() ?: return
        editor = e.copy(project = e.project.copy(spec = prev, name = prev.title), undo = e.undo.dropLast(1), redo = e.redo + e.spec)
        scheduleSave()
    }

    fun redo() {
        val e = editor ?: return
        val next = e.redo.lastOrNull() ?: return
        editor = e.copy(project = e.project.copy(spec = next, name = next.title), undo = e.undo + e.spec, redo = e.redo.dropLast(1))
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            val p = editor?.project ?: return@launch
            withContext(Dispatchers.IO) { store.save(p) }
        }
    }

    fun flushSave() {
        saveJob?.cancel()
        val p = editor?.project ?: return
        store.save(p)
        refreshProjects()
    }

    override fun onCleared() { flushSave() }
}
