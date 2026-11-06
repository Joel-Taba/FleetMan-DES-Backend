"""Essai bout en bout : interactions gestionnaire ↔ chauffeur sur les trajets (tournées multi-arrêts).

Backend en mode Kernel (port 8081) avec APPLICATION_FILE_MODE=local, comptes réels : manager.dupont
(gestionnaire des flottes Douala et Yaoundé), driver.bella (flotte Douala), driver.kouam (rattaché à Douala le temps de l'essai : un trajet ne passe qu'à un chauffeur de sa flotte).
Mot de passe : ligne DEMO_PASSWORD= du fichier .env (lu ici, jamais affiché).

Chaque vérification contrôle les deux côtés : l'action du rôle X, puis ce que voit le rôle Y (listes
ou fiche lues avec son jeton) et la notification reçue par Y (GET /api/v1/notifications : `type` et
clé d'identifiant dans `data`).

 1. Planification → chauffeur : TRIP_ASSIGNED, trajet visible avec arrêts, horaire, mission, manifeste.
 2. Modifications avant le départ → TRIP_UPDATED / STOP_ADDED / STOP_UPDATED, manifeste éditable.
 3. Changement de chauffeur → TRIP_UNASSIGNED à l'ancien, TRIP_ASSIGNED au nouveau ; changement de véhicule.
 4. Départ (gestionnaire seul) → TRIP_DEPARTED, trajet actif du chauffeur ; l'auteur n'est pas notifié.
 5. En route : écart de quantités (STOP_SHORTAGE), complément signalé (SUBMISSION_CREATED) puis validé
    (SUBMISSION_APPROVED) ou refusé (SUBMISSION_REJECTED), arrêt urgent ajouté (STOP_ADDED) puis passé
    (STOP_SKIPPED), arrivée en retard (STOP_LATE_ARRIVAL), arrêt non servi (TRIP_STOP_FAILED), fin de
    tournée (TRIP_TOUR_DONE, une seule fois).
 6. Retour à la base → TRIP_COMPLETED, historique du chauffeur.
 7. Annulation (TRIP_CANCELLED avec motif) et suppression d'un trajet planifié (TRIP_DELETED).
 8. Trajet manqué (automatisme toutes les 5 min) → TRIP_MISSED au gestionnaire ET au chauffeur.
    Attente jusqu'à 6 min ; `--skip-missed` pour passer cette partie.
Les données touchées sont restaurées à la fin (bloc finally).
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
ENV = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".env")
PW = next(l.split("=", 1)[1].strip() for l in open(ENV, encoding="utf-8") if l.startswith("DEMO_PASSWORD="))
SKIP_MISSED = "--skip-missed" in sys.argv
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


def notifications(token):
    s, page = call("GET", "/api/v1/notifications?page=0&size=100", token=token)
    items = page.get("content", []) if isinstance(page, dict) else (page or [])
    for n in items:
        data = n.get("data")
        if not isinstance(data, dict):
            try:
                data = json.loads(n.get("dataJson") or "{}")
            except Exception:
                data = {}
        n["_data"] = data
    return items


def notified(token, event, key=None, value=None, contains=None):
    """Notifications de `token` portant ce type (et cet identifiant, ou ce texte)."""
    out = []
    for n in notifications(token):
        d = n["_data"]
        if d.get("type") != event:
            continue
        if key and str(d.get(key)) != str(value):
            continue
        if contains and contains not in (n.get("message") or ""):
            continue
        out.append(n)
    return out


def my_history(token):
    s, r = call("GET", "/api/v1/trips/my-history", token=token)
    return r if s == 200 and isinstance(r, list) else []


def my_trip(token, trip_id):
    return next((t for t in my_history(token) if t.get("id") == trip_id), None)


def my_active(token):
    s, r = call("GET", "/api/v1/trips/my-active", token=token)
    return r if s == 200 and isinstance(r, dict) and r.get("id") else None


def submissions(trip_id, token):
    s, r = call("GET", f"/api/v1/trips/{trip_id}/submissions", token=token)
    return r if s == 200 and isinstance(r, list) else []


MGR, DRV, NANA_T = login("manager.dupont"), login("driver.bella"), login("driver.kouam")
MANAGER = sql("select id from fleet.users where username='manager.dupont'")
BELLA = sql("select id from fleet.users where username='driver.bella'")
NANA = sql("select id from fleet.users where username='driver.kouam'")
DOUALA = sql("select id from fleet.fleets where name='Flotte Douala'")
# Second chauffeur de la même flotte que le trajet (séparation des flottes) : driver.kouam, rattaché à Douala le temps de l'essai.
saved_second_fleet = sql(f"select coalesce(fleet_id::text, '') from fleet.drivers where user_id='{NANA}'")
sql(f"update fleet.drivers set fleet_id='{DOUALA}' where user_id='{NANA}'")
V1 = sql("select id from fleet.vehicles where license_plate='DL-203-FF'")
V2 = sql(f"select id from fleet.vehicles where fleet_id='{DOUALA}' and id<>'{V1}' and deleted_at is null "
         f"and status='AVAILABLE' order by license_plate limit 1")
V2_PLATE = sql(f"select license_plate from fleet.vehicles where id='{V2}'") if V2 else ""
VEHICLES = ",".join(f"'{v}'" for v in (V1, V2) if v)
# Repères pris AVANT l'essai (horloge de la base) : l'app écrit en heure locale, la base en UTC.
marks = {t: sql(f"select coalesce(max(created_at), 'epoch') from fleet.{t}") for t in ("trips", "notifications")}
saved_vehicles = sql(f"select json_agg(json_build_object('id', id, 's', status, 't', tank_capacity, 'd', current_driver_id)) "
                     f"from fleet.vehicles where id in ({VEHICLES})")
saved_drivers = sql(f"select json_agg(json_build_object('id', user_id, 'v', assigned_vehicle_id)) from fleet.drivers "
                    f"where user_id in ('{BELLA}','{NANA}')")
saved_odo = sql(f"select odometer_reading from fleet.operational_parameters where vehicle_id='{V1}'")
odo = float(saved_odo or 0)
sql(f"update fleet.vehicles set tank_capacity=60 where id in ({VEHICLES})")
for v in [x for x in (V1, V2) if x]:
    for doc in ("INSURANCE", "TECHNICAL_CONTROL", "REGISTRATION"):
        sql(f"insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, expiry_date, file_url, review_status, is_current, "
            f"created_at, updated_at) select gen_random_uuid(), 'VEHICLE', '{v}', '{doc}', 'TEST-068-{doc}', current_date + 400, "
            f"'/api/v1/files/TEST-068.pdf', 'APPROVED', TRUE, now(), now() "
            f"where not exists (select 1 from fleet.documents where target_id='{v}' and doc_type='{doc}' and is_current)")
sql("insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, license_categories, expiry_date, file_url, "
    "review_status, is_current, created_at, updated_at) select gen_random_uuid(), 'DRIVER', d.user_id, 'DRIVING_LICENSE', 'TEST-068-LIC', 'B,C,D', "
    "current_date + 400, '/api/v1/files/TEST-068.pdf', 'APPROVED', TRUE, now(), now() from fleet.drivers d "
    "where not exists (select 1 from fleet.documents x where x.target_id=d.user_id and x.doc_type='DRIVING_LICENSE' and x.is_current)")

now = datetime.datetime.now().replace(second=0, microsecond=0)
start = now + datetime.timedelta(minutes=30)
ret = start + datetime.timedelta(hours=6)
iso = lambda d: d.strftime("%Y-%m-%dT%H:%M:%S")
trip_ids, trip_codes = [], []


def later():
    return datetime.datetime.now() + datetime.timedelta(minutes=2)


def plan(st, rt, mission, stops, details, driver=None, vehicle=None):
    body = {"vehicleId": vehicle or V1, "driverId": driver or BELLA, "fleetId": DOUALA,
            "startDate": st.date().isoformat(), "startTime": st.strftime("%H:%M:00"),
            "scheduledReturnDatetime": iso(rt), "departureLocation": "Dépôt Bonabéri",
            "departureKmIndex": odo, "departureFuelIndex": 40, "missionObject": mission,
            "stops": stops, "details": details}
    s, t = call("POST", "/api/v1/trips", body, MGR)
    if s == 201 and isinstance(t, dict) and t.get("id"):
        trip_ids.append(t["id"])
        trip_codes.append(t["tripCode"])
    return s, t


# Les plannings de démo occupent ces ressources en journée : leurs postes sont mis de côté le temps de l'essai.
parked = sql(f"select string_agg(id::text || ':' || status, ',') from fleet.assignments where status in ('PENDING','IN_PROGRESS') "
             f"and (driver_id in ('{BELLA}','{NANA}') or vehicle_id in ({VEHICLES})) "
             f"and start_datetime < now() + interval '36 hours' and end_datetime > now() - interval '12 hours'")
if parked:
    sql(f"update fleet.assignments set status='CANCELLED' where id in ({','.join(repr(x.split(':')[0]) for x in parked.split(','))})")
try:
    print("1. Planification → chauffeur")
    stops = [
        {"stopType": "DELIVERY", "location": "Boutique Akwa", "contactName": "Awa", "contactPhone": "+237 690 00 00 01",
         "plannedArrival": iso(start + datetime.timedelta(hours=1))},
        {"stopType": "DELIVERY", "location": "Supermarché Bonapriso", "plannedArrival": iso(start + datetime.timedelta(hours=3))},
    ]
    lines = [{"itemType": "CARGO", "description": "Cartons de lait", "quantity": 10, "stopIndex": 0},
             {"itemType": "CARGO", "description": "Sacs de riz", "quantity": 8, "stopIndex": 1}]
    s, trip = plan(start, ret, "Tournée essai 068", stops, lines)
    check("gestionnaire : tournée de 2 arrêts planifiée", s == 201 and len(trip.get("stops", [])) == 2, f"({s} {code(trip)})")
    T, CODE = trip["id"], trip["tripCode"]
    SA, SB = [x["id"] for x in trip["stops"]]
    n = notified(DRV, "TRIP_ASSIGNED", "tripId", T)
    check("chauffeuse : notification TRIP_ASSIGNED (tripId)", len(n) == 1 and CODE in n[0]["message"], f"({len(n)})")
    mine = my_trip(DRV, T)
    check("chauffeuse : trajet visible avec arrêts, horaire, mission et manifeste",
          mine and mine["status"] == "SCHEDULED" and len(mine.get("stops", [])) == 2 and mine.get("missionObject") == "Tournée essai 068"
          and len([d for d in mine.get("details", []) if d.get("stopId")]) == 2 and mine.get("scheduledReturnDatetime"),
          f"({mine and mine.get('status')})")
    check("gestionnaire auteur : pas notifié de sa propre planification", not notified(MGR, "TRIP_ASSIGNED", "tripId", T))

    print("2. Modifications avant le départ")
    new_start = start + datetime.timedelta(minutes=15)
    s, r = call("PATCH", f"/api/v1/trips/{T}", {"startTime": new_start.strftime("%H:%M:00"), "missionObject": "Tournée essai 068 bis",
                                                "scheduledReturnDatetime": iso(ret + datetime.timedelta(hours=1))}, MGR)
    check("gestionnaire : horaire, retour prévu et mission modifiés", s == 200 and r.get("missionObject") == "Tournée essai 068 bis", f"({s} {code(r)})")
    n = notified(DRV, "TRIP_UPDATED", "tripId", T)
    check("chauffeuse : TRIP_UPDATED résumant les changements", n and "départ déplacé" in n[0]["message"] and "mission" in n[0]["message"],
          f"({n[0]['message'] if n else '-'})")
    mine = my_trip(DRV, T)
    check("chauffeuse : nouvel horaire et nouvelle mission visibles",
          mine and mine.get("startTime", "")[:5] == new_start.strftime("%H:%M") and mine.get("missionObject") == "Tournée essai 068 bis")
    s, r = call("PATCH", f"/api/v1/trips/{T}", {"missionObject": "Tournée essai 068 bis"}, MGR)
    check("modification sans changement : aucune notification de plus", len(notified(DRV, "TRIP_UPDATED", "tripId", T)) == 1)

    s, D = call("POST", f"/api/v1/trips/{T}/stops", {"stop": {"stopType": "PICKUP", "location": "Fournisseur Bassa"}, "position": 1}, MGR)
    check("gestionnaire : arrêt ajouté", s == 200 and D.get("id"), f"({s} {code(D)})")
    n = notified(DRV, "STOP_ADDED", "stopId", D.get("id"))
    check("chauffeuse : STOP_ADDED (tripId + stopId)", len(n) == 1 and n[0]["_data"].get("tripId") == T)
    s, r = call("PUT", f"/api/v1/trips/{T}/stops/{SB}", dict(stops[1], contactName="M. Ekane"), MGR)
    check("chauffeuse : STOP_UPDATED après modification d'un arrêt",
          s == 200 and len(notified(DRV, "STOP_UPDATED", "stopId", SB)) == 1, f"({s} {code(r)})")
    s, r = call("PUT", f"/api/v1/trips/{T}/stops/order", {"stopIds": [SA, SB, D["id"]]}, MGR)
    check("gestionnaire : ordre de passage changé", s == 200, f"({s} {code(r)})")
    s, r = call("DELETE", f"/api/v1/trips/{T}/stops/{D['id']}", token=MGR)
    upd = notified(DRV, "TRIP_UPDATED", "tripId", T)
    check("chauffeuse : ordre puis retrait d'arrêt notifiés", s == 200 and any("ordre de passage" in x["message"] for x in upd)
          and any("retiré de la tournée" in x["message"] for x in upd), f"({s} {len(upd)})")
    mine = my_trip(DRV, T)
    check("chauffeuse : tournée à jour (2 arrêts, contact modifié)", mine and len(mine["stops"]) == 2
          and mine["stops"][1].get("contactName") == "M. Ekane")

    s, r = call("POST", f"/api/v1/trips/{T}/details", {"stopId": SB, "itemType": "CARGO", "description": "Packs d'eau", "quantity": 6}, MGR)
    check("gestionnaire : ligne ajoutée au manifeste", s in (200, 201, 204), f"({s} {code(r)})")
    mine = my_trip(DRV, T)
    water = next((d for d in (mine or {}).get("details", []) if d.get("description") == "Packs d'eau"), None)
    check("chauffeuse : ligne visible sur l'arrêt et notifiée", water and water.get("stopId") == SB
          and any("Packs d'eau" in x["message"] for x in notified(DRV, "TRIP_UPDATED", "tripId", T)))
    s, r = call("PUT", f"/api/v1/trips/{T}/details/{water['id']}", {"itemType": "CARGO", "description": "Packs d'eau", "quantity": 4}, MGR)
    mine = my_trip(DRV, T)
    check("gestionnaire : quantité modifiée, vue par la chauffeuse", s in (200, 204)
          and any(d.get("id") == water["id"] and d.get("quantity") == 4 for d in mine.get("details", [])), f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/trips/{T}/details", {"stopId": SB, "itemType": "CARGO", "quantity": 1}, DRV)
    check("manifeste : modification refusée au chauffeur", s == 403, f"({s})")
    s, r = call("POST", f"/api/v1/trips/{T}/details", {"stopId": SB, "itemType": "BOXES", "quantity": 1}, MGR)
    check("manifeste : type inconnu refusé", s == 422 and code(r) == "TRP_037", f"({s} {code(r)})")

    print("3. Changement de chauffeur et de véhicule")
    s, r = call("PATCH", f"/api/v1/trips/{T}", {"driverId": NANA}, MGR)
    check("gestionnaire : trajet confié à driver.kouam", s == 200 and r.get("driverId") == NANA, f"({s} {code(r)})")
    check("ancienne chauffeuse : TRIP_UNASSIGNED et trajet retiré de sa liste",
          len(notified(DRV, "TRIP_UNASSIGNED", "tripId", T)) == 1 and my_trip(DRV, T) is None)
    check("nouveau chauffeur : TRIP_ASSIGNED et trajet visible",
          len(notified(NANA_T, "TRIP_ASSIGNED", "tripId", T)) == 1 and my_trip(NANA_T, T) is not None)
    s, r = call("PATCH", f"/api/v1/trips/{T}/driver", {"newDriverId": BELLA}, MGR)
    check("retour à driver.bella : chacun prévenu", s == 200 and len(notified(NANA_T, "TRIP_UNASSIGNED", "tripId", T)) == 1
          and len(notified(DRV, "TRIP_ASSIGNED", "tripId", T)) == 2, f"({s} {code(r)})")
    if V2:
        s, r = call("PATCH", f"/api/v1/trips/{T}/vehicle", {"newVehicleId": V2}, MGR)
        check("changement de véhicule : la chauffeuse reçoit la nouvelle plaque", s == 200
              and any(V2_PLATE in x["message"] for x in notified(DRV, "TRIP_UPDATED", "tripId", T)), f"({s} {code(r)})")
        s, r = call("PATCH", f"/api/v1/trips/{T}/vehicle", {"newVehicleId": V1}, MGR)
        check("véhicule d'origine rétabli", s == 200 and my_trip(DRV, T).get("vehicleId") == V1, f"({s})")

    print("4. Départ (gestionnaire seul)")
    s, r = call("POST", f"/api/v1/trips/{T}/start", {"departureKmIndex": odo, "departureFuelIndex": 40}, DRV)
    check("le chauffeur ne peut pas enregistrer le départ", s == 403, f"({s})")
    s, r = call("POST", f"/api/v1/trips/{T}/start", {"departureKmIndex": odo, "departureFuelIndex": 40}, MGR)
    check("gestionnaire : départ enregistré", s == 200 and r.get("status") == "DEPARTED", f"({s} {code(r)})")
    check("chauffeuse : TRIP_DEPARTED", len(notified(DRV, "TRIP_DEPARTED", "tripId", T)) == 1)
    act = my_active(DRV)
    check("chauffeuse : c'est son trajet actif", act and act.get("id") == T and act.get("status") == "DEPARTED")
    check("gestionnaire auteur : pas notifié de son propre départ", not notified(MGR, "TRIP_DEPARTED", "tripId", T))

    print("5. En route : actions de la chauffeuse et décisions du gestionnaire")
    milk = next(d["id"] for d in act["details"] if d.get("description") == "Cartons de lait")
    s, r = call("POST", f"/api/v1/trips/{T}/stops/{SA}/arrive", {}, DRV)
    check("chauffeuse : arrivée à l'heure, aucune alerte de retard", s == 200 and not notified(MGR, "STOP_LATE_ARRIVAL", "stopId", SA), f"({s})")
    s, r = call("POST", f"/api/v1/trips/{T}/stops/{SA}/complete",
                {"recipientName": "Awa", "notes": "Cartons abîmés", "proofUrl": "/api/v1/files/preuve-essai-068.jpg",
                 "lines": [{"detailId": milk, "quantity": 7, "shortageReason": "DAMAGED"}]}, DRV)
    check("chauffeuse : arrêt 1 livré avec écart motivé", s == 200 and r.get("status") == "COMPLETED", f"({s} {code(r)})")
    check("gestionnaire : STOP_SHORTAGE (tripId + stopId)", len(notified(MGR, "STOP_SHORTAGE", "stopId", SA)) == 1)
    s, t = call("GET", f"/api/v1/trips/{T}", token=MGR)
    stop_a = next(x for x in t["stops"] if x["id"] == SA)
    line = next(d for d in t["details"] if d["id"] == milk)
    check("gestionnaire : voit réceptionnaire, remarques, preuve, horodatage, ponctualité et quantités",
          stop_a.get("recipientName") == "Awa" and stop_a.get("notes") == "Cartons abîmés" and stop_a.get("proofUrl")
          and stop_a.get("arrivedAt") and stop_a.get("completedAt") and stop_a.get("punctuality")
          and line.get("deliveredQuantity") == 7 and line.get("shortageReason") == "DAMAGED")

    s, sid = call("POST", f"/api/v1/trips/{T}/submissions", {"changeType": "ADD", "itemType": "CARGO", "description": "Sacs de ciment",
                                                             "quantity": 3, "notes": "Chargés au marché", "stopId": SB}, DRV)
    sub_id = sid if isinstance(sid, str) else (sid or {}).get("id") if isinstance(sid, dict) else None
    check("chauffeuse : complément de chargement signalé", s in (200, 201) and sub_id, f"({s} {code(sid)})")
    check("gestionnaire : SUBMISSION_CREATED (tripId + submissionId)", len(notified(MGR, "SUBMISSION_CREATED", "submissionId", sub_id)) == 1)
    pending = [x for x in submissions(T, MGR) if x["id"] == sub_id]
    check("gestionnaire : complément en attente avec les remarques du chauffeur",
          pending and pending[0]["status"] == "PENDING" and pending[0].get("notes") == "Chargés au marché")
    s, r = call("PATCH", f"/api/v1/trips/submissions/{sub_id}/approve", {}, MGR)
    check("gestionnaire : complément validé", s == 200, f"({s} {code(r)})")
    check("chauffeuse : SUBMISSION_APPROVED (tripId + submissionId)", len(notified(DRV, "SUBMISSION_APPROVED", "submissionId", sub_id)) == 1)
    act = my_active(DRV)
    check("chauffeuse : complément validé et ligne ajoutée à son trajet actif",
          any(x["id"] == sub_id and x["status"] == "APPROVED" for x in submissions(T, DRV))
          and any(d.get("description") == "Sacs de ciment" and d.get("stopId") == SB for d in act.get("details", [])))
    rice = next(d["id"] for d in act["details"] if d.get("description") == "Sacs de riz")
    s, sid2 = call("POST", f"/api/v1/trips/{T}/submissions", {"changeType": "REMOVE", "detailId": rice, "itemType": "CARGO",
                                                              "description": "Sacs de riz", "quantity": 2}, DRV)
    sub2 = sid2 if isinstance(sid2, str) else None
    s, r = call("PATCH", f"/api/v1/trips/submissions/{sub2}/reject", {"reason": "Quantité non confirmée"}, MGR)
    check("gestionnaire : retrait refusé avec motif", s in (200, 204), f"({s} {code(r)})")
    n = notified(DRV, "SUBMISSION_REJECTED", "submissionId", sub2)
    check("chauffeuse : SUBMISSION_REJECTED avec le motif, visible dans son trajet",
          len(n) == 1 and "Quantité non confirmée" in n[0]["message"]
          and any(x["id"] == sub2 and x["status"] == "REJECTED" and x.get("rejectionReason") == "Quantité non confirmée"
                  for x in submissions(T, DRV)))

    s, E = call("POST", f"/api/v1/trips/{T}/stops", {"stop": {"stopType": "PICKUP", "location": "Ramassage urgent Ndokoti"}}, MGR)
    check("gestionnaire : ramassage urgent ajouté en route", s == 200 and E.get("id"), f"({s} {code(E)})")
    n = notified(DRV, "STOP_ADDED", "stopId", E.get("id"))
    check("chauffeuse : STOP_ADDED (alerte) et arrêt dans son trajet actif", len(n) == 1 and n[0].get("type") == "WARNING"
          and any(x["id"] == E["id"] for x in my_active(DRV)["stops"]))
    s, r = call("POST", f"/api/v1/trips/{T}/stops/{E['id']}/skip", {"reason": "Client annulé"}, MGR)
    act = my_active(DRV)
    skipped = next((x for x in act["stops"] if x["id"] == E["id"]), {})
    check("chauffeuse : STOP_SKIPPED avec motif, arrêt marqué passé", s == 200 and len(notified(DRV, "STOP_SKIPPED", "stopId", E["id"])) == 1
          and skipped.get("status") == "SKIPPED" and skipped.get("failureNotes") == "Client annulé", f"({s})")

    # Heure prévue 60 min avant l'heure applicative (locale) : retour hors tolérance quelle que soit la base.
    local_planned = (datetime.datetime.now() - datetime.timedelta(minutes=60)).strftime("%Y-%m-%d %H:%M:%S")
    sql(f"update fleet.trip_stops set planned_arrival = '{local_planned}' where id='{SB}'")
    s, r = call("POST", f"/api/v1/trips/{T}/stops/{SB}/arrive", {}, DRV)
    check("chauffeuse : arrivée en retard sur l'arrêt 2", s == 200 and r.get("punctuality") == "LATE", f"({s} {r.get('punctuality')})")
    n = notified(MGR, "STOP_LATE_ARRIVAL", "stopId", SB)
    check("gestionnaire : STOP_LATE_ARRIVAL (tripId + stopId)", len(n) == 1 and n[0]["_data"].get("tripId") == T)
    s, r = call("POST", f"/api/v1/trips/{T}/stops/{SB}/fail", {"reason": "CLOSED", "notes": "Rideau baissé"}, DRV)
    check("chauffeuse : arrêt 2 non servi", s == 200 and r.get("status") == "FAILED", f"({s} {code(r)})")
    n = notified(MGR, "TRIP_STOP_FAILED", "stopId", SB)
    check("gestionnaire : TRIP_STOP_FAILED avec le motif", len(n) == 1 and "fermé" in n[0]["message"] and "Rideau baissé" in n[0]["message"])
    s, t = call("GET", f"/api/v1/trips/{T}", token=MGR)
    stop_b = next(x for x in t["stops"] if x["id"] == SB)
    check("gestionnaire : voit le motif et les précisions de l'échec", stop_b.get("failureReason") == "CLOSED"
          and stop_b.get("failureNotes") == "Rideau baissé")
    done = notified(MGR, "TRIP_TOUR_DONE", "tripId", T)
    check("gestionnaire : TRIP_TOUR_DONE une seule fois, trajet « livraisons terminées »",
          len(done) == 1 and t.get("status") == "ARRIVED", f"({len(done)} {t.get('status')})")
    check("chauffeuse : n'est pas notifiée de ses propres actions",
          not notified(DRV, "TRIP_STOP_FAILED", "tripId", T) and not notified(DRV, "STOP_SHORTAGE", "tripId", T))

    print("6. Retour à la base")
    s, r = call("POST", "/api/v1/trips/return", {"tripCode": CODE, "returnDate": later().date().isoformat(),
                                                  "returnTime": later().strftime("%H:%M:00"), "returnLocation": "Dépôt Bonabéri",
                                                  "returnKmIndex": odo + 42, "returnFuelIndex": 25}, MGR)
    check("gestionnaire : retour enregistré", s == 200 and r.get("status") == "COMPLETED", f"({s} {code(r)})")
    check("chauffeuse : TRIP_COMPLETED, plus de trajet actif, trajet terminé dans son historique",
          len(notified(DRV, "TRIP_COMPLETED", "tripId", T)) == 1 and my_active(DRV) is None
          and (my_trip(DRV, T) or {}).get("status") == "COMPLETED")

    print("7. Annulation et suppression")
    st2 = ret + datetime.timedelta(hours=3)
    s, t2 = plan(st2, st2 + datetime.timedelta(hours=3), "Essai 068 annulation",
                 [{"stopType": "DELIVERY", "location": "Entrepôt Akwa"}], [])
    s, r = call("PATCH", f"/api/v1/trips/{t2['id']}/cancel", {"reason": "Client fermé ce jour"}, MGR)
    n = notified(DRV, "TRIP_CANCELLED", "tripId", t2["id"])
    check("chauffeuse : TRIP_CANCELLED avec le motif", s == 200 and len(n) == 1 and "Client fermé ce jour" in n[0]["message"], f"({s})")
    mine = my_trip(DRV, t2["id"])
    check("chauffeuse : trajet annulé et motif visibles", mine and mine["status"] == "CANCELLED" and mine.get("cancelReason") == "Client fermé ce jour")
    st3 = st2 + datetime.timedelta(hours=4)
    s, t3 = plan(st3, st3 + datetime.timedelta(hours=2), "Essai 068 suppression",
                 [{"stopType": "DELIVERY", "location": "Entrepôt Bassa"}], [])
    s, r = call("DELETE", f"/api/v1/trips/{t3['id']}", token=MGR)
    n = notified(DRV, "TRIP_DELETED", contains=t3["tripCode"])
    check("chauffeuse : TRIP_DELETED (sans tripId) et trajet retiré de sa liste",
          s in (200, 204) and len(n) == 1 and "tripId" not in n[0]["_data"] and my_trip(DRV, t3["id"]) is None, f"({s})")

    print("8. Trajet manqué")
    if SKIP_MISSED:
        print("  (passé : --skip-missed)")
    else:
        st4 = st3 + datetime.timedelta(hours=3)
        s, t4 = plan(st4, st4 + datetime.timedelta(hours=2), "Essai 068 manqué",
                     [{"stopType": "DELIVERY", "location": "Client Deido"}], [])
        past = datetime.datetime.now() - datetime.timedelta(hours=3)
        sql(f"update fleet.trips set start_date='{past.date()}', start_time='{past.strftime('%H:%M:00')}', "
            f"scheduled_return_datetime='{iso(past + datetime.timedelta(hours=1))}' where id='{t4['id']}'")
        status = None
        for _ in range(37):
            status = sql(f"select status from fleet.trips where id='{t4['id']}'")
            if status == "MISSED":
                break
            time.sleep(10)
        check("automatisme : trajet marqué manqué", status == "MISSED", f"({status})")
        check("gestionnaire ET chauffeuse : TRIP_MISSED (tripId)",
              len(notified(MGR, "TRIP_MISSED", "tripId", t4["id"])) == 1 and len(notified(DRV, "TRIP_MISSED", "tripId", t4["id"])) == 1)
        check("chauffeuse : trajet manqué visible dans sa liste", (my_trip(DRV, t4["id"]) or {}).get("status") == "MISSED")
finally:
    sql(f"update fleet.drivers set fleet_id={repr(saved_second_fleet) if saved_second_fleet else 'NULL'} where user_id='{NANA}'")
    for x in (parked.split(",") if parked else []):
        sql(f"update fleet.assignments set status='{x.split(':')[1]}' where id='{x.split(':')[0]}'")
    trips = ",".join(f"'{i}'" for i in trip_ids) or "NULL"
    restore = "".join(f"update fleet.vehicles set status='{v['s']}', tank_capacity={v['t'] if v['t'] is not None else 'NULL'}, "
                      f"current_driver_id={repr(v['d']) if v['d'] else 'NULL'} where id='{v['id']}';"
                      for v in json.loads(saved_vehicles or "[]"))
    restore += "".join(f"update fleet.drivers set assigned_vehicle_id={repr(d['v']) if d['v'] else 'NULL'} where user_id='{d['id']}';"
                       for d in json.loads(saved_drivers or "[]"))
    refs = [f"data like '%{i}%'" for i in trip_ids] + [f"message like '%{c}%'" for c in trip_codes]
    sql(f"""
        delete from fleet.documents where doc_number like 'TEST-068-%';
        delete from fleet.trip_mission_submissions where trip_id in ({trips});
        delete from fleet.trip_details where trip_id in ({trips});
        delete from fleet.trip_stops where trip_id in ({trips});
        delete from fleet.trips where id in ({trips});
        delete from fleet.notifications where created_at > '{marks['notifications']}' and ({" or ".join(refs) or 'false'});
        {restore}
        update fleet.operational_parameters set odometer_reading={saved_odo or 'NULL'}, mileage={saved_odo or 'NULL'} where vehicle_id='{V1}';
    """)
    print(f"\n{sum(results)}/{len(results)} vérifications réussies")
