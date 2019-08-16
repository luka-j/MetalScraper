package rs.lukaj.metalarchives.scraper

import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

val ROOT_DIR = File("/home/luka/Documents/metalarchive")

fun main() {
    val dbUser = System.getenv("PG_USER")
    val dbPass = System.getenv("PG_PASSWORD")
    val dbName = System.getenv("PG_METALDB")
    val db = Database(dbUser, dbPass, dbName)
    db.use {
        val scraper = Scraper(db, MediaDownloader(File(ROOT_DIR, "new")))
        while(scraper.sample(600)) {
            println("[${DateTimeFormatter.ISO_INSTANT.format(Instant.now())}] Finished batch.")
        }
    }
}