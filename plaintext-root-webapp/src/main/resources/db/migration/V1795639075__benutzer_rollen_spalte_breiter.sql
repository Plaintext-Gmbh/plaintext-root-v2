-- Karte 2517: Die Rollen stehen als XStream-Set in my_user_entity.roles. Mit vielen Modulrollen wird der
-- Text laenger als 255 (V820503559), und Speichern unter Rollenzuteilung scheitert. Verbreitern auf 4000.
ALTER TABLE my_user_entity ALTER COLUMN roles SET DATA TYPE VARCHAR(4000);
