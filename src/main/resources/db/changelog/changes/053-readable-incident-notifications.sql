--liquibase formatted sql
--changeset fleet-business:readable-incident-notifications-v1 splitStatements:true

-- Les notifications d'incident affichaient les codes techniques (« Incident ACCIDENT (CRITICAL) »).
-- Le service les rédige désormais en clair ; on réécrit les notifications déjà enregistrées.
UPDATE fleet.alert_events
SET title = '🚨 Incident grave signalé'
WHERE title = '🚨 Incident CRITIQUE signalé';

UPDATE fleet.alert_events
SET message = regexp_replace(regexp_replace(regexp_replace(regexp_replace(regexp_replace(regexp_replace(
              regexp_replace(regexp_replace(regexp_replace(regexp_replace(regexp_replace(
                  message,
                  '^Incident ACCIDENT ', 'Accident '),
                  '^Incident BREAKDOWN ', 'Panne '),
                  '^Incident THEFT ', 'Vol '),
                  '^Incident VANDALISM ', 'Vandalisme '),
                  '^Incident TRAFFIC_VIOLATION ', 'Infraction routière '),
                  '^Incident OTHER ', 'Autre incident '),
                  '\(LOW\)', '(gravité faible)'),
                  '\(MEDIUM\)', '(gravité moyenne)'),
                  '\(HIGH\)', '(gravité élevée)'),
                  '\(CRITICAL\)', '(gravité critique)'),
                  '\s+$', '')
WHERE trigger_type = 'INCIDENT_REPORTED' AND message ~ '^Incident [A-Z_]+ \((LOW|MEDIUM|HIGH|CRITICAL)\)';
