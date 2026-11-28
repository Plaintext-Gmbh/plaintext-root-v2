-- Karte 2560: Einladung in einen bestehenden Mandanten (Verein). Ein Einladungstoken behaelt seinen Mandanten,
-- auch wenn plaintext.selfservice.mandant-je-registrierung neue Registrierungen in eigene Mandanten legt.
ALTER TABLE REGISTRATION_TOKEN ADD COLUMN EINLADUNG BOOLEAN NOT NULL DEFAULT FALSE;
