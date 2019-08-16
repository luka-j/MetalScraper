package rs.lukaj.metalarchives.scraper

import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.LinkedBlockingQueue

class MediaDownloader(rootDir: File) {
    private val bandLogos = File(rootDir, "bandLogos")
    private val bandPics = File(rootDir, "bandPics")
    private val covers = File(rootDir, "covers")

    private val executor = BlockingExecutor("media", Integer.MAX_VALUE, 1, 150, 20L, LinkedBlockingQueue())

    init {
        if(!bandLogos.isDirectory) bandLogos.mkdirs()
        if(!bandPics.isDirectory) bandPics.mkdirs()
        if(!covers.isDirectory) covers.mkdirs()
    }

    fun downloadBandLogo(url: String, filename: String) {
        return downloadImage(url, filename, bandLogos)
    }

    fun downloadBandPic(url: String, filename:String) {
        downloadImage(url, filename, bandPics)
    }

    fun downloadRecordCover(url: String, filename: String) {
        downloadImage(url, filename, covers)
    }

    private fun downloadImage(url: String, filename: String, root: File) {
        executor.execute {
            val file = File(root, filename)
            if (file.exists()) System.err.println("Warning: File ${file.absolutePath} already exists!")
            file.createNewFile()
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.readTimeout = 15_000
                conn.connectTimeout = 5_000
                conn.connect()
                if (conn.responseCode == 404) {
                    System.err.println("Resource at $url doesn't exist; skipping download")
                    file.delete()
                    return@execute
                }
                if (conn.responseCode != 200) System.err.println("Invalid response code: ${conn.responseCode}")
                val contentStream = conn.inputStream
                FileUtils.copyInputStreamToFile(contentStream, file)
            } catch (ex: Exception) {
                if(ex is FileNotFoundException) {
                    System.err.println("Caught FileNotFoundException @ downloadImage($url); ignoring")
                    file.delete()
                    return@execute
                }
                System.err.println("Caught ${ex.javaClass.name} @ MediaDownloader ($url); trying again shortly")
                file.delete()
                Thread.sleep(4_800)
                downloadImage(url, filename, root)
            }
        }
    }

    fun await() {
        executor.await()
    }
}