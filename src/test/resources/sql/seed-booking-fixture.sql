-- Deterministic fixture for SqlSeededBookingIntegrationTest, loaded via @Sql rather than built
-- through the API like the other integration tests — exercises the schema/migrations directly,
-- the way a data-migration or reporting query against this schema would. Looked up by name/title
-- in the test rather than by hardcoded id, since ids are BIGSERIAL and Flyway's own V2 demo data
-- (and any earlier test in the same run) may already occupy low ids.

INSERT INTO cities (name) VALUES ('SqlSeedCity');

INSERT INTO theaters (city_id, name, address)
SELECT id, 'SqlSeedTheater', '1 Fixture Row'
FROM cities WHERE name = 'SqlSeedCity';

INSERT INTO screens (theater_id, name)
SELECT id, 'SqlSeedScreen'
FROM theaters WHERE name = 'SqlSeedTheater';

-- 2 rows x 4 seats, all REGULAR — enough to exercise a hold/confirm/cancel flow.
INSERT INTO seats (screen_id, row_label, seat_number, seat_type)
SELECT sc.id, r.row_label, n.seat_number, 'REGULAR'
FROM screens sc
CROSS JOIN (SELECT unnest(ARRAY ['A', 'B']) AS row_label) r
CROSS JOIN (SELECT generate_series(1, 4) AS seat_number) n
WHERE sc.name = 'SqlSeedScreen';

INSERT INTO movies (title, duration_minutes, language, genre)
VALUES ('SQL Seed Movie', 100, 'English', 'Test');

INSERT INTO shows (movie_id, screen_id, start_time, end_time)
SELECT m.id, sc.id, now() + interval '4 hours', now() + interval '6 hours'
FROM movies m
JOIN screens sc ON sc.name = 'SqlSeedScreen'
WHERE m.title = 'SQL Seed Movie';

INSERT INTO show_seats (show_id, seat_id, status, price)
SELECT sh.id, se.id, 'AVAILABLE', 250.00
FROM shows sh
JOIN screens sc ON sc.id = sh.screen_id AND sc.name = 'SqlSeedScreen'
JOIN seats se ON se.screen_id = sc.id
JOIN movies m ON m.id = sh.movie_id AND m.title = 'SQL Seed Movie';
