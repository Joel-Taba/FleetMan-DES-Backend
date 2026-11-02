"""Essai : programmes, postes et trajets (migration 058).

Backend en mode Kernel (port 8081), comptes réels : manager.dupont (gestionnaire de la flotte
Douala) et driver.bella (chauffeuse de cette flotte). Mot de passe : DEMO_PASSWORD du fichier .env.

1. Programme : brouillon invisible du chauffeur ; publication → poste visible et notification.
2. Trajet : destination obligatoire ; rattaché au poste qui le couvre ; statuts du poste manuels
   limités à NO_SHOW / CANCELLED.
3. Départ : compteur et réservoir contrôlés ; le poste démarre ; annulation du poste refusée
   tant qu'un trajet est en cours.
4. Compléments de chargement (ajout / retrait) validés par le gestionnaire → manifeste mis à jour.
5. Arrivée à destination (quantités livrées), puis retour à la base.
6. Automatismes (tâche toutes les 5 min) : trajet non parti 2 h après l'heure prévue → MISSED ;
   poste terminé → COMPLETED avec les km de ses trajets.
Les données touchées sont restaurées à la fin.
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
            if isinstance(data, dict) and "data" in data and len(data) <= 4:
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
    # psql sort en 0 même si la requête échoue : une erreur muette fausserait l'essai.
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


def content(page):
    return page.get("content", []) if isinstance(page, dict) else (page or [])


MGR, DRV = login("manager.dupont"), login("driver.bella")
BELLA = sql("select id from fleet.users where username='driver.bella'")
DOUALA = sql("select id from fleet.fleets where name='Flotte Douala'")
V1 = sql("select id from fleet.vehicles where license_plate='DL-203-FF'")
# Repères de nettoyage pris dans les tables (horloge base UTC ≠ horloge application locale).
marks = {t: sql(f"select coalesce(max(created_at), 'epoch') from fleet.{t}")
         for t in ("trips", "assignments", "schedules", "notifications", "trip_mission_submissions")}
saved_vehicle = sql(f"select status || '|' || coalesce(tank_capacity::text,'') from fleet.vehicles where id='{V1}'")
saved_odo = sql(f"select odometer_reading from fleet.operational_parameters where vehicle_id='{V1}'")
odo = float(saved_odo or 0)
sql(f"update fleet.vehicles set tank_capacity=60 where id='{V1}'")
# Aucune pièce n'est en règle dans la base de démo : dossier valide le temps de l'essai.
for doc in ("INSURANCE", "TECHNICAL_CONTROL", "REGISTRATION"):
    sql(f"insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, expiry_date, file_url, review_status, is_current, "
        f"created_at, updated_at) values (gen_random_uuid(), 'VEHICLE', '{V1}', '{doc}', 'TEST-058-{doc}', current_date + 400, "
        f"'/api/v1/files/TEST-058.pdf', 'APPROVED', TRUE, now(), now())")
sql("insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, license_categories, expiry_date, file_url, "
    "review_status, is_current, created_at, updated_at) select gen_random_uuid(), 'DRIVER', d.user_id, 'DRIVING_LICENSE', 'TEST-058-LIC', 'B,C,D', "
    "current_date + 400, '/api/v1/files/TEST-058.pdf', 'APPROVED', TRUE, now(), now() from fleet.drivers d")

now = datetime.datetime.now().replace(second=0, microsecond=0)
shift_start = now + datetime.timedelta(minutes=10)
shift_end = now + datetime.timedelta(hours=6)
iso = lambda d: d.strftime("%Y-%m-%dT%H:%M:%S")
trip_ids = []
notif_keys = []

try:
    print("1. Programme publié aux chauffeurs")
    s, sched = call("POST", "/api/v1/schedules", {"fleetId": DOUALA, "title": "Essai 058", "periodType": "WEEKLY",
                                                   "startDate": now.date().isoformat(),
                                                   "endDate": (now.date() + datetime.timedelta(days=6)).isoformat()}, MGR)
    check("programme créé en brouillon", s == 201 and sched.get("status") == "DRAFT", f"({s} {code(sched)})")
    s, shift = call("POST", "/api/v1/assignments", {"scheduleId": sched["id"], "fleetId": DOUALA, "vehicleId": V1,
                                                    "driverId": BELLA, "startDatetime": iso(shift_start),
                                                    "endDatetime": iso(shift_end)}, MGR)
    check("poste créé (sans lieux ni km estimés)", s == 201 and "estimatedKm" not in shift and shift.get("tripCount") == 0,
          f"({s} {code(shift)})")
    SHIFT = shift["id"]
    notif_keys.append(sched["id"])
    s, page = call("GET", f"/api/v1/assignments/driver/{BELLA}?size=200", token=DRV)
    check("brouillon invisible du chauffeur", s == 200 and SHIFT not in [a["id"] for a in content(page)], f"({s})")
    s, r = call("PATCH", f"/api/v1/schedules/{sched['id']}/publish", token=MGR)
    s2, page = call("GET", f"/api/v1/assignments/driver/{BELLA}?size=200", token=DRV)
    notif = sql(f"select count(*) from fleet.notifications where user_id='{BELLA}' and data like '%{sched['id']}%'")
    check("publication : poste visible et chauffeur notifié", s == 200 and SHIFT in [a["id"] for a in content(page)]
          and notif == "1", f"({s} {s2} notif={notif})")

    print("2. Trajet rattaché au poste")
    base = {"vehicleId": V1, "driverId": BELLA, "fleetId": DOUALA,
            "startDate": (shift_start + datetime.timedelta(minutes=20)).date().isoformat(),
            "startTime": (shift_start + datetime.timedelta(minutes=20)).strftime("%H:%M:00"),
            "scheduledReturnDatetime": iso(shift_start + datetime.timedelta(hours=3)),
            "departureLocation": "Dépôt Bonabéri", "departureKmIndex": odo, "departureFuelIndex": 40,
            "missionObject": "Livraison essai 058",
            "details": [{"itemType": "CARGO", "description": "Cartons", "quantity": 10}]}
    s, r = call("POST", "/api/v1/trips", base, MGR)
    check("sans destination → refusé", s == 400, f"({s} {code(r)})")
    s, trip = call("POST", "/api/v1/trips", {**base, "destinationLocation": "Entrepôt Akwa"}, MGR)
    check("trajet créé, rattaché au poste", s == 201 and trip.get("assignmentId") == SHIFT
          and trip.get("destinationLocation") == "Entrepôt Akwa" and "missionCost" not in trip,
          f"({s} {code(trip)} {trip.get('assignmentId') if isinstance(trip, dict) else ''})")
    TRIP = trip["id"]
    trip_ids.append(TRIP)
    s, a = call("GET", f"/api/v1/assignments/{SHIFT}", token=MGR)
    check("le poste compte son trajet", s == 200 and a.get("tripCount") == 1, f"({s} {a.get('tripCount')})")
    s, r = call("PATCH", f"/api/v1/assignments/{SHIFT}/status", {"status": "IN_PROGRESS"}, MGR)
    check("démarrage manuel du poste → refusé", s == 422 and code(r) == "PLN_017", f"({s} {code(r)})")
    s, r = call("PATCH", f"/api/v1/assignments/{SHIFT}/status", {"status": "NO_SHOW"}, DRV)
    check("le chauffeur ne change pas le statut du poste", s == 403, f"({s})")

    print("3. Départ")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/start", {"departureKmIndex": max(odo - 10, 0)}, MGR)
    check("index km sous le compteur → refusé", s == 422 and code(r) == "TRP_021", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/start", {"departureFuelIndex": 80}, MGR)
    check("carburant au-delà du réservoir → refusé", s == 422 and code(r) == "TRP_022", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/start", {"departureKmIndex": odo + 5, "departureFuelIndex": 40}, MGR)
    shift_status = sql(f"select status from fleet.assignments where id='{SHIFT}'")
    check("départ : trajet parti, poste démarré", s == 200 and r.get("status") == "DEPARTED"
          and float(r.get("departureKmIndex")) == odo + 5 and shift_status == "IN_PROGRESS", f"({s} {code(r)} {shift_status})")
    s, r = call("PATCH", f"/api/v1/assignments/{SHIFT}/status", {"status": "CANCELLED"}, MGR)
    check("annuler le poste pendant un trajet → refusé", s == 409 and code(r) == "PLN_018", f"({s} {code(r)})")

    print("4. Compléments de chargement")
    detail_id = r_detail = sql(f"select id from fleet.trip_details where trip_id='{TRIP}'")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/submissions", {"changeType": "REMOVE", "itemType": "CARGO", "quantity": 2}, DRV)
    check("retrait sans ligne du manifeste → refusé", s == 422 and code(r) == "TRP_024", f"({s} {code(r)})")
    s, add_id = call("POST", f"/api/v1/trips/{TRIP}/submissions",
                     {"changeType": "ADD", "itemType": "CARGO", "description": "Colis ramassé", "quantity": 3}, DRV)
    s2, rem_id = call("POST", f"/api/v1/trips/{TRIP}/submissions",
                      {"changeType": "REMOVE", "detailId": detail_id, "itemType": "CARGO", "quantity": 2,
                       "description": "Cartons abîmés"}, DRV)
    check("ajout et retrait signalés par la chauffeuse", s == 201 and s2 == 201, f"({s} {s2})")
    s, subs = call("GET", f"/api/v1/trips/{TRIP}/submissions", token=MGR)
    check("le gestionnaire voit les deux signalements", s == 200 and len(subs or []) == 2, f"({s} {len(subs or [])})")
    s, r = call("PATCH", f"/api/v1/trips/submissions/{add_id}/approve", token=MGR)
    s2, r2 = call("PATCH", f"/api/v1/trips/submissions/{rem_id}/approve", token=MGR)
    lines = sql(f"select string_agg(description || ':' || quantity, ',' order by description) from fleet.trip_details where trip_id='{TRIP}'")
    check("validés : manifeste mis à jour (+3 colis, cartons 10 → 8)", s == 200 and s2 == 200
          and lines == "Cartons:8,Colis ramassé:3", f"({s} {s2} {lines})")
    s, r = call("PATCH", f"/api/v1/trips/submissions/{add_id}/reject", {"reason": "doublon"}, MGR)
    check("complément déjà traité → refusé", s == 404 and code(r) == "TRP_026", f"({s} {code(r)})")

    print("5. Arrivée puis retour")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/arrive", {"delivered": [{"detailId": detail_id, "deliveredQuantity": 11}]}, DRV)
    check("quantité livrée > chargée → refusée", s == 422 and code(r) == "TRP_025", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/arrive",
                {"notes": "Reçu par M. Ndoumbe", "delivered": [{"detailId": detail_id, "deliveredQuantity": 8}]}, DRV)
    delivered = sql(f"select delivered_quantity from fleet.trip_details where id='{detail_id}'")
    check("arrivée à destination enregistrée", s == 200 and r.get("status") == "ARRIVED"
          and r.get("arrivalLocation") == "Entrepôt Akwa" and delivered == "8", f"({s} {code(r)} {delivered})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/arrive", {}, DRV)
    check("deuxième arrivée → refusée", s == 409 and code(r) == "TRP_023", f"({s} {code(r)})")
    s, active = call("GET", "/api/v1/trips/my-active", token=DRV)
    check("le trajet arrivé reste le trajet actif de la chauffeuse", s == 200 and active.get("id") == TRIP, f"({s})")
    today = datetime.datetime.now()
    s, r = call("POST", "/api/v1/trips/return", {"tripCode": trip["tripCode"], "returnDate": today.date().isoformat(),
                                                 "returnTime": (today + datetime.timedelta(hours=1)).strftime("%H:%M"),
                                                 "returnLocation": "Dépôt Bonabéri", "returnKmIndex": odo + 47,
                                                 "returnFuelIndex": 30}, MGR)
    check("retour à la base enregistré", s == 200 and r.get("status") == "COMPLETED"
          and float(r.get("computedDistanceKm")) == 42, f"({s} {code(r)})")

    print("6. Automatismes du planning")
    s, late = call("POST", "/api/v1/trips", {**base, "destinationLocation": "Marché Central",
                                             "startDate": (shift_start + datetime.timedelta(hours=4)).date().isoformat(),
                                             "startTime": (shift_start + datetime.timedelta(hours=4)).strftime("%H:%M:00"),
                                             "scheduledReturnDatetime": iso(shift_start + datetime.timedelta(hours=5)),
                                             "details": []}, MGR)
    check("second trajet planifié", s == 201, f"({s} {code(late)})")
    if s == 201:
        trip_ids.append(late["id"])
        three_h_ago = datetime.datetime.now() - datetime.timedelta(hours=3)
        sql(f"update fleet.trips set start_date='{three_h_ago.date()}', start_time='{three_h_ago.strftime('%H:%M:00')}', "
            f"assignment_id=NULL where id='{late['id']}'")
    past = datetime.datetime.now()
    sql(f"update fleet.assignments set start_datetime='{iso(past - datetime.timedelta(hours=1))}', "
        f"end_datetime='{iso(past - datetime.timedelta(minutes=1))}' where id='{SHIFT}'")
    print("  … attente de la tâche automatique (5 min au plus)")
    deadline = time.time() + 330
    while time.time() < deadline:
        st = sql(f"select (select status from fleet.trips where id='{trip_ids[-1]}') || '|' || "
                 f"(select status || ':' || coalesce(actual_km::text,'') from fleet.assignments where id='{SHIFT}')")
        if st.startswith("MISSED|COMPLETED"):
            break
        time.sleep(10)
    check("trajet non parti 2 h après l'heure prévue → MISSED", st.split("|")[0] == "MISSED", f"({st})")
    check("poste terminé : COMPLETED, km = ceux de ses trajets", st.split("|")[1] == "COMPLETED:42.00", f"({st})")
    mgr_notif = sql(f"select count(*) from fleet.notifications where data like '%{trip_ids[-1]}%' and type='WARNING'")
    check("le gestionnaire est notifié du trajet manqué", mgr_notif == "1", f"({mgr_notif})")
finally:
    ids = ",".join(f"'{i}'" for i in trip_ids) or "NULL"
    sql(f"""
        delete from fleet.documents where doc_number like 'TEST-058-%';
        delete from fleet.trip_mission_submissions where created_at > '{marks['trip_mission_submissions']}';
        delete from fleet.trip_details where trip_id in ({ids});
        delete from fleet.trips where id in ({ids});
        delete from fleet.notifications where created_at > '{marks['notifications']}'
            and ({" or ".join(f"data like '%{k}%'" for k in notif_keys + trip_ids) or 'false'});
        delete from fleet.assignments where created_at > '{marks['assignments']}';
        delete from fleet.schedules where created_at > '{marks['schedules']}';
        update fleet.vehicles set status='{saved_vehicle.split('|')[0]}',
            tank_capacity={saved_vehicle.split('|')[1] or 'NULL'} where id='{V1}';
        update fleet.operational_parameters set odometer_reading={saved_odo or 'NULL'}, mileage={saved_odo or 'NULL'}
            where vehicle_id='{V1}';
    """)
    print(f"\n{sum(results)}/{len(results)} vérifications réussies")
