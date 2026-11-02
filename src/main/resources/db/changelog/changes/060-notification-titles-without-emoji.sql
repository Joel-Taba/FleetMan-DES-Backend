--liquibase formatted sql

-- Les titres de notifications enregistrés avec un emoji passent au texte seul : l'interface
-- porte ses propres icônes (migration 053 : « Incident grave signalé »).

--changeset fleetman:060-notification-titles-without-emoji
UPDATE fleet.notifications
SET title = TRIM(regexp_replace(title, '^[^[:alnum:]À-ÿ]+', ''))
WHERE title ~ '^[^[:alnum:]À-ÿ]';

--changeset fleetman:060-alert-event-titles-without-emoji
UPDATE fleet.alert_events
SET title = TRIM(regexp_replace(title, '^[^[:alnum:]À-ÿ]+', ''))
WHERE title ~ '^[^[:alnum:]À-ÿ]';
