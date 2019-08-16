create table bands (
  id                bigserial primary key,
  name              text,
  country           text,
  location          text,
  status            text,
  founded_in        smallint,
  active            text,
  label             text,
  logo              text,
  picture           text
);

create table records (
  id                bigserial primary key,
  name              text,
  release_year      smallint,
  label             text,
  format            text,
  reviews_no        smallint,
  avg_rating        smallint,
  length            int,
  cover             text,
  band_id           bigint references bands(id)
);
create index ix_album_band on records(band_id);

create table genres (
  id                bigserial primary key,
  name              text
);

create table themes (
  id                bigserial primary key,
  name              text
);

create table band_genres (
  band_id           bigint references bands(id),
  genre_id          bigint references genres(id),
  primary key (band_id, genre_id)
);

create table band_themes (
  band_id           bigint references bands(id),
  theme_id          bigint references themes(id),
  primary key (band_id, theme_id)
);

alter table genres add constraint uq_genre_name unique(name);
alter table themes add constraint uq_theme_name unique(name);
alter table records add column type text;

create table songs (
    id bigserial primary key,
    number smallint,
    name text,
    length int,
    instrumental boolean,
    lyrics text,
    record_id bigint references records(id)
);
