package com.dragoncity.autobot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val layout = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   setPadding(32, 48, 32, 32)
  }
  val title = TextView(this).apply { text = "DragonCity AutoBot"; textSize = 24f }
  val info = TextView(this).apply {
   text = "Versão alfa: seleção de jogo e autorização de acessibilidade. Reconhecimento visual e tarefas automáticas ainda não estão implementados."
   textSize = 16f
  }
  val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  val apps = packageManager.queryIntentActivities(launchIntent, 0)
   .filter { it.activityInfo.packageName != packageName }
   .distinctBy { it.activityInfo.packageName }
   .sortedBy { it.loadLabel(packageManager).toString() }
  val spinner = Spinner(this)
  spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
   apps.map { "${it.loadLabel(packageManager)} (${it.activityInfo.packageName})" })
  val save = Button(this).apply { text = "Selecionar jogo" }
  save.setOnClickListener {
   val i = spinner.selectedItemPosition
   if (i in apps.indices) {
    getSharedPreferences("bot", MODE_PRIVATE).edit()
     .putString("package", apps[i].activityInfo.packageName).apply()
    Toast.makeText(this, "Jogo selecionado", Toast.LENGTH_SHORT).show()
   }
  }
  val permissions = Button(this).apply { text = "Autorizar acessibilidade" }
  permissions.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
  val open = Button(this).apply { text = "Abrir jogo selecionado" }
  open.setOnClickListener {
   val pkg = getSharedPreferences("bot", MODE_PRIVATE).getString("package", null)
   val intent = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
   if (intent != null) startActivity(intent)
   else Toast.makeText(this, "Selecione o jogo primeiro", Toast.LENGTH_SHORT).show()
  }
  layout.addView(title)
  layout.addView(info)
  layout.addView(spinner)
  layout.addView(save)
  layout.addView(permissions)
  layout.addView(open)
  setContentView(ScrollView(this).apply { addView(layout) })
 }
}