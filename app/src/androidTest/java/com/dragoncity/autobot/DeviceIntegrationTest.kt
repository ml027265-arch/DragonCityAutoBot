package com.dragoncity.autobot

import android.app.UiAutomation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DeviceIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun shell(command: String): String = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        .executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() }
    private fun waitFor(message: String, seconds: Int=15, condition: () -> Boolean) {
        val end=System.currentTimeMillis()+seconds*1000
        while (System.currentTimeMillis()<end) { if (condition()) return; Thread.sleep(100) }
        assertTrue(message + "; service status=" + context.getSharedPreferences("bot",Context.MODE_PRIVATE).getString("status",""),condition())
    }
    private fun main(block: () -> Unit) { instrumentation.runOnMainSync(block) }
    private fun <T> withService(block: (BotAccessibilityService) -> T): T {
        // Automatic permission setup is restricted to disposable Android SDK emulators.
        require(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val previous = shell("settings get secure enabled_accessibility_services").trim()
        val enabled = shell("settings get secure accessibility_enabled").trim()
        shell("settings put secure enabled_accessibility_services ${context.packageName}/.BotAccessibilityService")
        shell("settings put secure accessibility_enabled 1")
        try {
            waitFor("Accessibility service did not connect") { BotAccessibilityService.active!=null }
            return block(BotAccessibilityService.active!!)
        } finally {
            main { BotAccessibilityService.active?.stopAll() }
            if (previous=="null" || previous.isEmpty()) shell("settings delete secure enabled_accessibility_services")
            else shell("settings put secure enabled_accessibility_services $previous")
            if (enabled=="null") shell("settings delete secure accessibility_enabled")
            else shell("settings put secure accessibility_enabled $enabled")
            if (!previous.contains(context.packageName)) waitFor("Service did not disconnect") { BotAccessibilityService.active==null }
        }
    }
    @Test fun launcherOpensWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }
    @Test fun bundledOcrDetectsPurchaseAndAcceptsAttack() {
        val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            fun text(value: String): String {
                val bitmap=Bitmap.createBitmap(900,200,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                Canvas(bitmap).drawText(value,30f,120f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK; textSize=80f })
                return try { Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0)),15,TimeUnit.SECONDS).text }
                finally { bitmap.recycle() }
            }
            assertTrue(SafetyPolicy.spendingRisk(text("BUY GEMS $5")))
            val attack=text("ATTACK"); assertTrue(attack.contains("ATTACK")); assertFalse(SafetyPolicy.spendingRisk(attack))
        } finally { recognizer.close() }
    }
    @Test fun teachesCapturesPersistsAndAdaptivelyReplaysTwoActions() {
        withService { service ->
            val store=BattleDemonstration(context)
            context.getSharedPreferences("bot",Context.MODE_PRIVATE).edit().putString("package",context.packageName).commit()
            ActivityScenario.launch(BattleFixtureActivity::class.java).use { scenario ->
                lateinit var fixture: BattleFixtureActivity
                scenario.onActivity { fixture=it }
                instrumentation.waitForIdleSync(); Thread.sleep(600)
                var point=intArrayOf()
                main { point=fixture.target(); service.beginTraining() }
                Thread.sleep(500); shell("input tap ${point[0]} ${point[1]}")
                waitFor("No verified demonstration saved",25) { store.load().size==1 }
                assertEquals(1,fixture.actionCount); assertFalse(store.load()[0].approved)
                Thread.sleep(400); shell("input tap ${point[0]} ${point[1]}")
                waitFor("Second demonstration step missing",25) { store.load().size==2 }
                assertEquals(2,fixture.actionCount)
                main { service.stopAll(); fixture.reset() }
                assertTrue(store.approve(0)); assertTrue(store.approve(1))
                main { fixture.targetShift=8; fixture.surface.invalidate() }
                Thread.sleep(600)
                main { service.beginReplay() }
                waitFor("Replay did not tap both synthetic controls",30) { fixture.actionCount==2 }
                waitFor("Replay did not complete verification",15) {
                    context.getSharedPreferences("bot",Context.MODE_PRIVATE).getString("status","")!!.startsWith("Sequência verificada")
                }
                assertEquals(2,fixture.actionCount)
                assertEquals(fixture.surface.width/2+8, fixture.lastTouchX)
            }
            store.clear()
        }
    }
    @Test fun purchaseScreenPreventsTrainingGesture() {
        withService { service ->
            context.getSharedPreferences("bot",Context.MODE_PRIVATE).edit().putString("package",context.packageName).commit()
            val store=BattleDemonstration(context)
            ActivityScenario.launch(BattleFixtureActivity::class.java).use { scenario ->
                lateinit var fixture: BattleFixtureActivity
                scenario.onActivity { fixture=it; it.showPurchase=true; it.surface.invalidate() }
                Thread.sleep(600)
                var point=intArrayOf(); main { point=fixture.target(); service.beginTraining() }
                Thread.sleep(500); shell("input tap ${point[0]} ${point[1]}")
                waitFor("Purchase guard did not stop") {
                    context.getSharedPreferences("bot",Context.MODE_PRIVATE).getString("status","")!!.contains("compra/gasto")
                }
                assertEquals(0,fixture.actionCount); assertTrue(store.load().isEmpty())
            }
            store.clear()
        }
    }
    @Test fun unknownScreenPreventsReplayGesture() {
        withService { service ->
            context.getSharedPreferences("bot",Context.MODE_PRIVATE).edit().putString("package",context.packageName).commit()
            val store=BattleDemonstration(context); store.clear()
            val before=Bitmap.createBitmap(400,600,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            val after=Bitmap.createBitmap(400,600,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
            assertTrue(store.add(before,after,200,300)); before.recycle(); after.recycle()
            assertTrue(store.approve(0))
            ActivityScenario.launch(BattleFixtureActivity::class.java).use { scenario ->
                lateinit var fixture: BattleFixtureActivity; scenario.onActivity { fixture=it }
                Thread.sleep(600); main { service.beginReplay() }
                waitFor("Unknown screen did not halt replay") {
                    context.getSharedPreferences("bot",Context.MODE_PRIVATE).getString("status","")!!.contains("Tela desconhecida")
                }
                assertEquals(0,fixture.actionCount)
            }
            store.clear()
        }
    }
}
