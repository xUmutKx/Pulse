package com.umutk.pulse

import android.os.Environment
import java.io.File

/**
 * Everything Pulse puts in the shared Downloads folder lives under one folder, Download/Pulse: Share (what was received), AI (the models), Backup,
 * Crash (the crash and freeze logs), Notepad. An older folder of the same use is moved there once.
 */
object PulseDirs {
    fun root(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pulse")
    fun sub(name: String): File = File(root(), name)
    private fun downloads(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /** Moves [old] to [new] when [new] is not there yet (a folder with what the person already has stays theirs). */
    fun migrate(old: File, new: File) {
        runCatching { if (old.isDirectory && !new.exists()) { new.parentFile?.mkdirs(); old.renameTo(new) } }
    }

    /** The folder of what other devices sent (LocalSend and Relay both): Download/Pulse/Share. */
    fun share(): File {
        migrate(File(downloads(), "LocalSend"), sub("Share"))
        migrate(sub("Relay"), sub("Share"))
        return sub("Share")
    }

    /** The updates of Pulse itself that were downloaded (the APKs): Download/Pulse/Updates. */
    fun updates(): File = sub("Updates")

    /** What YTXTube downloads: Download/Pulse/YTXTube (its old Download/YTXTube Downloads goes when it is empty; one with files stays, its list knows those paths). */
    fun ytx(): File {
        runCatching {
            val old = File(downloads(), "YTXTube Downloads")
            if (old.isDirectory && old.walkTopDown().none { it.isFile }) old.deleteRecursively()
        }
        return sub("YTXTube")
    }

    /** Moves what is in [old] into [new] one by one (what [new] already has stays), then removes [old] when it is empty. */
    private fun merge(old: File, new: File) {
        runCatching {
            if (!old.isDirectory) return
            new.mkdirs()
            old.listFiles()?.forEach { f -> val d = File(new, f.name); if (!d.exists()) f.renameTo(d) }
            if (old.list().isNullOrEmpty()) old.delete()
        }
    }

    /** Once per start (cheap when there is nothing to do): the folders Pulse made in Downloads before are moved under Download/Pulse. */
    fun migrateAll() {
        merge(File(downloads(), "Pulse backup"), sub("Backup"))
        merge(File(downloads(), "PulseAI"), sub("AI"))
        merge(File(downloads(), "LocalSend"), sub("Share"))
        ytx()
        // the update APKs saved loose in Downloads (Pulse-1.0.443.apk …) go to their own folder
        runCatching {
            downloads().listFiles { f -> f.isFile && Regex("^Pulse-\\d+(\\.\\d+)+.*\\.apk$").matches(f.name) }?.forEach { f ->
                updates().mkdirs(); val d = File(updates(), f.name); if (!d.exists()) f.renameTo(d)
            }
        }
    }

    /** The AI models: Download/Pulse/AI (the old Download/PulseAI is moved there). */
    fun ai(): File {
        migrate(File(downloads(), "PulseAI"), sub("AI"))
        return sub("AI")
    }
}
