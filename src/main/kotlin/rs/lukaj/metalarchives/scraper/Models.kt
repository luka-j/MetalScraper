package rs.lukaj.metalarchives.scraper

import java.util.regex.Pattern

private val GENRE_DELIMITER = Pattern.compile("(\\(.*\\))*($|/|(\\s*,\\s*))");

class Band(val name: String, val country: String, val location: String, val status: String, val foundedIn: Int,
           val active: String, genre: String, themes: String, val label: String, val logo: String?, val picture: String?) {
    val genres = HashSet(genre.split(GENRE_DELIMITER).map { s -> s.replace(" Metal", "") })
    val themes = HashSet(themes.split(Regex(",\\s*")))
    var dbId: Long = -1
}

class Record(val name: String, val type: String, releaseDate: String, val label: String, val format: String,
             val reviewsNo: Int, val rating: Int, length: List<String>, val cover: String?, val band: Band) {
    val releaseYear = releaseDate.substring(releaseDate.length-4).toInt()
    val length = if(length.isEmpty()) 0 else length.map { len -> len.toSeconds() }.reduce { l1, l2 -> l1 + l2 }
    var dbId: Long = -1
}

fun String.toSeconds() : Int {
    if(isBlank()) return 0
    val segments = split(":").reversed()
    var sec = segments[0].toInt()
    if(segments.size > 1) sec += 60*segments[1].toInt()
    if(segments.size > 2) sec += 60*60*segments[2].toInt()
    return sec
}

class Song(val no: Int, val name: String, length: String, val instrumental: Boolean, val lyrics: String, val record: Record) {
    val length = length.toSeconds()
}