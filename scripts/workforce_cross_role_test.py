"""Essai : interactions gestionnaire <-> chauffeur du domaine « effectifs » (migration 069).

Backend en mode Kernel (port 8081), comptes réels : manager.dupont (gestionnaire), driver.bella
(chauffeuse de la flotte Douala), driver.nana (autre chauffeur, autre flotte). Mot de passe : ligne
DEMO_PASSWORD= du fichier .env (jamais affiché). Fichiers en mode local (aucun envoi au Kernel).

Chaque vérification porte sur les deux côtés : l'action d'un rôle, puis ce que voit l'autre (API) et
la notification qu'il reçoit (GET /api/v1/notifications, type + identifiant dans data).
  1. Postes : publication, accusé de réception, modification / ajout / retrait / annulation /
     suppression motivés, poste hors planning, chauffeur marqué absent.
  2. Absences : saisie et annulation par le gestionnaire ; demande du chauffeur refusée, acceptée,
     en conflit avec un poste, retirée ; cloisonnement entre chauffeurs.
  3. Véhicule : véhicule attitré attribué, changé, retiré ; relevés terrain (auteur, jauge basse,
     compteur qui recule refusé et signalé).
  4. Documents : dépôt -> file de contrôle, rejet motivé, nouveau dépôt, approbation, pièce demandée.
Optionnel (WAIT_JOBS=1) : alerte « poste non confirmé » de WorkforceAlertJob (attente jusqu'à 11 min).
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


def notifs(token, event, **match):
    """Notifications de ce compte pour cet événement dont data contient les paires données."""
    s, page = call("GET", "/api/v1/notifications?page=0&size=200", token=token)
    out = []
    for n in content(page):
        try:
            data = json.loads(n.get("dataJson") or "{}")
        except ValueError:
            continue
        if data.get("type") == event and all(data.get(k) == v for k, v in match.items()):
            out.append({**n, "data": data})
    return out


def has(token, event, **match):
    return len(notifs(token, event, **match)) > 0


def db_notif(user_id, event, key, value):
    """Pour un chauffeur sans compte Kernel : notification lue en base."""
    return sql(f"select count(*) from fleet.notifications where user_id='{user_id}' and created_at > '{marks['notifications']}' "
               f"and data like '%\"type\":\"{event}\"%' and data like '%\"{key}\":\"{value}\"%'") != "0"


MGR, DRV, OTHER = login("manager.dupont"), login("driver.bella"), login("driver.nana")
if not (MGR and DRV and OTHER):
    raise SystemExit("Connexion impossible (manager.dupont, driver.bella, driver.nana) : vérifiez le backend et DEMO_PASSWORD.")
BELLA = sql("select id from fleet.users where username='driver.bella'")
NANA = sql("select id from fleet.users where username='driver.nana'")
DOUALA = sql(f"select fleet_id from fleet.drivers where user_id='{BELLA}'")
VA = sql(f"select assigned_vehicle_id from fleet.drivers where user_id='{BELLA}'")
if not VA:
    VA = sql(f"select id from fleet.vehicles where fleet_id='{DOUALA}' and deleted_at is null and status='AVAILABLE' "
             f"and id not in (select assigned_vehicle_id from fleet.drivers where assigned_vehicle_id is not null) order by license_plate limit 1")
    sql(f"update fleet.drivers set assigned_vehicle_id='{VA}' where user_id='{BELLA}'")
    BELLA_HAD_VEHICLE = None
else:
    BELLA_HAD_VEHICLE = VA
VA_TYPE = sql(f"select coalesce(vehicle_type_id::text, '') from fleet.vehicles where id='{VA}'")
V_FREE = sql(f"select id from fleet.vehicles v where fleet_id='{DOUALA}' and deleted_at is null and status='AVAILABLE' and id<>'{VA}' "
             f"and not exists (select 1 from fleet.drivers d where d.assigned_vehicle_id=v.id) "
             f"order by (coalesce(vehicle_type_id::text,'')='{VA_TYPE}') desc, license_plate limit 1")
MATE = sql(f"select d.user_id from fleet.drivers d where d.fleet_id='{DOUALA}' and d.user_id<>'{BELLA}' and d.status='ACTIVE' "
           f"and d.deleted_at is null order by d.user_id limit 1")
marks = {t: sql(f"select coalesce(max(created_at), 'epoch') from fleet.{t}")
         for t in ("notifications", "driver_absences", "documents", "assignments", "schedules")}
for t in ("driver_absence_requests", "vehicle_operational_updates", "document_requests"):
    col = "requested_at" if t == "document_requests" else "created_at"
    marks[t] = sql(f"select coalesce(max({col}), 'epoch') from fleet.{t}")
saved_op = sql(f"select json_build_object('o', odometer_reading, 'm', mileage, 'f', fuel_level) from fleet.operational_parameters where vehicle_id='{VA}'")

# Pièces de conformité manquantes (véhicules, permis) : insérées pour l'essai, retirées à la fin.
for v in [x for x in (VA, V_FREE) if x]:
    for doc in ("INSURANCE", "TECHNICAL_CONTROL", "REGISTRATION"):
        sql(f"insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, expiry_date, file_url, review_status, is_current, "
            f"created_at, updated_at) select gen_random_uuid(), 'VEHICLE', '{v}', '{doc}', 'TEST-069-{doc}', current_date + 400, "
            f"'/api/v1/files/TEST-069.pdf', 'APPROVED', TRUE, now(), now() "
            f"where not exists (select 1 from fleet.documents where target_id='{v}' and doc_type='{doc}' and is_current)")
sql("insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, license_categories, expiry_date, file_url, "
    "review_status, is_current, created_at, updated_at) select gen_random_uuid(), 'DRIVER', d.user_id, 'DRIVING_LICENSE', 'TEST-069-LIC', "
    "'A,B,C,D,E', current_date + 400, '/api/v1/files/TEST-069.pdf', 'APPROVED', TRUE, now(), now() from fleet.drivers d "
    "where not exists (select 1 from fleet.documents x where x.target_id=d.user_id and x.doc_type='DRIVING_LICENSE' and x.is_current)")

today = datetime.date.today()
day = lambda n: today + datetime.timedelta(days=n)
at = lambda n, h: f"{day(n).isoformat()}T{h:02d}:00:00"
WAIT_JOBS = os.environ.get("WAIT_JOBS") == "1"

# Les plannings de démo peuvent occuper ces ressources : leurs postes sont mis de côté le temps de l'essai.
window_from = "now() - interval '12 hours'" if WAIT_JOBS else f"'{day(39)}'"
parked = sql(f"select string_agg(id::text || ':' || status, ',') from fleet.assignments where status in ('PENDING','IN_PROGRESS') "
             f"and (driver_id in ('{BELLA}', '{MATE or BELLA}') or vehicle_id in ('{VA}', '{V_FREE or VA}')) "
             f"and start_datetime < '{day(56)}' and end_datetime > {window_from}")
if parked:
    sql(f"update fleet.assignments set status='CANCELLED' where id in ({','.join(repr(x.split(':')[0]) for x in parked.split(','))})")

schedule_id = None
created_shifts = []
try:
    print("1. Postes")
    s, sch = call("POST", "/api/v1/schedules", {"fleetId": DOUALA, "title": "Essai 069", "periodType": "WEEKLY",
                                                 "startDate": day(40).isoformat(), "endDate": day(46).isoformat()}, MGR)
    schedule_id = sch.get("id") if isinstance(sch, dict) else None
    check("gestionnaire : planning brouillon créé", s == 201 and schedule_id, f"({s} {code(sch)})")
    s, a1 = call("POST", "/api/v1/assignments", {"scheduleId": schedule_id, "fleetId": DOUALA, "vehicleId": VA, "driverId": BELLA,
                                                  "startDatetime": at(40, 8), "endDatetime": at(40, 16)}, MGR)
    A1 = a1.get("id") if isinstance(a1, dict) else None
    created_shifts.append(A1)
    s2, mine = call("GET", f"/api/v1/assignments/driver/{BELLA}?page=0&size=200", token=DRV)
    check("brouillon : poste créé, invisible pour la chauffeuse", s == 201 and A1 and A1 not in [x["id"] for x in content(mine)],
          f"({s} {code(a1)})")

    s, pub = call("PUT", f"/api/v1/schedules/{schedule_id}/publish", token=MGR)
    s2, mine = call("GET", f"/api/v1/assignments/driver/{BELLA}?page=0&size=200", token=DRV)
    seen = next((x for x in content(mine) if x["id"] == A1), None)
    check("publication : la chauffeuse voit son poste, à confirmer", s == 200 and seen and not seen.get("acknowledgedAt"), f"({s})")
    check("publication : notification SCHEDULE_PUBLISHED (scheduleId) à la chauffeuse",
          has(DRV, "SCHEDULE_PUBLISHED", scheduleId=schedule_id))
    s, d = call("GET", f"/api/v1/schedules/{schedule_id}", token=MGR)
    check("gestionnaire : suivi des accusés 0/1", d.get("upcomingShifts") == 1 and d.get("acknowledgedShifts") == 0,
          f"({d.get('acknowledgedShifts')}/{d.get('upcomingShifts')})")

    s, r = call("POST", f"/api/v1/assignments/{A1}/acknowledge", token=DRV)
    s2, g = call("GET", f"/api/v1/assignments/{A1}", token=MGR)
    s3, d = call("GET", f"/api/v1/schedules/{schedule_id}", token=MGR)
    check("chauffeuse accuse réception -> le gestionnaire le voit (poste et planning)",
          s == 200 and g.get("acknowledgedAt") and d.get("acknowledgedShifts") == 1, f"({s} {d.get('acknowledgedShifts')})")

    s, r = call("PATCH", f"/api/v1/assignments/{A1}", {"startDatetime": at(40, 9), "endDatetime": at(40, 17)}, MGR)
    check("modification après publication sans motif refusée", s in (400, 409, 422), f"({s} {code(r)})")
    s, r = call("PATCH", f"/api/v1/assignments/{A1}", {"startDatetime": at(40, 9), "endDatetime": at(40, 17),
                                                        "reason": "Livraison décalée"}, MGR)
    s2, mine = call("GET", f"/api/v1/assignments/driver/{BELLA}?page=0&size=200", token=DRV)
    seen = next((x for x in content(mine) if x["id"] == A1), {})
    check("modification motivée : la chauffeuse voit le motif, accusé à refaire",
          s == 200 and seen.get("changeReason") == "Livraison décalée" and not seen.get("acknowledgedAt"), f"({s} {code(r)})")
    check("modification : notification SHIFT_CHANGED (assignmentId, change=MODIFIED)",
          has(DRV, "SHIFT_CHANGED", assignmentId=A1, change="MODIFIED"))
    s, d = call("GET", f"/api/v1/schedules/{schedule_id}", token=MGR)
    check("gestionnaire : poste modifié non confirmé compté", d.get("changedUnacknowledged") == 1 and d.get("acknowledgedShifts") == 0,
          f"({d.get('changedUnacknowledged')})")

    s, a2 = call("POST", "/api/v1/assignments", {"scheduleId": schedule_id, "fleetId": DOUALA, "vehicleId": VA, "driverId": BELLA,
                                                  "startDatetime": at(41, 8), "endDatetime": at(41, 16), "changeReason": "Renfort"}, MGR)
    A2 = a2.get("id") if isinstance(a2, dict) else None
    created_shifts.append(A2)
    check("poste ajouté après publication : SHIFT_CHANGED (change=NEW) à la chauffeuse",
          s == 201 and has(DRV, "SHIFT_CHANGED", assignmentId=A2, change="NEW"), f"({s} {code(a2)})")

    if MATE:
        s, r = call("PATCH", f"/api/v1/assignments/{A2}", {"driverId": MATE, "reason": "Réorganisation"}, MGR)
        s2, mine = call("GET", f"/api/v1/assignments/driver/{BELLA}?page=0&size=200", token=DRV)
        removed = [n for n in notifs(DRV, "SHIFT_CHANGED", change="REMOVED") if "assignmentId" not in n["data"]]
        check("poste confié à un collègue : retiré de la liste de la chauffeuse", s == 200 and A2 not in [x["id"] for x in content(mine)],
              f"({s} {code(r)})")
        check("retrait : SHIFT_CHANGED (change=REMOVED, sans assignmentId) à la chauffeuse", len(removed) > 0)
        check("le collègue reçoit SHIFT_CHANGED (change=NEW, assignmentId)", db_notif(MATE, "SHIFT_CHANGED", "assignmentId", A2))
        s, r = call("PATCH", f"/api/v1/assignments/{A2}/status", {"status": "CANCELLED", "reason": "Client absent"}, MGR)
        check("annulation motivée : le collègue est prévenu (change=CANCELLED)", s == 200 and r.get("cancelReason") == "Client absent"
              and db_notif(MATE, "SHIFT_CHANGED", "change", "CANCELLED"), f"({s} {code(r)})")
    else:
        s, r = call("PATCH", f"/api/v1/assignments/{A2}/status", {"status": "CANCELLED", "reason": "Client absent"}, MGR)
        s2, g = call("GET", f"/api/v1/assignments/{A2}", token=DRV)
        check("annulation motivée : la chauffeuse voit le motif et est prévenue (change=CANCELLED)",
              s == 200 and g.get("status") == "CANCELLED" and g.get("cancelReason") == "Client absent"
              and has(DRV, "SHIFT_CHANGED", assignmentId=A2, change="CANCELLED"), f"({s} {code(r)})")

    s, a3 = call("POST", "/api/v1/assignments", {"scheduleId": schedule_id, "fleetId": DOUALA, "vehicleId": VA, "driverId": BELLA,
                                                  "startDatetime": at(43, 8), "endDatetime": at(43, 16)}, MGR)
    A3 = a3.get("id") if isinstance(a3, dict) else None
    s, r = call("DELETE", f"/api/v1/assignments/{A3}", token=MGR)
    check("suppression après publication sans motif refusée", s in (400, 409, 422), f"({s} {code(r)})")
    s, r = call("DELETE", f"/api/v1/assignments/{A3}?reason=Erreur%20de%20saisie", token=MGR)
    deleted = [n for n in notifs(DRV, "SHIFT_CHANGED", change="DELETED") if "Erreur de saisie" in (n.get("message") or "")]
    check("suppression motivée : SHIFT_CHANGED (change=DELETED) avec le motif", s == 204 and deleted, f"({s})")
    if s != 204:
        created_shifts.append(A3)

    s, a4 = call("POST", "/api/v1/assignments", {"fleetId": DOUALA, "vehicleId": VA, "driverId": BELLA,
                                                  "startDatetime": at(42, 8), "endDatetime": at(42, 16)}, MGR)
    A4 = a4.get("id") if isinstance(a4, dict) else None
    created_shifts.append(A4)
    check("poste hors planning : visible et notifié (change=NEW)",
          s == 201 and has(DRV, "SHIFT_CHANGED", assignmentId=A4, change="NEW"), f"({s} {code(a4)})")
    s, r = call("POST", f"/api/v1/assignments/{A4}/acknowledge", token=DRV)
    s2, g = call("GET", f"/api/v1/assignments/{A4}", token=MGR)
    check("poste hors planning : l'accusé de réception est enregistré", s == 200 and g.get("acknowledgedAt"), f"({s})")
    s, r = call("POST", f"/api/v1/assignments/{A4}/acknowledge", token=OTHER)
    check("un autre chauffeur ne peut pas accuser réception", s in (403, 404, 409, 422), f"({s})")

    s, r = call("PATCH", f"/api/v1/assignments/{A1}/status", {"status": "NO_SHOW", "reason": "Injoignable"}, MGR)
    s2, g = call("GET", f"/api/v1/assignments/{A1}", token=DRV)
    no_show = [n for n in notifs(DRV, "SHIFT_NO_SHOW", assignmentId=A1) if "Injoignable" in (n.get("message") or "")]
    check("chauffeuse marquée absente : elle voit le statut et reçoit SHIFT_NO_SHOW",
          s == 200 and g.get("status") == "NO_SHOW" and no_show, f"({s} {code(r)})")
    marked = sql(f"select no_show_notified_at is not null from fleet.assignments where id='{A1}'")
    check("absence au poste marquée notifiée (pas de doublon par la tâche planifiée)", marked == "t", f"({marked})")

    if WAIT_JOBS:
        now = datetime.datetime.now().replace(minute=0, second=0, microsecond=0) + datetime.timedelta(hours=2)
        s, a5 = call("POST", "/api/v1/assignments", {"fleetId": DOUALA, "vehicleId": VA, "driverId": BELLA,
                                                      "startDatetime": now.strftime("%Y-%m-%dT%H:%M:%S"),
                                                      "endDatetime": (now + datetime.timedelta(hours=2)).strftime("%Y-%m-%dT%H:%M:%S")}, MGR)
        A5 = a5.get("id") if isinstance(a5, dict) else None
        created_shifts.append(A5)
        found = False
        for _ in range(23):
            if has(MGR, "SHIFT_UNACKNOWLEDGED", assignmentId=A5):
                found = True
                break
            time.sleep(30)
        check("poste non confirmé à moins de 4 h : SHIFT_UNACKNOWLEDGED au gestionnaire", s == 201 and found, f"({s} {code(a5)})")

    print("2. Absences")
    s, ab = call("POST", f"/api/v1/drivers/{BELLA}/absences", {"type": "LEAVE", "startDate": day(50).isoformat(),
                                                                "endDate": day(50).isoformat(), "reason": "Essai 069"}, MGR)
    AB = ab.get("id") if isinstance(ab, dict) else None
    s2, mine = call("GET", f"/api/v1/drivers/{BELLA}/absences", token=DRV)
    check("absence saisie par le gestionnaire : la chauffeuse la voit", s == 201 and AB in [x["id"] for x in (mine or [])],
          f"({s} {s2} {code(ab)})")
    check("absence saisie : ABSENCE_RECORDED (absenceId) à la chauffeuse", has(DRV, "ABSENCE_RECORDED", absenceId=AB))
    s, r = call("GET", f"/api/v1/drivers/{BELLA}/absences", token=OTHER)
    check("un autre chauffeur ne lit pas ses absences", s in (403, 404), f"({s})")
    s, r = call("POST", f"/api/v1/drivers/{BELLA}/absences/{AB}/cancel", token=MGR)
    s2, mine = call("GET", f"/api/v1/drivers/{BELLA}/absences", token=DRV)
    gone = next((x for x in (mine or []) if x["id"] == AB), {})
    check("absence annulée : la chauffeuse voit l'annulation et reçoit ABSENCE_CANCELLED",
          s == 200 and gone.get("cancelledAt") and has(DRV, "ABSENCE_CANCELLED", absenceId=AB), f"({s})")

    req = {"type": "LEAVE", "startDate": day(52).isoformat(), "endDate": day(53).isoformat(), "reason": "Essai 069"}
    s, rq = call("POST", f"/api/v1/drivers/{BELLA}/absence-requests", req, OTHER)
    check("un chauffeur ne demande pas d'absence pour un autre", s == 403, f"({s})")
    s, rq = call("POST", f"/api/v1/drivers/{BELLA}/absence-requests", {**req, "type": "SUSPENSION"}, DRV)
    check("une suspension ne se demande pas", s == 400, f"({s} {code(rq)})")
    s, rq = call("POST", f"/api/v1/drivers/{BELLA}/absence-requests", req, DRV)
    R1 = rq.get("id") if isinstance(rq, dict) else None
    s2, lst = call("GET", f"/api/v1/drivers/{BELLA}/absence-requests", token=MGR)
    mine_r = next((x for x in (lst or []) if x["id"] == R1), {})
    check("demande du chauffeur : le gestionnaire la voit en attente, sans conflit",
          s == 201 and mine_r.get("status") == "PENDING" and mine_r.get("conflicts") == 0, f"({s} {code(rq)})")
    check("demande : ABSENCE_REQUESTED (driverId) au gestionnaire", has(MGR, "ABSENCE_REQUESTED", driverId=BELLA, absenceRequestId=R1))
    s, r = call("POST", f"/api/v1/drivers/{BELLA}/absence-requests", req, DRV)
    check("pas deux demandes en attente sur les mêmes dates", s == 400, f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/drivers/{BELLA}/absence-requests/{R1}/approve", token=DRV)
    check("le chauffeur ne valide pas sa propre demande", s == 403, f"({s})")
    s, r = call("POST", f"/api/v1/drivers/{BELLA}/absence-requests/{R1}/reject", {"reason": ""}, MGR)
    check("refus sans motif impossible", s == 400, f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/drivers/{BELLA}/absence-requests/{R1}/reject", {"reason": "Pic d'activité"}, MGR)
    s2, lst = call("GET", f"/api/v1/drivers/{BELLA}/absence-requests", token=DRV)
    mine_r = next((x for x in (lst or []) if x["id"] == R1), {})
    check("refus : la chauffeuse voit le statut et le motif", s == 200 and mine_r.get("status") == "REJECTED"
          and mine_r.get("decisionReason") == "Pic d'activité", f"({s} {code(r)})")
    check("refus : ABSENCE_REQUEST_REJECTED à la chauffeuse", has(DRV, "ABSENCE_REQUEST_REJECTED", absenceRequestId=R1))

    s, rq = call("POST", f"/api/v1/drivers/{BELLA}/absence-requests", req, DRV)
    R2 = rq.get("id") if isinstance(rq, dict) else None
    s, r = call("POST", f"/api/v1/drivers/{BELLA}/absence-requests/{R2}/approve", token=MGR)
    ABS2 = r.get("absenceId") if isinstance(r, dict) else None
    s2, mine = call("GET", f"/api/v1/drivers/{BELLA}/absences", token=DRV)
    check("acceptation : absence enregistrée, visible par la chauffeuse",
          s == 200 and r.get("status") == "APPROVED" and ABS2 in [x["id"] for x in (mine or [])], f"({s} {code(r)})")
    check("acceptation : ABSENCE_REQUEST_APPROVED (absenceId) à la chauffeuse", has(DRV, "ABSENCE_REQUEST_APPROVED", absenceId=ABS2))
    check("acceptation : pas de doublon ABSENCE_RECORDED", not has(DRV, "ABSENCE_RECORDED", absenceId=ABS2))

    s, rq = call("POST", f"/api/v1/drivers/{BELLA}/absence-requests",
                 {"type": "LEAVE", "startDate": day(42).isoformat(), "endDate": day(42).isoformat()}, DRV)
    R3 = rq.get("id") if isinstance(rq, dict) else None
    s2, lst = call("GET", f"/api/v1/drivers/{BELLA}/absence-requests", token=MGR)
    mine_r = next((x for x in (lst or []) if x["id"] == R3), {})
    check("demande sur un jour de poste : conflit signalé au gestionnaire", s == 201 and mine_r.get("conflicts", 0) >= 1,
          f"({s} conflicts={mine_r.get('conflicts')})")
    s, r = call("POST", f"/api/v1/drivers/{BELLA}/absence-requests/{R3}/approve", token=MGR)
    check("acceptation refusée tant que le poste n'est pas réaffecté", s == 409 and code(r) == "DRV_013", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/drivers/{BELLA}/absence-requests/{R3}/withdraw", token=DRV)
    check("retrait par la chauffeuse : ABSENCE_REQUEST_WITHDRAWN au gestionnaire",
          s == 200 and r.get("status") == "WITHDRAWN" and has(MGR, "ABSENCE_REQUEST_WITHDRAWN", absenceRequestId=R3), f"({s} {code(r)})")

    print("3. Véhicule attitré et relevés terrain")
    if V_FREE:
        s, r = call("POST", f"/api/v1/drivers/{BELLA}/assign-vehicle", {"vehicleId": V_FREE}, MGR)
        s2, prof = call("GET", f"/api/v1/drivers/{BELLA}", token=DRV)
        check("véhicule attitré changé : la chauffeuse le voit", s == 200 and prof.get("assignedVehicleId") == V_FREE,
              f"({s} {code(r)})")
        check("changement : VEHICLE_ASSIGNED (vehicleId) à la chauffeuse", has(DRV, "VEHICLE_ASSIGNED", vehicleId=V_FREE))
        s, r = call("POST", f"/api/v1/drivers/{BELLA}/unassign-vehicle", token=MGR)
        s2, prof = call("GET", f"/api/v1/drivers/{BELLA}", token=DRV)
        check("véhicule retiré : la chauffeuse ne l'a plus et reçoit VEHICLE_UNASSIGNED",
              s == 200 and not prof.get("assignedVehicleId") and has(DRV, "VEHICLE_UNASSIGNED", vehicleId=V_FREE), f"({s})")
    else:
        print("  (aucun véhicule libre dans la flotte : attribution non testée)")
    s, r = call("POST", f"/api/v1/drivers/{BELLA}/assign-vehicle", {"vehicleId": VA}, MGR)
    check("véhicule d'origine rendu : VEHICLE_ASSIGNED", s == 200 and has(DRV, "VEHICLE_ASSIGNED", vehicleId=VA), f"({s} {code(r)})")

    op = json.loads(saved_op) if saved_op else {}
    odo = float(op.get("o") or 0)
    s, r = call("PATCH", f"/api/v1/vehicles/{VA}/operational", {"odometerReading": odo + 12, "fuelLevel": "55%"}, DRV)
    s2, ups = call("GET", f"/api/v1/vehicles/{VA}/operational/updates?limit=5", token=MGR)
    s3, veh = call("GET", f"/api/v1/vehicles/{VA}", token=MGR)
    last = (ups or [{}])[0] if isinstance(ups, list) else {}
    check("relevé de la chauffeuse : le gestionnaire voit valeur, auteur et heure",
          s in (200, 204) and float(last.get("odometerAfter") or 0) == odo + 12 and last.get("authorName") and last.get("createdAt")
          and float(((veh or {}).get("operationalParameters") or {}).get("odometerReading") or 0) == odo + 12,
          f"({s} {last.get('authorName')})")
    s, r = call("PATCH", f"/api/v1/vehicles/{VA}/operational", {"fuelLevel": "10%"}, DRV)
    s2, ups = call("GET", f"/api/v1/vehicles/{VA}/operational/updates?limit=5", token=MGR)
    check("jauge très basse : anomalie FUEL_LOW et VEHICLE_FUEL_LOW (vehicleId) au gestionnaire",
          s in (200, 204) and "FUEL_LOW" in ((ups or [{}])[0].get("anomalies") or []) and has(MGR, "VEHICLE_FUEL_LOW", vehicleId=VA),
          f"({s})")
    s, r = call("PATCH", f"/api/v1/vehicles/{VA}/operational", {"odometerReading": odo}, DRV)
    s2, ups = call("GET", f"/api/v1/vehicles/{VA}/operational/updates?limit=5", token=MGR)
    first = (ups or [{}])[0] if isinstance(ups, list) else {}
    check("compteur qui recule : refusé pour la chauffeuse", s == 422 and code(r) == "VHC_020", f"({s} {code(r)})")
    check("compteur qui recule : tracé (refusé) et VEHICLE_ODOMETER_ANOMALY au gestionnaire",
          first.get("accepted") is False and "ODOMETER_DECREASE" in (first.get("anomalies") or [])
          and has(MGR, "VEHICLE_ODOMETER_ANOMALY", vehicleId=VA), f"({first.get('anomalies')})")
    s, r = call("PATCH", f"/api/v1/vehicles/{VA}/operational", {"fuelLevel": "50%"}, OTHER)
    check("un autre chauffeur ne saisit pas de relevé sur ce véhicule", s == 403, f"({s})")

    print("4. Documents")
    n = [0]

    def piece(doc_type):
        n[0] += 1
        return {"docType": doc_type, "docNumber": f"TEST-069-{n[0]}", "issuer": "Essai", "issueDate": day(-30).isoformat(),
                "expiryDate": day(400).isoformat(), "fileUrl": f"/api/v1/files/essai-069-{n[0]}.pdf",
                "fileOriginalName": "piece.pdf", "fileMimeType": "application/pdf", "fileSizeBytes": 1000}

    existing_medical = sql(f"select count(*) from fleet.documents where target_id='{BELLA}' and doc_type='MEDICAL_CERT' "
                           f"and archived_at is null and review_status in ('PENDING','APPROVED')")
    if existing_medical != "0":
        print("  (visite médicale déjà au dossier : le dépôt teste le renouvellement)")
    s, d1 = call("POST", f"/api/v1/drivers/{BELLA}/documents", piece("MEDICAL_CERT"), DRV)
    D1 = d1.get("id") if isinstance(d1, dict) else None
    s2, pend = call("GET", "/api/v1/documents/pending", token=MGR)
    check("dépôt de la chauffeuse : dans la file de contrôle du gestionnaire", s == 201 and D1 in [x["id"] for x in (pend or [])],
          f"({s} {code(d1)})")
    check("dépôt : DOCUMENT_SUBMITTED (documentId) au gestionnaire", has(MGR, "DOCUMENT_SUBMITTED", documentId=D1))
    s, r = call("POST", f"/api/v1/documents/{D1}/reject", {"reason": "Illisible"}, MGR)
    s2, g = call("GET", f"/api/v1/documents/{D1}", token=DRV)
    check("rejet motivé : la chauffeuse voit le statut et le motif", s == 200 and g.get("reviewStatus") == "REJECTED"
          and g.get("rejectionReason") == "Illisible", f"({s} {code(r)})")
    check("rejet : DOCUMENT_REJECTED (documentId) à la chauffeuse", has(DRV, "DOCUMENT_REJECTED", documentId=D1))
    s, d2 = call("POST", f"/api/v1/drivers/{BELLA}/documents", piece("MEDICAL_CERT"), DRV)
    D2 = d2.get("id") if isinstance(d2, dict) else None
    check("nouveau dépôt : de nouveau DOCUMENT_SUBMITTED", s == 201 and has(MGR, "DOCUMENT_SUBMITTED", documentId=D2), f"({s} {code(d2)})")
    s, r = call("POST", f"/api/v1/documents/{D2}/approve", {}, MGR)
    s2, g = call("GET", f"/api/v1/documents/{D2}", token=DRV)
    check("approbation : la chauffeuse voit la pièce approuvée et reçoit DOCUMENT_APPROVED",
          s == 200 and g.get("reviewStatus") == "APPROVED" and has(DRV, "DOCUMENT_APPROVED", documentId=D2), f"({s} {code(r)})")
    check("approbation : le gestionnaire n'est pas notifié de sa propre décision", not has(MGR, "DOCUMENT_APPROVED", documentId=D2))

    has_card = sql(f"select count(*) from fleet.documents where target_id='{BELLA}' and doc_type='PROFESSIONAL_CARD' and archived_at is null")
    s, r = call("POST", f"/api/v1/drivers/{BELLA}/documents/requests", {"docType": "PROFESSIONAL_CARD", "message": "Avant lundi"}, MGR)
    s2, cl = call("GET", f"/api/v1/drivers/{BELLA}/documents/checklist", token=DRV)
    item = next((x for x in (cl or []) if x.get("docType") == "PROFESSIONAL_CARD"), {})
    check("pièce demandée : visible dans la checklist de la chauffeuse", s == 201 and item.get("requestedAt")
          and item.get("requestMessage") == "Avant lundi", f"({s} {code(r)})")
    check("pièce demandée : DOCUMENT_REQUESTED (driverId) à la chauffeuse",
          has(DRV, "DOCUMENT_REQUESTED", driverId=BELLA, docType="PROFESSIONAL_CARD"))
    s, r = call("POST", f"/api/v1/drivers/{BELLA}/documents/requests", {"docType": "MEDICAL_CERT"}, DRV)
    check("un chauffeur ne demande pas de pièce", s == 403, f"({s})")
    if has_card == "0":
        s, d3 = call("POST", f"/api/v1/drivers/{BELLA}/documents", piece("PROFESSIONAL_CARD"), DRV)
        s2, cl = call("GET", f"/api/v1/drivers/{BELLA}/documents/checklist", token=MGR)
        item = next((x for x in (cl or []) if x.get("docType") == "PROFESSIONAL_CARD"), {})
        check("dépôt de la pièce demandée : la demande est close (vue gestionnaire)", s == 201 and not item.get("requestedAt"),
              f"({s} {code(d3)})")
finally:
    for x in (parked.split(",") if parked else []):
        sql(f"update fleet.assignments set status='{x.split(':')[1]}' where id='{x.split(':')[0]}'")
    shifts = ",".join(f"'{i}'" for i in created_shifts if i) or "NULL"
    op = json.loads(saved_op) if saved_op else None
    restore_op = (f"update fleet.operational_parameters set odometer_reading={op['o'] if op['o'] is not None else 'NULL'}, "
                  f"mileage={op['m'] if op['m'] is not None else 'NULL'}, "
                  f"fuel_level={repr(op['f']) if op['f'] is not None else 'NULL'} where vehicle_id='{VA}';") if op else ""
    restore_vehicle = (f"update fleet.drivers set assigned_vehicle_id={repr(BELLA_HAD_VEHICLE) if BELLA_HAD_VEHICLE else 'NULL'} "
                       f"where user_id='{BELLA}';")
    sql(f"""
        delete from fleet.assignments where id in ({shifts});
        delete from fleet.assignments where schedule_id = {repr(schedule_id) if schedule_id else 'NULL'};
        delete from fleet.schedules where id = {repr(schedule_id) if schedule_id else 'NULL'};
        delete from fleet.driver_absence_requests where driver_id = '{BELLA}' and created_at > '{marks['driver_absence_requests']}';
        delete from fleet.driver_absences where driver_id = '{BELLA}' and created_at > '{marks['driver_absences']}';
        delete from fleet.vehicle_operational_updates where created_at > '{marks['vehicle_operational_updates']}'
            and vehicle_id in ('{VA}', '{V_FREE or VA}');
        delete from fleet.document_requests where target_id = '{BELLA}' and requested_at > '{marks['document_requests']}';
        update fleet.documents set is_current = TRUE, superseded_by = NULL, superseded_at = NULL
            where superseded_by in (select id from fleet.documents where doc_number like 'TEST-069-%');
        delete from fleet.documents where doc_number like 'TEST-069-%';
        delete from fleet.notifications where created_at > '{marks['notifications']}'
            and user_id in ('{BELLA}', '{NANA}', '{MATE or BELLA}', (select manager_id from fleet.fleets where id = '{DOUALA}'))
            and (data like '%"type":"SHIFT_%' or data like '%"type":"SCHEDULE_PUBLISHED"%' or data like '%"type":"ABSENCE_%'
                 or data like '%"type":"VEHICLE_%' or data like '%"type":"DOCUMENT_%');
        {restore_op}
        {restore_vehicle}
    """)
    # Lien véhicule -> chauffeur tenu par l'API (vehicles) : remis comme au départ.
    if BELLA_HAD_VEHICLE:
        call("POST", f"/api/v1/drivers/{BELLA}/assign-vehicle", {"vehicleId": BELLA_HAD_VEHICLE}, MGR)
        sql(f"delete from fleet.notifications where created_at > '{marks['notifications']}' and user_id = '{BELLA}' "
            f"and data like '%\"type\":\"VEHICLE_%'")
    print(f"\n{sum(results)}/{len(results)} vérifications réussies")
