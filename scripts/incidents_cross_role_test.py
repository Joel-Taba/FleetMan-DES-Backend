"""Essai croisé gestionnaire / chauffeur / administrateur : dossiers d'incident.

Backend en mode Kernel (port 8081), comptes réels : manager.dupont (gestionnaire de la Flotte Douala),
driver.bella (chauffeuse de cette flotte), driver.nana (autre chauffeur), adminfleet (administrateur de
l'entreprise). Mot de passe : DEMO_PASSWORD du fichier .env (jamais affiché). Pièces jointes : URL
d'essai, aucun fichier envoyé au Kernel.

Chaque contrôle vérifie les deux côtés : l'action d'un rôle, puis ce que l'autre voit (liste, dossier)
et la notification reçue (GET /api/v1/notifications, type + incidentId dans data).

1. La chauffeuse déclare un incident grave, véhicule non roulant, pendant un trajet en cours.
2. Le gestionnaire instruit : prise en charge, demande d'information, réponse, message, pièces,
   ordre de réparation, résolution (mission reprise), réouverture, annulation.
3. Le gestionnaire déclare un incident pour la chauffeuse ; immobilisation et remise en service
   décidées par lui ; dossier confié à l'administrateur.
4. L'administrateur suit les incidents de toutes les flottes.
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
BASE = "/api/v1/operations/incidents"
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


def notifs(token, event, incident_id):
    """Notifications reçues par ce compte pour cet incident et cet événement."""
    s, page = call("GET", "/api/v1/notifications?page=0&size=200", token=token)
    out = []
    for n in (page or {}).get("content", []) if isinstance(page, dict) else []:
        try:
            data = json.loads(n.get("dataJson") or "{}")
        except ValueError:
            continue
        if data.get("type") == event and data.get("incidentId") == incident_id:
            out.append({**n, "data": data})
    return out


def has(token, event, incident_id, **match):
    return any(all(n["data"].get(k) == v for k, v in match.items()) for n in notifs(token, event, incident_id))


MGR, DRV, OTHER, ADM = login("manager.dupont"), login("driver.bella"), login("driver.nana"), login("adminfleet")
BELLA = sql("select id from fleet.users where username='driver.bella'")
DUPONT = sql("select id from fleet.users where username='manager.dupont'")
DOUALA = sql("select id from fleet.fleets where name='Flotte Douala'")
V = sql("select id from fleet.vehicles where license_plate='DL-203-FF'")
marks = {t: sql(f"select coalesce(max(created_at), 'epoch') from fleet.{t}") for t in ("notifications",)}
saved_vehicle = sql(f"select status from fleet.vehicles where id='{V}'")
saved_bella = sql(f"select coalesce(assigned_vehicle_id::text,'') from fleet.drivers where user_id='{BELLA}'")
ids = []
files = 0
TRIP = None


def declare(token, **body):
    base = {"type": "BREAKDOWN", "severity": "LOW", "description": "Essai croisé 067", "vehicleId": V, "reportedBy": "Essai"}
    s, r = call("POST", BASE, {**base, **body}, token)
    if s == 201:
        ids.append(r["id"])
    return s, r


def case(i, token=MGR):
    return call("GET", f"{BASE}/{i}/case", token=token)


def step(i, status, token=MGR, **extra):
    return call("POST", f"{BASE}/{i}/transition", {"status": status, **extra}, token)


def attach(i, token):
    global files
    files += 1
    return call("POST", f"{BASE}/{i}/attachments", {"kind": "PHOTO", "fileUrl": f"/api/v1/files/essai-067-{files}.jpg",
                                                    "fileName": f"photo-{files}.jpg", "mimeType": "image/jpeg"}, token)


def comment(i, token, message, request_info=False):
    return call("POST", f"{BASE}/{i}/comments", {"message": message, "requestInfo": request_info}, token)


def find(lst, i):
    return next((x for x in (lst or []) if x.get("id") == i), None)


def vstatus():
    return sql(f"select status from fleet.vehicles where id='{V}'")


def trip_state():
    return sql(f"select coalesce(interrupted_at::text,'') || '|' || coalesce(resumed_at::text,'') || '|' || "
               f"coalesce(interruption_incident_id::text,'') from fleet.trips where id='{TRIP}'").split("|")


sql(f"update fleet.drivers set assigned_vehicle_id='{V}' where user_id='{BELLA}'")
sql(f"update fleet.vehicles set status='ON_TRIP' where id='{V}'")
TRIP = sql(f"insert into fleet.trips (id, trip_code, vehicle_id, driver_id, fleet_id, status, start_date, start_time, created_at) "
           f"values (gen_random_uuid(), 'ESSAI-067', '{V}', '{BELLA}', '{DOUALA}', 'DEPARTED', current_date, localtime(0), now()) returning id").splitlines()[0]  # psql ajoute « INSERT 0 1 »
try:
    print("1. La chauffeuse déclare : le gestionnaire est prévenu tout de suite")
    s, i1 = declare(DRV, type="ACCIDENT", severity="CRITICAL", vehicleDrivable=False, locationLabel="Carrefour Ndokoti",
                    latitude=4.0511, longitude=9.7679, description="Choc arrière, essai croisé 067")
    check("déclaration acceptée, la chauffeuse est le chauffeur concerné (sans driverId envoyé)",
          s == 201 and i1.get("driverId") == BELLA, f"({s} {code(i1)})")
    I1 = i1["id"] if s == 201 else ""
    s, lst = call("GET", BASE, token=MGR)
    row = (lst or [{}])[0] if s == 200 else {}
    check("en tête de la liste du gestionnaire", s == 200 and row.get("id") == I1, f"({s})")
    check("liste : chauffeur, gravité, non roulant, immobilisé, lieu, GPS",
          row.get("driverFullName") and row.get("severity") == "CRITICAL" and row.get("vehicleDrivable") is False
          and row.get("vehicleImmobilized") is True and row.get("locationLabel") == "Carrefour Ndokoti"
          and abs(float((row.get("location") or {}).get("latitude") or 0) - 4.0511) < 1e-6, f"({row.get('driverFullName')})")
    check("liste : trajet interrompu signalé", row.get("tripCode") == "ESSAI-067" and row.get("tripInterrupted") is True,
          f"({row.get('tripCode')} {row.get('tripInterrupted')})")
    n = notifs(MGR, "INCIDENT_DECLARED", I1)
    check("gestionnaire notifié (INCIDENT_DECLARED, alerte)", len(n) == 1 and n[0].get("type") == "WARNING"
          and n[0]["data"].get("tripId") == TRIP, f"({len(n)} {n[0].get('type') if n else ''})")
    check("la déclarante n'est pas notifiée de sa propre déclaration ni de l'interruption",
          not notifs(DRV, "INCIDENT_DECLARED", I1) and not notifs(DRV, "INCIDENT_TRIP_INTERRUPTED", I1), "")
    t = trip_state()
    check("véhicule immobilisé, mission interrompue (visible des deux côtés)", vstatus() == "MAINTENANCE" and t[0] and not t[1]
          and t[2] == I1, f"({vstatus()} {t})")
    # Le chauffeur consulte son trajet par /trips/my-active (GET /trips/{id} est réservé au gestionnaire).
    s, tr = call("GET", "/api/v1/trips/my-active", token=DRV)
    check("la chauffeuse voit son trajet interrompu", s == 200 and (tr or {}).get("id") == TRIP
          and (tr or {}).get("interruptionIncidentId") == I1, f"({s} {(tr or {}).get('tripCode')})")
    s, mine = call("GET", f"{BASE}/mine", token=DRV)
    m = find(mine, I1) or {}
    check("« mes incidents » : statut Déclaré", s == 200 and m.get("status") == "REPORTED", f"({s} {m.get('status')})")
    s, r = case(I1, OTHER)
    check("un autre chauffeur ne lit pas le dossier", s == 403, f"({s})")
    check("l'autre chauffeur n'est pas notifié", not notifs(OTHER, "INCIDENT_DECLARED", I1), "")

    print("2. Photos de la chauffeuse")
    s, r = attach(I1, DRV)
    check("photo ajoutée juste après la déclaration : pas de 2e notification", s == 200 and not notifs(MGR, "INCIDENT_ATTACHMENT_ADDED", I1), f"({s})")
    sql(f"update fleet.notifications set is_read=TRUE where user_id='{DUPONT}' and data like '%{I1}%'")
    s, r = attach(I1, DRV)
    check("nouvelle photo, notification précédente lue : gestionnaire notifié", s == 200 and has(MGR, "INCIDENT_ATTACHMENT_ADDED", I1), f"({s})")
    s, lst = call("GET", BASE, token=MGR)
    check("liste : 2 photos", (find(lst, I1) or {}).get("photoCount") == 2, f"({(find(lst, I1) or {}).get('photoCount')})")

    print("3. Instruction par le gestionnaire, suivie par la chauffeuse")
    s, r = step(I1, "ACKNOWLEDGED", token=DRV)
    check("la chauffeuse n'instruit pas", s == 403, f"({s})")
    s, r = step(I1, "ACKNOWLEDGED")
    check("prise en charge", s == 200 and r["incident"]["status"] == "ACKNOWLEDGED", f"({s} {code(r)})")
    check("chauffeuse notifiée : pris en charge", has(DRV, "INCIDENT_STATUS_CHANGED", I1, status="ACKNOWLEDGED"), "")
    check("l'auteur (gestionnaire) n'est pas notifié", not notifs(MGR, "INCIDENT_STATUS_CHANGED", I1), "")
    s, mine = call("GET", f"{BASE}/mine", token=DRV)
    s2, c = case(I1, DRV)
    check("côté chauffeuse : statut à jour et étape dans l'historique", (find(mine, I1) or {}).get("status") == "ACKNOWLEDGED"
          and s2 == 200 and c["timeline"][-1]["toStatus"] == "ACKNOWLEDGED" and c.get("handlerName"), f"({s2})")

    s, r = comment(I1, MGR, "Pouvez-vous envoyer la photo du constat ?", True)
    check("demande d'information", s == 200 and r["timeline"][-1]["type"] == "INFO_REQUESTED", f"({s} {code(r)})")
    check("chauffeuse notifiée (INCIDENT_INFO_REQUESTED)", has(DRV, "INCIDENT_INFO_REQUESTED", I1), "")
    s, c = case(I1, DRV)
    s3, mine = call("GET", f"{BASE}/mine", token=DRV)
    check("la chauffeuse voit la demande en attente", c.get("pendingInfoRequest") == "Pouvez-vous envoyer la photo du constat ?"
          and (find(mine, I1) or {}).get("infoRequestPending") is True, f"({c.get('pendingInfoRequest')})")
    s, r = comment(I1, DRV, "Constat signé, je l'envoie ce soir.", True)
    check("réponse de la chauffeuse (requestInfo ignoré pour elle)", s == 200 and r["timeline"][-1]["type"] == "INFO_PROVIDED"
          and r["timeline"][-1].get("byDriver") is True and r.get("pendingInfoRequest") is None, f"({s} {code(r)})")
    check("gestionnaire notifié de la réponse (INCIDENT_DRIVER_REPLY)", has(MGR, "INCIDENT_DRIVER_REPLY", I1), "")
    check("la chauffeuse ne reçoit pas sa propre réponse", not notifs(DRV, "INCIDENT_DRIVER_REPLY", I1), "")
    s, c = case(I1, MGR)
    check("le gestionnaire voit la réponse dans l'historique", s == 200 and any(e["type"] == "INFO_PROVIDED"
          and e["note"].startswith("Constat signé") for e in c["timeline"]) and c.get("pendingInfoRequest") is None, f"({s})")
    s, r = comment(I1, DRV, "Précision : le tiers a pris la fuite.")
    check("message libre de la chauffeuse", s == 200 and r["timeline"][-1]["type"] == "DRIVER_COMMENT", f"({s})")
    s, r = comment(I1, MGR, "Bien noté, merci.")
    check("message du gestionnaire → chauffeuse notifiée (INCIDENT_COMMENT)", s == 200 and has(DRV, "INCIDENT_COMMENT", I1), f"({s})")
    s, r = comment(I1, MGR, "   ")
    check("message vide refusé", s in (400, 422), f"({s} {code(r)})")

    s, r = step(I1, "UNDER_INVESTIGATION")
    check("en traitement → chauffeuse notifiée", s == 200 and has(DRV, "INCIDENT_STATUS_CHANGED", I1, status="UNDER_INVESTIGATION"), f"({s})")
    s, r = attach(I1, MGR)
    check("pièce du gestionnaire → chauffeuse notifiée", s == 200 and has(DRV, "INCIDENT_ATTACHMENT_ADDED", I1), f"({s})")
    s, r = call("POST", f"{BASE}/{I1}/repair-order", {"subject": "Essai 067 réparation"}, MGR)
    mid = (r or {}).get("details", {}).get("maintenanceId")
    check("ordre de réparation → chauffeuse notifiée avec maintenanceId", s == 200 and mid
          and has(DRV, "INCIDENT_REPAIR_ORDER", I1, maintenanceId=mid), f"({s})")

    s, r = step(I1, "RESOLVED", report="Véhicule réparé")
    t = trip_state()
    check("résolu : véhicule libéré, mission reprise", s == 200 and vstatus() == "ON_TRIP" and t[1], f"({s} {vstatus()} {t})")
    check("chauffeuse notifiée : résolu et mission reprise", has(DRV, "INCIDENT_STATUS_CHANGED", I1, status="RESOLVED")
          and has(DRV, "INCIDENT_TRIP_RESUMED", I1, tripId=TRIP), "")
    s, r = step(I1, "UNDER_INVESTIGATION", note="Fuite constatée")
    n = [x for x in notifs(DRV, "INCIDENT_STATUS_CHANGED", I1) if x.get("title") == "Incident rouvert"]
    check("réouverture → chauffeuse notifiée (« Incident rouvert »)", s == 200 and n, f"({s})")
    s, r = step(I1, "CANCELLED", note="Essai terminé")
    n = [x for x in notifs(DRV, "INCIDENT_STATUS_CHANGED", I1) if x["data"].get("status") == "CANCELLED"]
    check("annulation motivée → chauffeuse notifiée avec le motif", s == 200 and n and "Essai terminé" in n[0].get("message", ""), f"({s})")
    s, r = comment(I1, DRV, "Encore une question")
    check("dossier annulé : plus d'échange", s >= 400, f"({s})")
    s, mine = call("GET", f"{BASE}/mine", token=DRV)
    check("« mes incidents » : statut Annulé", (find(mine, I1) or {}).get("status") == "CANCELLED", "")

    print("4. Le gestionnaire déclare pour la chauffeuse")
    s, i2 = declare(MGR, type="TRAFFIC_VIOLATION", severity="LOW", driverId=BELLA, description="Excès de vitesse, essai 067")
    I2 = i2["id"] if s == 201 else ""
    check("déclaration du gestionnaire", s == 201 and i2.get("driverId") == BELLA, f"({s} {code(i2)})")
    n = notifs(DRV, "INCIDENT_DECLARED", I2)
    check("chauffeuse notifiée (« Incident déclaré vous concernant »)", n and n[0].get("title") == "Incident déclaré vous concernant", "")
    check("le gestionnaire auteur n'est pas notifié", not notifs(MGR, "INCIDENT_DECLARED", I2), "")
    s, mine = call("GET", f"{BASE}/mine", token=DRV)
    check("visible dans « mes incidents » de la chauffeuse", find(mine, I2) is not None, f"({s})")
    s, i3 = declare(MGR, type="OTHER", severity="LOW", description="Rétroviseur, essai 067")
    I3 = i3["id"] if s == 201 else ""
    check("sans chauffeur indiqué : le chauffeur du trajet en cours est retenu", s == 201 and i3.get("driverId") == BELLA, f"({s} {i3.get('driverId')})")
    s, r = call("PATCH", f"{BASE}/{I3}/immobilization", {"immobilized": True, "reason": "Contrôle de sécurité"}, MGR)
    check("immobilisation décidée → chauffeuse notifiée (véhicule + mission interrompue)", s == 200 and vstatus() == "MAINTENANCE"
          and has(DRV, "INCIDENT_VEHICLE_IMMOBILIZED", I3) and has(DRV, "INCIDENT_TRIP_INTERRUPTED", I3, tripId=TRIP), f"({s} {vstatus()})")
    s, r = call("PATCH", f"{BASE}/{I3}/immobilization", {"immobilized": False, "reason": "Contrôle fait"}, MGR)
    check("remise en service → chauffeuse notifiée, mission reprise", s == 200 and vstatus() == "ON_TRIP"
          and has(DRV, "INCIDENT_VEHICLE_RELEASED", I3) and has(DRV, "INCIDENT_TRIP_RESUMED", I3), f"({s} {vstatus()})")
    s, c = case(I3)
    other = next((h["id"] for h in c.get("handlers", []) if h["id"] != DUPONT), None) if s == 200 else None
    s, r = step(I3, "ACKNOWLEDGED", handlerId=other) if other else (0, {})
    check("dossier confié à l'administrateur → il est notifié (INCIDENT_ASSIGNED)", s == 200 and has(ADM, "INCIDENT_ASSIGNED", I3), f"({s} {other})")

    print("5. L'administrateur suit toutes les flottes")
    s, lst = call("GET", f"{BASE}/organization", token=ADM)
    a1, a3 = find(lst, I1) or {}, find(lst, I3) or {}
    check("incidents visibles avec le même statut", s == 200 and a1.get("status") == "CANCELLED" and a3.get("status") == "ACKNOWLEDGED"
          and a3.get("handlerName"), f"({s} {a1.get('status')} {a3.get('status')})")
    s, c = case(I1, ADM)
    check("dossier lisible par l'administrateur, historique complet", s == 200 and any(e["type"] == "INFO_PROVIDED" for e in c["timeline"]), f"({s})")
    s, r = call("GET", f"{BASE}/organization", token=MGR)
    check("liste entreprise réservée à l'administrateur", s == 403, f"({s})")
    for i in (I2, I3):
        if i:
            step(i, "CANCELLED", note="Fin de l'essai")
finally:
    if ids:
        lst = ",".join(f"'{i}'" for i in ids)
        like = ",".join(f"'%{i}%'" for i in ids)
        sql(f"""
            delete from fleet.alert_events where source_entity_id in ({lst})
                or source_entity_id in (select maintenance_id from fleet.incident_cases where incident_id in ({lst}));
            delete from fleet.notifications where data like any (array[{like}]);
            update fleet.trips set interruption_incident_id = NULL where interruption_incident_id in ({lst});
            delete from fleet.maintenances where id in (select maintenance_id from fleet.incident_cases where incident_id in ({lst}));
            delete from fleet.incidents where id in ({lst});
        """)
        sql(f"delete from fleet.notifications where created_at > '{marks['notifications']}' and data like '%Essai 067%'")
    if TRIP:
        sql(f"delete from fleet.notifications where data like '%{TRIP}%'")
        sql(f"delete from fleet.trips where id='{TRIP}'")
    sql(f"update fleet.vehicles set status='{saved_vehicle}' where id='{V}'")
    sql(f"update fleet.drivers set assigned_vehicle_id = {repr(saved_bella) if saved_bella else 'NULL'} where user_id='{BELLA}'")
    print(f"  ↺ données restaurées : {sum(results)}/{len(results)} contrôles OK")
