"""Essai : plans tarifaires, contrôle d'accès par abonnement et parcours de souscription.

Backend en mode Kernel (port 8081), comptes réels : super-admin (identité Yowyob) et
gestionnaire manager.dupont. Mot de passe : DEMO_PASSWORD du fichier .env.

1. Administration : catalogue cohérent ; plans incohérents refusés (SUB_007, SUB_008).
2. Entreprise de manager.dupont (plan Essentiel, porté par l'admin adminfleet depuis la
   migration 056) : fonctionnalités hors plan refusées au gestionnaire (SUB_006), passage à un
   plan trop petit refusé (SUB_011).
3. Souscription Pro annuelle par l'admin : commande en attente → paiement simulé → abonnement actif,
   confirmation idempotente, renouvellement qui prolonge la période.
4. Abonnement expiré au-delà de la grâce : fonctions bloquées (SUB_001), page d'abonnement
   accessible.
Les données (abonnement de manager.dupont, commandes, plan d'essai) sont restaurées à la fin.
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
OWNER = next(l.split("=", 1)[1].strip() for l in open(ENV, encoding="utf-8") if l.startswith("KERNEL_OWNER_PRINCIPAL="))
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
    out = subprocess.run(["docker", "exec", "fleet-management-db", "sh", "-c",
                          f'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "{query}"'],
                         capture_output=True, text=True, check=True)
    return out.stdout.strip()


def check(label, ok, detail=""):
    results.append(ok)
    print(f"  {'✅' if ok else '❌'} {label} {detail}")


def login(identifier):
    s, d = call("POST", "/api/v1/auth/login", {"identifier": identifier, "password": PW})
    time.sleep(1)
    return d["accessToken"] if s == 200 else None


admin = login(OWNER)
manager = login("manager.dupont")
company_admin = login("adminfleet")
manager_id = sql("select id from fleet.users where username='manager.dupont'")
company_id = sql(f"select created_by_admin_id from fleet.fleet_managers where user_id='{manager_id}'")
saved = sql(f"select plan_id||'|'||subscription_status||'|'||coalesce(subscription_start::text,'')||'|'||"
            f"coalesce(subscription_end::text,'')||'|'||coalesce(billing_cycle,'') "
            f"from fleet.companies where id='{company_id}'")
# Point de départ de l'essai : Essentiel annuel (l'entreprise est sur l'offre gratuite après 056).
sql(f"update fleet.companies set plan_id=(select id from fleet.subscription_plans where name='Essentiel' and is_active), "
    f"subscription_status='ACTIVE', subscription_start=current_date, subscription_end=current_date + 300, "
    f"billing_cycle='ANNUAL' where id='{company_id}'")
created_plan = None

try:
    print("1. Administration des plans")
    s, plans = call("GET", "/api/v1/admin/super/plans", token=admin)
    active = [p for p in plans if p["isActive"]]
    check("catalogue : 4 plans actifs dans l'ordre", [p["name"] for p in active] == ["Découverte", "Essentiel", "Pro", "Entreprise"],
          f"({[p['name'] for p in active]})")
    pro = next(p for p in active if p["name"] == "Pro")
    check("fonctionnalités du plan renvoyées", "KPI_REPORTS" in pro.get("includedFeatures", []) and pro.get("recommended"))
    base = {"name": "Essai cohérence", "description": "", "maxFleets": 120, "maxVehicles": 20, "maxDrivers": 50,
            "monthlyPrice": 75000, "currency": "XAF"}
    s, r = call("POST", "/api/v1/admin/super/plans", base, admin)
    check("120 flottes pour 20 véhicules → refusé", s == 422 and r.get("code") == "SUB_007", f"({s} {r.get('code')})")
    s, r = call("POST", "/api/v1/admin/super/plans", {**base, "maxFleets": 2, "annualPrice": 999999}, admin)
    check("annuel > 12 mensualités → refusé", s == 422 and r.get("code") == "SUB_007", f"({s})")
    s, r = call("POST", "/api/v1/admin/super/plans", {**base, "name": "pro", "maxFleets": 2}, admin)
    check("nom déjà pris (casse ignorée) → refusé", s == 409 and r.get("code") == "SUB_008", f"({s} {r.get('code')})")
    s, r = call("POST", "/api/v1/admin/super/plans", {**base, "maxFleets": 2, "annualPrice": 750000, "sortOrder": 9,
                                                       "technicalFeatures": [{"key": "TRIPS", "label": "x", "enabled": True}]}, admin)
    created_plan = r.get("id") if s in (200, 201) else None
    check("plan cohérent créé, libellé dérivé des fonctionnalités", s in (200, 201) and r.get("features") == "Trajets",
          f"({s} features={r.get('features')})")

    print("2. Gestionnaire sur Essentiel : accès contrôlé par l'API")
    s, sub = call("GET", "/api/v1/organization/subscription", token=manager)
    check("abonnement Essentiel annuel", sub.get("planName") == "Essentiel" and sub.get("billingCycle") == "ANNUAL",
          f"({sub.get('planName')} {sub.get('billingCycle')})")
    fleet_id = sql(f"select id from fleet.fleets where manager_id='{manager_id}' and deleted_at is null limit 1")
    s, r = call("GET", f"/api/v1/kpis/fleet/{fleet_id}?period=MONTHLY", token=manager)
    check("KPI (hors Essentiel) → refusé", s == 403 and r.get("code") == "SUB_006", f"({s} {r.get('code')})")
    check("message lisible", "KPI & Rapports" in str(r.get("detail")), f"({r.get('detail')})")
    s, _ = call("GET", "/api/v1/geofence/zones", token=manager)
    check("géofencing (hors Essentiel) → refusé", s == 403, f"({s})")
    for path in ("/api/v1/trips", "/api/v1/operations/incidents", "/api/v1/alerts/events"):
        s, _ = call("GET", path, token=manager)
        check(f"{path} (inclus) → autorisé", s == 200, f"({s})")
    s, plans_pub = call("GET", "/api/v1/public/subscription-plans")
    free_id = next(p["id"] for p in plans_pub if p["name"] == "Découverte")
    pro_id = next(p["id"] for p in plans_pub if p["name"] == "Pro")
    s, r = call("POST", "/api/v1/organization/subscription/checkout", {"planId": free_id, "billingCycle": "MONTHLY"}, company_admin)
    check("passer sur Découverte avec 3 flottes → refusé", s == 409 and r.get("code") == "SUB_011", f"({s} {r.get('code')})")

    print("3. Souscription Pro annuelle (paiement simulé)")
    s, order = call("POST", "/api/v1/organization/subscription/checkout", {"planId": pro_id, "billingCycle": "ANNUAL"}, company_admin)
    check("commande en attente, montant calculé côté serveur",
          s == 200 and order.get("status") == "PENDING" and float(order.get("amount", 0)) == 450000 and order.get("simulation"),
          f"({s} {order.get('status')} {order.get('amount')})")
    s, paid = call("POST", f"/api/v1/organization/subscription/orders/{order['id']}/simulate-payment", token=company_admin)
    year = (datetime.date.today().replace(year=datetime.date.today().year + 1)).isoformat()
    check("paiement simulé → commande payée, période d'un an", s == 200 and paid.get("status") == "PAID" and paid.get("periodEnd") == year,
          f"({s} {paid.get('status')} {paid.get('periodEnd')})")
    s, sub = call("GET", "/api/v1/organization/subscription", token=manager)
    check("abonnement Pro actif jusqu'à dans un an", sub.get("planName") == "Pro" and sub.get("subscriptionEnd") == year,
          f"({sub.get('planName')} {sub.get('subscriptionEnd')})")
    s, _ = call("GET", f"/api/v1/kpis/fleet/{fleet_id}?period=MONTHLY", token=manager)
    check("KPI désormais accessible", s == 200, f"({s})")
    s, again = call("POST", f"/api/v1/organization/subscription/orders/{order['id']}/simulate-payment", token=company_admin)
    check("confirmation répétée → pas de seconde prolongation", s == 200 and again.get("periodEnd") == year, f"({again.get('periodEnd')})")
    s, renew = call("POST", "/api/v1/organization/subscription/checkout", {"planId": pro_id, "billingCycle": "MONTHLY"}, company_admin)
    s, renewed = call("POST", f"/api/v1/organization/subscription/orders/{renew['id']}/simulate-payment", token=company_admin)
    expected = (datetime.date.fromisoformat(year) + datetime.timedelta(days=0))
    m = expected.month % 12 + 1
    expected = expected.replace(year=expected.year + (expected.month == 12), month=m).isoformat()
    check("renouvellement mensuel → prolongé à partir de l'échéance en cours",
          renewed.get("periodStart") == year and renewed.get("periodEnd") == expected,
          f"({renewed.get('periodStart')} → {renewed.get('periodEnd')})")
    s, orders = call("GET", "/api/v1/organization/subscription/orders", token=company_admin)
    check("historique des commandes", s == 200 and len(orders) >= 2, f"({len(orders)} commandes)")

    print("4. Abonnement expiré au-delà de la période de grâce")
    sql(f"update fleet.companies set subscription_end = current_date - 40 where id='{company_id}'")
    s, r = call("GET", "/api/v1/vehicles", token=manager)
    check("fonctions bloquées", s == 403 and r.get("code") == "SUB_001", f"({s} {r.get('code')})")
    s, _ = call("GET", "/api/v1/organization/subscription", token=company_admin)
    check("page d'abonnement accessible à l'admin (renouvellement possible)", s == 200, f"({s})")
    check("connexion toujours possible", login("manager.dupont") is not None)
finally:
    plan_id, status, start, end, cycle = (saved.split("|") + [""] * 5)[:5]
    sql(f"update fleet.companies set plan_id='{plan_id}', subscription_status='{status}', "
        f"subscription_start=nullif('{start}','')::date, subscription_end=nullif('{end}','')::date, "
        f"billing_cycle=nullif('{cycle}','') where id='{company_id}'")
    sql(f"delete from fleet.subscription_orders where company_id='{company_id}'")
    sql(f"delete from fleet.subscription_events where company_id='{company_id}'")
    if created_plan:
        sql(f"delete from fleet.subscription_plans where id='{created_plan}'")
    print(f"  ↺ données restaurées — {sum(results)}/{len(results)} contrôles OK")
