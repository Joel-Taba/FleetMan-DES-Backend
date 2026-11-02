"""Essai : modèle entreprise (migration 056) — l'admin est l'entreprise, les gestionnaires ses employés.

Backend en mode Kernel (port 8081), comptes réels : adminfleet (administrateur de l'entreprise)
et manager.dupont (gestionnaire). Mot de passe : DEMO_PASSWORD du fichier .env. Aucune ressource
n'est créée dans le Kernel : les créations de flotte testées sont refusées avant l'appel distant.

1. Abonnement porté par l'entreprise : même plan pour l'admin et le gestionnaire, limites de
   l'entreprise entière, souscription réservée à l'admin.
2. Création de flotte : gestionnaire obligatoire et actif, nom unique dans l'entreprise, plan.
3. Transfert contrôlé : même entreprise, gestionnaire actif, aucun engagement ouvert ; véhicules suivent.
4. Suspension (motif) : flottes gelées ; réactivation ; clôture refusée tant qu'il reste des flottes.
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
    results.append(ok)
    print(f"  {'✅' if ok else '❌'} {label} {detail}")


def code(r):
    return r.get("code") if isinstance(r, dict) else None


def login(identifier):
    s, d = call("POST", "/api/v1/auth/login", {"identifier": identifier, "password": PW})
    time.sleep(1)
    return d["accessToken"] if s == 200 else None


ADMIN = login("adminfleet")
MGR = login("manager.dupont")
uid = lambda u: sql(f"select id from fleet.users where username='{u}'")
COMPANY, DUPONT, GABIN, PHONE = uid("adminfleet"), uid("manager.dupont"), uid("gabin.taba"), uid("phonereuse1")
fleet = lambda n: sql(f"select id from fleet.fleets where name='{n}' and deleted_at is null")
GAROUA, YAOUNDE, DOUALA = fleet("Flotte Garoua"), fleet("Flotte Yaoundé"), fleet("Flotte Douala")
saved_company = sql(f"select plan_id||'|'||subscription_status||'|'||coalesce(subscription_start::text,'')||'|'||"
                    f"coalesce(subscription_end::text,'')||'|'||coalesce(billing_cycle,'') from fleet.companies where id='{COMPANY}'")
saved_fleets = sql("select json_agg(json_build_object('id', id, 'm', manager_id)) from fleet.fleets")
saved_status = sql("select json_agg(json_build_object('id', fm.user_id, 's', fm.account_status, 'r', fm.suspension_reason, "
                   "'a', u.is_active)) from fleet.fleet_managers fm join fleet.users u on u.id = fm.user_id")

try:
    print("1. Abonnement de l'entreprise")
    s, sa = call("GET", "/api/v1/organization/subscription", token=ADMIN)
    s, sm = call("GET", "/api/v1/organization/subscription", token=MGR)
    check("admin et gestionnaire voient le même abonnement (celui de l'entreprise)",
          sa.get("managerId") == COMPANY and sm.get("managerId") == COMPANY and sa.get("planName") == sm.get("planName"),
          f"({sa.get('planName')} / {sm.get('planName')})")
    check("usage compté sur toute l'entreprise", sa.get("currentFleets") == 4, f"({sa.get('currentFleets')} flottes)")
    s, r = call("GET", f"/api/v1/kpis/fleet/{DOUALA}?period=MONTHLY", token=MGR)
    check("offre gratuite : KPI refusés au gestionnaire", s == 403 and code(r) == "SUB_006", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/admin/management/fleets", {"name": "Essai 056", "managerId": DUPONT}, ADMIN)
    check("limite de flottes de l'entreprise (1 sur l'offre gratuite) → refusée", s in (403, 409) and str(code(r)).startswith("SUB"),
          f"({s} {code(r)})")
    s, plans = call("GET", "/api/v1/public/subscription-plans")
    pro = next(p["id"] for p in plans if p["name"] == "Pro")
    s, r = call("POST", "/api/v1/organization/subscription/checkout", {"planId": pro, "billingCycle": "ANNUAL"}, MGR)
    check("un gestionnaire ne peut pas souscrire", s == 403, f"({s})")
    s, order = call("POST", "/api/v1/organization/subscription/checkout", {"planId": pro, "billingCycle": "ANNUAL"}, ADMIN)
    s, paid = call("POST", f"/api/v1/organization/subscription/orders/{order['id']}/simulate-payment", token=ADMIN)
    check("l'admin souscrit Pro (paiement simulé)", s == 200 and paid.get("status") == "PAID", f"({s} {paid.get('status')})")
    s, _ = call("GET", f"/api/v1/kpis/fleet/{DOUALA}?period=MONTHLY", token=MGR)
    check("le gestionnaire profite du plan de l'entreprise (KPI accessibles)", s == 200, f"({s})")

    print("2. Création de flotte par l'admin")
    s, r = call("POST", "/api/v1/admin/management/fleets", {"name": "Essai 056"}, ADMIN)
    check("sans gestionnaire → refusée", s == 400 and code(r) == "FLT_008", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/admin/management/fleets", {"name": "Essai 056", "managerId": PHONE}, ADMIN)
    check("gestionnaire suspendu → refusé", s == 409 and code(r) == "FLT_010", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/admin/management/fleets", {"name": "flotte douala", "managerId": DUPONT}, ADMIN)
    check("nom déjà pris dans l'entreprise → refusé", s == 409 and code(r) == "FLT_011", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/fleets", {"name": "Essai 056"}, MGR)
    check("un gestionnaire ne crée pas de flotte", s in (403, 404, 405), f"({s})")

    print("3. Transfert contrôlé")
    s, r = call("POST", f"/api/v1/admin/management/managers/{GABIN}/fleets", {"fleetIds": [GAROUA]}, ADMIN)
    moved = sql(f"select manager_id from fleet.fleets where id='{GAROUA}'")
    check("Flotte Garoua transférée à gabin.taba", s == 200 and moved == GABIN, f"({s} {code(r)})")
    s, mine = call("GET", "/api/v1/fleets", token=MGR)
    check("l'ancien gestionnaire ne la voit plus", all(f["id"] != GAROUA for f in (mine or [])), f"({len(mine or [])} flottes)")
    s, r = call("POST", f"/api/v1/admin/management/managers/{GABIN}/fleets", {"fleetIds": [GAROUA]}, ADMIN)
    check("vers le même gestionnaire → refusé", s == 409 and code(r) == "FLT_014", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/admin/management/managers/{PHONE}/fleets", {"fleetIds": [DOUALA]}, ADMIN)
    check("vers un gestionnaire suspendu → refusé", s == 409 and code(r) == "FLT_010", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/admin/management/managers/{GABIN}/fleets", {"fleetIds": [YAOUNDE]}, ADMIN)
    check("flotte avec un trajet en cours → refusée", s == 409 and code(r) == "FLT_012", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/admin/management/managers/{DUPONT}/fleets", {"fleetIds": [GAROUA]}, ADMIN)
    check("retour à manager.dupont", s == 200, f"({s} {code(r)})")
    vehicles_ok = sql(f"select count(*) from fleet.vehicles v join fleet.fleets f on f.id = v.fleet_id "
                      f"where v.manager_id is distinct from f.manager_id and v.deleted_at is null")
    check("véhicules alignés sur le gestionnaire de leur flotte", vehicles_ok == "0", f"({vehicles_ok} écart)")
    s, _ = call("DELETE", f"/api/v1/admin/management/managers/{DUPONT}/fleets/{GAROUA}", token=ADMIN)
    check("« désassigner » n'existe plus", s in (404, 405), f"({s})")

    print("4. Suspension, réactivation, clôture")
    s, r = call("POST", f"/api/v1/admin/management/managers/{DUPONT}/suspend", {"reason": ""}, ADMIN)
    check("suspension sans motif → refusée", s == 400, f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/admin/management/managers/{DUPONT}/suspend", {"reason": "Enquête interne"}, ADMIN)
    s, lst = call("GET", "/api/v1/admin/management/managers", token=ADMIN)
    d = next((m for m in lst if m["id"] == DUPONT), {})
    check("statut suspendu et motif visibles", d.get("accountStatus") == "SUSPENDED" and d.get("suspensionReason") == "Enquête interne",
          f"({d.get('accountStatus')} {d.get('suspensionReason')})")
    vid = sql(f"select id from fleet.vehicles where fleet_id='{DOUALA}' and status='AVAILABLE' limit 1")
    did = sql(f"select user_id from fleet.drivers where fleet_id='{DOUALA}' and status='ACTIVE' limit 1")
    s, r = call("POST", "/api/v1/trips", {"vehicleId": vid, "driverId": did, "startDate": "2026-12-01", "startTime": "08:00:00",
                                          "scheduledReturnDatetime": "2026-12-01T12:00:00", "departureKmIndex": 1000,
                                          "departureFuelIndex": 30, "destinationLocation": "Essai"}, ADMIN)
    check("flotte gelée : nouveau trajet refusé", s == 409 and code(r) == "FLT_013", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/admin/management/managers/{DUPONT}/reactivate", token=ADMIN)
    active = sql(f"select account_status||'|'||u.is_active from fleet.fleet_managers fm join fleet.users u on u.id=fm.user_id "
                 f"where fm.user_id='{DUPONT}'")
    check("réactivation", s == 200 and active == "ACTIVE|true", f"({s} {active})")
    s, r = call("DELETE", f"/api/v1/admin/management/managers/{DUPONT}", token=ADMIN)
    check("clôture refusée tant qu'il a des flottes", s == 409 and code(r) == "ADMN_022", f"({s} {code(r)})")
finally:
    plan_id, status, start, end, cycle = (saved_company.split("|") + [""] * 5)[:5]
    sql(f"update fleet.companies set plan_id='{plan_id}', subscription_status='{status}', "
        f"subscription_start=nullif('{start}','')::date, subscription_end=nullif('{end}','')::date, "
        f"billing_cycle=nullif('{cycle}','') where id='{COMPANY}'")
    sql(f"delete from fleet.subscription_orders where company_id='{COMPANY}'")
    for f in json.loads(saved_fleets or "[]"):
        m = "NULL" if f["m"] is None else f"'{f['m']}'"
        sql(f"update fleet.fleets set manager_id={m} where id='{f['id']}'")
    sql("update fleet.vehicles v set manager_id = f.manager_id from fleet.fleets f "
        "where v.fleet_id = f.id and f.manager_id is not null and v.manager_id is distinct from f.manager_id")
    for m in json.loads(saved_status or "[]"):
        r = "NULL" if m["r"] is None else "'" + m["r"].replace("'", "''") + "'"
        sql(f"update fleet.fleet_managers set account_status='{m['s']}', suspension_reason={r} where user_id='{m['id']}'; "
            f"update fleet.users set is_active={str(m['a']).lower()} where id='{m['id']}'")
    print(f"  ↺ données restaurées — {sum(results)}/{len(results)} contrôles OK")
