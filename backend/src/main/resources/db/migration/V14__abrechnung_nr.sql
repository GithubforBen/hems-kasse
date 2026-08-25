-- Abrechnungs-Nummer: die aufgedruckte Nummer des Geldumschlags, den eine Gruppe
-- für ihre Schicht bekommt. Wird beim Login eingegeben und verbindet die Abrechnung
-- mit dem physischen Umschlag.
alter table shift add column abrechnung_nr integer;

-- Einen Umschlag gibt es genau einmal, also ist die Nummer global eindeutig.
-- Altbestand vor dieser Einführung bleibt NULL und wird vom Index nicht erfasst.
create unique index uq_shift_abrechnung_nr
    on shift (abrechnung_nr)
    where abrechnung_nr is not null;
