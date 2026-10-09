package com.dragoncity.autobot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Versioned private storage: unreviewed and incomplete steps can never be replayed. */
data class BattleStep(
    val file: String, val nx: Double, val ny: Double,
    val width: Int, val height: Int, val offsetX: Double, val offsetY: Double,
    val before: IntArray, val after: IntArray, val approved: Boolean = false
)
class BattleDemonstration(context: Context) {
    var loadError: String? = null
        private set
    private val folder = File(context.filesDir, "battle_demo").apply { mkdirs() }
    private val index = AtomicDataFile(File(folder, "steps.json"))
    private val prefs = context.getSharedPreferences("bot", Context.MODE_PRIVATE)
    @Synchronized fun clear() { folder.listFiles()?.forEach { it.delete() } }
    @Synchronized fun load(): List<BattleStep> = try {
        loadError = null
        val root = JSONObject(String(index.readFully(), Charsets.UTF_8))
        require(root.getInt("version") == 2)
        require(root.getString("package") == prefs.getString("package", null))
        val a = root.getJSONArray("steps")
        require(a.length() in 0..12)
        (0 until a.length()).map { i ->
            val j = a.getJSONObject(i)
            val file = j.getString("file")
            require(file == "step_$i.png")
            fun pixels(key: String): IntArray {
                val p = j.getJSONArray(key); require(p.length() == 768)
                return IntArray(p.length()) { p.getInt(it) }
            }
            BattleStep(file, j.getDouble("nx"), j.getDouble("ny"),
                j.getInt("width"), j.getInt("height"), j.getDouble("offsetX"), j.getDouble("offsetY"),
                pixels("before"), pixels("after"), j.getBoolean("approved")).also {
                require(SafetyPolicy.validPoint(it.nx, it.ny))
                require(it.width >= 120 && it.height >= 120)
                require(it.offsetX in 0.0..1.0 && it.offsetY in 0.0..1.0)
                require(File(folder, file).isFile)
                require(File(folder, "preview_$i.png").isFile)
            }
        }
    } catch (e: Exception) { loadError = e.toString(); emptyList() }
    private fun save(steps: List<BattleStep>) {
        val a = JSONArray()
        steps.forEach { s ->
            a.put(JSONObject().put("file", s.file).put("nx", s.nx).put("ny", s.ny)
                .put("width", s.width).put("height", s.height)
                .put("offsetX", s.offsetX).put("offsetY", s.offsetY)
                .put("before", JSONArray(s.before.toList())).put("after", JSONArray(s.after.toList()))
                .put("approved", s.approved))
        }
        val data = JSONObject().put("version", 2).put("package", prefs.getString("package", null))
            .put("steps", a).toString().toByteArray(Charsets.UTF_8)
        index.write { it.write(data) }
    }
    @Synchronized fun add(screen: Bitmap, after: Bitmap, x: Int, y: Int): Boolean {
        val steps = load()
        if (steps.size >= 12 || screen.width < 120 || screen.height < 120 ||
            !SafetyPolicy.validPoint(x.toDouble()/screen.width, y.toDouble()/screen.height) ||
            screen.width != after.width || screen.height != after.height) return false
        val beforePixels = VisualContext.sample(screen)
        val afterPixels = VisualContext.sample(after)
        if (VisualContext.similarity(beforePixels, afterPixels) >= 0.985) return false
        val left = (x - 48).coerceIn(0, screen.width - 96)
        val top = (y - 48).coerceIn(0, screen.height - 96)
        val name = "step_${steps.size}.png"
        val image = AtomicDataFile(File(folder, name))
        val crop = Bitmap.createBitmap(screen, left, top, 96, 96)
        try { image.write { check(crop.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { crop.recycle() }
        AtomicDataFile(File(folder, "preview_${steps.size}.png")).write {
            check(screen.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        save(steps + BattleStep(name, x.toDouble()/screen.width, y.toDouble()/screen.height,
            screen.width, screen.height, (x-left)/96.0, (y-top)/96.0, beforePixels, afterPixels))
        return true
    }
    @Synchronized fun approve(position: Int): Boolean {
        val steps = load()
        if (position !in steps.indices) return false
        save(steps.mapIndexed { i, s -> if (i == position) s.copy(approved = true) else s })
        return true
    }
    fun template(step: BattleStep): Bitmap? =
        if (step.file.matches(Regex("step_([0-9]|1[01])\\.png")))
            BitmapFactory.decodeFile(File(folder, step.file).absolutePath) else null
    fun preview(step: BattleStep): Bitmap? =
        if (step.file.matches(Regex("step_([0-9]|1[01])\\.png")))
            BitmapFactory.decodeFile(File(folder, step.file.replace("step_", "preview_")).absolutePath) else null
}
