package com.wertvoll.graphro.data

import android.content.Context
import com.graphro.core.project.Project
import com.graphro.core.project.ProjectCodec
import java.io.File

/** Saves projects as small JSON files in the app's private storage. Nothing leaves the device. */
class ProjectStore(context: Context) {
    private val dir = File(context.filesDir, "projects").apply { mkdirs() }

    fun list(): List<Project> = dir.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { f -> runCatching { ProjectCodec.decode(f.readText()) }.getOrNull() }
        ?.sortedByDescending { it.updatedAt }
        ?: emptyList()

    fun save(p: Project) {
        val tmp = File(dir, "${p.id}.json.tmp")
        tmp.writeText(ProjectCodec.encode(p))
        val target = File(dir, "${p.id}.json")
        if (!tmp.renameTo(target)) { target.writeText(tmp.readText()); tmp.delete() }
    }

    fun delete(id: String) { File(dir, "$id.json").delete() }

    fun deleteAll() { dir.listFiles()?.forEach { it.delete() } }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("graphro", Context.MODE_PRIVATE)
    var seenSplashAt: Long
        get() = sp.getLong("splash", 0L)
        set(v) { sp.edit().putLong("splash", v).apply() }
}
