package com.umutk.pulse.files.editor

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.IOException

/**
 * Plain "Save": puts the new bytes at the same path the document was opened from.
 * Never makes a copy: a failure throws, so the caller shows it and the original stays as it was.
 */
object SaveTo {
    fun overwrite(tmp: File, src: File?, uri: Uri?, cr: ContentResolver) {
        if (src != null) {
            // write beside the original, then rename over it: a failed write never leaves a half-written file
            val side = File(src.parentFile, ".${src.name}.pulse-save")
            try {
                tmp.copyTo(side, overwrite = true)
                // some storage refuses rename over a file: then copy in place (the temp copy is complete by now)
                if (!side.renameTo(src)) tmp.copyTo(src, overwrite = true)
            } finally { side.delete() }
            return
        }
        val out = requireNotNull(uri) { "no file to save to" }
        cr.openOutputStream(out, "wt")?.use { o -> tmp.inputStream().use { it.copyTo(o) } } ?: throw IOException("cannot write to the file")
    }
}
