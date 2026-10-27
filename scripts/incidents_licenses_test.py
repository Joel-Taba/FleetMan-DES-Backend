"""Essai : permis de conduire et effets des incidents (backend fake-auth, port 8081).

1. Permis : un chauffeur dont le permis ne couvre que B est refusé sur un camion (DRV_011),
   puis accepté une fois la catégorie C ajoutée.
2. Incident grave pendant un trajet : véhicule immobilisé (MAINTENANCE), trajet interrompu,
   remise en service manuelle refusée (VHC_012), véhicule de remplacement → trajet repris,
   résolution de l'incident → véhicule d'origine de nouveau disponible.

Lancer le backend avec SPRING_PROFILES_ACTIVE=local,demo,fake-auth. Le script remet les
données dans leur état initial à la fin (via psql dans le conteneur fleet-management-db).
"""
import datetime
import json
import subprocess
import urllib.error
import urllib.request

B = "http://localhost:8081"
H = {"Authorization": "Bearer fake-token-manager1@fleetman.cm", "Content-Type": "application/json"}


def call(method, path, body=None):
    req = urllib.request.Request(B + path, method=method, headers=H,
                                 data=json.dumps(body).encode() if body is not None else None)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read()
            data = json.loads(raw) if raw else None
            return r.status, (data.get("data", data) if isinstance(data, dict) else data)
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"{}")


def sql(query):
    out = subprocess.run(["docker", "exec", "fleet-management-db", "sh", "-c",
                          f'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "{query}"'],
                         capture_output=True, text=True, check=True)
    return out.stdout.strip()


def items(x):
    return x.get("content", x) if isinstance(x, dict) else x


def check(label, condition, detail=""):
    print(f"  {'✅' if condition else '❌'} {label} {detail}")
    return condition


_, fleets = call("GET", "/api/v1/fleets")
fleet_ids = {f["id"] for f in items(fleets)}
_, vehicles = call("GET", "/api/v1/vehicles")
_, drivers = call("GET", "/api/v1/drivers")
vehicles = [v for v in items(vehicles) if v.get("fleetId") in fleet_ids and v.get("status") == "AVAILABLE"]
drivers = items(drivers)
type_by_vehicle = dict(line.split("|") for line in sql(
    "select v.id, coalesce(vt.code,'') from fleet.vehicles v left join fleet.vehicle_types vt "
    "on vt.id = v.vehicle_type_id").splitlines())

truck = next(v for v in vehicles if type_by_vehicle.get(v["id"]) == "TRUCK")
fleet_drivers = [d for d in drivers if d.get("fleetId") == truck["fleetId"]]
driver = fleet_drivers[0]
driver_id = driver.get("userId") or driver.get("id")
other_vehicle = next(v for v in vehicles if v["fleetId"] == truck["fleetId"] and v["id"] != truck["id"])
day = (datetime.date.today() + datetime.timedelta(days=5)).isoformat()
created_trips = []
saved_categories = sql(f"select string_agg(id || '=' || coalesce(license_categories,''), ';') "
                       f"from fleet.driver_documents where driver_id='{driver_id}' and doc_type='DRIVING_LICENSE'")

try:
    print("1. Permis de conduire")
    sql(f"update fleet.driver_documents set license_categories='B' "
        f"where driver_id='{driver_id}' and doc_type='DRIVING_LICENSE'")
    body = {"vehicleId": truck["id"], "driverId": driver_id, "startDate": day, "startTime": "08:00:00",
            "scheduledReturnDatetime": f"{day}T12:00:00", "departureKmIndex": 1000, "departureFuelIndex": 30}
    s, r = call("POST", "/api/v1/trips", body)
    check("permis B seul sur un camion → refusé", s == 422 and r.get("code") == "DRV_011", f"({s} {r.get('code')})")
    sql(f"update fleet.driver_documents set license_categories='B,C' "
        f"where driver_id='{driver_id}' and doc_type='DRIVING_LICENSE'")
    s, r = call("POST", "/api/v1/trips", body)
    if check("permis B,C sur un camion → accepté", s in (200, 201), f"({s})"):
        created_trips.append(r["id"])

    print("2. Incident grave pendant un trajet")
    now = datetime.datetime.now()
    s, trip = call("POST", "/api/v1/trips", {
        "vehicleId": truck["id"], "driverId": driver_id,
        "startDate": now.date().isoformat(), "startTime": now.strftime("%H:%M:%S"),
        "scheduledReturnDatetime": (now + datetime.timedelta(hours=3)).strftime("%Y-%m-%dT%H:%M:%S"),
        "departureKmIndex": 1000, "departureFuelIndex": 30})
    check("trajet créé", s in (200, 201), f"({s} {trip.get('code') if s >= 400 else ''})")
    created_trips.append(trip["id"])
    s, trip = call("POST", f"/api/v1/trips/{trip['id']}/start")
    check("départ enregistré", s == 200 and trip.get("status") == "DEPARTED", f"({s})")

    s, incident = call("POST", "/api/v1/operations/incidents", {
        "type": "ACCIDENT", "severity": "CRITICAL", "description": "Essai automatisé",
        "vehicleId": truck["id"], "driverId": driver_id, "cost": 0})
    check("incident CRITICAL déclaré", s in (200, 201), f"({s})")
    status = sql(f"select status from fleet.vehicles where id='{truck['id']}'")
    check("véhicule immobilisé", status == "MAINTENANCE", f"({status})")
    _, t = call("GET", f"/api/v1/trips/{trip['id']}")
    check("trajet interrompu, toujours ouvert", t.get("interrupted") and t.get("status") == "DEPARTED",
          f"(interrupted={t.get('interrupted')}, status={t.get('status')})")

    s, r = call("PATCH", f"/api/v1/vehicles/{truck['id']}", {"status": "AVAILABLE"})
    check("remise en service manuelle refusée", s == 409 and r.get("code") == "VHC_012", f"({s} {r.get('code')})")

    sql(f"update fleet.driver_documents set license_categories='B,C,D' "
        f"where driver_id='{driver_id}' and doc_type='DRIVING_LICENSE'")
    s, t = call("PATCH", f"/api/v1/trips/{trip['id']}/vehicle", {"newVehicleId": other_vehicle["id"]})
    check("véhicule de remplacement → mission reprise", s == 200 and not t.get("interrupted")
          and t.get("resumedAt"), f"({s} {t.get('code') if s >= 400 else ''})")

    s, r = call("PATCH", f"/api/v1/operations/incidents/{incident['id']}/status", {"status": "RESOLVED"})
    check("incident résolu", s == 200, f"({s})")
    status = sql(f"select status from fleet.vehicles where id='{truck['id']}'")
    check("véhicule d'origine de nouveau disponible", status == "AVAILABLE", f"({status})")
finally:
    # Remise en état : trajets, incident d'essai, dépenses auto, permis et statuts.
    for trip_id in created_trips:
        sql(f"delete from fleet.trips where id='{trip_id}'")
    sql("delete from fleet.expenses where source_id in "
        "(select id from fleet.incidents where description='Essai automatisé')")
    sql("delete from fleet.incidents where description='Essai automatisé'")
    for pair in filter(None, (saved_categories or "").split(";")):
        doc_id, cats = pair.split("=", 1)
        sql(f"update fleet.driver_documents set license_categories=nullif('{cats}','') where id='{doc_id}'")
    sql(f"update fleet.vehicles set status='AVAILABLE' where id in ('{truck['id']}','{other_vehicle['id']}')")
    print("  ↺ données d'essai supprimées")
