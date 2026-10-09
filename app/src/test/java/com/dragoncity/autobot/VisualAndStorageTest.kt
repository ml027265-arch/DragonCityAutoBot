package com.dragoncity.autobot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VisualAndStorageTest {
    private lateinit var store: BattleDemonstration
    private lateinit var context: Context
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("bot", Context.MODE_PRIVATE).edit().putString("package", "fixture.game").commit()
        store = BattleDemonstration(context); store.clear()
    }
    private fun solid(color: Int, w: Int=400, h: Int=600) = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    private fun anchor() = Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888).apply {
        val random = Random(4)
        for (y in 0 until height) for (x in 0 until width)
            setPixel(x,y,Color.rgb(random.nextInt(256),random.nextInt(256),random.nextInt(256)))
    }
    @Test fun adaptsTapToShiftedAnchor() {
        val screen = solid(Color.BLACK); val anchor = anchor()
        Canvas(screen).drawBitmap(anchor, 157f, 252f, null)
        val found = ScreenMatcher().find(screen,anchor,TaskType.BATTLE,0.99,200,300)
        assertNotNull(found); assertEquals(205, found!!.x); assertEquals(300,found.y)
    }
    @Test fun preservesEdgeCropTapOffset() {
        val screen=solid(Color.BLACK); val anchor=anchor()
        Canvas(screen).drawBitmap(anchor,5f,200f,null)
        val found=ScreenMatcher().find(screen,anchor,TaskType.BATTLE,0.99,15,248,1.0,0.1,0.5)
        assertNotNull(found); assertEquals(14,found!!.x); assertEquals(248,found.y)
    }
    @Test fun rejectsAmbiguousDuplicateControls() {
        val screen=solid(Color.BLACK); val anchor=anchor()
        Canvas(screen).apply { drawBitmap(anchor,80f,250f,null); drawBitmap(anchor,176f,250f,null) }
        assertNull(ScreenMatcher().find(screen,anchor,TaskType.BATTLE,0.99,176,298))
    }
    @Test fun rejectsUniformTemplates() {
        assertNull(ScreenMatcher().find(solid(Color.WHITE),solid(Color.WHITE,96,96),TaskType.BATTLE))
    }
    @Test fun rejectsMissingTargetAndDistantTarget() {
        val screen=solid(Color.BLACK); val anchor=anchor()
        assertNull(ScreenMatcher().find(screen,anchor,TaskType.BATTLE,0.96,200,300))
        Canvas(screen).drawBitmap(anchor,0f,0f,null)
        assertNull(ScreenMatcher().find(screen,anchor,TaskType.BATTLE,0.96,200,300))
    }
    @Test fun fullScreenContextRejectsDifferentScreen() {
        val a=VisualContext.sample(solid(Color.WHITE)); val b=VisualContext.sample(solid(Color.BLACK))
        assertEquals(1.0,VisualContext.similarity(a,a),0.0001)
        assertEquals(0.0,VisualContext.similarity(a,b),0.0001)
        assertEquals(0.0,VisualContext.similarity(intArrayOf(),a),0.0001)
    }
    @Test fun contextIgnoresStopBand() {
        val a=solid(Color.BLACK); val b=solid(Color.BLACK)
        for (y in 0 until 60) for (x in 0 until b.width) b.setPixel(x,y,Color.WHITE)
        assertEquals(1.0,VisualContext.similarity(VisualContext.sample(a),VisualContext.sample(b)),0.0001)
    }
    @Test fun persistsUnapprovedStepThenExplicitApproval() {
        assertTrue(store.add(solid(Color.WHITE),solid(Color.BLACK),200,300))
        val loaded=BattleDemonstration(context).load()
        assertEquals(1,loaded.size); assertFalse(loaded[0].approved)
        val approved = store.approve(0)
        assertTrue(store.loadError, approved); assertTrue(store.load()[0].approved)
        assertNotNull(store.template(store.load()[0])); assertFalse(store.approve(5))
    }
    @Test fun rejectsTrainingWithoutVisualChange() {
        assertFalse(store.add(solid(Color.BLACK),solid(Color.BLACK),200,300)); assertTrue(store.load().isEmpty())
    }
    @Test fun rejectsTrainingInControlBandAndOrientationChange() {
        assertFalse(store.add(solid(Color.WHITE),solid(Color.BLACK),200,20))
        assertFalse(store.add(solid(Color.WHITE),solid(Color.BLACK,600,400),200,300))
    }
    @Test fun capsDemonstrationAndErasesImages() {
        repeat(12) { assertTrue(store.add(solid(Color.WHITE),solid(Color.BLACK),200,300)) }
        assertEquals(store.loadError, 12, store.load().size)
        assertFalse(store.add(solid(Color.WHITE),solid(Color.BLACK),200,300))
        store.clear(); assertTrue(store.load().isEmpty())
        assertEquals(0, File(context.filesDir,"battle_demo").listFiles()!!.size)
    }
    @Test fun switchingPackageInvalidatesDemonstration() {
        store.add(solid(Color.WHITE),solid(Color.BLACK),200,300)
        context.getSharedPreferences("bot",Context.MODE_PRIVATE).edit().putString("package","other.game").commit()
        assertTrue(store.load().isEmpty())
    }
    @Test fun corruptIndexFailsClosed() {
        File(context.filesDir,"battle_demo/steps.json").writeText("invalid json")
        assertTrue(store.load().isEmpty())
    }
    @Test fun missingTemplateFailsClosed() {
        store.add(solid(Color.WHITE),solid(Color.BLACK),200,300)
        File(context.filesDir,"battle_demo/step_0.png").delete()
        assertTrue(store.load().isEmpty())
    }
    @Test fun legacyIndexCannotReplay() {
        File(context.filesDir,"battle_demo/steps.json").writeText("[{\"file\":\"step_0.png\",\"nx\":0.5,\"ny\":0.5}]")
        assertTrue(store.load().isEmpty())
    }
    @Test fun templatePathTraversalRejected() {
        val dummy = BattleStep("../secret.png",0.5,0.5,400,600,0.5,0.5,IntArray(768),IntArray(768))
        assertNull(store.template(dummy))
    }
    @Test fun generalEngineCannotInitiateBattle() {
        val engine = AutomationEngine(now={5000L}); engine.start()
        assertNull(engine.next(solid(Color.BLACK),mapOf(TaskType.BATTLE to anchor()),AutomationConfig(gold=false,food=false,rewards=false,battles=true)))
        assertEquals(BotState.SEARCHING,engine.state)
    }
    @Test fun invalidEngineConfigurationPauses() {
        val engine=AutomationEngine(now={5000L}); engine.start()
        assertNull(engine.next(solid(Color.BLACK),emptyMap(),AutomationConfig(intervalMs=1)))
        assertEquals(BotState.PAUSED,engine.state)
    }
}
