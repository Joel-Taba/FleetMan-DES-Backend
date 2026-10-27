--liquibase formatted sql
--changeset fleet-business:trip-fuel-consumed-with-refuels-v1 splitStatements:true

-- Carburant consommé (L) = index départ + pleins enregistrés pendant le trajet − index retour.
-- L'ancien calcul (départ − retour) devenait négatif dès qu'un plein était fait en route.
-- On recalcule les valeurs négatives ; celles qui restent impossibles (plein non saisi,
-- index erroné) deviennent NULL plutôt que d'afficher une consommation négative.
UPDATE fleet.trips t
SET computed_fuel_consumed = CASE
        WHEN r.available >= t.return_fuel_index THEN r.available - t.return_fuel_index
        ELSE NULL
    END
FROM (
    SELECT tr.id,
           tr.departure_fuel_index + COALESCE((
               SELECT SUM(fr.quantity) FROM fleet.fuel_recharges fr
               WHERE fr.vehicle_id = tr.vehicle_id
                 AND fr.recharge_date_time >= (tr.start_date + COALESCE(tr.start_time, TIME '00:00'))
                 AND fr.recharge_date_time <= GREATEST(tr.return_registered_at,
                                                       tr.end_date + COALESCE(tr.end_time, TIME '23:59:59'))
           ), 0) AS available
    FROM fleet.trips tr
    WHERE tr.computed_fuel_consumed < 0
      AND tr.departure_fuel_index IS NOT NULL
      AND tr.return_fuel_index IS NOT NULL
) r
WHERE t.id = r.id;
