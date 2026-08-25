-- Gruppen- und Admin-Konten wandern aus der .env in die Datenbank, damit Administratoren
-- sie im Admin-Bereich anlegen, umbenennen und mit neuen Passwörtern versehen können.
--
-- Das Passwort liegt AES-GCM-verschlüsselt (Schlüssel: KASSE_SECRET_KEY) statt gehasht,
-- weil der Passwort-Zettel jederzeit nachgedruckt werden können soll — dafür muss der
-- Klartext wieder herstellbar sein.
create table account (
    id           uuid         primary key,
    role         varchar(20)  not null,
    name         varchar(120) not null,
    password_enc varchar(500) not null,
    active       boolean      not null default true,
    created_at   timestamp with time zone not null,
    updated_at   timestamp with time zone not null
);

-- Anmeldung ist case-insensitiv, also darf es "Gruppe 1" und "gruppe 1" nicht nebeneinander geben.
create unique index uq_account_role_name on account (role, lower(name));
create index idx_account_role on account (role);
