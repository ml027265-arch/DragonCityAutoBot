package com.dragoncity.autobot

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Sync before atomic replacement. A failed write leaves the previous file intact. */
internal class AtomicDataFile(private val file: File) {
    fun readFully(): ByteArray = file.readBytes()
    fun write(block: (FileOutputStream) -> Unit) {
        val temporary = File(file.parentFile, file.name + ".new")
        try {
            FileOutputStream(temporary).use { output -> block(output); output.flush(); output.fd.sync() }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
}
