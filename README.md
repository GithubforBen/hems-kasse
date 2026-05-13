# Schulkasse · Kuchenverkauf

Ein Kassensystem für Schulkuchen-Verkäufe — basierend auf dem Claude-Design-Prototyp in `design/`.

- **Frontend:** Nuxt 3 (Vue 3, TypeScript, Pinia)
- **Backend:** Spring Boot 3.3, Java 21, Spring Security + JWT, JPA, Flyway
- **Datenbank:** PostgreSQL (H2 nur für Tests)
- **Login:** Klassen-Passwörter für Verkäufer:innen, persönliche Passwörter für Admins — beide aus `.env`
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
# .env bearbeiten: KASSE_CLASS_PASSWORDS, KASSE_ADMIN_USERS, KASSE_JWT_SECRET,
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
# .env anpassen: KASSE_CLASS_PASSWORDS, KASSE_ADMIN_USERS, KASSE_JWT_SECRET, KASSE_EPC_*

# Postgres bereitstellen (lokal)
createdb kasse

mvn spring-boot:run
# REST API läuft auf http://localhost:8080
```

Flyway erzeugt die Tabellen automatisch und legt die Standard-Kategorien (Kuchen, Muffins & Kekse, Herzhaft, Getränke) inklusive Produkten aus dem Prototyp an.

### Konfiguration (`.env`)

```
# Klassen-Passwörter (Verkauf): KLASSE:passwort, KLASSE:passwort
KASSE_CLASS_PASSWORDS=BG12e:Passw0rd,BG12f:Görner

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
| GET  | `/api/shifts?from&to&klasse&q` | ADMIN | alle Schichten |
| GET  | `/api/sales` | jeder eingeloggt | Verkäufe der aktuellen Schicht |
| POST | `/api/sales` | jeder eingeloggt | Verkauf buchen (Server prüft Totals) |
| GET  | `/api/me/pref`, PUT | jeder eingeloggt | Theme-Pref |
| GET  | `/api/payments/epc-qr.png?amountCents=…` | jeder eingeloggt | EPC-QR PNG |
| GET  | `/api/payments/epc-payload?amountCents=…` | jeder eingeloggt | Roher EPC-Text (Debug) |
| GET  | `/api/shifts/{id}/export.csv?type=…` | Besitzer ODER ADMIN | CSV einer einzelnen Schicht |
| GET  | `/api/shifts/mine/export.csv?type=…` | jeder eingeloggt | CSV aller eigenen Schichten |
| GET  | `/api/shifts/export.csv?type=…&from=&to=&klasse=&q=` | ADMIN | CSV aller Schichten (gefiltert) |

### CSV-Export-Typen (`?type=…`)

Vier vordefinierte Berichte, alle in Excel-freundlichem Format (UTF-8 mit BOM, `;` als Trenner, deutsche Dezimalkommas):

| Typ | Inhalt |
| --- | --- |
| `shifts` (Default für `mine`/`export.csv`) | Eine Zeile pro Schicht: Datum, Person, Klasse, Anfangsbestand, Umsatz Bar/Karte/Gesamt, **Soll/Ist/Diff**, Bons, Artikel, Anmerkungen |
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

```bash
# Backend
cd backend && mvn test

# Login + Katalog
TOKEN=$(curl -s -XPOST localhost:8080/api/auth/login \
  -H content-type:application/json \
  -d '{"role":"VERKAUF","name":"Timo","klasse":"BG12e","password":"Passw0rd"}' \
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
