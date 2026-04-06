

CREATE TABLE travel.user_account (
    id                    BIGSERIAL PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL UNIQUE,
    password_hash         VARCHAR(255) NOT NULL,
    full_name             VARCHAR(255),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_blocked            BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    last_failed_login_at  TIMESTAMPTZ
);

COMMENT ON TABLE travel.user_account IS
    'Пользователи мобильного приложения';
COMMENT ON COLUMN travel.user_account.email IS
    'Уникальный e-mail пользователя';
COMMENT ON COLUMN travel.user_account.password_hash IS
    'Хэш пароля пользователя';

CREATE TABLE travel.trip (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES travel.user_account(id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    destination      VARCHAR(255) NOT NULL,
    destination_lat  NUMERIC(9,6),
    destination_lon  NUMERIC(9,6),
    start_date       DATE,
    end_date         DATE,
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_trip_dates CHECK (
        start_date IS NULL OR end_date IS NULL OR start_date <= end_date
    )
);

COMMENT ON TABLE travel.trip IS
    'Поездки пользователей';

CREATE INDEX idx_trip_user_id ON travel.trip(user_id);

-- 4. Таблица достопримечательностей / мест
CREATE TABLE travel.entertainment_place (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    lat         NUMERIC(9,6),
    lon         NUMERIC(9,6),
    address     VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE travel.entertainment_place IS
    'Достопримечательности и места развлечений';

CREATE INDEX idx_ent_place_coords ON travel.entertainment_place(lat, lon);

CREATE TABLE travel.trip_entertainment (
    trip_id     BIGINT NOT NULL REFERENCES travel.trip(id) ON DELETE CASCADE,
    place_id    BIGINT NOT NULL REFERENCES travel.entertainment_place(id) ON DELETE CASCADE,
    is_favorite BOOLEAN NOT NULL DEFAULT FALSE,
    added_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (trip_id, place_id)
);

COMMENT ON TABLE travel.trip_entertainment IS
    'Связь поездок и мест, с признаком избранного';

CREATE INDEX idx_trip_entertainment_trip_id ON travel.trip_entertainment(trip_id);
CREATE INDEX idx_trip_entertainment_place_id ON travel.trip_entertainment(place_id);

CREATE TABLE travel.login_audit (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES travel.user_account(id) ON DELETE CASCADE,
    login_time  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    success     BOOLEAN NOT NULL,
    ip_address  INET,
    user_agent  TEXT
);

COMMENT ON TABLE travel.login_audit IS
    'Лог попыток входа пользователя в систему';

CREATE INDEX idx_login_audit_user_time
    ON travel.login_audit(user_id, login_time DESC);

CREATE OR REPLACE FUNCTION travel.create_trip_with_favorite_place(
    p_user_id BIGINT,
    p_trip_name VARCHAR,
    p_destination VARCHAR,
    p_destination_lat NUMERIC,
    p_destination_lon NUMERIC,
    p_start_date DATE,
    p_end_date DATE,
    p_notes TEXT,
    p_place_id BIGINT
)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_trip_id BIGINT;
BEGIN
    INSERT INTO travel.trip (
        user_id, name, destination, destination_lat, destination_lon,
        start_date, end_date, notes
    )
    VALUES (
        p_user_id, p_trip_name, p_destination, p_destination_lat, p_destination_lon,
        p_start_date, p_end_date, p_notes
    )
    RETURNING id INTO v_trip_id;

    INSERT INTO travel.trip_entertainment (trip_id, place_id, is_favorite)
    VALUES (v_trip_id, p_place_id, TRUE)
    ON CONFLICT (trip_id, place_id) DO UPDATE
        SET is_favorite = EXCLUDED.is_favorite;

    RETURN v_trip_id;
END;
$$;


CREATE OR REPLACE FUNCTION travel.fn_after_login_audit()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_failed_count INTEGER;
BEGIN
    IF NEW.user_id IS NULL THEN
        RETURN NEW;
    END IF;

    IF NEW.success = FALSE THEN
        UPDATE travel.user_account
        SET failed_login_attempts = failed_login_attempts + 1,
            last_failed_login_at = NEW.login_time
        WHERE id = NEW.user_id;

        SELECT COUNT(*)
        INTO v_failed_count
        FROM travel.login_audit
        WHERE user_id = NEW.user_id
          AND success = FALSE
          AND login_time >= (NEW.login_time - INTERVAL '10 minutes');

        IF v_failed_count >= 5 THEN
            UPDATE travel.user_account
            SET is_blocked = TRUE
            WHERE id = NEW.user_id;
        END IF;
    ELSE
        UPDATE travel.user_account
        SET failed_login_attempts = 0
        WHERE id = NEW.user_id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_after_login_audit
AFTER INSERT ON travel.login_audit
FOR EACH ROW
EXECUTE FUNCTION travel.fn_after_login_audit();


CREATE ROLE app_admin NOINHERIT LOGIN PASSWORD 'admin_password';
CREATE ROLE app_manager NOINHERIT;
CREATE ROLE app_user NOINHERIT;

GRANT app_user TO app_manager;
GRANT app_manager TO app_admin;

GRANT USAGE ON SCHEMA travel TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON
    travel.trip,
    travel.trip_entertainment,
    travel.entertainment_place
TO app_user;

GRANT SELECT ON
    travel.user_account,
    travel.login_audit
TO app_manager;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA travel TO app_admin;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA travel TO app_admin;

GRANT UPDATE ON travel.user_account TO app_user;
REVOKE UPDATE (is_blocked) ON travel.user_account FROM app_user;

INSERT INTO travel.user_account (email, password_hash, full_name)
VALUES
('user1@example.com', 'hash_заглушка', 'Иван Иванов');

INSERT INTO travel.entertainment_place (name, description, lat, lon, address)
VALUES
('Красная площадь', 'Исторический центр Москвы', 55.753930, 37.620795, 'Москва, Красная площадь'),
('Третьяковская галерея', 'Крупнейший музей русского искусства', 55.741394, 37.620793, 'Москва, Лаврушинский пер., 10');

INSERT INTO travel.trip (user_id, name, destination, destination_lat, destination_lon, start_date, end_date, notes)
VALUES
(1, 'Поездка в Москву', 'Москва, Россия', 55.751244, 37.618423, '2025-05-01', '2025-05-05', 'Посетить центр и музеи');

INSERT INTO travel.trip_entertainment (trip_id, place_id, is_favorite)
VALUES
(1, 1, TRUE),
(1, 2, FALSE);

