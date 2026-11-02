"""Essai : suivi des budgets (migration 061).

Backend en mode Kernel (port 8081), comptes réels : adminfleet, manager.dupont (flotte Douala),
driver.bella. Mot de passe : DEMO_PASSWORD du fichier .env.

1. Journal : création, changement de montant, suppression tracés avec leur auteur.
2. Grand livre : chaque dépense approuvée avec le solde après coup, seuils 80 % et 100 %, dépenses
   en attente et rejetées à part.
3. Vue consolidée : les niveaux s'additionnent, mêmes montants que les dépenses ; périmètres.
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
    results.append(bool(ok))
    print(f"  {'✅' if ok else '❌'} {label} {detail}")


def code(r):
    return r.get("code") if isinstance(r, dict) else None


def login(identifier):
    s, d = call("POST", "/api/v1/auth/login", {"identifier": identifier, "password": PW})
    time.sleep(1)
    return d["accessToken"] if s == 200 else None


ADMIN, MGR, DRV = login("adminfleet"), login("manager.dupont"), login("driver.bella")
uid = lambda u: sql(f"select id from fleet.users where username='{u}'")
BELLA = uid("driver.bella")
DOUALA = sql("select id from fleet.fleets where name='Flotte Douala'")
COMPANY = sql(f"select company_id from fleet.fleets where id='{DOUALA}'")
V1 = sql("select id from fleet.vehicles where license_plate='DL-203-FF'")
today = datetime.date.today()
month = today.replace(day=1).isoformat()
marks = {t: sql(f"select coalesce(max(created_at), 'epoch') from fleet.{t}") for t in ("expenses", "budgets", "budget_events")}
saved_plan = sql(f"select plan_id||'|'||subscription_status from fleet.companies where id='{COMPANY}'")
saved_fleet = sql(f"select coalesce(monthly_budget::text,'') from fleet.fleets where id='{DOUALA}'")
saved_bella = sql(f"select coalesce(assigned_vehicle_id::text,'') from fleet.drivers where user_id='{BELLA}'")
saved_budget = sql(f"select id||'|'||amount from fleet.budgets where scope='FLEET' and entity_id='{DOUALA}' and budget_month='{month}'")
sql(f"update fleet.companies set plan_id=(select id from fleet.subscription_plans where name='Pro' and is_active), "
    f"subscription_status='ACTIVE' where id='{COMPANY}'")
sql(f"update fleet.drivers set assigned_vehicle_id='{V1}' where user_id='{BELLA}'")
RECEIPT = {"fileUrl": "/api/v1/files/essai-061.pdf", "fileName": "ticket.pdf", "mimeType": "application/pdf"}
created_budgets = []


def spend(amount, approve=True, token=None, kind="TOLL"):
    s, e = call("POST", "/api/v1/budget/expenses", {"vehicleId": V1, "expenseType": kind, "amount": amount, "description": "Essai 061"}, DRV)
    assert s == 201, (s, e)
    call("PUT", f"/api/v1/budget/expenses/{e['id']}/receipt", RECEIPT, DRV)
    if approve:
        s, r = call("PATCH", f"/api/v1/budget/expenses/{e['id']}/approve", token=token or MGR)
        assert s == 200, (s, r)
    return e["id"]


def ledger(budget_id, token=MGR):
    return call("GET", f"/api/v1/budget/budgets/{budget_id}/ledger", token=token)


def fleet_row(overview):
    return next(f for f in overview["fleets"] if f["fleetId"] == DOUALA)


try:
    print("1. Journal du budget")
    s, fl = call("GET", f"/api/v1/admin/management/fleets/{DOUALA}", token=ADMIN)
    s, r = call("PUT", f"/api/v1/admin/management/fleets/{DOUALA}", {"name": fl["name"], "monthlyBudget": 2000000}, ADMIN)
    envelope = sql(f"select id from fleet.budgets where scope='FLEET' and entity_id='{DOUALA}' and budget_month='{month}'")
    check("l'enveloppe suit la flotte", s == 200 and envelope, f"({s})")
    s, vb = call("POST", "/api/v1/budget/budgets", {"scope": "VEHICLE", "entityId": V1, "budgetMonth": month, "amount": 1000000, "notes": "Essai 061"}, MGR)
    check("budget de véhicule créé", s == 201, f"({s} {code(vb)})")
    VB = vb["id"]
    created_budgets.append(VB)
    s, l = ledger(VB)
    first = l["entries"][0]
    check("création tracée avec son auteur", s == 200 and first["kind"] == "CREATED" and float(first["amount"]) == 1000000
          and first["actor"] == "Jean Dupont", f"({first['kind']} {first['actor']})")
    s, r = call("PUT", f"/api/v1/budget/budgets/{VB}", {"amount": 100000}, MGR)
    s, l = ledger(VB)
    ch = next(e for e in l["entries"] if e["kind"] == "AMOUNT_CHANGED")
    check("changement de montant : avant et après", float(ch["oldAmount"]) == 1000000 and float(ch["amount"]) == 100000 and ch["actor"], f"({ch['oldAmount']} → {ch['amount']})")

    print("2. Grand livre")
    before = float(l["consumed"])
    e1 = spend(50000)
    s, l = ledger(VB)
    x = next(e for e in l["entries"] if e["expenseId"] == e1)
    check("dépense au grand livre, solde recalculé", float(x["remainingAfter"]) == 100000 - before - 50000 and x["expenseType"] == "TOLL", f"({x['remainingAfter']})")
    check("qui, quoi, validé par qui, justificatif", x["submittedBy"] == "Claire Bella" and x["validatedBy"] == "Jean Dupont"
          and x["vehiclePlate"] == "DL-203-FF" and x["receiptUrl"] and x["validatedAt"], f"({x['submittedBy']} / {x['validatedBy']})")
    spend(35000)
    s, l = ledger(VB)
    kinds = [e["kind"] for e in l["entries"]]
    check("seuil de 80 % journalisé", "THRESHOLD_80" in kinds and l["alertLevel"] == "WARNING", f"({l['alertLevel']} {kinds})")
    spend(20000, token=ADMIN)
    s, l = ledger(VB)
    kinds = [e["kind"] for e in l["entries"]]
    check("dépassement journalisé", "THRESHOLD_100" in kinds and l["alertLevel"] == "EXCEEDED" and float(l["remaining"]) < 0, f"({l['alertLevel']} {l['remaining']})")
    check("le solde final du livre = plafond − consommé", float(l["entries"][-1]["remainingAfter"]) == float(l["remaining"]),
          f"({l['entries'][-1]['remainingAfter']} / {l['remaining']})")
    s, r = call("PUT", f"/api/v1/budget/budgets/{VB}", {"amount": 500000}, MGR)
    s, l = ledger(VB)
    check("retour sous le seuil journalisé", "RECOVERED" in [e["kind"] for e in l["entries"]], "")
    pending_id = spend(7000, approve=False)
    s, l = ledger(VB)
    consumed_with_pending = float(l["consumed"])
    check("dépense en attente à part, sans effet sur le solde", any(p["id"] == pending_id for p in l["pending"])
          and pending_id not in [e["expenseId"] for e in l["entries"]], f"({len(l['pending'])})")
    call("PATCH", f"/api/v1/budget/expenses/{pending_id}/reject", {"rejectionReason": "Sans objet"}, MGR)
    s, l = ledger(VB)
    check("dépense rejetée à part, avec son motif", any(p["id"] == pending_id and p["rejectionReason"] == "Sans objet" for p in l["rejected"]), f"({len(l['rejected'])})")

    print("3. Vue consolidée")
    s, o = call("GET", f"/api/v1/budget/budgets/tracking?month={month}", token=MGR)
    check("accessible au gestionnaire", s == 200 and o["fleets"], f"({s} {code(o)})")
    f = fleet_row(o)
    db_consumed = float(sql(f"select coalesce(sum(amount),0) from fleet.expenses where fleet_id='{DOUALA}' and status='APPROVED' "
                            f"and expense_date >= '{month}' and expense_date < date '{month}' + interval '1 month'"))
    check("consommé de la flotte = dépenses approuvées", float(f["usage"]["consumed"]) == db_consumed, f"({f['usage']['consumed']} / {db_consumed})")
    check("les véhicules s'additionnent à la flotte", abs(sum(float(v["usage"]["consumed"]) for v in f["vehicles"]) - db_consumed) < 0.01, "")
    v = next(v for v in f["vehicles"] if v["vehicleId"] == V1)
    check("ligne véhicule : budget, consommé, taux", float(v["usage"]["budget"]) == 500000 and float(v["usage"]["consumed"]) == consumed_with_pending
          and v["budgetId"] == VB, f"({v['usage']})")
    check("répartition : alloué et reste à répartir", float(f["allocatedToVehicles"]) >= 500000
          and float(f["unallocated"]) == float(f["usage"]["budget"]) - float(f["allocatedToVehicles"]), f"({f['allocatedToVehicles']} {f['unallocated']})")
    total_types = sum(float(t["amount"]) for t in o["byType"])
    check("les types s'additionnent au total", abs(total_types - float(o["totals"]["consumed"])) < 0.01, f"({total_types})")
    check("par chauffeur : Claire Bella visible", any(d["label"] == "Claire Bella" for d in o["byDriver"]), "")
    check("tendance sur 6 mois, cumul quotidien", len(o["trend"]) == 6 and o["daily"][-1]["date"] == today.isoformat()
          and float(o["daily"][-1]["cumulative"]) == float(o["totals"]["consumed"]), f"({len(o['trend'])} {o['daily'][-1]})")
    check("projection de fin de mois", o["totals"]["projected"] is not None and o["daysInMonth"] >= 28, f"({o['totals']['projected']})")
    s, one = call("GET", f"/api/v1/budget/budgets/tracking?month={month}&fleetId={DOUALA}", token=MGR)
    check("filtre par flotte", s == 200 and [x["fleetId"] for x in one["fleets"]] == [DOUALA], "")
    s, a = call("GET", f"/api/v1/budget/budgets/tracking?month={month}", token=ADMIN)
    check("l'administrateur voit toutes ses flottes", s == 200 and DOUALA in [x["fleetId"] for x in a["fleets"]], f"({s} {len(a.get('fleets', []))})")
    s, r = call("GET", f"/api/v1/budget/budgets/tracking?month={month}", token=DRV)
    check("le chauffeur n'y a pas accès", s == 403, f"({s})")
    s, r = ledger(VB, DRV)
    check("ni au grand livre", s == 403, f"({s})")
    other = sql(f"select id from fleet.fleets where company_id <> '{COMPANY}' and deleted_at is null limit 1")
    if other:
        s, x = call("GET", f"/api/v1/budget/budgets/tracking?month={month}&fleetId={other}", token=MGR)
        check("une flotte hors périmètre reste invisible", s == 200 and x["fleets"] == [], f"({s})")
    s, r = call("DELETE", f"/api/v1/budget/budgets/{VB}", token=MGR)
    kept = sql(f"select count(*) from fleet.budget_events where entity_id='{V1}' and budget_month='{month}' and event_type='DELETED'")
    check("budget supprimé : le journal reste", s == 204 and int(kept) >= 1, f"({s} {kept})")
    created_budgets.remove(VB)
finally:
    fleet_saved = f"'{saved_fleet}'" if saved_fleet else "NULL"
    sql(f"""
        delete from fleet.expenses where created_at > '{marks['expenses']}' and description = 'Essai 061';
        delete from fleet.budgets where created_at > '{marks['budgets']}' and scope = 'VEHICLE';
        delete from fleet.budget_events where created_at > '{marks['budget_events']}';
        update fleet.fleets set monthly_budget = {saved_fleet or 'NULL'} where id = '{DOUALA}';
    """)
    if saved_budget:
        bid, amount = saved_budget.split("|")
        # Consommé et niveau recalculés depuis les dépenses restantes.
        sql(f"""update fleet.budgets b set amount = {amount},
                consumed = coalesce((select sum(e.amount) from fleet.expenses e where e.status = 'APPROVED' and e.fleet_id = b.entity_id
                                     and e.expense_date >= b.budget_month and e.expense_date < b.budget_month + interval '1 month'), 0)
                where id = '{bid}'""")
        sql(f"""update fleet.budgets set alert_level = case when consumed >= amount then 'EXCEEDED'
                when consumed >= amount * 0.8 then 'WARNING' else 'NORMAL' end where id = '{bid}'""")
    else:
        sql(f"delete from fleet.budgets where scope='FLEET' and entity_id='{DOUALA}' and budget_month='{month}'")
    plan_id, plan_status = saved_plan.split("|")
    sql(f"update fleet.companies set plan_id='{plan_id}', subscription_status='{plan_status}' where id='{COMPANY}'")
    sql(f"update fleet.drivers set assigned_vehicle_id = {repr(saved_bella) if saved_bella else 'NULL'} where user_id='{BELLA}'")
    print(f"  ↺ données restaurées — {sum(results)}/{len(results)} contrôles OK")
