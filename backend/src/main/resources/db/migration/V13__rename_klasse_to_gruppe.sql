-- "Klassen" wurden zu "Gruppen" (Gruppe 1, 2, 3 …) umbenannt.
-- Die Spalte trägt jetzt den neuen Namen; die Werte bleiben unverändert,
-- damit bestehende Schichten weiterhin ihrer Gruppe zugeordnet sind.
ALTER TABLE shift RENAME COLUMN klasse TO gruppe;
