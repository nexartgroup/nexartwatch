package de.mb.tagesring.phone.data

/**
 * Farbfolge für Termine, abgestimmt auf die Farbwelt von nexart.de (gedämpfte, helle Töne auf dunklem Grund).
 * Die Reihenfolge wechselt jeweils zu einem Komplementär-Ton, damit Nachbarn klar verschieden aussehen:
 * Blau - Bernstein, Türkis - Lachs, Lavendel - Limette. Danach wiederholt sich die Folge.
 */
object Palette {
    val COLORS: List<Int> = listOf(
        0xFF3FA9F5.toInt(), // Blau (Akzent)
        0xFFE3B25E.toInt(), // Bernstein
        0xFF5FD0C0.toInt(), // Türkis
        0xFFEF8A7A.toInt(), // Lachs
        0xFFB3A6EC.toInt(), // Lavendel
        0xFFA9D36F.toInt(), // Limette
    )
}
