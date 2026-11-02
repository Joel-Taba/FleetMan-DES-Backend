"""Essai R6 : demandes concurrentes sur le même véhicule / créneau (backend fake-auth, port 8081).

Envoie N demandes simultanées (trajets puis affectations) sur le même véhicule et le même
créneau, avec des chauffeurs différents : une seule doit réussir, les autres être refusées
(409) avec un code explicite. Les trajets, affectations et pièces de test créés sont supprimés
à la fin. Lancer le backend avec SPRING_PROFILES_ACTIVE=local,demo,fake-auth.
"""
import json, sys, threading, urllib.request, urllib.error, datetime, collections, subprocess

B = "http://localhost:8081"
H = {"Authorization": "Bearer fake-token-manager1@fleetman.cm", "Content-Type": "application/json"}
N = 8


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


def items(x):
    return x.get("content", x) if isinstance(x, dict) else x


def burst(make_body, path):
    barrier = threading.Barrier(len(bodies := [make_body(i) for i in range(N)]))
    results = [None] * N

    def worker(i):
        barrier.wait()
        results[i] = call("POST", path, bodies[i])

    threads = [threading.Thread(target=worker, args=(i,)) for i in range(N)]
    for t in threads: t.start()
    for t in threads: t.join()
    return results


_, vehicles = call("GET", "/api/v1/vehicles")
_, drivers = call("GET", "/api/v1/drivers")
vehicles, drivers = items(vehicles), items(drivers)
_, fleets = call("GET", "/api/v1/fleets")
fleet_ids = {f["id"] for f in items(fleets)}
vehicle = next(v for v in vehicles if v.get("status") == "AVAILABLE" and v.get("fleetId") in fleet_ids
               and any(d.get("fleetId") == v["fleetId"] for d in drivers))
driver_ids = [d.get("userId") or d.get("id") for d in drivers if d.get("fleetId") == vehicle["fleetId"]]
if len(driver_ids) < 2:
    sys.exit("pas assez de chauffeurs")

def sql(query):
    subprocess.run(["docker", "exec", "-i", "fleet-management-db", "sh", "-c", 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -q'],
                   input=query, text=True, check=True)


# Depuis la migration 055, le véhicule doit être en règle pour être engagé : pièces temporaires.
for doc in ("INSURANCE", "TECHNICAL_CONTROL", "REGISTRATION"):
    sql(f"insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, expiry_date, file_url, review_status, is_current, "
        f"created_at, updated_at) values (gen_random_uuid(), 'VEHICLE', '{vehicle['id']}', '{doc}', 'TEST-R6-{doc}', current_date + 400, "
        f"'/api/v1/files/TEST-R6.pdf', 'APPROVED', TRUE, now(), now())")
sql("insert into fleet.documents (id, target_type, target_id, doc_type, doc_number, license_categories, expiry_date, file_url, "
    "review_status, is_current, created_at, updated_at) select gen_random_uuid(), 'DRIVER', d.user_id, 'DRIVING_LICENSE', 'TEST-R6-LIC', 'B,C,D', "
    "current_date + 400, '/api/v1/files/TEST-R6.pdf', 'APPROVED', TRUE, now(), now() from fleet.drivers d")

day = (datetime.date.today() + datetime.timedelta(days=5)).isoformat()
created_trips, created_assignments = [], []

# ── Trajets concurrents : même véhicule, même créneau, chauffeurs différents ──
trip_results = burst(lambda i: {
    "vehicleId": vehicle["id"], "driverId": driver_ids[i % len(driver_ids)],
    "startDate": day, "startTime": "08:00:00", "scheduledReturnDatetime": f"{day}T12:00:00",
    "departureKmIndex": 1000, "departureFuelIndex": 30, "destinationLocation": "Essai",
}, "/api/v1/trips")
ok = [r for s, r in trip_results if s in (200, 201)]
created_trips = [r["id"] for r in ok]
codes = collections.Counter((s, (r or {}).get("code")) for s, r in trip_results if s not in (200, 201))
print(f"TRAJETS      : {N} demandes simultanées → {len(ok)} acceptée(s), refus : {dict(codes)}")

# ── Affectations concurrentes : même véhicule, même créneau (un autre jour) ──
day2 = (datetime.date.today() + datetime.timedelta(days=6)).isoformat()
asg_results = burst(lambda i: {
    "fleetId": vehicle["fleetId"], "vehicleId": vehicle["id"], "driverId": driver_ids[i % len(driver_ids)],
    "startDatetime": f"{day2}T08:00:00", "endDatetime": f"{day2}T18:00:00",
}, "/api/v1/assignments")
ok2 = [r for s, r in asg_results if s in (200, 201)]
created_assignments = [r["id"] for r in ok2]
codes2 = collections.Counter((s, (r or {}).get("code") or str((r or {}).get("detail"))[:60]) for s, r in asg_results if s not in (200, 201))
print(f"AFFECTATIONS : {N} demandes simultanées → {len(ok2)} acceptée(s), refus : {dict(codes2)}")

for tid in created_trips:
    sql(f"delete from fleet.trip_details where trip_id='{tid}'; delete from fleet.trips where id='{tid}';")
for aid in created_assignments:
    sql(f"delete from fleet.assignments where id='{aid}';")
sql("delete from fleet.documents where doc_number like 'TEST-R6-%';")
print(f"↺ {vehicle['licensePlate']} : {len(created_trips)} trajet(s) et {len(created_assignments)} affectation(s) d'essai supprimés")
