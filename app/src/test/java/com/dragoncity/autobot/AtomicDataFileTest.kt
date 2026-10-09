package com.dragoncity.autobot

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AtomicDataFileTest {
    @Test fun replacesExistingFileAndReadsNewestData() {
        val folder=Files.createTempDirectory("dragonbot-atomic").toFile()
        try {
            val file=java.io.File(folder,"steps.json"); val atomic=AtomicDataFile(file)
            atomic.write { it.write("old".toByteArray()) }
            atomic.write { it.write("new".toByteArray()) }
            assertEquals("new",String(atomic.readFully()))
            assertFalse(java.io.File(folder,"steps.json.new").exists())
        } finally { folder.listFiles()?.forEach { it.delete() }; folder.delete() }
    }
    @Test fun failedWritePreservesLastCompleteFile() {
        val folder=Files.createTempDirectory("dragonbot-atomic").toFile()
        try {
            val atomic=AtomicDataFile(java.io.File(folder,"steps.json"))
            atomic.write { it.write("complete".toByteArray()) }
            try { atomic.write { it.write("partial".toByteArray()); throw java.io.IOException("simulated disk failure") }; fail("Expected failure") }
            catch (_: java.io.IOException) { }
            assertEquals("complete",String(atomic.readFully()))
            assertFalse(java.io.File(folder,"steps.json.new").exists())
        } finally { folder.listFiles()?.forEach { it.delete() }; folder.delete() }
    }
}
