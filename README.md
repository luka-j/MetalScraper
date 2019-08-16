# Metal Archives scraper
### scraps metal-archives.com for everything

A Kotlin toy project.

Downloads group, record and song data from metal-archives and stores it to a PostgreSQL database. Images (album covers, 
band logos and pictures) are stored off-database. Requires a running instance of PostgreSQL with 
appropriate creds set as environment variables (PG_USER, PG_PASSWORD and PG_METALDB). Database initialization script is
given in `database.sql`.

Scraper first downloads all band urls and stores them to a file, and afterwards randomly takes one by one and downloads
data associated with it. All downloads done using `Scraper#sample` are executed inside a database transaction.