-- Reference data (pricing/refund policy) an admin would normally configure via the API,
-- plus a small demo catalog so a fresh clone is immediately explorable (Swagger UI / Loom demo)
-- without requiring a long setup sequence first. Show times are relative to `now()` so the
-- demo data never goes stale regardless of when this is applied.
-- NOTE: the ADMIN user itself is NOT seeded here — it's created by a startup seeder
-- (see AdminSeeder) so its password is hashed with the app's actual PasswordEncoder
-- rather than a hardcoded bcrypt literal. Credentials are documented in README.md.

INSERT INTO pricing_rules (seat_type, base_price, weekend_multiplier)
VALUES ('REGULAR', 200.00, 1.20),
       ('PREMIUM', 350.00, 1.20);

INSERT INTO refund_policy_rules (min_hours_before_show, refund_percentage)
VALUES (24, 100.00),
       (2, 50.00),
       (0, 0.00);

INSERT INTO discount_codes (code, type, value, valid_from, valid_to, max_uses, used_count, active)
VALUES ('WELCOME10', 'PERCENTAGE', 10.00, now() - interval '1 day', now() + interval '90 days', 1000, 0, TRUE),
       ('FLAT50', 'FLAT', 50.00, now() - interval '1 day', now() + interval '90 days', 500, 0, TRUE);

INSERT INTO cities (id, name) VALUES (1, 'Mumbai'), (2, 'Bengaluru');
SELECT setval('cities_id_seq', (SELECT max(id) FROM cities));

INSERT INTO theaters (id, city_id, name, address)
VALUES (1, 1, 'PVR Phoenix Marketcity', 'LBS Marg, Kurla West, Mumbai'),
       (2, 2, 'INOX Forum Mall', 'Hosur Road, Koramangala, Bengaluru');
SELECT setval('theaters_id_seq', (SELECT max(id) FROM theaters));

INSERT INTO screens (id, theater_id, name)
VALUES (1, 1, 'Screen 1'),
       (2, 2, 'Screen 1');
SELECT setval('screens_id_seq', (SELECT max(id) FROM screens));

-- 5 rows (A-E) x 8 seats per screen; rows A-B are PREMIUM, C-E are REGULAR.
INSERT INTO seats (screen_id, row_label, seat_number, seat_type)
SELECT screen_id, row_label, seat_number,
       CASE WHEN row_label IN ('A', 'B') THEN 'PREMIUM' ELSE 'REGULAR' END
FROM (SELECT id AS screen_id FROM screens) s
CROSS JOIN (SELECT unnest(ARRAY ['A', 'B', 'C', 'D', 'E']) AS row_label) r
CROSS JOIN (SELECT generate_series(1, 8) AS seat_number) n;

INSERT INTO movies (id, title, duration_minutes, language, genre)
VALUES (1, 'Interstellar Redux', 168, 'English', 'Sci-Fi'),
       (2, 'The Silent Sea', 132, 'English', 'Thriller');
SELECT setval('movies_id_seq', (SELECT max(id) FROM movies));

-- A few upcoming shows per theater, spread across the next few days so both the
-- >24h and <24h refund-policy tiers are reachable from a fresh clone.
INSERT INTO shows (movie_id, screen_id, start_time, end_time)
VALUES (1, 1, now() + interval '3 hours', now() + interval '3 hours' + interval '168 minutes'),
       (2, 1, now() + interval '1 day', now() + interval '1 day' + interval '132 minutes'),
       (1, 2, now() + interval '6 hours', now() + interval '6 hours' + interval '168 minutes'),
       (2, 2, now() + interval '2 days', now() + interval '2 days' + interval '132 minutes');

-- Generate a show_seats row for every seat of a show's screen, priced from pricing_rules
-- with the weekend multiplier applied when the show falls on a Saturday/Sunday.
INSERT INTO show_seats (show_id, seat_id, status, price)
SELECT sh.id,
       se.id,
       'AVAILABLE',
       CASE
           WHEN extract(ISODOW FROM sh.start_time) IN (6, 7)
               THEN pr.base_price * pr.weekend_multiplier
           ELSE pr.base_price
           END
FROM shows sh
JOIN seats se ON se.screen_id = sh.screen_id
JOIN pricing_rules pr ON pr.seat_type = se.seat_type;
