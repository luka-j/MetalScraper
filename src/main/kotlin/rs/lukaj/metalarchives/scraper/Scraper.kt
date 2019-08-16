package rs.lukaj.metalarchives.scraper

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.*
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.stream.Collectors
import kotlin.random.Random


class Scraper(private val database: Database, private val media: MediaDownloader) {

    private val recordExecutor = BlockingExecutor("records", 600, 2, 100, 10L, LinkedBlockingQueue())
    private val songExecutor = BlockingExecutor("songs", 1000, 2, 160, 10L, LinkedBlockingQueue())
    private val recordUrlExecutor = BlockingExecutor("recordUrls", 400, 1, 20, 30, LinkedBlockingQueue())

    fun scrape() {
        sample(Integer.MAX_VALUE)
    }

    fun sample(n: Int) : Boolean {
        val saver = Saver(ROOT_DIR)
        val urls =
            if(saver.hasSavedUrls()) saver.loadUrls()
            else scrapeBandUrls()
        println("Got ${urls.size} band urls; collecting a sample of $n.")
        if(!saver.hasSavedUrls()) saver.saveUrls(urls)
        var finished = false
        for(i in 1..n) {
            if(urls.isEmpty()) {finished = true; break}
            if(i%50 == 1) println("Downloaded ${i-1} bands")
            val ix = Random.nextInt(urls.size)
            scrapeBand(urls[ix])
            urls.removeAt(ix)
        }

        println("Collected sample; waiting for tasks to complete")
        recordUrlExecutor.await()
        recordExecutor.await()
        songExecutor.await()
        println("Waiting for MediaDownloader...")
        media.await()
        println("All data here; committing to database and finishing")

        database.commit()
        saver.saveUrls(urls)
        return !finished
    }

    private fun scrapeBandUrls() : ArrayList<String> {
        val urls = ArrayList<String>()
        val letters = ArrayList<String>()
        letters.addAll(('A'..'Z').map { c -> c.toString() })
        letters.add("NBR")
        letters.add("~")
        for(letter in letters) {
            println("Scraping letter $letter")
            var page = 0
            while(true) {
                val url = "https://www.metal-archives.com/browse/ajax-letter/l/$letter/json/1?sEcho=${page+1}&iColumns=4&sColumns=&iDisplayStart=${page*500}&iDisplayLength=500&mDataProp_0=0&mDataProp_1=1&mDataProp_2=2&mDataProp_3=3&iSortCol_0=0&sSortDir_0=asc&iSortingCols=1&bSortable_0=true&bSortable_1=true&bSortable_2=true&bSortable_3=false&_=${page+1564615202623}"
                val parser = JsonParser()
                val json = parser.parse(URL(url)).asJsonObject
                val data = json.getAsJsonArray("aaData")
                if(data.size() == 0) break
                for(band in data) {
                    val bandJson = band.asJsonArray
                    val a = bandJson[0]
                    val bandUrl = a.asString.split("href='", "'>", limit=3)[1]
                    urls.add(bandUrl)
                }
                page++;
            }
        }
        return urls
    }

    private fun scrapeBand(url: String) {
        val id = url.substring(url.lastIndexOf('/')+1).toLong()
        val body = Jsoup.parse(URL(url).downloadAll())
        val bandInfo = body.getElementById("band_info")
        val name = bandInfo.getElementsByClass("band_name")[0].text()
        val details = bandInfo.getElementById("band_stats").getElementsByTag("dd")
        val country = details[0].text()
        val location = details[1].text()
        val status = details[2].text()
        val formedIn = details[3].text()
        val formedInYear = if(formedIn == "N/A") 0 else formedIn.toInt()
        val genres = details[4].text()
        val themes = details[5].text()
        val label = details[6].text()
        val yearsActive = details[7].text()
        val logoUrl = body.getElementById("logo")?.attr("href")
        val picUrl = body.getElementById("photo")?.attr("href")
        if(logoUrl != null) media.downloadBandLogo(logoUrl, id.toString())
        if(picUrl != null) media.downloadBandPic(picUrl, id.toString())
        val logo = if(logoUrl != null) id.toString() else null
        val photo = if(picUrl != null) id.toString() else null
        val band = Band(name, country, location, status, formedInYear, yearsActive, genres, themes, label, logo, photo)
        database.insertBand(band)
        recordUrlExecutor.execute {
            val albumUrls = scrapeRecordUrls(id)
            for (album in albumUrls) {
                recordExecutor.execute {
                    scrapeRecord(band, album)
                }
            }
        }
    }

    private fun scrapeRecordUrls(bandId: Long) : Queue<String> {
        val url = "https://www.metal-archives.com/band/discography/id/$bandId/tab/all"
        val urls = LinkedList<String>()
        val table = URL(url).downloadAll()
        val tokens = table.split("<td><a href=\"", "\" class=\"")
        val iterateOver = (2 until tokens.size).step(2)
        for(i in iterateOver)
            urls.add(tokens[i])
        return urls
    }

    private fun scrapeRecord(band: Band, url: String) {
        val id = url.substring(url.lastIndexOf('/')+1).toLong()
        val body: Element
        try {
            body = Jsoup.parse(URL(url).downloadAll())
        } catch (ex: SocketTimeoutException) {
            System.err.println("Timeout @ scrapeRecord! Trying again...")
            return scrapeRecord(band, url)
        }
        val albumInfo = body.getElementById("album_info")
        val name = albumInfo.getElementsByClass("album_name")[0].text()
        val details = albumInfo.select("dt,dd")
        var releaseDate = ""; var label = ""; var format = ""; var type = ""; var reviewsNo = 0; var rating = -1
        val it = details.listIterator()
        while(it.hasNext()) {
            val el = it.next()
            if(el.tagName() != "dt") throw RuntimeException("Unexpected tag ${el.tagName()}; expected dt.")
            when(el.text()) {
                "Type:" -> type = it.next().text()
                "Release date:" -> releaseDate = it.next().text()
                "Label:" -> label = it.next().text()
                "Format:" -> format = it.next().text()
                "Reviews:" -> {
                    val reviewText = it.next().text()
                    if(reviewText.trim() == "None yet") {
                        reviewsNo = 0
                        rating = -1
                    } else {
                        val tokens = reviewText.split(" review", "avg. ")
                        reviewsNo = tokens[0].toInt()
                        rating = tokens[2].removeSuffix("%)").toInt()
                    }
                }
                else -> it.next()
            }
        }
        val songs = body.getElementsByClass("table_lyrics")[0]
        val length = songs.getElementsByTag("strong").map {el -> el.text()}
        val coverUrl = body.getElementById("cover")?.attr("href")
        if(coverUrl != null) media.downloadRecordCover(coverUrl, id.toString())
        val cover = if(coverUrl != null) id.toString() else null
        val record = Record(name, type, releaseDate, label, format, reviewsNo, rating, length, cover, band)
        database.insertRecord(record)
        scrapeSongs(record, songs)
    }

    private fun scrapeSongs(record: Record, table: Element) {
        val songRows = table.getElementsByClass("even")
        songRows.addAll(table.getElementsByClass("odd"))
        for(songRow in songRows) {
            songExecutor.execute {
                val els = songRow.children()
                val no = els[0].text().removeSuffix(".").toInt()
                val name = els[1].text()
                val length = els[2].text()
                val lyricsCell = els[3]
                var instrumental = false
                var lyrics = ""
                val type = lyricsCell.text()

                if (type == "Show lyrics") {
                    val id = lyricsCell.getElementsByTag("a")[0].attr("href").removePrefix("#")
                    val lyricsHtml =
                        URL("https://www.metal-archives.com/release/ajax-view-lyrics/id/$id").downloadAll()
                    lyrics = lyricsHtml.replace("<br />", "\n")
                } else {
                    if (type == "instrumental") instrumental = true
                }
                val song = Song(no, name, length, instrumental, lyrics, record)
                database.insertSong(song)
            }
        }
    }


    fun URL.downloadAll() : String {
        val conn = openConnection() as HttpURLConnection
        try {
            if(conn.responseCode != 200) System.err.println("Got response ${conn.responseCode} @ downloadAll (${toString()})")
            val br = BufferedReader(InputStreamReader(conn.inputStream))
            return br.lines().collect(Collectors.joining())
        } catch (ex: Exception) {
            System.err.println("An ${ex.javaClass.name} occurred; trying again shortly")
            Thread.sleep(5_200)
            return downloadAll()
        }
    }
}

private class Saver(rootDir: File) {
    private val urlFile = File(rootDir, "urls")

    fun saveUrls(urls: ArrayList<String>) {
        urlFile.createNewFile()
        val fw =  BufferedWriter(FileWriter(urlFile))
        fw.use {
            for (url in urls) {
                fw.write(url)
                fw.newLine()
            }
        }
    }

    fun hasSavedUrls() : Boolean {
        return urlFile.exists() && urlFile.length() > 0
    }

    fun loadUrls() : ArrayList<String> {
        if(!hasSavedUrls()) return ArrayList()
        val fr = BufferedReader(FileReader(urlFile))
        fr.use {
            return ArrayList(fr.lines().collect(Collectors.toList()) as MutableCollection<out String>) //some bizarre compilation error
        }
    }
}

fun JsonParser.parse(url: URL): JsonElement {
    val conn = url.openConnection() as HttpURLConnection
    conn.connect()
    val src = conn.inputStream
    try {
        return parse(BufferedReader(InputStreamReader(src)))
    } finally {
        conn.disconnect()
    }
}