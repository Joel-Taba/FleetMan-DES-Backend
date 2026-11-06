"""Essai bout en bout : suivi GPS en direct d'un trajet (chauffeur -> gestionnaire).

Backend en mode Kernel (port 8081), comptes réels : manager.dupont (gestionnaire des flottes Douala et
Yaoundé), driver.bella (flotte Douala), driver.nana (flotte Yaoundé). Mot de passe : ligne DEMO_PASSWORD=
du fichier .env (lu ici, jamais affiché). Nécessite la migration 072-trip-positions.

 1. Trajet planifié pour driver.bella : position refusée avant le départ (TRP_039).
 2. Départ enregistré par le gestionnaire ; la chauffeuse envoie sa position (acceptée).
 3. Point trop rapproché (< 15 s) ignoré sans erreur (accepted=false, rien en base) ; point suivant accepté.
 4. Coordonnées invalides refusées (TRP_038).
 5. Le gestionnaire lit la dernière position (vitesse, horodatage, plaque) et le tracé ; la carte de suivi
    (/live-positions) contient le trajet ; la dernière position du véhicule est à jour.
 6. Un autre chauffeur (driver.nana) ne peut ni envoyer ni lire la position (403).
 7. Gestionnaire hors flotte : trajet déplacé le temps du test dans une flotte qu'il ne gère pas (si elle
    existe) : lecture refusée, absent de /live-positions, filtre fleetId refusé.
 8. Historique borné : 200 points au plus par trajet.
 9. Après le retour à la base : position refusée (TRP_039), tracé toujours lisible par le gestionnaire.
Les données touchées sont restaurées à la fin (bloc finally).
"""
import datetime
import json
import os
import subprocess
import time
import urllib.error
import urllib.request

B = "http://localhost:8081"
ENV = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".env")
PW = next(l.split("=", 1)[1].strip() for l in open(ENV, encoding="utf-8") if l.startswith("DEMO_PASSWORD="))
results = []


def call(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(B + path, method=method, headers=headers,
                                 data=json.dumps(body).encode() if body is not None else None)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read()
            data = json.loads(raw) if raw else None
            if isinstance(data, dict) and "data" in data and len(data) <= 4 and "content" not in data:
                data = data["data"]
            return r.status, data
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read() or b"{}")
        except Exception:
            return e.code, {}


def sql(query):
    out = subprocess.run(["docker", "exec", "-i", "fleet-management-db", "sh", "-c",
                          'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'],
                         input=query, capture_output=True, text=True, check=True)
    if "ERROR:" in out.stderr:
        raise RuntimeError(out.stderr.strip())
    return out.stdout.strip()


def check(label, ok, detail=""):
    results.append(bool(ok))
    print(f"  {'✅' if ok else '❌'} {label} {detail}")


def code(r):
    return r.get("code") if isinstance(r, dict) else None


def login(identifier):
    s, d = call("POST", "/api/v1/auth/login", {"identifier": identifier, "password": PW})
    time.sleep(1)
    return d["accessToken"] if s == 200 else None


def send(token, trip_id, lat, lng, speed=None, accuracy=None):
    return call("POST", f"/api/v1/trips/{trip_id}/telemetry", {"lat": lat, "lng": lng, "speed": speed, "accuracy": accuracy}, token)


def point_count(trip_id):
    return int(sql(f"select count(*) from fleet.trip_positions where trip_id='{trip_id}'") or 0)


def age_last_point(trip_id, seconds=20):
    """Vieillit le dernier point (au lieu d'attendre 15 s) pour que le suivant soit accepté."""
    sql(f"update fleet.trips set last_position_at = last_position_at - interval '{seconds} seconds' where id='{trip_id}';"
        f"update fleet.trip_positions set recorded_at = recorded_at - interval '{seconds} seconds' where trip_id='{trip_id}';")


def live_rows(token, fleet_id=None):
    qs = f"?fleetId={fleet_id}" if fleet_id else ""
    s, r = call("GET", f"/api/v1/trips/live-positions{qs}", token=token)
    rows = r.get("content", []) if isinstance(r, dict) and "content" in r else r
    return s, (rows if isinstance(rows, list) else [])


MGR, DRV, NANA_T = login("manager.dupont"), login("driver.bella"), login("driver.nana")
MANAGER = sql("select id from fleet.users where username='manager.dupont'")
BELLA = sql("select id from fleet.users where username='driver.bella'")
NANA = sql("select id from fleet.users where username='driver.nana'")
DOUALA = sql("select id from fleet.fleets where name='Flotte Douala'")
V1 = sql("select id from fleet.vehicles where license_plate='DL-203-FF'")
V1_PLATE = "DL-203-FF"
# Flotte qu'aucun lien ne rattache à manager.dupont (gestionnaire, créateur ou organisation) : test « hors flotte ».
FOREIGN = sql(f"select id from fleet.fleets where deleted_at is null and coalesce(manager_id::text,'') <> '{MANAGER}' "
              f"and coalesce(created_by::text,'') <> '{MANAGER}' and coalesce(company_id::text,'') <> '{MANAGER}' "
              f"and not exists (select 1 from fleet.fleet_managers m where m.user_id = fleets.manager_id "
              f"and m.created_by_admin_id = '{MANAGER}') order by created_at limit 1")
# Repères pris AVANT l'essai (horloge de la base) pour le nettoyage.
marks = {t: sql(f"select coalesce(max(created_at), 'epoch') from fleet.{t}") for t in ("trips", "notifications")}
saved_vehicle = sql(f"select json_build_object('s', status, 't', tank_capacity, 'd', current_driver_id, 'lat', last_lat, 'lng', last_lng, "
                    f"'sp', last_speed, 'at', last_position_at, 'tr', last_position_trip_id) from fleet.vehicles where id='{V1}'")
saved_driver = sql(f"select coalesce(assigned_vehicle_id::text, '') from fleet.drivers where user_id='{BELLA}'")
saved_op = sql(f"select json_build_object('o', odometer_reading, 'm', mileage, 'sp', current_speed, 'ts', timestamp) "
               f"from fleet.operational_parameters where vehicle_id='{V1}'")
odo = float((json.loads(saved_op).get("o") if saved_op else 0) or 0)
sql(f"update fleet.vehicles set tank_capacity=60 where id='{V1}'")
for doc in ("INSURANCE", "TECHNICAL_CONTROL", "REGISTRATION"):
    sql(f"insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, expiry_date, file_url, review_status, is_current, "
        f"created_at, updated_at) select gen_random_uuid(), 'VEHICLE', '{V1}', '{doc}', 'TEST-072-{doc}', current_date + 400, "
        f"'/api/v1/files/TEST-072.pdf', 'APPROVED', TRUE, now(), now() "
        f"where not exists (select 1 from fleet.documents where target_id='{V1}' and doc_type='{doc}' and is_current)")
sql("insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, license_categories, expiry_date, file_url, "
    "review_status, is_current, created_at, updated_at) select gen_random_uuid(), 'DRIVER', d.user_id, 'DRIVING_LICENSE', 'TEST-072-LIC', "
    "'B,C,D', current_date + 400, '/api/v1/files/TEST-072.pdf', 'APPROVED', TRUE, now(), now() from fleet.drivers d "
    f"where d.user_id='{BELLA}' and not exists (select 1 from fleet.documents x where x.target_id=d.user_id "
    "and x.doc_type='DRIVING_LICENSE' and x.is_current)")

now = datetime.datetime.now().replace(second=0, microsecond=0)
start = now + datetime.timedelta(minutes=30)
ret = start + datetime.timedelta(hours=4)
iso = lambda d: d.strftime("%Y-%m-%dT%H:%M:%S")
trip_ids, trip_codes = [], []
moved_fleet = None

# Les plannings de démo occupent ces ressources en journée : leurs postes sont mis de côté le temps de l'essai.
parked = sql(f"select string_agg(id::text || ':' || status, ',') from fleet.assignments where status in ('PENDING','IN_PROGRESS') "
             f"and (driver_id in ('{BELLA}','{NANA}') or vehicle_id='{V1}') "
             f"and start_datetime < now() + interval '36 hours' and end_datetime > now() - interval '12 hours'")
if parked:
    sql(f"update fleet.assignments set status='CANCELLED' where id in ({','.join(repr(x.split(':')[0]) for x in parked.split(','))})")
try:
    print("1. Trajet planifié : pas encore de partage")
    body = {"vehicleId": V1, "driverId": BELLA, "fleetId": DOUALA,
            "startDate": start.date().isoformat(), "startTime": start.strftime("%H:%M:00"),
            "scheduledReturnDatetime": iso(ret), "departureLocation": "Dépôt Bonabéri",
            "departureKmIndex": odo, "departureFuelIndex": 40, "missionObject": "Essai 072 suivi GPS",
            "stops": [{"stopType": "DELIVERY", "location": "Boutique Akwa", "latitude": 4.0511, "longitude": 9.7085}], "details": []}
    s, trip = call("POST", "/api/v1/trips", body, MGR)
    check("gestionnaire : trajet planifié pour driver.bella", s == 201 and isinstance(trip, dict) and trip.get("id"), f"({s} {code(trip)})")
    T, CODE = trip["id"], trip["tripCode"]
    trip_ids.append(T)
    trip_codes.append(CODE)
    s, r = send(DRV, T, 4.0450, 9.7000, 30)
    check("chauffeuse : position refusée avant le départ (TRP_039)", s == 422 and code(r) == "TRP_039", f"({s} {code(r)})")
    s, r = call("GET", f"/api/v1/trips/{T}/positions", token=MGR)
    check("gestionnaire : aucune position encore partagée", s == 200 and r.get("last") is None and r.get("track") == [], f"({s})")

    print("2. Départ puis partage de position")
    s, r = call("POST", f"/api/v1/trips/{T}/start", {"departureKmIndex": odo, "departureFuelIndex": 40}, MGR)
    check("gestionnaire : départ enregistré", s == 200 and r.get("status") == "DEPARTED", f"({s} {code(r)})")
    s, ack = send(DRV, T, 4.0450, 9.7000, 30.5, 12)
    check("chauffeuse : premier point accepté", s == 200 and ack.get("accepted") is True and ack.get("outcome") == "RECORDED",
          f"({s} {ack})")
    check("base : un point enregistré", point_count(T) == 1)

    print("3. Limitation : un point par 15 s")
    s, ack = send(DRV, T, 4.0460, 9.7010, 31)
    check("point trop rapproché ignoré sans erreur", s == 200 and ack.get("accepted") is False and ack.get("outcome") == "TOO_SOON"
          and ack.get("retryInSeconds", 0) > 0, f"({s} {ack})")
    check("base : toujours un seul point", point_count(T) == 1)
    time.sleep(16)
    s, ack = send(DRV, T, 4.0475, 9.7032, 42.5, 8)
    check("point suivant (16 s plus tard) accepté", s == 200 and ack.get("accepted") is True, f"({s} {ack})")
    check("base : deux points", point_count(T) == 2)

    print("4. Coordonnées invalides")
    for lat, lng in ((95, 9.7), (4.0, -181), (None, 9.7)):
        age_last_point(T)
        s, r = send(DRV, T, lat, lng)
        check(f"position ({lat}, {lng}) refusée (TRP_038)", s == 400 and code(r) == "TRP_038", f"({s} {code(r)})")
    check("base : aucun point invalide enregistré", point_count(T) == 2)

    print("5. Lecture par le gestionnaire")
    s, pos = call("GET", f"/api/v1/trips/{T}/positions", token=MGR)
    last = (pos or {}).get("last") or {}
    track = (pos or {}).get("track") or []
    check("dernière position, vitesse et horodatage", s == 200 and abs(last.get("lat", 0) - 4.0475) < 1e-6
          and abs(last.get("lng", 0) - 9.7032) < 1e-6 and last.get("speed") == 42.5 and last.get("at"), f"({s} {last})")
    check("tracé du plus ancien au plus récent, plaque et statut", len(track) == 2 and track[0]["lat"] == 4.0450
          and track[1]["lat"] == 4.0475 and pos.get("licensePlate") == V1_PLATE and pos.get("status") == "DEPARTED",
          f"({len(track)} {pos.get('licensePlate')})")
    s, rows = live_rows(MGR)
    row = next((x for x in rows if x.get("tripId") == T), None)
    check("carte de suivi : trajet présent avec sa position et son tracé", s == 200 and row and row.get("last")
          and len(row.get("track") or []) == 2 and row.get("licensePlate") == V1_PLATE, f"({s} {bool(row)})")
    s, rows = live_rows(MGR, DOUALA)
    check("carte de suivi filtrée sur la flotte Douala", s == 200 and any(x.get("tripId") == T for x in rows), f"({s})")
    veh = sql(f"select last_position_trip_id || '|' || last_lat || '|' || last_speed from fleet.vehicles where id='{V1}'")
    check("véhicule : dernière position connue à jour", veh == f"{T}|4.0475|42.5", f"({veh})")
    s, r = call("GET", f"/api/v1/trips/{T}/positions", token=DRV)
    check("chauffeuse : peut relire sa propre position", s == 200 and (r.get("last") or {}).get("lat") == 4.0475, f"({s})")

    print("6. Un autre chauffeur est refusé")
    age_last_point(T)
    s, r = send(NANA_T, T, 4.05, 9.71, 20)
    check("driver.nana : envoi refusé sur le trajet de bella (403)", s == 403, f"({s} {code(r)})")
    check("base : aucun point de driver.nana", point_count(T) == 2)
    s, r = call("GET", f"/api/v1/trips/{T}/positions", token=NANA_T)
    check("driver.nana : lecture refusée (403)", s == 403, f"({s} {code(r)})")
    s, r = call("GET", "/api/v1/trips/live-positions", token=DRV)
    check("chauffeuse : carte de suivi réservée aux gestionnaires (403)", s == 403, f"({s})")

    print("7. Gestionnaire hors flotte")
    if not FOREIGN:
        print("  (passé : aucune flotte étrangère à manager.dupont)")
    else:
        moved_fleet = FOREIGN
        sql(f"update fleet.trips set fleet_id='{FOREIGN}' where id='{T}'")
        s, r = call("GET", f"/api/v1/trips/{T}/positions", token=MGR)
        check("trajet d'une flotte non gérée : lecture refusée (403)", s == 403, f"({s} {code(r)})")
        s, rows = live_rows(MGR)
        check("trajet d'une flotte non gérée : absent de la carte de suivi", s == 200 and not any(x.get("tripId") == T for x in rows))
        s, rows = live_rows(MGR, FOREIGN)
        check("filtre sur une flotte non gérée refusé (403)", s == 403, f"({s})")
        sql(f"update fleet.trips set fleet_id='{DOUALA}' where id='{T}'")
        moved_fleet = None

    print("8. Historique borné")
    sql(f"insert into fleet.trip_positions (trip_id, vehicle_id, driver_id, latitude, longitude, speed_kmh, recorded_at) "
        f"select '{T}', '{V1}', '{BELLA}', 4.0 + g / 10000.0, 9.7, 20, now() - interval '2 hours' + g * interval '10 seconds' "
        f"from generate_series(1, 210) g")
    age_last_point(T, 60)
    s, ack = send(DRV, T, 4.0500, 9.7100, 25)
    check("nouveau point accepté", s == 200 and ack.get("accepted") is True, f"({s} {ack})")
    n = point_count(T)
    check("au plus 200 points conservés pour le trajet", n == 200, f"({n})")
    s, pos = call("GET", f"/api/v1/trips/{T}/positions?limit=50", token=MGR)
    check("tracé limité à la demande, le dernier point en fin", s == 200 and len(pos.get("track") or []) == 50
          and pos["track"][-1]["lat"] == 4.05, f"({s} {len((pos or {}).get('track') or [])})")

    print("9. Après le retour à la base")
    later = datetime.datetime.now() + datetime.timedelta(minutes=2)
    s, r = call("POST", "/api/v1/trips/return", {"tripCode": CODE, "returnDate": later.date().isoformat(),
                                                  "returnTime": later.strftime("%H:%M:00"), "returnLocation": "Dépôt Bonabéri",
                                                  "returnKmIndex": odo + 12, "returnFuelIndex": 30}, MGR)
    check("gestionnaire : retour enregistré", s == 200 and r.get("status") == "COMPLETED", f"({s} {code(r)})")
    age_last_point(T)
    s, r = send(DRV, T, 4.0510, 9.7110, 10)
    check("chauffeuse : position refusée après la fin du trajet (TRP_039)", s == 422 and code(r) == "TRP_039", f"({s} {code(r)})")
    s, pos = call("GET", f"/api/v1/trips/{T}/positions", token=MGR)
    check("gestionnaire : parcours du trajet terminé toujours lisible", s == 200 and pos.get("status") == "COMPLETED"
          and (pos.get("last") or {}).get("lat") == 4.05, f"({s})")
    s, rows = live_rows(MGR)
    check("carte de suivi : trajet terminé retiré", s == 200 and not any(x.get("tripId") == T for x in rows))
finally:
    for x in (parked.split(",") if parked else []):
        sql(f"update fleet.assignments set status='{x.split(':')[1]}' where id='{x.split(':')[0]}'")
    trips = ",".join(f"'{i}'" for i in trip_ids) or "NULL"
    v = json.loads(saved_vehicle) if saved_vehicle else None
    op = json.loads(saved_op) if saved_op else None
    lit = lambda value: "NULL" if value is None else repr(str(value))
    restore = ""
    if v:
        restore += (f"update fleet.vehicles set status='{v['s']}', tank_capacity={v['t'] if v['t'] is not None else 'NULL'}, "
                    f"current_driver_id={lit(v['d'])}, last_lat={lit(v['lat'])}, last_lng={lit(v['lng'])}, last_speed={lit(v['sp'])}, "
                    f"last_position_at={lit(v['at'])}, last_position_trip_id={lit(v['tr'])} where id='{V1}';")
    if op:
        restore += (f"update fleet.operational_parameters set odometer_reading={lit(op['o'])}, mileage={lit(op['m'])}, "
                    f"current_speed={lit(op['sp'])}, timestamp={lit(op['ts'])} where vehicle_id='{V1}';")
    restore += f"update fleet.drivers set assigned_vehicle_id={lit(saved_driver or None)} where user_id='{BELLA}';"
    refs = [f"data like '%{i}%'" for i in trip_ids] + [f"message like '%{c}%'" for c in trip_codes]
    sql(f"""
        delete from fleet.documents where doc_number like 'TEST-072-%';
        delete from fleet.trip_positions where trip_id in ({trips});
        delete from fleet.trip_mission_submissions where trip_id in ({trips});
        delete from fleet.trip_details where trip_id in ({trips});
        delete from fleet.trip_stops where trip_id in ({trips});
        delete from fleet.trips where id in ({trips}) or (created_at > '{marks['trips']}' and mission_object = 'Essai 072 suivi GPS');
        delete from fleet.notifications where created_at > '{marks['notifications']}' and ({" or ".join(refs) or 'false'});
        {restore}
    """)
    print(f"\n{sum(results)}/{len(results)} vérifications réussies")
