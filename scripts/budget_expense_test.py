"""Essai : budget et dépenses (migration 057).

Backend en mode Kernel (port 8081), comptes réels : adminfleet (administrateur de l'entreprise),
manager.dupont (gestionnaire de la flotte Douala) et driver.bella (chauffeuse de cette flotte).
Mot de passe : DEMO_PASSWORD du fichier .env.

1. Enveloppe : fixée par l'admin (le gestionnaire ne peut pas) ; budgets de véhicules plafonnés
   par l'enveloppe ; mois écoulés clos.
2. Validation : justificatif exigé ; personne ne valide sa propre dépense ; niveau admin si saisie
   par un gestionnaire, au-dessus du seuil ou en dépassement de budget ; budgets recalculés aussitôt.
3. Garde-fous : types automatiques refusés en saisie manuelle, date future, dépense approuvée figée.
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
V2 = sql("select id from fleet.vehicles where license_plate='DL-201-CC'")
month = datetime.date.today().replace(day=1).isoformat()
last_month = (datetime.date.today().replace(day=1) - datetime.timedelta(days=1)).replace(day=1).isoformat()
# Repères de nettoyage pris dans les tables elles-mêmes : l'horloge de la base (UTC) et celle de
# l'application (heure locale) diffèrent, un « now() » effaçait aussi des données antérieures.
last_expense = sql("select coalesce(max(created_at), 'epoch') from fleet.expenses")
last_budget = sql("select coalesce(max(created_at), 'epoch') from fleet.budgets")
saved_fleet = sql(f"select coalesce(monthly_budget::text,'') from fleet.fleets where id='{DOUALA}'")
saved_threshold = sql(f"select expense_approval_threshold from fleet.companies where id='{COMPANY}'")
saved_bella = sql(f"select coalesce(assigned_vehicle_id::text,'') from fleet.drivers where user_id='{BELLA}'")
# driver.bella conduit DL-203-FF le temps de l'essai (un chauffeur déclare sur le véhicule qu'il conduit).
sql(f"update fleet.drivers set assigned_vehicle_id='{V1}' where user_id='{BELLA}'")
RECEIPT = {"fileUrl": "/api/v1/files/essai-justificatif.pdf", "fileName": "ticket.pdf", "mimeType": "application/pdf"}


def declare(token, body):
    base = {"vehicleId": V1, "expenseType": "TOLL", "amount": 2000, "description": "Essai 057"}
    return call("POST", "/api/v1/budget/expenses", {**base, **body}, token)


try:
    print("1. Enveloppe et budgets de véhicules")
    s, r = call("POST", "/api/v1/budget/budgets", {"scope": "FLEET", "entityId": DOUALA, "budgetMonth": month, "amount": 300000}, MGR)
    check("le gestionnaire ne fixe pas l'enveloppe de flotte", s == 403 and code(r) == "BDG_024", f"({s} {code(r)})")
    s, fl = call("GET", f"/api/v1/admin/management/fleets/{DOUALA}", token=ADMIN)
    s, r = call("PUT", f"/api/v1/admin/management/fleets/{DOUALA}", {"name": fl["name"], "monthlyBudget": 300000}, ADMIN)
    env = sql(f"select amount from fleet.budgets where scope='FLEET' and entity_id='{DOUALA}' and budget_month='{month}'")
    check("l'admin fixe l'enveloppe : budget du mois créé", s == 200 and env == "300000.00", f"({s} {env})")
    s, vb = call("POST", "/api/v1/budget/budgets", {"scope": "VEHICLE", "entityId": V1, "budgetMonth": month, "amount": 200000}, MGR)
    check("budget de véhicule dans l'enveloppe", s == 201, f"({s} {code(vb)})")
    s, r = call("POST", "/api/v1/budget/budgets", {"scope": "VEHICLE", "entityId": V2, "budgetMonth": month, "amount": 150000}, MGR)
    check("total des véhicules au-delà de l'enveloppe → refusé", s == 409 and code(r) == "BDG_021", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/budget/budgets", {"scope": "VEHICLE", "entityId": V2, "budgetMonth": last_month, "amount": 1000}, MGR)
    check("mois écoulé → clos", s == 422 and code(r) == "BDG_023", f"({s} {code(r)})")
    s, r = call("PUT", f"/api/v1/admin/management/fleets/{DOUALA}", {"name": fl["name"], "monthlyBudget": 100000}, ADMIN)
    check("enveloppe sous la somme déjà répartie → refusée", s == 409 and code(r) == "BDG_021", f"({s} {code(r)})")

    print("2. Validation des dépenses")
    s, e1 = declare(DRV, {})
    check("le chauffeur déclare un péage (niveau gestionnaire)", s == 201 and e1.get("approvalLevel") == "MANAGER"
          and e1.get("driverId") == BELLA, f"({s} {code(e1)} {e1.get('approvalLevel')})")
    s, r = call("PATCH", f"/api/v1/budget/expenses/{e1['id']}/approve", token=MGR)
    check("approbation sans justificatif → refusée", s == 422 and code(r) == "BDG_016", f"({s} {code(r)})")
    s, r = call("PUT", f"/api/v1/budget/expenses/{e1['id']}/receipt", RECEIPT, DRV)
    check("le chauffeur joint le justificatif", s == 200 and r.get("receiptUrl"), f"({s})")
    s, r = call("PATCH", f"/api/v1/budget/expenses/{e1['id']}/approve", token=MGR)
    consumed = sql(f"select consumed from fleet.budgets where id='{vb['id']}'")
    check("le gestionnaire approuve, budget du véhicule recalculé aussitôt", s == 200 and r.get("status") == "APPROVED"
          and float(consumed) >= 2000, f"({s} consommé {consumed})")
    s, e2 = declare(MGR, {"expenseType": "FINE", "amount": 5000, "receiptUrl": RECEIPT["fileUrl"]})
    check("dépense saisie par le gestionnaire → niveau admin", s == 201 and e2.get("approvalLevel") == "ADMIN", f"({s} {e2.get('approvalLevel')})")
    s, r = call("PATCH", f"/api/v1/budget/expenses/{e2['id']}/approve", token=MGR)
    check("il ne valide pas sa propre dépense", s == 403 and code(r) == "BDG_017", f"({s} {code(r)})")
    s, r = call("PATCH", f"/api/v1/budget/expenses/{e2['id']}/approve", token=ADMIN)
    check("l'admin la valide", s == 200 and r.get("status") == "APPROVED", f"({s} {code(r)})")
    s, e3 = declare(DRV, {"expenseType": "OTHER", "amount": 150000, "description": "Remorquage", "receiptUrl": RECEIPT["fileUrl"]})
    s, r = call("PATCH", f"/api/v1/budget/expenses/{e3['id']}/approve", token=MGR)
    check("au-delà du seuil de 100 000 → réservée à l'admin", s == 403 and code(r) == "BDG_018"
          and "seuil" in str(r.get("detail")), f"({s} {r.get('detail')})")
    s, r = call("PATCH", f"/api/v1/budget/expenses/{e3['id']}/reject", {"rejectionReason": "Doublon"}, ADMIN)
    check("l'admin peut la rejeter avec un motif", s == 200 and r.get("status") == "REJECTED", f"({s})")
    s, r = call("PUT", "/api/v1/organization/expense-policy", {"approvalThreshold": 1000000}, MGR)
    check("le gestionnaire ne modifie pas le seuil", s == 403, f"({s})")
    call("PUT", "/api/v1/organization/expense-policy", {"approvalThreshold": 1000000}, ADMIN)
    s, e4 = declare(DRV, {"expenseType": "OTHER", "amount": 199000, "description": "Réparation pneu", "receiptUrl": RECEIPT["fileUrl"]})
    check("dépense qui ferait dépasser le budget du véhicule → signalée", s == 201 and e4.get("overBudget")
          and e4.get("approvalLevel") == "ADMIN", f"({s} overBudget={e4.get('overBudget')})")
    s, r = call("PATCH", f"/api/v1/budget/expenses/{e4['id']}/approve", token=MGR)
    check("escalade vers l'admin (dépassement)", s == 403 and "dépassement" in str(r.get("detail")), f"({s} {r.get('detail')})")
    s, r = call("PATCH", f"/api/v1/budget/expenses/{e4['id']}/approve", token=ADMIN)
    level = sql(f"select alert_level from fleet.budgets where id='{vb['id']}'")
    check("l'admin approuve le dépassement, alerte du budget levée", s == 200 and level == "EXCEEDED", f"({s} {level})")
    s, lst = call("GET", "/api/v1/budget/expenses/company?adminOnly=true", token=ADMIN)
    check("file de validation de l'admin", s == 200 and isinstance(lst, list), f"({s} {len(lst or [])} en attente)")

    print("3. Garde-fous")
    s, r = declare(MGR, {"expenseType": "FUEL"})
    check("carburant en saisie manuelle → refusé (compté depuis le plein)", s == 400 and code(r) == "BDG_028", f"({s} {code(r)})")
    future = (datetime.datetime.now() + datetime.timedelta(days=3)).strftime("%Y-%m-%dT%H:%M:%S")
    s, r = declare(DRV, {"expenseDate": future})
    check("date future → refusée", s == 400 and code(r) == "BDG_019", f"({s} {code(r)})")
    s, r = call("DELETE", f"/api/v1/budget/expenses/{e1['id']}", token=MGR)
    check("dépense approuvée non supprimable", s == 422 and code(r) == "BDG_027", f"({s} {code(r)})")
finally:
    sql(f"delete from fleet.expenses where created_at > '{last_expense}'")
    sql(f"delete from fleet.budgets where created_at > '{last_budget}'")
    sql(f"update fleet.fleets set monthly_budget=nullif('{saved_fleet}','')::numeric where id='{DOUALA}'")
    sql(f"update fleet.companies set expense_approval_threshold={saved_threshold} where id='{COMPANY}'")
    sql(f"update fleet.drivers set assigned_vehicle_id=nullif('{saved_bella}','')::uuid where user_id='{BELLA}'")
    sql("""update fleet.budgets b set consumed = coalesce((select sum(e.amount) from fleet.expenses e where e.status='APPROVED'
             and e.expense_date >= b.budget_month and e.expense_date < b.budget_month + interval '1 month'
             and ((b.scope='FLEET' and e.fleet_id=b.entity_id) or (b.scope='VEHICLE' and e.vehicle_id=b.entity_id))), 0);
           update fleet.budgets set alert_level = case when consumed >= amount then 'EXCEEDED'
             when consumed >= amount*0.8 then 'WARNING' else 'NORMAL' end;""")
    print(f"  ↺ données restaurées — {sum(results)}/{len(results)} contrôles OK")
