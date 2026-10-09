package com.dragoncity.autobot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Private local demonstration. Each step stores a visual anchor BEFORE a tap. */
data class BattleStep(val file: String, val nx: Double, val ny: Double)
class BattleDemonstration(private val context: Context) {
 private val folder = File(context.filesDir, "battle_demo").apply { mkdirs() }
 private val index = File(folder, "steps.json")
 fun clear() { folder.listFiles()?.forEach { it.delete() } }
 fun load(): List<BattleStep> = try {
  val a = JSONArray(index.readText())
  (0 until a.length()).map { val j = a.getJSONObject(it); BattleStep(j.getString("file"),j.getDouble("nx"),j.getDouble("ny")) }
 } catch (_: Exception) { emptyList() }
 fun add(screen: Bitmap, x: Int, y: Int): Boolean {
  if (load().size >= 12 || screen.width < 120 || screen.height < 120) return false
  val radius = 48
  val left = (x-radius).coerceIn(0,screen.width-radius*2)
  val top = (y-radius).coerceIn(0,screen.height-radius*2)
  val name = "step_${load().size}.png"
  val crop = Bitmap.createBitmap(screen,left,top,radius*2,radius*2)
  try { FileOutputStream(File(folder,name)).use { crop.compress(Bitmap.CompressFormat.PNG,100,it) } }
  finally { crop.recycle() }
  val a = JSONArray()
  (load()+BattleStep(name,x.toDouble()/screen.width,y.toDouble()/screen.height)).forEach {
   a.put(JSONObject().put("file",it.file).put("nx",it.nx).put("ny",it.ny))
  }
  index.writeText(a.toString())
  return true
 }
 fun template(step: BattleStep): Bitmap? = BitmapFactory.decodeFile(File(folder,step.file).absolutePath)
}
