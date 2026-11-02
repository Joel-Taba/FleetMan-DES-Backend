"""Essai : refonte véhicules / chauffeurs (migration 055).

Backend en mode Kernel (port 8081), gestionnaire réel manager.dupont (flotte Douala),
mot de passe DEMO_PASSWORD du fichier .env. Aucune ressource n'est créée dans le Kernel :
les créations testées sont refusées avant l'appel distant.

1. Création : type obligatoire, réservoir obligatoire, plaque unique normalisée, téléphone chauffeur.
2. Caractéristiques techniques conservées (réservoir, carburant normalisé, VIN).
3. Cycle de vie : immobilisation avec motif, statut « en trajet » réservé aux trajets.
4. Conformité : assurance / visite technique / carte grise exigées pour planifier un trajet.
5. Absences datées : congé bloquant, chevauchements refusés, statut du jour, annulation.
6. Véhicule attitré : conformité, statut et lien des deux côtés.
7. Retrait du parc : engagements ouverts refusés, place du plan libérée, fiche figée.
8. Suppression réservée aux fiches sans historique.
9. Départ / réintégration d'un chauffeur.
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
    return out.stdout.strip()


def check(label, ok, detail=""):
    results.append(ok)
    print(f"  {'✅' if ok else '❌'} {label} {detail}")


def code(r):
    return (r or {}).get("code") if isinstance(r, dict) else None


def items(data):
    return data.get("content", data) if isinstance(data, dict) else data


s, d = call("POST", "/api/v1/auth/login", {"identifier": "manager.dupont", "password": PW})
M = d["accessToken"]
time.sleep(1)

V = {p: sql(f"select id from fleet.vehicles where license_plate='{p}'") for p in ("DL-203-FF", "DL-201-CC", "CE-456-AB")}
D = {u: sql(f"select id from fleet.users where username='{u}'") for u in ("driver.bella", "driver.mvogo", "driver.meke")}
FLEET = sql(f"select fleet_id from fleet.vehicles where id='{V['DL-203-FF']}'")
VAN = sql("select id from fleet.vehicle_types where code='VAN'")
today = datetime.date.today()
day = lambda n: (today + datetime.timedelta(days=n)).isoformat()

saved_vehicles = sql("select json_agg(row_to_json(v)) from (select id, status, status_reason, status_changed_at, retired_at, "
                     "retirement_type, retirement_reason, sale_price, current_driver_id, license_plate, fuel_type, "
                     "transmission_type, tank_capacity, total_seat_number, average_fuel_consumption, vehicle_serial_number "
                     f"from fleet.vehicles where id in ('{V['DL-203-FF']}','{V['DL-201-CC']}','{V['CE-456-AB']}')) v")
saved_drivers = sql("select json_agg(row_to_json(d)) from (select user_id, status, left_at, left_reason, hire_date, assigned_vehicle_id "
                    f"from fleet.drivers where user_id in ('{D['driver.bella']}','{D['driver.mvogo']}','{D['driver.meke']}')) d")
created_trips = []
# Depuis la migration 059, seules les pièces approuvées comptent : permis valides pour tous les
# chauffeurs, visite technique expirée sur DL-201-CC (contrôle de conformité), le temps de l'essai.
sql("insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, license_categories, expiry_date, file_url, "
    "review_status, is_current, created_at, updated_at) select gen_random_uuid(), 'DRIVER', d.user_id, 'DRIVING_LICENSE', 'TEST-055-LIC', "
    "'B,C,D', current_date + 400, '/api/v1/files/test-055.pdf', 'APPROVED', TRUE, now(), now() from fleet.drivers d")
sql(f"insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, expiry_date, file_url, review_status, is_current, "
    f"created_at, updated_at) values (gen_random_uuid(), 'VEHICLE', '{V['DL-201-CC']}', 'TECHNICAL_CONTROL', 'TEST-055-VT', "
    f"current_date - 30, '/api/v1/files/test-055.pdf', 'APPROVED', TRUE, now(), now())")
# Repère pris dans la table (horloges base / application différentes).
last_trip = sql("select coalesce(max(created_at), 'epoch') from fleet.trips")
# Depuis la migration 056, l'abonnement est celui de l'entreprise (offre gratuite : 3 véhicules) :
# plan Pro le temps de l'essai, restauré à la fin.
COMPANY = sql(f"select created_by_admin_id from fleet.fleet_managers where user_id=(select id from fleet.users where username='manager.dupont')")
saved_plan = sql(f"select plan_id||'|'||subscription_status from fleet.companies where id='{COMPANY}'")
sql(f"update fleet.companies set plan_id=(select id from fleet.subscription_plans where name='Pro' and is_active), "
    f"subscription_status='ACTIVE' where id='{COMPANY}'")


def trip(vehicle, driver, n):
    return call("POST", "/api/v1/trips", {
        "vehicleId": vehicle, "driverId": driver, "startDate": day(n), "startTime": "08:00:00",
        "scheduledReturnDatetime": f"{day(n)}T12:00:00", "departureKmIndex": 1000, "departureFuelIndex": 30, "destinationLocation": "Essai",
    }, M)


try:
    print("1. Création : règles vérifiées avant tout appel au Kernel")
    base = {"fleetId": FLEET, "licensePlate": "TEST-055-XX", "brand": "Toyota", "model": "Hiace",
            "fuelType": "DIESEL", "transmissionType": "MANUAL", "tankCapacity": 70}
    s, r = call("POST", "/api/v1/vehicles", base, M)
    check("sans type de véhicule → refusé", s == 400 and code(r) == "VHC_021", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/vehicles", {**base, "vehicleTypeId": VAN, "tankCapacity": None}, M)
    check("véhicule thermique sans réservoir → refusé", s == 400 and code(r) == "VHC_019", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/vehicles", {**base, "vehicleTypeId": VAN, "licensePlate": "dl 203 ff"}, M)
    check("plaque « dl 203 ff » = DL-203-FF → refusée", s == 409 and code(r) == "VHC_001", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/drivers", {"fleetId": FLEET, "firstName": "Test", "lastName": "Sans Tel",
                                           "licenceNumber": "TEST-055"}, M)
    check("chauffeur sans téléphone → refusé", s == 400 and code(r) == "DRV_017", f"({s} {code(r)})")

    print("2. Caractéristiques techniques conservées")
    s, r = call("PUT", f"/api/v1/vehicles/{V['DL-203-FF']}",
                {"tankCapacity": 70, "fuelType": "Gasoil", "vehicleSerialNumber": "VF1TEST055", "averageFuelConsumption": 9.5}, M)
    s, v = call("GET", f"/api/v1/vehicles/{V['DL-203-FF']}", token=M)
    check("réservoir, carburant normalisé, VIN et consommation relus",
          v.get("tankCapacity") == 70 and v.get("fuelType") == "DIESEL" and v.get("vehicleSerialNumber") == "VF1TEST055"
          and v.get("averageFuelConsumption") == 9.5,
          f"({v.get('tankCapacity')} {v.get('fuelType')} {v.get('vehicleSerialNumber')} {v.get('averageFuelConsumption')})")
    check("compteur et jauge exposés", (v.get("operationalParameters") or {}).get("odometerReading") is not None,
          f"({(v.get('operationalParameters') or {}).get('odometerReading')} km)")
    s, r = call("PUT", f"/api/v1/vehicles/{V['DL-203-FF']}", {"licensePlate": "ce 456 ab"}, M)
    check("plaque déjà prise (forme différente) → refusée", s == 409 and code(r) == "VHC_001", f"({s} {code(r)})")

    print("3. Cycle de vie")
    s, r = call("PUT", f"/api/v1/vehicles/{V['DL-203-FF']}", {"status": "IMMOBILIZED"}, M)
    check("immobilisation sans motif → refusée", s == 400 and code(r) == "VHC_014", f"({s} {code(r)})")
    s, r = call("PUT", f"/api/v1/vehicles/{V['DL-203-FF']}", {"status": "IMMOBILIZED", "statusReason": "Pare-brise fissuré"}, M)
    check("immobilisation avec motif", s == 200 and r.get("status") == "IMMOBILIZED"
          and (r.get("lifecycle") or {}).get("statusReason") == "Pare-brise fissuré", f"({s} {r.get('status')})")
    s, r = trip(V["DL-203-FF"], D["driver.bella"], 5)
    check("trajet sur véhicule immobilisé → refusé, message lisible",
          s >= 400 and "immobilisé" in str(r.get("detail")), f"({s} {r.get('detail')})")
    s, r = call("PUT", f"/api/v1/vehicles/{V['DL-203-FF']}", {"status": "ON_TRIP"}, M)
    check("« en trajet » posé à la main → refusé", s == 422 and code(r) == "VHC_010", f"({s} {code(r)})")
    s, r = call("PUT", f"/api/v1/vehicles/{V['DL-203-FF']}", {"status": "AVAILABLE"}, M)
    check("remise en service, motif effacé", s == 200 and r.get("status") == "AVAILABLE"
          and not (r.get("lifecycle") or {}).get("statusReason"), f"({s} {r.get('status')})")

    print("4. Conformité réglementaire")
    s, r = trip(V["DL-201-CC"], D["driver.bella"], 5)
    check("véhicule à la visite technique expirée → refusé", s == 422 and code(r) == "VHC_013"
          and "visite technique expirée" in str(r.get("detail")), f"({s} {r.get('detail')})")
    s, r = trip(V["DL-203-FF"], D["driver.bella"], 5)
    check("véhicule sans assurance → refusé", s == 422 and "assurance absente" in str(r.get("detail")), f"({s} {r.get('detail')})")
    for doc in ("INSURANCE", "TECHNICAL_CONTROL", "REGISTRATION"):
        sql(f"insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, expiry_date, file_url, review_status, "
            f"is_current, created_at, updated_at) values (gen_random_uuid(), 'VEHICLE', '{V['DL-203-FF']}', '{doc}', 'TEST-055-{doc}', "
            f"'{day(400)}', '/api/v1/files/test-055.pdf', 'APPROVED', TRUE, now(), now())")
    s, r = trip(V["DL-203-FF"], D["driver.bella"], 5)
    ok = s in (200, 201)
    if ok:
        created_trips.append(r["id"])
    check("véhicule en règle → trajet planifié", ok, f"({s} {r.get('detail') if not ok else r.get('tripCode', '')})")

    print("5. Absences datées")
    s, r = call("POST", f"/api/v1/drivers/{D['driver.bella']}/absences",
                {"type": "LEAVE", "startDate": day(5), "endDate": day(7)}, M)
    check("congé recouvrant un trajet prévu → refusé", s == 409 and code(r) == "DRV_013", f"({s} {code(r)})")
    s, leave = call("POST", f"/api/v1/drivers/{D['driver.bella']}/absences",
                    {"type": "LEAVE", "startDate": day(10), "endDate": day(12), "reason": "Congé annuel"}, M)
    check("congé déclaré", s == 201, f"({s} {code(leave)})")
    s, r = trip(V["DL-203-FF"], D["driver.bella"], 11)
    check("trajet pendant le congé → refusé", s == 422 and code(r) == "DRV_012", f"({s} {r.get('detail')})")
    s, r = call("POST", f"/api/v1/drivers/{D['driver.bella']}/absences", {"type": "SICK_LEAVE", "startDate": day(12), "endDate": day(13)}, M)
    check("absence chevauchant une autre → refusée", s == 409 and code(r) == "DRV_015", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/drivers/{D['driver.bella']}/absences", {"type": "SUSPENSION", "startDate": day(20)}, M)
    check("suspension sans motif → refusée", s == 400 and code(r) == "DRV_014", f"({s} {code(r)})")
    s, cur = call("POST", f"/api/v1/drivers/{D['driver.mvogo']}/absences",
                  {"type": "SICK_LEAVE", "startDate": day(0), "endDate": day(1)}, M)
    s, dr = call("GET", f"/api/v1/drivers/{D['driver.mvogo']}", token=M)
    check("statut du jour : en congé", dr.get("status") == "ON_LEAVE" and dr.get("contractStatus") == "ACTIVE",
          f"({dr.get('status')} / {dr.get('contractStatus')})")
    call("POST", f"/api/v1/drivers/{D['driver.mvogo']}/absences/{cur['id']}/cancel", token=M)
    s, dr = call("GET", f"/api/v1/drivers/{D['driver.mvogo']}", token=M)
    check("absence annulée → de nouveau actif", dr.get("status") == "ACTIVE", f"({dr.get('status')})")

    print("6. Véhicule attitré")
    s, r = call("POST", f"/api/v1/drivers/{D['driver.bella']}/assign-vehicle", {"vehicleId": V["DL-201-CC"]}, M)
    check("véhicule non conforme → refusé", s == 422 and code(r) == "VHC_013", f"({s} {code(r)})")
    call("PUT", f"/api/v1/vehicles/{V['DL-203-FF']}", {"status": "IMMOBILIZED", "statusReason": "Test"}, M)
    s, r = call("POST", f"/api/v1/drivers/{D['driver.bella']}/assign-vehicle", {"vehicleId": V["DL-203-FF"]}, M)
    check("véhicule immobilisé → refusé", s == 409 and code(r) == "VHC_016", f"({s} {code(r)})")
    call("PUT", f"/api/v1/vehicles/{V['DL-203-FF']}", {"status": "AVAILABLE"}, M)
    s, r = call("POST", f"/api/v1/drivers/{D['driver.bella']}/assign-vehicle", {"vehicleId": V["DL-203-FF"]}, M)
    linked = sql(f"select current_driver_id from fleet.vehicles where id='{V['DL-203-FF']}'")
    previous = sql(f"select coalesce(assigned_vehicle_id::text,'') from fleet.drivers where user_id='{D['driver.meke']}'")
    check("attribution : lien des deux côtés, ancien titulaire libéré",
          s == 200 and linked == D["driver.bella"] and previous == "", f"({s} {code(r)})")

    print("7. Retrait du parc")
    s, sub = call("GET", "/api/v1/fleet-managers/me/subscription", token=M)
    before = sub.get("currentVehicles")
    s, r = call("POST", f"/api/v1/vehicles/{V['DL-203-FF']}/retire", {"retiredAt": day(0), "retirementType": "SOLD"}, M)
    check("véhicule avec trajet prévu → refusé", s == 409 and code(r) == "VHC_009", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/vehicles/{V['DL-201-CC']}/retire", {"retiredAt": day(3), "retirementType": "SOLD"}, M)
    check("date de sortie future → refusée", s == 400 and code(r) == "VHC_018", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/vehicles/{V['DL-201-CC']}/retire",
                {"retiredAt": day(0), "retirementType": "SOLD", "reason": "Vendu à un transporteur", "salePrice": 8500000}, M)
    check("vente enregistrée", s == 200 and r.get("status") == "RETIRED"
          and (r.get("lifecycle") or {}).get("salePrice") == 8500000, f"({s} {r.get('status')})")
    s, sub = call("GET", "/api/v1/fleet-managers/me/subscription", token=M)
    check("place du plan libérée", sub.get("currentVehicles") == before - 1, f"({before} → {sub.get('currentVehicles')})")
    s, r = call("PUT", f"/api/v1/vehicles/{V['DL-201-CC']}", {"color": "Rouge"}, M)
    check("fiche retirée figée", s == 409 and code(r) == "VHC_015", f"({s} {code(r)})")
    s, r = trip(V["DL-201-CC"], D["driver.mvogo"], 6)
    check("trajet sur véhicule retiré → refusé", s >= 400 and "retiré du parc" in str(r.get("detail")), f"({s} {r.get('detail')})")

    print("8. Suppression")
    if sql(f"select exists(select 1 from fleet.trips where vehicle_id='{V['CE-456-AB']}')") == "t":
        s, r = call("DELETE", f"/api/v1/vehicles/{V['CE-456-AB']}", token=M)
        check("véhicule avec historique → suppression refusée", s == 409 and code(r) == "VHC_017", f"({s} {code(r)})")

    print("9. Départ et réintégration d'un chauffeur")
    s, r = call("POST", f"/api/v1/drivers/{D['driver.bella']}/leave", {"leftAt": day(0)}, M)
    check("chauffeur avec trajet prévu → départ refusé", s == 409 and code(r) == "DRV_006", f"({s} {code(r)})")
    before = sub.get("currentDrivers")
    s, r = call("POST", f"/api/v1/drivers/{D['driver.mvogo']}/leave", {"leftAt": day(0), "reason": "Fin de contrat"}, M)
    check("départ enregistré", s == 200 and r.get("status") == "LEFT" and r.get("leftReason") == "Fin de contrat", f"({s} {r.get('status')})")
    s, sub = call("GET", "/api/v1/fleet-managers/me/subscription", token=M)
    check("place du plan rendue", sub.get("currentDrivers") == before - 1, f"({before} → {sub.get('currentDrivers')})")
    s, r = trip(V["DL-203-FF"], D["driver.mvogo"], 8)
    check("trajet avec un chauffeur parti → refusé", s >= 400, f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/drivers/{D['driver.mvogo']}/reinstate", token=M)
    check("réintégration", s == 200 and r.get("status") == "ACTIVE", f"({s} {r.get('status')})")
finally:
    # Tout trajet créé pendant l'essai (y compris un trajet accepté à tort) est supprimé.
    sql(f"delete from fleet.trip_details where trip_id in (select id from fleet.trips where created_at > '{last_trip}'); "
        f"delete from fleet.trips where created_at > '{last_trip}'")
    sql("delete from fleet.documents where doc_number like 'TEST-055-%'")
    sql(f"delete from fleet.driver_absences where driver_id in ('{D['driver.bella']}','{D['driver.mvogo']}')")
    for v in json.loads(saved_vehicles or "[]"):
        def lit(x):
            return "NULL" if x is None else "'" + str(x).replace("'", "''") + "'"
        sql("update fleet.vehicles set status={s}, status_reason={sr}, status_changed_at={sc}, retired_at={ra}, retirement_type={rt}, "
            "retirement_reason={rr}, sale_price={sp}, current_driver_id={cd}, license_plate={lp}, fuel_type={ft}, transmission_type={tt}, "
            "tank_capacity={tc}, total_seat_number={ts}, average_fuel_consumption={af}, vehicle_serial_number={vs} where id='{id}'".format(
                s=lit(v["status"]), sr=lit(v["status_reason"]), sc=lit(v["status_changed_at"]), ra=lit(v["retired_at"]),
                rt=lit(v["retirement_type"]), rr=lit(v["retirement_reason"]), sp=lit(v["sale_price"]), cd=lit(v["current_driver_id"]),
                lp=lit(v["license_plate"]), ft=lit(v["fuel_type"]), tt=lit(v["transmission_type"]), tc=lit(v["tank_capacity"]),
                ts=lit(v["total_seat_number"]), af=lit(v["average_fuel_consumption"]), vs=lit(v["vehicle_serial_number"]), id=v["id"]))
    for d in json.loads(saved_drivers or "[]"):
        sql("update fleet.drivers set status='{s}', left_at={la}, left_reason={lr}, hire_date={hd}, assigned_vehicle_id={av} where user_id='{id}'".format(
            s=d["status"], la="NULL" if d["left_at"] is None else f"'{d['left_at']}'",
            lr="NULL" if d["left_reason"] is None else "'" + d["left_reason"].replace("'", "''") + "'",
            hd="NULL" if d["hire_date"] is None else f"'{d['hire_date']}'",
            av="NULL" if d["assigned_vehicle_id"] is None else f"'{d['assigned_vehicle_id']}'", id=d["user_id"]))
    plan_id, plan_status = saved_plan.split("|")
    sql(f"update fleet.companies set plan_id='{plan_id}', subscription_status='{plan_status}' where id='{COMPANY}'")
    print(f"  ↺ données restaurées — {sum(results)}/{len(results)} contrôles OK")
