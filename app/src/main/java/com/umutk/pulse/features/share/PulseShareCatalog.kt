package com.umutk.pulse.features.share

// made by scripts/gen_standalone.py: do not edit by hand

/** What one app of Pulse brings over to the same app on its own: the names of its preferences files and the files / folders of Pulse's files folder. */
class ShareSpec(val prefs: List<String>, val files: List<String>)

object PulseShareCatalog {
    val specs = mapOf(
        "files" to ShareSpec(listOf("files_home_cache", "pulse_share", "pulse_types"), listOf()),
        "calendar" to ShareSpec(listOf("pulse_calendar"), listOf()),
        "contacts" to ShareSpec(listOf("contacts_app"), listOf()),
        "photos" to ShareSpec(listOf(), listOf()),
        "ytx" to ShareSpec(listOf("appearance", "language_settings", "music_dislikes", "playback", "resume", "sponsorblock", "watched_log", "ytx_neuro", "ytx_perf", "ytx_play", "ytx_taste"), listOf()),
        "island" to ShareSpec(listOf("pulse_island"), listOf()),
        "music" to ShareSpec(listOf("artist_art_miss", "listen_log", "music_eq", "music_hub", "music_modes", "music_settings", "music_tags", "music_tags_tried", "music_widgets", "music_wmp12", "online_history", "poweramp_line", "poweramp_vis", "pulse_mp_playlists", "pulse_poweramp", "track_canvas", "winamp_look", "winamp_radio", "winamp_skin_pick"), listOf("playlists", "resume.m3u8", "skins", "music_playlog.tsv", "music_library.tsv")),
        "video" to ShareSpec(listOf("video_player", "video_sub_saved", "video_subs"), listOf("subs")),
        "whitenoise" to ShareSpec(listOf("ambient_presets"), listOf()),
        "voice" to ShareSpec(listOf("voice_prefs"), listOf()),
        "notes" to ShareSpec(listOf("notes_ink", "notes_pens", "notes_prefs"), listOf("notes")),
        "paint" to ShareSpec(listOf(), listOf()),
        "browser" to ShareSpec(listOf("browser"), listOf()),
        "office" to ShareSpec(listOf("office", "office_tools", "p", "text_editor"), listOf()),
        "code" to ShareSpec(listOf("pulse_code"), listOf()),
        "terminal" to ShareSpec(listOf("terminal"), listOf()),
        "calculator" to ShareSpec(listOf("calc_rates"), listOf()),
        "clock" to ShareSpec(listOf("lucid_glow"), listOf()),
        "ebook" to ShareSpec(listOf("ebook_collections", "ebook_reader", "ebook_rsvp", "ebook_state", "ebook_stats"), listOf("covers")),
        "tempmail" to ShareSpec(listOf("tempmail"), listOf()),
        "keyboard" to ShareSpec(listOf("kbd_stats", "pulse_kb_domains", "pulse_kb_emails", "pulse_kb_fullnames", "pulse_kb_hybrid", "pulse_kb_layouts", "pulse_kb_names", "pulse_kb_numbers", "pulse_kb_perapp", "pulse_kb_shortcuts", "pulse_kb_shots", "pulse_kb_speed", "pulse_kb_tile", "pulse_kb_tone", "pulse_keyboard"), listOf("clips", "kb_font", "kb_sound", "kb_picture.img", "kb_space.img", "kb_walker.img", "kbd", "kbd_banned.txt", "kbd_clips.json", "kbd_confusion.txt", "kbd_forms.txt", "kbd_keys.txt", "kbd_pairs.txt", "kbd_rejects.txt", "kbd_touch.txt", "kbd_typo_seen.txt", "kbd_typos.txt", "kbd_user.txt")),
        "ai" to ShareSpec(listOf("ai_app", "ai_buddy", "ai_chat", "ai_mascot", "ai_memory", "ai_overlay", "models"), listOf()),
        "weather" to ShareSpec(listOf("weather", "weather_report"), listOf()),
        "messages" to ShareSpec(listOf("msg"), listOf()),
        "screenrec" to ShareSpec(listOf("screenrec", "screenrec_pen"), listOf()),
        "notifhist" to ShareSpec(listOf("notifhist"), listOf()),
        "extrastore" to ShareSpec(listOf("extra_icons", "extra_icons_look", "extra_store", "launcher_glass", "pack_consent", "store_rows"), listOf()),
        "localsend" to ShareSpec(listOf("localsend"), listOf()),
        "palette" to ShareSpec(listOf("palette_studio"), listOf()),
        "gamehub" to ShareSpec(listOf("cover_tried", "gamehub"), listOf()),
        "health" to ShareSpec(listOf(), listOf()),
        "blackout" to ShareSpec(listOf("blackout", "hookstatus"), listOf()),
        "launcher" to ShareSpec(listOf("launcher", "launcher_note", "pulse_device_accent", "pulse_news", "pulse_phone_wall", "pulse_quick_tiles", "win_notepad", "win_tasks"), listOf()),
    )
}
