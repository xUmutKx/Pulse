package com.umutk.pulse.extras.music

/** A text for searching: lower case with the Turkish letters read as their plain forms (ı İ ş ğ ü ö ç), so "isik" finds "Işık" and "SARKI" finds "şarkı". */
internal fun String.fold(): String = buildString(length) {
    for (ch in this@fold) append(when (ch) {
        'ı', 'İ', 'I', 'i' -> 'i'; 'ş', 'Ş' -> 's'; 'ğ', 'Ğ' -> 'g'; 'ü', 'Ü' -> 'u'; 'ö', 'Ö' -> 'o'; 'ç', 'Ç' -> 'c'
        else -> ch.lowercaseChar()
    })
}

internal fun String?.foldHas(q: String) = this != null && fold().contains(q.fold())
