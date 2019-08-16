package rs.lukaj.metalarchives.scraper

import org.postgresql.util.PSQLException
import java.sql.DriverManager
import java.sql.Types

class Database(private val user: String, private val pass: String, private val name: String, private val autocommit: Boolean = false) : AutoCloseable {

    private var connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/$name", user, pass)

    private var insertBand = connection.prepareStatement("INSERT INTO bands (name, country, location, status, founded_in, active, label, logo, picture) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
    private var insertRecord = connection.prepareStatement("INSERT INTO records (name, type, release_year, label, format, reviews_no, avg_rating, length, cover, band_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
    private var insertGenre = connection.prepareStatement("INSERT INTO genres (name) VALUES (?)")
    private var insertTheme = connection.prepareStatement("INSERT INTO themes (name) VALUES (?)")
    private var insertSong = connection.prepareStatement("INSERT INTO songs (number, name, length, instrumental, lyrics, record_id) VALUES (?, ?, ?, ?, ?, ?)")
    private var bandSetGenre = connection.prepareStatement("INSERT INTO band_genres(band_id, genre_id) VALUES (?, ?)")
    private var bandSetTheme = connection.prepareStatement("INSERT INTO band_themes(band_id, theme_id) VALUES (?, ?)")

    private var getGenre = connection.prepareStatement("SELECT id FROM genres WHERE name=?")
    private var getTheme = connection.prepareStatement("SELECT id FROM themes WHERE name=?")

    private var getLastBandId = connection.prepareStatement("SELECT currval('bands_id_seq')")
    private var getLastGenreId = connection.prepareStatement("SELECT currval('genres_id_seq')")
    private var getLastThemeId = connection.prepareStatement("SELECT currval('themes_id_seq')")
    private var getLastRecordId = connection.prepareStatement("SELECT currval('records_id_seq')")

    private fun openConnection() {
        connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/$name", user, pass)
        insertBand = connection.prepareStatement("INSERT INTO bands (name, country, location, status, founded_in, active, label, logo, picture) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
        insertRecord = connection.prepareStatement("INSERT INTO records (name, type, release_year, label, format, reviews_no, avg_rating, length, cover, band_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        insertGenre = connection.prepareStatement("INSERT INTO genres (name) VALUES (?)")
        insertTheme = connection.prepareStatement("INSERT INTO themes (name) VALUES (?)")
        insertSong = connection.prepareStatement("INSERT INTO songs (number, name, length, instrumental, lyrics, record_id) VALUES (?, ?, ?, ?, ?, ?)")
        bandSetGenre = connection.prepareStatement("INSERT INTO band_genres(band_id, genre_id) VALUES (?, ?)")
        bandSetTheme = connection.prepareStatement("INSERT INTO band_themes(band_id, theme_id) VALUES (?, ?)")
        getGenre = connection.prepareStatement("SELECT id FROM genres WHERE name=?")
        getTheme = connection.prepareStatement("SELECT id FROM themes WHERE name=?")
        getLastBandId = connection.prepareStatement("SELECT currval('bands_id_seq')")
        getLastGenreId = connection.prepareStatement("SELECT currval('genres_id_seq')")
        getLastThemeId = connection.prepareStatement("SELECT currval('themes_id_seq')")
        getLastRecordId = connection.prepareStatement("SELECT currval('records_id_seq')")
        connection.autoCommit = autocommit
    }
    init {
        connection.autoCommit = autocommit
    }

    fun insertBand(band: Band) {
        ensureOpen()
        insertBand.setString(1, band.name)
        insertBand.setString(2, band.country)
        insertBand.setString(3, band.location)
        insertBand.setString(4, band.status)
        insertBand.setInt(5, band.foundedIn)
        insertBand.setString(6, band.active)
        insertBand.setString(7, band.label)
        if(band.logo != null) insertBand.setString(8, band.logo)
        else insertBand.setNull(8, Types.VARCHAR)
        if(band.picture != null) insertBand.setString(9, band.picture)
        else insertBand.setNull(9, Types.VARCHAR)
        insertBand.executeUpdate()
        val id = lastBandId();
        band.dbId = id
        for(genre in band.genres) {
            if(genre.isBlank()) continue
            var genreId = findGenre(genre)
            if(genreId == null) {
                genreId = insertGenre(genre)
            }
            setBandGenre(id, genreId)
        }
        for(theme in band.themes) {
            var themeId = findTheme(theme)
            if(themeId == null) {
                themeId = insertTheme(theme)
            }
            setBandTheme(id, themeId)
        }
    }

    fun insertRecord(record: Record) {
        ensureOpen()
        insertRecord.setString(1, record.name)
        insertRecord.setString(2, record.type)
        insertRecord.setInt(3, record.releaseYear)
        insertRecord.setString(4, record.label)
        insertRecord.setString(5, record.format)
        insertRecord.setInt(6, record.reviewsNo)
        insertRecord.setInt(7, record.rating)
        insertRecord.setInt(8, record.length)
        if(record.cover != null) insertRecord.setString(9, record.cover)
        else insertRecord.setNull(9, Types.VARCHAR)
        insertRecord.setLong(10, record.band.dbId)
        insertRecord.executeUpdate()
        record.dbId = lastRecordId()
    }

    fun insertSong(song: Song) {
        ensureOpen()
        insertSong.setInt(1, song.no)
        insertSong.setString(2, song.name)
        insertSong.setInt(3, song.length)
        insertSong.setBoolean(4, song.instrumental)
        insertSong.setString(5, song.lyrics)
        insertSong.setLong(6, song.record.dbId)
        insertSong.executeUpdate()
    }

    private fun findGenre(name: String) : Long? {
        getGenre.setString(1, name)
        val res = getGenre.executeQuery()
        res.use {
            return if(res.next()) res.getLong(1)
            else null
        }
    }

    private fun findTheme(name: String) : Long? {
        getTheme.setString(1, name)
        val res = getTheme.executeQuery()
        res.use {
            return if(res.next()) res.getLong(1)
            else null
        }
    }

    private fun insertGenre(name: String) : Long {
        insertGenre.setString(1, name)
        insertGenre.executeUpdate()
        return lastGenreId()
    }

    private fun insertTheme(name: String) : Long {
        insertTheme.setString(1, name)
        insertTheme.executeUpdate()
        return lastThemeId()
    }

    private fun setBandGenre(bandId: Long, genreId: Long) {

        bandSetGenre.setLong(1, bandId)
        bandSetGenre.setLong(2, genreId)
        try {
            bandSetGenre.executeUpdate()
        } catch (ex: PSQLException) {
            System.err.println("Oops, PSQLException occured @ setBandGenre($bandId, $genreId); ignoring")
            ex.printStackTrace()
        }
    }

    private fun setBandTheme(bandId: Long, themeId: Long) {
        bandSetTheme.setLong(1, bandId)
        bandSetTheme.setLong(2, themeId)
        try {
            bandSetTheme.executeUpdate()
        } catch (ex: PSQLException) {
            System.err.println("Oops, PSQLException occured @ setBandTheme($bandId, $themeId); ignoring")
            ex.printStackTrace()
        }
    }

    private fun lastBandId() : Long {
        val idRes = getLastBandId.executeQuery()
        idRes.use {
            if (!idRes.next()) throw RuntimeException("There is no id for inserted band!")
            return idRes.getLong(1)
        }
    }

    private fun lastGenreId() : Long {
        val idRes = getLastGenreId.executeQuery()
        idRes.use {
            if (!idRes.next()) throw RuntimeException("There is no id for inserted genre!")
            return idRes.getLong(1)
        }
    }

    private fun lastThemeId() : Long {
        val idRes = getLastThemeId.executeQuery()
        idRes.use {
            if (!idRes.next()) throw RuntimeException("There is no id for inserted theme!")
            return idRes.getLong(1)
        }
    }

    private fun lastRecordId() : Long {
        val idRes = getLastRecordId.executeQuery()
        idRes.use {
            if (!idRes.next()) throw RuntimeException("There is no id for inserted record!")
            return idRes.getLong(1)
        }
    }

    @Synchronized
    private fun ensureOpen() {
        if(!connection.isClosed) return
        openConnection()
    }

    fun commit() {
        connection.commit()
    }

    override fun close() {
        if(!autocommit) connection.commit()
        connection.close()
        //technically, one should close all PreparedStatements here as well
    }
}