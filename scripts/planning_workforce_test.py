"""Essai : plannings et postes au plus près du terrain (migration 066).

Backend en authentification factice (SPRING_PROFILES_ACTIVE=local,demo,fake-auth) : adminfleet,
manager.dupont et fleetdriver (rattaché le temps de l'essai à la flotte de Douala). Mot de passe :
DEMO_PASSWORD du fichier .env.

1. Règles de temps de travail de l'entreprise : lecture, réglage par l'administrateur seul.
2. Modèles de postes : valeurs par défaut, ajout, nom unique, modification, suppression.
3. Dépassement (poste trop long, repos trop court, jours d'affilée) : refusé sans motif, accepté avec.
4. Création en série, tout ou rien, poste de nuit.
5. Après publication : accusé de réception, motif exigé pour modifier, réaffecter, annuler ou
   supprimer, chauffeur notifié, accusé à refaire.
6. Duplication sur la semaine suivante, postes refusés listés avec leur motif.
Les données touchées sont restaurées à la fin.
"""
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




ADMIN, MGR, DRV = login("adminfleet"), login("manager.dupont"), login("fleetdriver")
uid = lambda u: sql(f"select id from fleet.users where username='{u}'")
FD, BELLA, MEKE, MVOGO = uid("fleetdriver"), uid("driver.bella"), uid("driver.meke"), uid("driver.mvogo")
COMPANY = uid("adminfleet")
DOUALA = sql("select id from fleet.fleets where name='Flotte Douala'")
V1 = sql("select id from fleet.vehicles where license_plate='DL-203-FF'")
V2 = sql("select id from fleet.vehicles where license_plate='CE-456-AB'")
V3 = sql("select id from fleet.vehicles where license_plate='DL-201-CC'")
STARTED = sql("select to_char(localtimestamp, 'YYYY-MM-DD HH24:MI:SS')")
saved_fd = sql(f"select coalesce(fleet_id::text,'') from fleet.drivers where user_id='{FD}'")
saved_rules = sql(f"select max_shift_hours||'|'||min_rest_hours||'|'||max_weekly_hours||'|'||max_consecutive_days from fleet.companies where id='{COMPANY}'")
sql(f"update fleet.drivers set fleet_id='{DOUALA}' where user_id='{FD}'")
# Trajet de démonstration resté « parti » depuis juillet : il occupe le chauffeur sur tout créneau futur.
blocking_trips = sql(f"select string_agg(id::text, ',') from fleet.trips where driver_id='{FD}' and status='DEPARTED'")
if blocking_trips:
    sql(f"update fleet.trips set status='COMPLETED' where id in ({','.join(repr(t) for t in blocking_trips.split(','))})")
for v in (V1, V2, V3):
    for doc in ("INSURANCE", "TECHNICAL_CONTROL", "REGISTRATION"):
        sql(f"insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, expiry_date, file_url, review_status, is_current, created_at, updated_at) "
            f"select gen_random_uuid(), 'VEHICLE', '{v}', '{doc}', 'TEST-066-{doc}', date '2027-12-31', '/api/v1/files/TEST-066.pdf', 'APPROVED', TRUE, now(), now() "
            f"where not exists (select 1 from fleet.documents where target_id='{v}' and doc_type='{doc}' and is_current)")
sql("insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, license_categories, expiry_date, file_url, review_status, is_current, created_at, updated_at) "
    "select gen_random_uuid(), 'DRIVER', d.user_id, 'DRIVING_LICENSE', 'TEST-066-LIC', 'B,C,D', date '2027-12-31', '/api/v1/files/TEST-066.pdf', 'APPROVED', TRUE, now(), now() from fleet.drivers d "
    "where not exists (select 1 from fleet.documents x where x.target_id=d.user_id and x.doc_type='DRIVING_LICENSE' and x.is_current)")
schedule_ids = []
created_template = None


def notif(user, title):
    return int(sql(f"select count(*) from fleet.notifications where user_id='{user}' and title='{title}' and created_at >= '{STARTED}'"))


def shift(start, end, driver, vehicle, schedule, **extra):
    return call("POST", "/api/v1/assignments", {"scheduleId": schedule, "fleetId": DOUALA, "vehicleId": vehicle, "driverId": driver,
                                                 "startDatetime": start, "endDatetime": end, **extra}, MGR)


try:
    print("1. Règles de temps de travail")
    s, r = call("GET", "/api/v1/organization/work-rules", token=MGR)
    check("le gestionnaire lit les règles", s == 200 and r.get("maxShiftHours") == 12 and r.get("maxConsecutiveDays") == 6, f"({s} {r})")
    s, r = call("PUT", "/api/v1/organization/work-rules", {"maxShiftHours": 10, "minRestHours": 11, "maxWeeklyHours": 60, "maxConsecutiveDays": 6}, MGR)
    check("réglage refusé au gestionnaire", s == 403, f"({s})")
    s, r = call("PUT", "/api/v1/organization/work-rules", {"maxShiftHours": 30, "minRestHours": 11, "maxWeeklyHours": 60, "maxConsecutiveDays": 6}, ADMIN)
    check("valeur hors bornes refusée", s == 422 and code(r) == "PLN_024", f"({s} {code(r)})")
    s, r = call("PUT", "/api/v1/organization/work-rules", {"maxShiftHours": 12, "minRestHours": 11, "maxWeeklyHours": 60, "maxConsecutiveDays": 6}, ADMIN)
    check("réglage par l'administrateur", s == 200 and r.get("maxShiftHours") == 12, f"({s})")

    print("2. Modèles de postes")
    s, tpl = call("GET", "/api/v1/planning/shift-templates", token=MGR)
    names = [t["name"] for t in tpl or []]
    check("modèles par défaut", s == 200 and {"Matin", "Après-midi", "Nuit"} <= set(names), f"({names})")
    s, t = call("POST", "/api/v1/planning/shift-templates", {"name": "Soirée essai", "startTime": "16:00", "endTime": "23:00"}, MGR)
    created_template = t.get("id") if isinstance(t, dict) else None
    check("modèle ajouté", s == 201 and created_template, f"({s} {code(t)})")
    s, r = call("POST", "/api/v1/planning/shift-templates", {"name": "soirée essai", "startTime": "16:00", "endTime": "23:00"}, MGR)
    check("nom unique", s == 422 and code(r) == "PLN_022", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/planning/shift-templates", {"name": "Vide", "startTime": "08:00", "endTime": "08:00"}, MGR)
    check("début = fin refusé", s == 422 and code(r) == "PLN_022", f"({s} {code(r)})")
    s, r = call("PUT", f"/api/v1/planning/shift-templates/{created_template}", {"name": "Soirée essai", "startTime": "15:00", "endTime": "23:00"}, MGR)
    check("modèle modifié", s == 200 and r.get("startTime", "").startswith("15:00"), f"({s} {r})")

    print("3. Dépassements des règles")
    s, sc = call("POST", "/api/v1/schedules", {"fleetId": DOUALA, "title": "Essai 066", "periodType": "WEEKLY",
                                                "startDate": "2027-01-04", "endDate": "2027-01-10", "notes": "Essai"}, MGR)
    SC = sc["id"]; schedule_ids.append(SC)
    s, r = shift("2027-01-04T06:00:00", "2027-01-04T19:00:00", MEKE, V3, SC)
    check("poste de 13 h refusé sans motif", s == 409 and code(r) == "PLN_020" and "13 h" in (r.get("detail") or r.get("message") or ""), f"({s} {code(r)})")
    s, long_shift = shift("2027-01-04T06:00:00", "2027-01-04T19:00:00", MEKE, V3, SC, workRuleOverride="Inventaire annuel")
    check("accepté avec motif, motif conservé", s == 201 and long_shift.get("workRuleOverride") == "Inventaire annuel", f"({s} {code(long_shift)})")
    s, r = call("GET", f"/api/v1/assignments/work-check?fleetId={DOUALA}&driverId={MEKE}&start=2027-01-05T03:00:00&end=2027-01-05T10:00:00", token=MGR)
    check("contrôle préalable : repos trop court signalé", s == 200 and any(v["code"] == "REST_TOO_SHORT" for v in r["violations"]),
          f"({[v['code'] for v in r.get('violations', [])]})")

    print("4. Création en série")
    bulk = {"scheduleId": SC, "fleetId": DOUALA, "vehicleId": V1, "driverId": FD, "startTime": "07:00", "endTime": "16:00",
            "dates": ["2027-01-04", "2027-01-05", "2027-01-06", "2027-01-07", "2027-01-08"]}
    s, r = call("POST", "/api/v1/assignments/bulk", bulk, MGR)
    check("5 postes créés en une fois", s == 201 and isinstance(r, list) and len(r) == 5, f"({s} {code(r) if isinstance(r, dict) else len(r)})")
    week_shifts = r if isinstance(r, list) else []
    count0 = sql(f"select count(*) from fleet.assignments where schedule_id='{SC}'")
    s, r = call("POST", "/api/v1/assignments/bulk", {**bulk, "vehicleId": V2, "driverId": MVOGO, "dates": ["2027-01-07", "2027-01-08"],
                                                      "startTime": "06:00", "endTime": "14:00", "dates": ["2027-01-07", "2027-01-08"]}, MGR)
    s2, r2 = call("POST", "/api/v1/assignments/bulk", {**bulk, "vehicleId": V2, "driverId": MVOGO, "dates": ["2027-01-09", "2027-01-08"],
                                                       "startTime": "10:00", "endTime": "12:00"}, MGR)
    check("un jour en conflit : rien n'est créé (tout ou rien)", s2 in (409, 422) and "08/01" in (r2.get("detail") or r2.get("message") or "")
          and sql(f"select count(*) from fleet.assignments where schedule_id='{SC}'") == str(int(count0) + 2), f"({s2} {code(r2)})")
    s, night = call("POST", "/api/v1/assignments/bulk", {**bulk, "vehicleId": V2, "driverId": BELLA, "dates": ["2027-01-04"],
                                                         "startTime": "22:00", "endTime": "06:00"}, MGR)
    check("poste de nuit : fin le lendemain", s == 201 and night[0]["endDatetime"].startswith("2027-01-05T06:00"), f"({s})")
    s, r = call("POST", "/api/v1/assignments/bulk", {**bulk, "dates": ["2027-01-09", "2027-01-10"], "startTime": "08:00", "endTime": "12:00"}, MGR)
    check("7e jour d'affilée : refusé sans motif", s == 409 and code(r) == "PLN_020" and "d'affilée" in (r.get("detail") or r.get("message") or ""), f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/assignments/bulk", {**bulk, "dates": ["2027-01-09", "2027-01-10"], "startTime": "08:00", "endTime": "12:00",
                                                     "workRuleOverride": "Renfort exceptionnel"}, MGR)
    check("accepté avec motif", s == 201 and len(r) == 2, f"({s})")

    print("5. Après publication")
    s, r = call("PUT", f"/api/v1/schedules/{SC}/publish", token=MGR)
    check("planning publié, chauffeur notifié", s == 200 and notif(FD, "Programme publié") == 1, f"({s})")
    s, r = call("POST", f"/api/v1/schedules/{SC}/acknowledge", token=DRV)
    check("accusé de réception du planning", s == 200 and r.get("acknowledged") == 7, f"({s} {r})")
    first = week_shifts[0]["id"]
    s, r = call("GET", f"/api/v1/assignments/{first}", token=MGR)
    check("le gestionnaire voit l'accusé", r.get("acknowledgedAt") is not None)
    s, r = call("PATCH", f"/api/v1/assignments/{first}", {"startDatetime": "2027-01-04T08:00:00", "endDatetime": "2027-01-04T17:00:00"}, MGR)
    check("modification sans motif refusée", s == 422 and code(r) == "PLN_021", f"({s} {code(r)})")
    s, r = call("PATCH", f"/api/v1/assignments/{first}", {"startDatetime": "2027-01-04T08:00:00", "endDatetime": "2027-01-04T17:00:00",
                                                           "reason": "Livraison client décalée"}, MGR)
    check("horaires modifiés avec motif", s == 200 and r["startDatetime"].startswith("2027-01-04T08:00") and r.get("changeReason") == "Livraison client décalée"
          and r.get("acknowledgedAt") is None, f"({s} {code(r)})")
    check("chauffeur prévenu du changement", notif(FD, "Poste modifié") == 1)
    s, r = call("PATCH", f"/api/v1/assignments/{first}", {"startDatetime": "2027-01-04T06:00:00", "endDatetime": "2027-01-04T19:30:00",
                                                           "reason": "Inventaire"}, MGR)
    check("modification qui crée un dépassement : motif de dépassement exigé", s == 409 and code(r) == "PLN_020" and "13,5 h" in (r.get("detail") or ""),
          f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/assignments/{first}/acknowledge", token=DRV)
    check("nouvel accusé du poste", s == 200 and r.get("acknowledgedAt") is not None, f"({s})")
    s, r = call("POST", f"/api/v1/assignments/{night[0]['id']}/acknowledge", token=DRV)
    check("accusé refusé sur le poste d'un autre", s == 403 and code(r) == "PLN_025", f"({s} {code(r)})")
    second = week_shifts[1]["id"]
    s, r = call("PATCH", f"/api/v1/assignments/{second}", {"driverId": MVOGO, "reason": "Remplacement"}, MGR)
    check("réaffectation : ancien et nouveau chauffeur prévenus", s == 200 and notif(FD, "Poste retiré") == 1 and notif(MVOGO, "Nouveau poste") == 1, f"({s} {code(r)})")
    third = week_shifts[2]["id"]
    s, r = call("PATCH", f"/api/v1/assignments/{third}/status", {"status": "CANCELLED"}, MGR)
    check("annulation sans motif refusée", s == 422 and code(r) == "PLN_021", f"({s} {code(r)})")
    s, r = call("PATCH", f"/api/v1/assignments/{third}/status", {"status": "CANCELLED", "reason": "Client fermé"}, MGR)
    check("annulation motivée, chauffeur prévenu", s == 200 and r["status"] == "CANCELLED" and r.get("cancelReason") == "Client fermé"
          and notif(FD, "Poste annulé") == 1, f"({s} {code(r)})")
    fourth = week_shifts[3]["id"]
    s, r = call("DELETE", f"/api/v1/assignments/{fourth}", token=MGR)
    check("suppression sans motif refusée", s == 422 and code(r) == "PLN_021", f"({s} {code(r)})")
    s, r = call("DELETE", f"/api/v1/assignments/{fourth}?reason=Doublon", token=MGR)
    check("suppression motivée, chauffeur prévenu", s in (200, 204) and notif(FD, "Poste supprimé") == 1, f"({s})")
    s, r = shift("2027-01-06T17:30:00", "2027-01-06T20:00:00", BELLA, V3, SC, changeReason="Renfort du soir")
    check("poste ajouté au planning publié : chauffeur prévenu", s == 201 and notif(BELLA, "Nouveau poste") == 1, f"({s} {code(r)})")

    print("6. Duplication")
    s, d = call("POST", f"/api/v1/schedules/{SC}/duplicate", {"startDate": "2027-01-04"}, MGR)
    check("même période refusée", s == 422, f"({s} {code(d)})")
    s, d = call("POST", f"/api/v1/schedules/{SC}/duplicate", {"startDate": "2027-01-11"}, MGR)
    if isinstance(d, dict) and d.get("schedule"):
        schedule_ids.append(d["schedule"]["id"])
    check("copie en brouillon sur la semaine suivante", s == 201 and d["schedule"]["status"] == "DRAFT" and d["schedule"]["startDate"] == "2027-01-11"
          and d["schedule"]["endDate"] == "2027-01-17", f"({s} {code(d)})")
    reasons = [x["reason"][:60] for x in d.get("skipped", [])]
    check("postes refusés listés avec leur motif (temps de travail)", d["copied"] >= 5 and any("Temps de travail" in r for r in reasons),
          f"({d.get('copied')} copiés, refusés : {reasons})")
finally:
    ids = ",".join(f"'{i}'" for i in schedule_ids) or "NULL"
    a, b, c, dd = saved_rules.split("|")
    sql(f"""
        delete from fleet.assignments where schedule_id in ({ids});
        delete from fleet.schedules where id in ({ids});
        delete from fleet.notifications where created_at >= '{STARTED}' and (title in ('Programme publié','Poste modifié','Poste retiré','Nouveau poste','Poste annulé','Poste supprimé'));
        delete from fleet.shift_templates where name ilike 'soirée essai';
        delete from fleet.documents where doc_number like 'TEST-066-%';
        update fleet.drivers set fleet_id={"'" + saved_fd + "'" if saved_fd else 'NULL'} where user_id='{FD}';
        {"update fleet.trips set status='DEPARTED' where id in (" + ','.join(repr(t) for t in blocking_trips.split(',')) + ");" if blocking_trips else ""}
        update fleet.companies set max_shift_hours={a}, min_rest_hours={b}, max_weekly_hours={c}, max_consecutive_days={dd} where id='{COMPANY}';
    """)

print(f"\n{sum(results)}/{len(results)} vérifications réussies")
raise SystemExit(0 if all(results) else 1)
