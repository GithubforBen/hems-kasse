# Schulkasse · Kuchenverkauf

Ein Kassensystem für Schulkuchen-Verkäufe — basierend auf dem Claude-Design-Prototyp in `design/`.

- **Frontend:** Nuxt 3 (Vue 3, TypeScript, Pinia)
- **Backend:** Spring Boot 3.3, Java 21, Spring Security + JWT, JPA, Flyway
- **Datenbank:** PostgreSQL (H2 nur für Tests)
- **Login:** Gruppen-Passwörter für Verkäufer:innen, persönliche Passwörter für Admins — im Admin-Bereich verwaltet
- **Abrechnungs-Nr.:** beim Login wird die Nummer des Geldumschlags eingegeben; jede Abrechnung hängt an genau einem Umschlag
- **Karte = SEPA-Überweisung per EPC-QR (Girocode)** — der Backend rendert das PNG selbst (ZXing)
- **Schichthistorie:** Jede Person sieht eigene Abschlüsse, Admin sieht alle (mit Filtern)

## Projektstruktur

```
hems-kasse/
├── design/                  # Original-Prototyp (HTML/CSS/JSX, read-only)
├── backend/                 # Spring Boot
│   ├── pom.xml
│   ├── .env.example
│   └── src/main/...
└── frontend/                # Nuxt 3
    ├── package.json
    ├── nuxt.config.ts
    ├── .env.example
    └── app.vue, pages/, components/, stores/, utils/, assets/css/
```

## Schnellstart mit Docker (empfohlen)

```bash
cp .env.example .env
# .env bearbeiten: KASSE_SECRET_KEY, KASSE_GROUP_PASSWORDS, KASSE_ADMIN_USERS, KASSE_JWT_SECRET,
#                   KASSE_EPC_NAME / KASSE_EPC_IBAN, optional POSTGRES_PASSWORD …

docker compose up --build
# Frontend → http://localhost:3000
# Backend  → http://localhost:8080
# Postgres → localhost:5432 (Volume: kasse-pgdata, bleibt zwischen Neustarts erhalten)
```

Drei Services werden gebaut und gestartet:

| Service   | Image                            | Was       | Port |
| --------- | -------------------------------- | --------- | ---- |
| `db`      | `postgres:16-alpine`             | Datenbank | 5432 |
| `backend` | `schulkasse-backend:latest`      | Spring Boot (`backend/Dockerfile`, mehrstufig) | 8080 |
| `frontend`| `schulkasse-frontend:latest`     | Nuxt 3 (`frontend/Dockerfile`, mehrstufig) | 3000 |

Compose liest die Variablen aus `./.env`. Mit `docker compose down -v` werden auch die Daten gelöscht; `docker compose logs -f backend` zeigt Live-Logs.

Mehrere Hosts? Setze `NUXT_PUBLIC_API_BASE` und `KASSE_CORS_ORIGINS` in der `.env` auf die öffentlichen URLs (z. B. `https://kasse.example.org`).

## Lokale Entwicklung ohne Docker

### Voraussetzungen

- Java 21, Maven 3.9+
- Node 22+, pnpm (oder npm)
- PostgreSQL 14+ erreichbar (siehe `backend/.env.example`)

### Backend starten

```bash
cd backend
cp .env.example .env
# .env anpassen: KASSE_SECRET_KEY, KASSE_GROUP_PASSWORDS, KASSE_ADMIN_USERS, KASSE_JWT_SECRET, KASSE_EPC_*

# Postgres bereitstellen (lokal)
createdb kasse

mvn spring-boot:run
# REST API läuft auf http://localhost:8080
```

Flyway erzeugt die Tabellen automatisch und legt die Standard-Kategorien (Kuchen, Muffins & Kekse, Herzhaft, Getränke) inklusive Produkten aus dem Prototyp an.

### Gruppen & Logins (Admin-Bereich)

Unter **Admin → Gruppen & Logins** legen Administratoren Gruppen und weitere Admin-Konten an,
benennen sie um, deaktivieren sie und vergeben neue Passwörter.

- Beim Anlegen einer Gruppe wird automatisch ein Passwort erzeugt (10 Zeichen, ohne die leicht
  zu verwechselnden `0/O`, `1/l/I`) und sofort der Passwort-Zettel geöffnet.
- **Passwort-Zettel** lassen sich jederzeit neu drucken — einzeln oder gesammelt für alle
  Gruppen bzw. alle Konten. Jeder Zettel zeigt Name, Passwort im Klartext und einen QR-Code.
- Der **QR-Code** öffnet die Kasse mit vorausgefüllter Gruppe und Passwort. An der Kasse müssen
  nur noch Name und Abrechnungs-Nr. eingetragen werden. Die Zugangsdaten stehen im
  URL-Fragment (`#login=…`), das vom Browser nie an den Server geschickt wird — sie landen also
  in keinem Server- oder Proxy-Log. Die Login-Seite entfernt sie sofort aus der Adresszeile.
- Ein **neues Passwort macht alte Zettel ungültig** — der QR-Code darauf funktioniert nicht mehr.
- Das letzte aktive Admin-Konto lässt sich weder löschen noch deaktivieren, und niemand kann sein
  eigenes Konto löschen. Damit kann sich niemand selbst aussperren.
- Gelöschte Gruppen ändern nichts an der Historie: Schichten speichern den Gruppennamen als Text.

**Speicherung:** Die Passwörter liegen AES-256-GCM-verschlüsselt in der Datenbank (Schlüssel:
`KASSE_SECRET_KEY`), damit Zettel jederzeit nachgedruckt werden können. Ein Datenbank-Backup
allein gibt sie nicht preis; wer Datenbank **und** Schlüssel hat, kann sie lesen. Wird
`KASSE_SECRET_KEY` nachträglich geändert, sind alle gespeicherten Passwörter unlesbar und müssen
im Admin-Bereich neu erzeugt werden.

**Erstbefüllung:** Beim ersten Start werden `KASSE_GROUP_PASSWORDS` und `KASSE_ADMIN_USERS` in die
Datenbank übernommen, damit bestehende Installationen weiterlaufen. Danach ist die Datenbank
maßgeblich — im Admin-Bereich geänderte Passwörter werden von den `.env`-Werten nicht wieder
überschrieben. Existiert noch kein Konto mit dem Namen aus der `.env`, wird es beim Start ergänzt.

### Abrechnungs-Nr. (Geldumschläge)

Jede Schicht rechnet auf genau einen nummerierten Geldumschlag ab. Die Nummer wird beim Login
eingegeben und mit der Abrechnung verbunden — sie taucht in der Kopfzeile der Kasse, in der
Schichthistorie und in allen CSV-Exporten auf.

Regeln (serverseitig durchgesetzt, ein Umschlag existiert nur einmal):

- Ganze Zahl von 1 bis 999999.
- **Abgeschlossen ist abgeschlossen.** Eine Nummer, deren Abrechnung abgeschlossen wurde, kann
  nicht erneut verwendet werden — weder durch einen neuen Login noch durch ein Tab, das nach dem
  Abschluss neu geladen wird. In beiden Fällen landet man mit einer Meldung auf dem Login.
- **Gemeinsam kassieren.** Mehrere Personen derselben Gruppe können sich an derselben Kassette mit
  derselben Nummer anmelden und teilen sich die laufende Abrechnung.
- **Falscher Umschlag wird abgewiesen.** Läuft an der Kassette bereits eine andere Nummer, oder ist
  der Umschlag anderswo im Einsatz, wird die Anmeldung mit einer erklärenden Meldung abgelehnt.

Nach dem Abschluss endet die Sitzung bewusst: die nächste Schicht meldet sich mit dem nächsten
Umschlag an.

### Konfiguration (`.env`)

```
# Verschlüsselt die Konto-Passwörter (≥ 32 Zeichen) — `openssl rand -base64 48`
KASSE_SECRET_KEY=...

# Gruppen-Passwörter (Verkauf): GRUPPE:passwort, GRUPPE:passwort
# Nur Erstbefüllung — danach werden Logins im Admin-Bereich verwaltet.
KASSE_GROUP_PASSWORDS=1:Passw0rd,2:Görner

# Admin-Logins: user:passwort, user:passwort
KASSE_ADMIN_USERS=alice:adminPW1,bob:adminPW2

# JWT (HS256, ≥ 32 Bytes empfohlen) — z. B. `openssl rand -base64 48`
KASSE_JWT_SECRET=...
KASSE_JWT_TTL_HOURS=12

# Datenbank
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/kasse
SPRING_DATASOURCE_USERNAME=kasse
SPRING_DATASOURCE_PASSWORD=...

# Karte → EPC-QR (Girocode) Begünstigte:r
KASSE_EPC_NAME=Schulkasse Beispielschule
KASSE_EPC_IBAN=DE12500105170648489890
KASSE_EPC_BIC=
KASSE_EPC_PURPOSE=
KASSE_EPC_REMITTANCE=Kuchenverkauf

# CORS
KASSE_CORS_ORIGINS=http://localhost:3000
```

Die IBAN wird beim Start gegen ISO 7064 (mod-97) geprüft — eine ungültige IBAN führt zu einem Fehlstart, sodass die Kartenzahlung nicht stillschweigend kaputtgeht.

### Frontend starten

```bash
cd frontend
cp .env.example .env       # NUXT_PUBLIC_API_BASE setzen, falls nicht localhost:8080
pnpm install
pnpm dev
# UI läuft auf http://localhost:3000
```

Produktion: `pnpm build` und `node .output/server/index.mjs` (oder als statisches SPA mit `pnpm generate`).

## REST-Endpoints (Kurzüberblick)

| Methode | Pfad | Rolle | Beschreibung |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | öffentlich | Verkauf-/Admin-Login |
| GET  | `/api/auth/me` | jeder eingeloggt | aktueller User |
| GET  | `/api/categories` | jeder eingeloggt | Kategorien mit Produkten |
| POST/PATCH/DELETE | `/api/categories[...]` | ADMIN | CRUD |
| POST/PATCH/DELETE | `/api/products[...]` | ADMIN | CRUD |
| GET  | `/api/shifts/current` | jeder eingeloggt | aktive Schicht (wird bei Bedarf eröffnet) |
| PATCH| `/api/shifts/current` | jeder eingeloggt | Anfangsbestand/Notizen |
| POST | `/api/shifts/current/close` | jeder eingeloggt | Schicht abschließen |
| GET  | `/api/shifts/mine` | jeder eingeloggt | eigene archivierte Schichten |
| GET  | `/api/shifts/{id}` | Besitzer ODER ADMIN | Schichtdetails |
| GET  | `/api/shifts?from&to&gruppe&abrechnungNr&q` | ADMIN | alle Schichten |
| GET  | `/api/sales` | jeder eingeloggt | Verkäufe der aktuellen Schicht |
| POST | `/api/sales` | jeder eingeloggt | Verkauf buchen (Server prüft Totals) |
| GET  | `/api/accounts` | ADMIN | Gruppen & Admin-Logins (ohne Passwörter) |
| POST | `/api/accounts` | ADMIN | Konto anlegen (leeres Passwort ⇒ erzeugt) |
| PATCH| `/api/accounts/{id}` | ADMIN | umbenennen / aktivieren |
| POST | `/api/accounts/{id}/password` | ADMIN | Passwort setzen oder erzeugen |
| DELETE | `/api/accounts/{id}` | ADMIN | Konto löschen |
| GET  | `/api/accounts/slips?ids=…` | ADMIN | Zetteldaten inkl. Klartext-Passwort |
| GET  | `/api/me/pref`, PUT | jeder eingeloggt | Theme-Pref |
| GET  | `/api/payments/epc-qr.png?amountCents=…` | jeder eingeloggt | EPC-QR PNG |
| GET  | `/api/payments/epc-payload?amountCents=…` | jeder eingeloggt | Roher EPC-Text (Debug) |
| GET  | `/api/shifts/{id}/export.csv?type=…` | Besitzer ODER ADMIN | CSV einer einzelnen Schicht |
| GET  | `/api/shifts/mine/export.csv?type=…` | jeder eingeloggt | CSV aller eigenen Schichten |
| GET  | `/api/shifts/export.csv?type=…&from=&to=&gruppe=&abrechnungNr=&q=` | ADMIN | CSV aller Schichten (gefiltert) |

### CSV-Export-Typen (`?type=…`)

Vier vordefinierte Berichte, alle in Excel-freundlichem Format (UTF-8 mit BOM, `;` als Trenner, deutsche Dezimalkommas):

| Typ | Inhalt |
| --- | --- |
| `shifts` (Default für `mine`/`export.csv`) | Eine Zeile pro Schicht: Datum, **Abrechnung**, Person, Gruppe, Anfangsbestand, Umsatz Bar/Karte/Gesamt, **Soll/Ist/Diff**, Bons, Artikel, Anmerkungen |
| `sales` | Eine Zeile pro Bon: Datum, Uhrzeit, Bon-Nr., Zahlungsart, Summe, Gegeben, Rückgeld, Artikel-Liste |
| `items` (Default für `{id}`) | Eine Zeile pro Kassenposition (am detailliertesten): Produkt, Menge, Einzelpreis, Zeilensumme |
| `products` | Aggregat je Produkt: Rang, Menge, Anteil%, Umsatz, Anteil%, Ø-Preis, Bon-Anzahl — beantwortet *„Was wurde verkauft?"* |

Beispiel:
```bash
curl -OJ "localhost:8080/api/shifts/<id>/export.csv?type=products" \
  -H "Authorization: Bearer $TOKEN"
# → speichert "schicht-<short>-produkte.csv"
```

## Smoke-Test

> **Bekannte Einschränkung:** `mvn test` läuft aktuell nicht durch. Die Tests sind auf H2
> konfiguriert, das Schema braucht aber zwei PostgreSQL-Features ohne H2-Entsprechung:
> `split_part()` in `V4` und die partiellen Unique-Indizes in `V5`/`V14`
> (`create unique index … where …`). Für einen grünen Lauf die Datenquelle in
> `backend/src/test/resources/application-test.yml` auf eine echte PostgreSQL-Instanz zeigen
> lassen (Kommentar dort) — oder Testcontainers einrichten.

```bash
# Backend
cd backend && mvn test

# Login + Katalog
TOKEN=$(curl -s -XPOST localhost:8080/api/auth/login \
  -H content-type:application/json \
  -d '{"role":"VERKAUF","name":"Timo","gruppe":"1","abrechnungNr":1,"password":"Passw0rd"}' \
  | jq -r .token)
curl -s localhost:8080/api/categories -H "Authorization: Bearer $TOKEN" | jq .

# EPC-QR für 3,50 €
curl -s "localhost:8080/api/payments/epc-payload?amountCents=350" \
  -H "Authorization: Bearer $TOKEN"
# → liefert Zeilen "BCD … SCT … <IBAN> EUR3.50 … Kuchenverkauf"
```

## Sicherheitshinweise

- Passwörter stehen im Klartext im Backend-`.env` und werden **nie** zum Frontend ausgeliefert. Die Datei nicht commiten (`.gitignore` deckt das ab).
- JWTs sind kurzlebig (Default 12 h, konfigurierbar) und werden im Browser in einem `SameSite=Lax`-Cookie + In-Memory-Mirror gehalten.
- Geldbeträge laufen serverseitig als ganzzahlige Cents — keine Float-Drift.
- Karten-Stage = SEPA-Überweisung per QR — die UI bestätigt erst nach „Zahlung erhalten" durch das Personal; eine Webhook-Bestätigung der Bank gibt es (noch) nicht.

## Lizenz

Internes Schulprojekt — keine offene Lizenz.
