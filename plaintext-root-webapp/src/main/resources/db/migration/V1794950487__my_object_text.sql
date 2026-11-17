-- Karte 2347 (Folge 2333): Tabelleneinstellungen passten nicht mehr in MY_OBJECT VARCHAR(30000),
-- das Speichern scheiterte still (GenericEntityService faengt die Ausnahme). TEXT statt Limit.
-- SET DATA TYPE statt TYPE: in PostgreSQL gleichwertig, und der demo-H2-Pfad kennt nur diese Form.
ALTER TABLE simple_storable_entity ALTER COLUMN my_object SET DATA TYPE TEXT;
