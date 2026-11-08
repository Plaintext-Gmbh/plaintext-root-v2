-- Lader-Probe (Karte 1563): Migration, die NUR das synthetische Modul mitbringt.
-- Taucht sie in flyway_schema_history auf, hat Flyway das Modul-Jar aus loader.path gelesen.
CREATE TABLE IF NOT EXISTS probe_1563 (
    id   BIGSERIAL PRIMARY KEY,
    text TEXT NOT NULL
);
