#!/usr/bin/env python3
"""Séparation des flottes : un trajet, un poste ou un incident associe un chauffeur et un véhicule de la
même flotte ; les ressources proposées à la planification suivent la flotte du chauffeur ou du véhicule
choisi ; le gestionnaire change un chauffeur de flotte (parmi les siennes) pour lui confier les missions
de sa nouvelle flotte.

Prérequis : backend sur :8081, données de démo (scripts/seed_demo_data.py), DEMO_PASSWORD dans .env.
"""
import datetime
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

B = "http://localhost:8081"
PW = next(l.split("=", 1)[1].strip() for l in open(os.path.join(os.path.dirname(__file__), "..", ".env")) if l.startswith("DEMO_PASSWORD="))
results = []


def call(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(B + path, method=method, headers=headers, data=json.dumps(body).encode() if body is not None else None)
    try:
        with urllib.request.urlopen(req, timeout=90) as r:
            raw = r.read()
            data = json.loads(raw) if raw else None
            if isinstance(data, dict) and "data" in data and "success" in data:
                data = data["data"]
            return r.status, data
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read() or b"{}")
        except Exception:
            return e.code, {}


def sql(q):
    out = subprocess.run(["docker", "exec", "-i", "fleet-management-db", "sh", "-c", 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'],
                         input=q, capture_output=True, text=True, check=True)
    if "ERROR:" in out.stderr:
        raise RuntimeError(out.stderr.strip())
    return out.stdout.strip()


def check(label, ok, detail=""):
    results.append(bool(ok))
    print(f"  {'✅' if ok else '❌'} {label} {detail}")


def code(r):
    return r.get("code") if isinstance(r, dict) else None


def login(username):
    for _ in range(4):
        s, d = call("POST", "/api/v1/auth/login", {"identifier": username, "password": PW})
        if s == 200:
            return d["accessToken"]
        time.sleep(4)
    raise SystemExit(f"Connexion impossible ({username})")


MGR = login("manager.dupont")
uid = lambda u: sql(f"select id from fleet.users where username='{u}'")
vid = lambda p: sql(f"select id from fleet.vehicles where license_plate='{p}'")
fid = lambda n: sql(f"select id from fleet.fleets where name='{n}'")
YDE, DLA = fid("Flotte Yaoundé"), fid("Flotte Douala")
NANA, BELLA = uid("driver.nana"), uid("driver.bella")
CHRISTELLE, MOUSSA = uid("demo.christelle.ngono"), uid("demo.moussa.abdoulaye")
V_DLA, V_YDE = vid("DL-203-FF"), vid("YT-103-EE")
since = sql("select now()::timestamp")
saved_christelle = sql(f"select fleet_id || '|' || coalesce(assigned_vehicle_id::text, '') from fleet.drivers where user_id='{CHRISTELLE}'")
day = datetime.date.today() + datetime.timedelta(days=52)
while day.weekday() >= 5:
    day += datetime.timedelta(days=1)
trips = []
V_MOVE = vid("CE-782-YB")
saved_move_driver = sql(f"select coalesce(user_id::text, '') from fleet.drivers where assigned_vehicle_id='{V_MOVE}'")
trips_before = sql(f"select count(*) from fleet.trips where vehicle_id='{V_MOVE}' and fleet_id='{YDE}'")


def trip_body(vehicle, driver, fleet):
    return {"vehicleId": vehicle, "driverId": driver, "fleetId": fleet, "startDate": day.isoformat(), "startTime": "08:00:00",
            "scheduledReturnDatetime": f"{day}T12:00:00", "departureLocation": "Dépôt", "departureFuelIndex": 30,
            "missionObject": "Essai séparation des flottes",
            "stops": [{"stopType": "DELIVERY", "location": "Client essai", "plannedArrival": f"{day}T10:00:00"}], "details": []}


def avail(**extra):
    q = f"startDate={day}&startTime=08:00:00&scheduledReturn={day}T12:00:00" + "".join(f"&{k}={v}" for k, v in extra.items())
    return call("GET", f"/api/v1/trips/availability?{q}", token=MGR)


try:
    print("1. Trajet : chauffeur et véhicule de la même flotte")
    s, r = call("POST", "/api/v1/trips", trip_body(V_DLA, NANA, DLA), MGR)
    check("chauffeur de Yaoundé sur un véhicule de Douala → refusé", s == 422 and code(r) == "DRV_026", f"({s} {code(r)})")
    check("le message nomme les deux flottes", "Yaoundé" in (r.get("detail") or "") and "Douala" in (r.get("detail") or ""), f"({(r.get('detail') or '')[:90]})")
    s, r = call("POST", "/api/v1/trips", trip_body(V_DLA, BELLA, DLA), MGR)
    if s == 201:
        trips.append(r["id"])
    check("chauffeur et véhicule de Douala → trajet planifié", s == 201, f"({s} {code(r)} {(r or {}).get('detail', '')[:80] if isinstance(r, dict) else ''})")

    print("2. Poste : même règle")
    s, r = call("POST", "/api/v1/assignments", {"fleetId": DLA, "vehicleId": V_DLA, "driverId": NANA,
                                                "startDatetime": f"{day + datetime.timedelta(days=1)}T08:00:00",
                                                "endDatetime": f"{day + datetime.timedelta(days=1)}T16:00:00"}, MGR)
    check("poste avec un chauffeur d'une autre flotte → refusé", s == 422 and code(r) == "DRV_026", f"({s} {code(r)})")

    print("3. Ressources proposées à la planification")
    s, r = avail(driverId=NANA)
    fleets = {v.get("fleetId") for v in (r or {}).get("vehicles", [])}
    check("chauffeur de Yaoundé choisi → seuls des véhicules de Yaoundé", s == 200 and fleets <= {YDE} and len(fleets) == 1, f"({s} {len((r or {}).get('vehicles', []))} véhicules)")
    s, r = avail(vehicleId=V_DLA)
    fleets = {d.get("fleetId") for d in (r or {}).get("drivers", [])}
    check("véhicule de Douala choisi → seuls des chauffeurs de Douala", s == 200 and fleets <= {DLA}, f"({s} {len((r or {}).get('drivers', []))} chauffeurs)")
    s, r = avail(fleetId=YDE)
    check("flotte choisie → ressources de cette flotte seulement",
          s == 200 and {v["fleetId"] for v in r["vehicles"]} <= {YDE} and {d["fleetId"] for d in r["drivers"]} <= {YDE})

    print("4. Incident : chauffeur de la flotte du véhicule")
    s, r = call("POST", "/api/v1/operations/incidents", {"type": "OTHER", "severity": "LOW", "description": "Essai séparation",
                                                          "vehicleId": V_DLA, "driverId": NANA, "reportedBy": "Essai"}, MGR)
    check("incident avec un chauffeur d'une autre flotte → refusé", s == 422 and code(r) == "DRV_026", f"({s} {code(r)})")

    print("5. Changement de flotte d'un chauffeur")
    s, r = call("PUT", f"/api/v1/drivers/{MOUSSA}", {"fleetId": DLA}, MGR)
    check("chauffeur avec des postes à venir → changement refusé", s == 409 and code(r) == "DRV_006", f"({s} {code(r)})")
    s, r = call("PUT", f"/api/v1/drivers/{CHRISTELLE}", {"fleetId": DLA}, MGR)
    moved = sql(f"select fleet_id || '|' || coalesce(assigned_vehicle_id::text, '') from fleet.drivers where user_id='{CHRISTELLE}'")
    check("chauffeur libre → rejoint la flotte de Douala", s == 200 and moved.split("|")[0] == DLA, f"({s} {code(r)})")
    check("son véhicule attitré de Yaoundé lui est retiré", moved.split("|")[1] == "")
    check("le chauffeur est notifié du changement", int(sql(f"select count(*) from fleet.notifications where user_id='{CHRISTELLE}' "
                                                           f"and data like '%DRIVER_FLEET_CHANGED%' and created_at > '{since}'")) >= 1)
    s, r = avail(driverId=CHRISTELLE)
    check("elle se voit proposer les véhicules de Douala", s == 200 and {v["fleetId"] for v in r["vehicles"]} <= {DLA} and len(r["vehicles"]) > 0)
    s, r = call("POST", "/api/v1/trips", trip_body(V_YDE, CHRISTELLE, YDE), MGR)
    check("un véhicule de son ancienne flotte → refusé", s == 422 and code(r) == "DRV_026", f"({s} {code(r)})")
    s, r = call("PUT", f"/api/v1/drivers/{CHRISTELLE}", {"fleetId": "00000000-0000-4000-8000-000000000000"}, MGR)
    check("flotte hors de son périmètre → refusé", s in (403, 404), f"({s} {code(r)})")

    print("6. Changement de flotte d'un véhicule : son historique reste dans l'ancienne flotte")
    s, r = call("PUT", f"/api/v1/vehicles/{V_MOVE}", {"fleetId": DLA}, MGR)
    check("véhicule libre → rejoint la flotte de Douala", s == 200 and sql(f"select fleet_id from fleet.vehicles where id='{V_MOVE}'") == DLA, f"({s} {code(r)})")
    check("son chauffeur attitré lui est retiré", sql(f"select count(*) from fleet.drivers where assigned_vehicle_id='{V_MOVE}'") == "0")
    check("ses trajets passés restent à Yaoundé", sql(f"select count(*) from fleet.trips where vehicle_id='{V_MOVE}' and fleet_id='{YDE}'") == trips_before
          and int(trips_before) > 0, f"({trips_before})")
    s, incs = call("GET", "/api/v1/operations/incidents", token=MGR)
    mine = [i for i in (incs or []) if i.get("vehicleId") == V_MOVE]
    check("ses incidents restent rattachés à Yaoundé", s == 200 and mine and all(i.get("fleetId") == YDE for i in mine), f"({len(mine)})")
    s, ms = call("GET", "/api/v1/operations/maintenances", token=MGR)
    mine = [m for m in (ms or []) if m.get("vehicleId") == V_MOVE]
    check("ses entretiens aussi", s == 200 and mine and all(m.get("fleetId") == YDE for m in mine), f"({len(mine)})")
    s, r = avail(vehicleId=V_MOVE)
    check("il est désormais proposé avec les chauffeurs de Douala seulement", s == 200 and {d["fleetId"] for d in r["drivers"]} <= {DLA},
          f"({s} {len((r or {}).get('drivers', []))} chauffeurs)")
    s, r = call("POST", "/api/v1/trips", trip_body(V_MOVE, NANA, DLA), MGR)
    check("un chauffeur de Yaoundé ne peut plus le conduire", s == 422 and code(r) == "DRV_026", f"({s} {code(r)})")
finally:
    if V_MOVE:
        sql(f"update fleet.vehicles set fleet_id='{YDE}', current_driver_id={repr(saved_move_driver) if saved_move_driver else 'NULL'} where id='{V_MOVE}'")
        if saved_move_driver:
            sql(f"update fleet.drivers set assigned_vehicle_id='{V_MOVE}' where user_id='{saved_move_driver}'")
    for t in trips:
        sql(f"delete from fleet.trip_details where trip_id='{t}'; delete from fleet.trip_stops where trip_id='{t}'; delete from fleet.trips where id='{t}'")
    fleet, vehicle = (saved_christelle.split("|") + [""])[:2]
    sql(f"update fleet.drivers set fleet_id='{fleet}', assigned_vehicle_id={repr(vehicle) if vehicle else 'NULL'} where user_id='{CHRISTELLE}'")
    if vehicle:
        sql(f"update fleet.vehicles set current_driver_id='{CHRISTELLE}' where id='{vehicle}'")
    sql(f"delete from fleet.notifications where created_at > '{since}' and (data like '%DRIVER_FLEET_CHANGED%' or data like '%TRIP_%' "
        f"or data like '%Essai séparation%')")

print(f"\n{sum(results)}/{len(results)} vérifications réussies")
sys.exit(0 if all(results) else 1)
