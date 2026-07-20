--liquibase formatted sql
--changeset fleet-trips:fix-trip-code-sequence-v1 splitStatements:true runOnChange:true

-- La séquence fleet.trip_code_seq peut être en retard sur des trip_code déjà
-- présents en base (ex. données de démo insérées directement en SQL avec des
-- codes explicites 'TRJ-2026-0001'..'0010' sans jamais appeler nextval()).
-- Dans ce cas, generate_trip_code() renvoie un code déjà utilisé, l'INSERT
-- échoue en doublon, et — la création de trajet étant transactionnelle — toute
-- tentative de nouvelle requête sur la même transaction échoue ensuite avec
-- "current transaction is aborted" (PostgreSQL exige un ROLLBACK avant de
-- pouvoir réexécuter quoi que ce soit). Ce correctif aligne la séquence sur le
-- plus grand numéro de code déjà utilisé, quelle que soit l'année.
SELECT setval(
    'fleet.trip_code_seq',
    GREATEST(
        (SELECT COALESCE(MAX(SUBSTRING(trip_code FROM 10)::int), 0)
         FROM fleet.trips
         WHERE trip_code ~ '^TRJ-[0-9]{4}-[0-9]+$'),
        (SELECT last_value FROM fleet.trip_code_seq)
    )
);
