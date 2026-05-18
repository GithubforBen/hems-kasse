# Offene Aufgaben vor dem Deployment

## Muss erledigt werden

### 1. `ShiftController.SaleLine` fehlt `transactionRef`

Der Endpunkt `GET /api/shifts/{id}` gibt Verkäufe über `SaleLine` zurück, das `transactionRef` noch nicht enthält.

**Backend** – `ShiftController.java`:
- `SaleLine`-Record um `String transactionRef` erweitern
- `toLine()`-Methode: `x.getTransactionRef()` im Konstruktor-Aufruf ergänzen

**Frontend** – `types/api.ts`:
- `ShiftDetailDto` nutzt ein inline-Typ für Sales (Zeile 63–74) — dort `transactionRef: string` hinzufügen

**Frontend** – `pages/schicht/[id].vue`:
- In der Bons-Liste (Zeile 119–130): für Kartenzahlungen die Transaktions-ID anzeigen,
  z.B. als kleiner Badge `#{{ s.transactionRef }}` neben der Zahlungsart

---

## Testen vor dem Deployment

### 2. Kartenzahlung Durchlauf testen

- [ ] Karte wählen → QR-Code lädt → Überweisungsgrund in der Banking-App enthält `#XXXXXXXX`
- [ ] „Zahlung erhalten" klicken → Kassenbon zeigt `Transaktions-ID  #XXXXXXXX`
- [ ] Dieselbe ID im CSV-Export (`verkaeufe.csv`, Spalte `Transaktions-ID`) prüfen
- [ ] Gleiche ID in `artikel.csv` prüfen

### 3. Barzahlung Durchlauf testen

- [ ] Bar-Zahlung abschließen → Kassenbon zeigt `Transaktions-ID  #XXXXXXXX`
- [ ] ID erscheint im CSV-Export

### 4. Flyway-Migration prüfen

Die Migration `V2__add_transaction_ref.sql` befüllt bestehende Zeilen rückwirkend:
```sql
UPDATE sale SET transaction_ref = UPPER(SUBSTRING(REPLACE(id::text, '-', ''), 1, 8));
```
- [ ] Auf einem Staging-System mit vorhandenen Daten testen, dass die Migration sauber durchläuft
- [ ] Sicherstellen, dass Flyway keinen Checksum-Fehler auf `V1__init.sql` wirft (sollte nicht passieren, da V1 unverändert ist)

### 5. EPC-Payload-Länge

Der Überweisungsgrund ist jetzt `{KASSE_EPC_REMITTANCE} · {8-Zeichen-ID}`, z.B.
`Kuchenverkauf · A1B2C3D4` = 24 Zeichen (Limit: 140).
- [ ] Sicherstellen, dass `KASSE_EPC_REMITTANCE` in der Produktions-Umgebung nicht annähernd 130 Zeichen lang ist
