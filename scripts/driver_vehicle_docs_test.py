"""Essai : documents des véhicules du chauffeur (GET /api/v1/drivers/me/vehicles).

Backend en mode Kernel (port 8081), comptes réels : manager.dupont (gestionnaire de la flotte Douala)
et driver.bella (chauffeuse de cette flotte, véhicule habituel DL-201-CC).
Mot de passe : DEMO_PASSWORD du fichier .env (jamais affiché).

1. Véhicule habituel : listé avec REGULAR, ses documents sont lisibles (200).
2. Véhicule qu'elle ne conduit pas : absent de la liste, documents refusés (403).
3. Poste du jour sur ce véhicule (affectation insérée en base) : listé avec SHIFT, documents lisibles.
4. Liste réservée aux chauffeurs : le gestionnaire reçoit 403.
Les données touchées sont supprimées à la fin.
"""
import datetime
import json
import os
import subprocess
import time
import urllib.error
import urllib.request
import uuid

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


def login(identifier, attempts=4):
    """Connexion par nom d'utilisateur ; AUTH_006 (Kernel indisponible un instant) → nouvel essai."""
    for i in range(attempts):
        s, d = call("POST", "/api/v1/auth/login", {"identifier": identifier, "password": PW})
        time.sleep(1)
        if s == 200 and isinstance(d, dict) and d.get("accessToken"):
            return d["accessToken"]
        if code(d) == "AUTH_006" and i < attempts - 1:
            print(f"  … {identifier} : AUTH_006, nouvel essai dans 5 s")
            time.sleep(5)
            continue
        print(f"  ❌ connexion {identifier} impossible (HTTP {s}, code {code(d)})")
        return None
    return None


def entry(items, vehicle_id):
    return next((v for v in (items or []) if isinstance(v, dict) and v.get("vehicleId") == vehicle_id), None)


uid = lambda u: sql(f"select id from fleet.users where username='{u}'")
BELLA = uid("driver.bella")
REGULAR = sql(f"select coalesce(assigned_vehicle_id::text,'') from fleet.drivers where user_id='{BELLA}'")
REGULAR_PLATE = sql(f"select license_plate from fleet.vehicles where id='{REGULAR}'") if REGULAR else ""
TODAY = datetime.date.today().isoformat()

# Véhicule qu'elle ne conduit pas aujourd'hui : même flotte, non supprimé, ni habituel,
# ni celui d'un trajet en cours, ni celui d'un poste ouvert du jour.
OTHER = sql(f"""
select v.id from fleet.vehicles v
where v.deleted_at is null
  and v.fleet_id = (select fleet_id from fleet.drivers where user_id='{BELLA}')
  and v.id is distinct from (select assigned_vehicle_id from fleet.drivers where user_id='{BELLA}')
  and v.id not in (select vehicle_id from fleet.trips where driver_id='{BELLA}' and deleted_at is null
                   and status in ('DEPARTED','ARRIVED') and vehicle_id is not null)
  and v.id not in (select vehicle_id from fleet.assignments where driver_id='{BELLA}'
                   and status in ('PENDING','IN_PROGRESS')
                   and (status='IN_PROGRESS' or (start_datetime < date '{TODAY}' + 1 and end_datetime > date '{TODAY}')))
order by v.license_plate limit 1""")
OTHER_PLATE = sql(f"select license_plate from fleet.vehicles where id='{OTHER}'") if OTHER else ""

shift_id = None
try:
    print("0. Préparation")
    check("driver.bella a un véhicule habituel (DL-201-CC attendu)", bool(REGULAR), REGULAR_PLATE)
    check("un autre véhicule de sa flotte, hors de ses véhicules du jour", bool(OTHER), OTHER_PLATE)
    MGR, DRV = login("manager.dupont"), login("driver.bella")
    check("connexions manager.dupont et driver.bella", bool(MGR and DRV))

    print("1. Véhicule habituel")
    s, items = call("GET", "/api/v1/drivers/me/vehicles", token=DRV)
    check("GET /drivers/me/vehicles → 200 et tableau", s == 200 and isinstance(items, list), f"HTTP {s}")
    reg = entry(items, REGULAR)
    check("véhicule habituel listé avec REGULAR", bool(reg) and "REGULAR" in reg.get("reasons", []),
          json.dumps(reg, ensure_ascii=False) if reg else "")
    check("REGULAR en premier", isinstance(items, list) and bool(items) and items[0].get("vehicleId") == REGULAR)
    check("plaque et libellé renseignés", bool(reg) and reg.get("licensePlate") == REGULAR_PLATE and "label" in reg)
    ids = [v.get("vehicleId") for v in (items or []) if isinstance(v, dict)]
    check("une seule entrée par véhicule", len(ids) == len(set(ids)))
    s, _ = call("GET", f"/api/v1/vehicles/{REGULAR}/documents", token=DRV)
    check("documents du véhicule habituel lisibles", s == 200, f"HTTP {s}")

    print("2. Véhicule non conduit")
    check("absent de la liste", entry(items, OTHER) is None)
    s, r = call("GET", f"/api/v1/vehicles/{OTHER}/documents", token=DRV)
    check("documents refusés → 403", s == 403, f"HTTP {s} {code(r)}")

    print("3. Poste du jour")
    shift_id = str(uuid.uuid4())
    # Colonnes reprises d'une affectation existante de la chauffeuse (flotte, tenant).
    sql(f"""
    insert into fleet.assignments (id, fleet_id, vehicle_id, driver_id, start_datetime, end_datetime, status, notes, tenant_id)
    select '{shift_id}', d.fleet_id, '{OTHER}', d.user_id,
           date '{TODAY}' + time '08:00', date '{TODAY}' + time '17:00', 'PENDING', 'Essai documents véhicule',
           (select a.tenant_id from fleet.assignments a where a.driver_id = d.user_id order by a.created_at desc limit 1)
    from fleet.drivers d where d.user_id = '{BELLA}'""")
    check("poste inséré", sql(f"select count(*) from fleet.assignments where id='{shift_id}'") == "1")
    s, items = call("GET", "/api/v1/drivers/me/vehicles", token=DRV)
    sh = entry(items, OTHER)
    check("véhicule du poste listé avec SHIFT", s == 200 and bool(sh) and sh.get("reasons") == ["SHIFT"],
          json.dumps(sh, ensure_ascii=False) if sh else f"HTTP {s}")
    check("véhicule habituel toujours en premier", isinstance(items, list) and bool(items) and items[0].get("vehicleId") == REGULAR)
    s, _ = call("GET", f"/api/v1/vehicles/{OTHER}/documents", token=DRV)
    check("documents du véhicule du poste lisibles", s == 200, f"HTTP {s}")

    sql(f"update fleet.assignments set status='CANCELLED' where id='{shift_id}'")
    s, r = call("GET", f"/api/v1/vehicles/{OTHER}/documents", token=DRV)
    check("poste annulé → documents de nouveau refusés", s == 403, f"HTTP {s} {code(r)}")

    print("4. Réservé aux chauffeurs")
    s, r = call("GET", "/api/v1/drivers/me/vehicles", token=MGR)
    check("gestionnaire → 403", s == 403, f"HTTP {s} {code(r)}")
finally:
    if shift_id:
        sql(f"delete from fleet.assignments where id='{shift_id}'")
    print(f"\n{sum(results)}/{len(results)} vérifications réussies")
