"""Essai : tournées multi-arrêts et ressources disponibles au planning (migration 065).

Backend en mode Kernel (port 8081), comptes réels : manager.dupont (gestionnaire de la flotte
Douala), driver.bella (chauffeuse de cette flotte) et driver.nana (autre chauffeur). Mot de passe :
DEMO_PASSWORD du fichier .env.

1. Disponibilités : seuls les véhicules et chauffeurs libres sur le créneau sont proposés.
2. Plan : 1 à 20 arrêts, heures prévues dans le créneau et dans l'ordre, manifeste par arrêt.
3. Avant le départ : ordre, ajout, retrait, modification ; rien ne se sert avant le départ.
4. En route : arrivée, livraison (réceptionnaire, écart motivé, preuve), échec motivé, arrêt
   ajouté puis passé ; fin de tournée → « livraisons terminées » ; retour à la base.
5. Ancien formulaire (destination seule) et retour avec arrêts non servis.
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



MGR, DRV, OTHER = login("manager.dupont"), login("driver.bella"), login("driver.nana")
BELLA = sql("select id from fleet.users where username='driver.bella'")
DOUALA = sql("select id from fleet.fleets where name='Flotte Douala'")
V1 = sql("select id from fleet.vehicles where license_plate='DL-203-FF'")
V2 = sql(f"select id from fleet.vehicles where fleet_id='{DOUALA}' and id<>'{V1}' and deleted_at is null and status<>'RETIRED' order by license_plate limit 1")
marks = {t: sql(f"select coalesce(max(created_at), 'epoch') from fleet.{t}") for t in ("trips", "notifications", "driver_absences")}
saved_vehicles = sql(f"select json_agg(json_build_object('id', id, 's', status, 't', tank_capacity)) from fleet.vehicles where id in ('{V1}','{V2}')")
saved_odo = sql(f"select odometer_reading from fleet.operational_parameters where vehicle_id='{V1}'")
odo = float(saved_odo or 0)
sql(f"update fleet.vehicles set tank_capacity=60 where id in ('{V1}','{V2}')")
for v in (V1, V2):
    for doc in ("INSURANCE", "TECHNICAL_CONTROL", "REGISTRATION"):
        sql(f"insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, expiry_date, file_url, review_status, is_current, "
            f"created_at, updated_at) select gen_random_uuid(), 'VEHICLE', '{v}', '{doc}', 'TEST-065-{doc}', current_date + 400, "
            f"'/api/v1/files/TEST-065.pdf', 'APPROVED', TRUE, now(), now() "
            f"where not exists (select 1 from fleet.documents where target_id='{v}' and doc_type='{doc}' and is_current)")
sql("insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, license_categories, expiry_date, file_url, "
    "review_status, is_current, created_at, updated_at) select gen_random_uuid(), 'DRIVER', d.user_id, 'DRIVING_LICENSE', 'TEST-065-LIC', 'B,C,D', "
    "current_date + 400, '/api/v1/files/TEST-065.pdf', 'APPROVED', TRUE, now(), now() from fleet.drivers d "
    "where not exists (select 1 from fleet.documents x where x.target_id=d.user_id and x.doc_type='DRIVING_LICENSE' and x.is_current)")

now = datetime.datetime.now().replace(second=0, microsecond=0)
start = now + datetime.timedelta(minutes=30)
ret = start + datetime.timedelta(hours=8)
iso = lambda d: d.strftime("%Y-%m-%dT%H:%M:%S")
trip_ids = []


def later():
    """Heure de retour déclarée : après le départ réel (enregistré à la seconde)."""
    return datetime.datetime.now() + datetime.timedelta(minutes=2)


def avail(st, en, **extra):
    q = f"startDate={st.date().isoformat()}&startTime={st.strftime('%H:%M:00')}&scheduledReturn={iso(en)}"
    for k, v in extra.items():
        q += f"&{k}={v}"
    s, r = call("GET", f"/api/v1/trips/availability?{q}", token=MGR)
    return s, r


def ids(r, key):
    return [x["id"] for x in (r or {}).get(key, [])]


def stops_of(trip_id, token=MGR):
    s, r = call("GET", f"/api/v1/trips/{trip_id}/stops", token=token)
    return r if s == 200 else []


# Les plannings de démo occupent ces ressources en journée : leurs postes sont mis de côté le temps de l'essai.
parked = sql(f"select string_agg(id::text || ':' || status, ',') from fleet.assignments where status in ('PENDING','IN_PROGRESS') "
             f"and (driver_id='{BELLA}' or vehicle_id in ('{V1}','{V2}')) "
             f"and start_datetime < now() + interval '36 hours' and end_datetime > now() - interval '12 hours'")
if parked:
    sql(f"update fleet.assignments set status='CANCELLED' where id in ({','.join(repr(x.split(':')[0]) for x in parked.split(','))})")
try:
    print("1. Ressources disponibles au planning")
    s, r = avail(start, ret)
    check("véhicule et chauffeuse libres proposés", s == 200 and V1 in ids(r, "vehicles") and BELLA in ids(r, "drivers"),
          f"({s} {len(ids(r, 'vehicles'))} véhicules, {len(ids(r, 'drivers'))} chauffeurs)")
    s, r = call("GET", f"/api/v1/trips/availability?startDate={start.date()}&startTime=10:00:00&scheduledReturn={start.date()}T09:00:00", token=MGR)
    check("créneau incohérent refusé", s in (400, 409, 422), f"({s} {code(r)})")
    s, r = call("GET", f"/api/v1/trips/availability?startDate={start.date()}&startTime=08:00:00&scheduledReturn={start.date()}T18:00:00", token=DRV)
    check("réservé au gestionnaire", s == 403, f"({s})")

    print("2. Plan de la tournée")
    base = {"vehicleId": V1, "driverId": BELLA, "fleetId": DOUALA,
            "startDate": start.date().isoformat(), "startTime": start.strftime("%H:%M:00"),
            "scheduledReturnDatetime": iso(ret), "departureLocation": "Dépôt Bonabéri",
            "departureKmIndex": odo, "departureFuelIndex": 40, "missionObject": "Tournée essai 065"}
    stops = [
        {"stopType": "DELIVERY", "location": "Boutique Akwa", "contactName": "Awa", "contactPhone": "+237 690 00 00 01",
         "plannedArrival": iso(start + datetime.timedelta(hours=1))},
        {"stopType": "PICKUP", "location": "Fournisseur Bassa", "plannedArrival": iso(start + datetime.timedelta(hours=2))},
        {"stopType": "DELIVERY", "location": "Supermarché Bonapriso", "plannedArrival": iso(start + datetime.timedelta(hours=3))},
    ]
    lines = [{"itemType": "CARGO", "description": "Cartons de lait", "quantity": 10, "stopIndex": 0},
             {"itemType": "CARGO", "description": "Packs d'eau", "quantity": 5, "stopIndex": 0},
             {"itemType": "CARGO", "description": "Palettes vides", "quantity": 4, "stopIndex": 1},
             {"itemType": "CARGO", "description": "Sacs de riz", "quantity": 8, "stopIndex": 2}]
    bad = [dict(stops[0]), dict(stops[1], plannedArrival=iso(start + datetime.timedelta(minutes=30)))]
    s, r = call("POST", "/api/v1/trips", {**base, "stops": bad, "details": lines[:1]}, MGR)
    check("heures prévues hors de l'ordre de passage : refusé", s == 422 and code(r) == "TRP_027", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/trips", {**base, "stops": [dict(stops[0], plannedArrival=iso(ret + datetime.timedelta(hours=1)))]}, MGR)
    check("heure prévue après le retour : refusé", s == 422 and code(r) == "TRP_027", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/trips", {**base, "stops": stops, "details": [dict(lines[0], stopIndex=7)]}, MGR)
    check("ligne vers un arrêt inexistant : refusée", s == 422 and code(r) == "TRP_027", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/trips", {**base, "stops": [dict(stops[0], stopType="TRANSIT")]}, MGR)
    check("type d'arrêt inconnu : refusé", s == 422 and code(r) == "TRP_027", f"({s} {code(r)})")
    s, trip = call("POST", "/api/v1/trips", {**base, "stops": stops, "details": lines}, MGR)
    check("tournée de 3 arrêts créée", s == 201 and len(trip.get("stops", [])) == 3, f"({s} {code(trip)})")
    TRIP = trip["id"]; trip_ids.append(TRIP); CODE = trip["tripCode"]
    SA, SB, SC = [x["id"] for x in trip["stops"]]
    check("destination = dernier arrêt", trip["destinationLocation"] == "Supermarché Bonapriso", f"({trip['destinationLocation']})")
    by_desc = {d["description"]: d for d in trip["details"]}
    check("manifeste rattaché aux arrêts", by_desc["Cartons de lait"]["stopId"] == SA and by_desc["Palettes vides"]["stopId"] == SB
          and by_desc["Sacs de riz"]["stopId"] == SC)

    s, r = avail(start + datetime.timedelta(hours=1), start + datetime.timedelta(hours=2))
    check("créneau chevauchant : véhicule et chauffeuse retirés des listes", s == 200 and V1 not in ids(r, "vehicles")
          and BELLA not in ids(r, "drivers"), f"({r.get('unavailableVehicles')} véhicules / {r.get('unavailableDrivers')} chauffeurs indisponibles)")
    s, r = avail(start + datetime.timedelta(hours=1), start + datetime.timedelta(hours=2), excludeTripId=TRIP)
    check("en modification du même trajet : de nouveau proposés", V1 in ids(r, "vehicles") and BELLA in ids(r, "drivers"))
    s, r = avail(ret + datetime.timedelta(hours=1), ret + datetime.timedelta(hours=3))
    check("après le retour prévu : de nouveau libres", V1 in ids(r, "vehicles") and BELLA in ids(r, "drivers"))
    if V2:
        sql(f"update fleet.vehicles set status='MAINTENANCE' where id='{V2}'")
        s, r = avail(ret + datetime.timedelta(hours=1), ret + datetime.timedelta(hours=3))
        check("véhicule en maintenance : non proposé", V2 not in ids(r, "vehicles"))
        sql(f"update fleet.vehicles set status='AVAILABLE' where id='{V2}'")
    day = (ret + datetime.timedelta(days=2)).date().isoformat()
    sql(f"insert into fleet.driver_absences (id, driver_id, type, start_date, end_date, reason, created_at) "
        f"values (gen_random_uuid(), '{BELLA}', 'LEAVE', '{day}', '{day}', 'Essai 065', now())")
    s, r = call("GET", f"/api/v1/trips/availability?startDate={day}&startTime=08:00:00&scheduledReturn={day}T17:00:00", token=MGR)
    check("chauffeuse en congé : non proposée", s == 200 and BELLA not in ids(r, "drivers"), f"({s})")

    print("3. Avant le départ")
    s, r = call("PUT", f"/api/v1/trips/{TRIP}/stops/order", {"stopIds": [SA, SC, SB]}, MGR)
    check("ordre changé : heures prévues désormais incohérentes → refusé", s == 422 and code(r) == "TRP_027", f"({s} {code(r)})")
    s, r = call("PUT", f"/api/v1/trips/{TRIP}/stops/{SB}", dict(stops[1], plannedArrival=None, contactName="M. Ekane"), MGR)
    check("arrêt modifié (contact, sans heure)", s == 200 and r.get("contactName") == "M. Ekane", f"({s} {code(r)})")
    s, r = call("PUT", f"/api/v1/trips/{TRIP}/stops/order", {"stopIds": [SA, SC, SB]}, MGR)
    check("ordre changé", s == 200 and [x["id"] for x in r] == [SA, SC, SB], f"({s} {code(r)})")
    s, t = call("GET", f"/api/v1/trips/{TRIP}", token=MGR)
    check("destination suit le nouvel ordre", t.get("destinationLocation") == "Fournisseur Bassa", f"({t.get('destinationLocation')})")
    s, r = call("PUT", f"/api/v1/trips/{TRIP}/stops/order", {"stopIds": [SA, SC]}, MGR)
    check("ordre incomplet refusé", s == 422, f"({s} {code(r)})")
    s, D = call("POST", f"/api/v1/trips/{TRIP}/stops", {"stop": {"stopType": "DELIVERY", "location": "Client Deido"},
                                                        "lines": [{"itemType": "CARGO", "description": "Colis", "quantity": 2}]}, MGR)
    check("arrêt ajouté avec sa ligne", s == 200 and D.get("sequence") == 4, f"({s} {code(D)})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops", {"stop": {"stopType": "DELIVERY", "location": "X"}}, DRV)
    check("ajout refusé au chauffeur", s == 403, f"({s})")
    s, r = call("DELETE", f"/api/v1/trips/{TRIP}/stops/{D['id']}", token=MGR)
    left = stops_of(TRIP)
    colis = sql(f"select count(*) from fleet.trip_details where trip_id='{TRIP}' and description='Colis'")
    check("arrêt retiré avec ses lignes", s == 200 and len(left) == 3 and colis == "0", f"({s} {len(left)} arrêts)")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops/{SA}/arrive", {}, DRV)
    check("rien ne se sert avant le départ", s == 409 and code(r) == "TRP_035", f"({s} {code(r)})")

    print("4. En route")
    s, t = call("POST", f"/api/v1/trips/{TRIP}/start", {"departureKmIndex": odo, "departureFuelIndex": 40}, MGR)
    check("départ enregistré par le gestionnaire", s == 200 and t.get("status") == "DEPARTED", f"({s} {code(t)})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops/{SA}/arrive", {}, OTHER)
    check("un autre chauffeur ne peut pas agir", s in (403, 404), f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops/{SA}/arrive", {}, DRV)
    check("arrivée sur l'arrêt 1", s == 200 and r.get("status") == "ARRIVED" and r.get("arrivedAt"), f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops/{SA}/arrive", {}, DRV)
    check("deuxième arrivée refusée", s == 409 and code(r) == "TRP_029", f"({s} {code(r)})")
    milk, water = by_desc["Cartons de lait"]["id"], by_desc["Packs d'eau"]["id"]
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops/{SA}/complete", {"lines": []}, DRV)
    check("livraison sans réceptionnaire : refusée", s == 422 and code(r) == "TRP_033", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops/{SA}/complete", {"recipientName": "Awa", "lines": [{"detailId": milk, "quantity": 8}]}, DRV)
    check("écart sans motif : refusé", s == 422 and code(r) == "TRP_027", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops/{SA}/complete", {"recipientName": "Awa", "lines": [{"detailId": milk, "quantity": 11}]}, DRV)
    check("plus que chargé : refusé", s == 422 and code(r) == "TRP_025", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops/{SA}/complete", {"recipientName": "Awa", "proofUrl": "https://ailleurs.example/x.jpg"}, DRV)
    check("preuve hors FleetMan : refusée", s == 422 and code(r) == "TRP_034", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops/{SA}/complete",
                {"recipientName": "Awa", "notes": "RAS", "proofUrl": "/api/v1/files/preuve-essai-065.jpg",
                 "lines": [{"detailId": milk, "quantity": 8, "shortageReason": "DAMAGED"}]}, DRV)
    check("arrêt 1 livré", s == 200 and r.get("status") == "COMPLETED" and r.get("recipientName") == "Awa", f"({s} {code(r)})")
    check("ponctualité calculée", r.get("punctuality") in ("ON_TIME", "EARLY", "LATE") and r.get("delayMinutes") is not None,
          f"({r.get('punctuality')} {r.get('delayMinutes')} min)")
    q = sql(f"select delivered_quantity || '|' || coalesce(shortage_reason,'') from fleet.trip_details where id in ('{milk}','{water}') order by description")
    check("quantités : écart motivé, ligne non citée livrée en entier", q.splitlines() == ["8|DAMAGED", "5|"], f"({q.splitlines()})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops/{SA}/complete", {"recipientName": "Awa"}, DRV)
    check("arrêt déjà clos", s == 409 and code(r) == "TRP_029", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops/{SC}/fail", {"reason": "PARTI"}, DRV)
    check("motif d'échec inconnu", s == 422 and code(r) == "TRP_027", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops/{SC}/fail", {"reason": "CLOSED", "notes": "Rideau baissé"}, DRV)
    check("arrêt 2 non servi (fermé)", s == 200 and r.get("status") == "FAILED" and r.get("failureReason") == "CLOSED", f"({s} {code(r)})")
    rice = sql(f"select delivered_quantity from fleet.trip_details where trip_id='{TRIP}' and description='Sacs de riz'")
    notif = sql(f"select count(*) from fleet.notifications where created_at > '{marks['notifications']}' and data like '%TRIP_STOP_FAILED%{TRIP}%'")
    check("rien livré, gestionnaire prévenu", rice == "0" and notif == "1", f"(livré {rice}, notif {notif})")
    s, E = call("POST", f"/api/v1/trips/{TRIP}/stops", {"stop": {"stopType": "PICKUP", "location": "Ramassage urgent Ndokoti"}, "position": 0}, MGR)
    check("ramassage urgent ajouté en route, placé après les arrêts clos", s == 200 and E.get("sequence") == 3, f"({s} seq={E.get('sequence')})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops/{E['id']}/skip", {"reason": " "}, MGR)
    check("passer un arrêt exige un motif", s == 422, f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops/{E['id']}/skip", {"reason": "Client annulé"}, DRV)
    check("passer un arrêt : réservé au gestionnaire", s == 403, f"({s})")
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops/{E['id']}/skip", {"reason": "Client annulé"}, MGR)
    check("arrêt passé par le gestionnaire", s == 200 and r.get("status") == "SKIPPED", f"({s} {code(r)})")
    s, r = call("PUT", f"/api/v1/trips/{TRIP}/stops/order", {"stopIds": [x["id"] for x in stops_of(TRIP)]}, MGR)
    check("ordre figé après le départ", s == 409 and code(r) == "TRP_031", f"({s} {code(r)})")
    pallets = by_desc["Palettes vides"]["id"]
    s, r = call("POST", f"/api/v1/trips/{TRIP}/stops/{SB}/complete", {"lines": [{"detailId": pallets, "quantity": 6}]}, DRV)
    check("ramassage : plus que prévu accepté, sans réceptionnaire", s == 200 and r.get("status") == "COMPLETED", f"({s} {code(r)})")
    s, t = call("GET", f"/api/v1/trips/{TRIP}", token=MGR)
    done = sql(f"select count(*) from fleet.notifications where created_at > '{marks['notifications']}' and data like '%TRIP_TOUR_DONE%{TRIP}%'")
    check("tous les arrêts clos : livraisons terminées", t.get("status") == "ARRIVED" and t.get("arrivedAt") and done == "1",
          f"({t.get('status')} notif={done})")
    s, F = call("POST", f"/api/v1/trips/{TRIP}/stops", {"stop": {"stopType": "DELIVERY", "location": "Retour imprévu SBépanda"}}, MGR)
    s2, t = call("GET", f"/api/v1/trips/{TRIP}", token=MGR)
    check("un arrêt ajouté rouvre la tournée", s == 200 and t.get("status") == "DEPARTED" and t.get("arrivedAt") is None, f"({s} {t.get('status')})")
    call("POST", f"/api/v1/trips/{TRIP}/stops/{F['id']}/skip", {"reason": "Annulé"}, MGR)
    s, t = call("GET", f"/api/v1/trips/{TRIP}", token=MGR)
    check("de nouveau livraisons terminées", t.get("status") == "ARRIVED", f"({t.get('status')})")
    s, t = call("POST", "/api/v1/trips/return", {"tripCode": CODE, "returnDate": later().date().isoformat(),
                                                  "returnTime": later().strftime("%H:%M:00"), "returnLocation": "Dépôt Bonabéri",
                                                  "returnKmIndex": odo + 85, "returnFuelIndex": 20}, MGR)
    statuses = [x["status"] for x in (t.get("stops") or [])]
    check("retour à la base : trajet terminé, arrêts conservés", s == 200 and t.get("status") == "COMPLETED"
          and statuses.count("COMPLETED") == 2 and "FAILED" in statuses, f"({s} {statuses})")

    print("5. Ancien formulaire et retour anticipé")
    s, t2 = call("POST", "/api/v1/trips", {**base, "startDate": (ret + datetime.timedelta(hours=1)).date().isoformat(),
                                            "startTime": (ret + datetime.timedelta(hours=1)).strftime("%H:%M:00"),
                                            "scheduledReturnDatetime": iso(ret + datetime.timedelta(hours=4)),
                                            "destinationLocation": "Entrepôt Akwa",
                                            "details": [{"itemType": "CARGO", "description": "Fûts", "quantity": 3}]}, MGR)
    check("destination seule : un arrêt unique", s == 201 and len(t2.get("stops", [])) == 1
          and t2["stops"][0]["location"] == "Entrepôt Akwa" and t2["details"][0]["stopId"] == t2["stops"][0]["id"], f"({s} {code(t2)})")
    trip_ids.append(t2["id"])
    sql(f"update fleet.operational_parameters set odometer_reading={odo + 85} where vehicle_id='{V1}'")
    s, r = call("POST", f"/api/v1/trips/{t2['id']}/start", {"departureKmIndex": odo + 85, "departureFuelIndex": 40}, MGR)
    s, r = call("PATCH", f"/api/v1/trips/{t2['id']}", {"destinationLocation": "Entrepôt Bassa"}, MGR)
    check("destination modifiée : l'arrêt unique suit", s == 200 and r["stops"][0]["location"] == "Entrepôt Bassa", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/trips/return", {"tripCode": t2["tripCode"], "returnDate": later().date().isoformat(),
                                                  "returnTime": later().strftime("%H:%M:00"),
                                                  "returnKmIndex": odo + 100, "returnFuelIndex": 30}, MGR)
    check("retour sans livraison : arrêt marqué non desservi", s == 200 and r["stops"][0]["status"] == "SKIPPED", f"({s} {code(r)})")
finally:
    for x in (parked.split(",") if parked else []):
        sql(f"update fleet.assignments set status='{x.split(':')[1]}' where id='{x.split(':')[0]}'")
    trips = ",".join(f"'{i}'" for i in trip_ids) or "NULL"
    restore = "".join(f"update fleet.vehicles set status='{v['s']}', tank_capacity={v['t'] if v['t'] is not None else 'NULL'} where id='{v['id']}';"
                      for v in json.loads(saved_vehicles or "[]"))
    sql(f"""
        delete from fleet.documents where doc_number like 'TEST-065-%';
        delete from fleet.trip_details where trip_id in ({trips});
        delete from fleet.trips where id in ({trips});
        delete from fleet.notifications where created_at > '{marks['notifications']}' and ({" or ".join(f"data like '%{i}%'" for i in trip_ids) or 'false'});
        delete from fleet.driver_absences where reason = 'Essai 065';
        {restore}
        update fleet.operational_parameters set odometer_reading={saved_odo or 'NULL'}, mileage={saved_odo or 'NULL'} where vehicle_id='{V1}';
    """)
    print(f"\n{sum(results)}/{len(results)} vérifications réussies")
