"""Essai bout en bout : dépenses déclarées par le chauffeur (péage, amende, autre) et leur validation
par le gestionnaire, vues des deux côtés (écran « Déclarer une dépense » du mobile et du web chauffeur).

Backend en mode Kernel (port 8081, APPLICATION_FILE_MODE=local), comptes réels : manager.dupont
(gestionnaire), driver.bella (chauffeuse, véhicule attitré). Mot de passe : ligne DEMO_PASSWORD= du
fichier .env (lu ici, jamais affiché). Justificatifs : URL de fichier local factice (aucun envoi au Kernel).

1. Péage déclaré par la chauffeuse avec justificatif → dépense PENDING à son nom, gestionnaire notifié
   (EXPENSE_SUBMITTED), dépense visible dans « en attente » ; la chauffeuse la relit par GET /{id}.
2. Règles de saisie : type automatique refusé (BDG_028), « Autre » sans description refusé (BDG_026),
   date future refusée (BDG_019), véhicule qui n'est pas le sien refusé (403).
3. Rejet motivé → chauffeuse notifiée EXPENSE_REJECTED avec le motif, statut et motif visibles chez elle.
4. Amende approuvée → chauffeuse notifiée EXPENSE_APPROVED (ou escalade administrateur si seuil / budget).
5. « Autre » sans justificatif : approbation refusée (BDG_016), justificatif joint ensuite par la chauffeuse.
Toutes les données touchées sont restaurées à la fin.
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
            if isinstance(data, dict) and "data" in data and len(data) <= 4 and "content" not in data:
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


def login(identifier):
    s, d = call("POST", "/api/v1/auth/login", {"identifier": identifier, "password": PW})
    time.sleep(1)
    return d["accessToken"] if s == 200 and isinstance(d, dict) else None


def as_list(r):
    if isinstance(r, list):
        return r
    if isinstance(r, dict):
        return r.get("content") or r.get("data") or []
    return []


def notif(token, event, key, value):
    """Notification reçue : même code d'événement et identifiant attendu dans data."""
    s, page = call("GET", "/api/v1/notifications?page=0&size=50", token=token)
    for n in (page or {}).get("content", []) if s == 200 and isinstance(page, dict) else []:
        data = n.get("data") or {}
        if not data and n.get("dataJson"):
            try:
                data = json.loads(n["dataJson"])
            except Exception:
                data = {}
        if data.get("type") == event and str(data.get(key)) == str(value):
            return n
    return None


def find(items, ident):
    return next((x for x in as_list(items) if x.get("id") == ident), None)


def code(body):
    return (body or {}).get("code") if isinstance(body, dict) else None


MGR, DRV = login("manager.dupont"), login("driver.bella")
assert MGR and DRV, "connexion impossible (backend démarré ? DEMO_PASSWORD ?)"
BELLA = sql("select id from fleet.users where username='driver.bella'")
V = sql(f"select assigned_vehicle_id from fleet.drivers where user_id='{BELLA}'")
assert V, "driver.bella n'a pas de véhicule attitré"
PLATE = sql(f"select license_plate from fleet.vehicles where id='{V}'")
FLEET = sql(f"select fleet_id from fleet.vehicles where id='{V}'")
OTHER_V = sql(f"select id from fleet.vehicles where id <> '{V}' and fleet_id = '{FLEET}' "
              f"and id not in (select vehicle_id from fleet.trips where driver_id = '{BELLA}' and status in ('DEPARTED','ARRIVED')) limit 1")
COMPANY = sql(f"select company_id from fleet.fleets where id='{FLEET}'")

# Repères de nettoyage pris AVANT l'essai (horloges base / application décalées d'une heure).
marks = {t: sql(f"select coalesce(max(created_at), 'epoch') from fleet.{t}") for t in ("notifications", "budget_events")}
month = datetime.date.today().replace(day=1).isoformat()
saved_budgets = sql(f"select string_agg(id::text || ':' || consumed || ':' || alert_level || ':' || alert_80_sent || ':' || alert_100_sent, ',') "
                    f"from fleet.budgets where budget_month = '{month}'")
saved_plan = sql(f"select plan_id || '|' || subscription_status || '|' || coalesce(subscription_start::text, '') || '|' || "
                 f"coalesce(subscription_end::text, '') || '|' || coalesce(billing_cycle, '') from fleet.companies where id='{COMPANY}'")
plan_switched = False
expense_ids = []
receipt = "/api/v1/files/expense-receipt/TEST-EXP-recu.jpg"
# Une heure avant « maintenant » : jamais dans le futur, même avec l'horloge décalée.
when = (datetime.datetime.now() - datetime.timedelta(hours=1)).replace(microsecond=0).isoformat()

try:
    # Le module budget n'est pas réservé à une offre ; si l'abonnement de la démo bloque le
    # gestionnaire (SUB_*), offre Entreprise le temps de l'essai, puis offre d'origine restaurée.
    s, r = call("GET", "/api/v1/budget/expenses/pending", token=MGR)
    if s == 403 and str(code(r) or "").startswith("SUB_"):
        sql(f"update fleet.companies set plan_id='55000003-0000-4000-8000-000000000003', subscription_status='ACTIVE', "
            f"subscription_start=current_date, subscription_end=current_date + 30 where id='{COMPANY}'")
        plan_switched = True

    # ── 1. Péage déclaré par la chauffeuse ─────────────────────────────────
    print("1. Péage déclaré par la chauffeuse")
    s, e1 = call("POST", "/api/v1/budget/expenses", {
        "expenseType": "TOLL", "amount": 2500, "vehicleId": V, "description": "Péage d'Edéa (TEST-EXP)",
        "expenseDate": when, "receiptUrl": receipt, "receiptName": "recu.jpg", "receiptMime": "image/jpeg"}, DRV)
    ok = s == 201 and isinstance(e1, dict) and e1.get("id")
    check("la chauffeuse déclare le péage", ok, f"({s} {code(e1) if not ok else ''})")
    if ok:
        expense_ids.append(e1["id"])
        check("dépense PENDING, manuelle, à son nom, véhicule et justificatif",
              e1.get("status") == "PENDING" and e1.get("sourceType") == "MANUAL" and e1.get("driverId") == BELLA
              and e1.get("createdBy") == BELLA and e1.get("vehicleId") == V and e1.get("receiptUrl") == receipt
              and e1.get("vehicleRegistration") == PLATE, f"({e1.get('status')}, {e1.get('approvalLevel')})")
        n = notif(MGR, "EXPENSE_SUBMITTED", "expenseId", e1["id"])
        check("gestionnaire notifié EXPENSE_SUBMITTED (montant, plaque)", n is not None
              and "2" in (n.get("message") or "") and PLATE in (n.get("message") or ""))
        check("la chauffeuse n'est pas notifiée de sa propre déclaration", notif(DRV, "EXPENSE_SUBMITTED", "expenseId", e1["id"]) is None)
        s, pend = call("GET", "/api/v1/budget/expenses/pending", token=MGR)
        row = find(pend, e1["id"])
        check("le gestionnaire voit la dépense en attente avec justificatif et chauffeuse",
              row is not None and row.get("receiptUrl") == receipt and row.get("driverFullName"), f"({s})")
        s, mine = call("GET", f"/api/v1/budget/expenses/{e1['id']}", token=DRV)
        check("la chauffeuse relit sa dépense (GET /{id})", s == 200 and (mine or {}).get("status") == "PENDING", f"({s})")

    # ── 2. Règles de saisie ────────────────────────────────────────────────
    print("2. Règles de saisie")
    s, r = call("POST", "/api/v1/budget/expenses", {"expenseType": "FUEL", "amount": 1000, "vehicleId": V}, DRV)
    check("type automatique (FUEL) refusé : BDG_028", s == 400 and code(r) == "BDG_028", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/budget/expenses", {"expenseType": "OTHER", "amount": 1000, "vehicleId": V}, DRV)
    check("« Autre » sans description refusé : BDG_026", s == 400 and code(r) == "BDG_026", f"({s} {code(r)})")
    future = (datetime.datetime.now() + datetime.timedelta(days=2)).replace(microsecond=0).isoformat()
    s, r = call("POST", "/api/v1/budget/expenses", {"expenseType": "TOLL", "amount": 1000, "vehicleId": V, "expenseDate": future}, DRV)
    check("date future refusée : BDG_019", s == 400 and code(r) == "BDG_019", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/budget/expenses", {"expenseType": "TOLL", "amount": 0, "vehicleId": V}, DRV)
    check("montant nul refusé", s == 400, f"({s})")
    if OTHER_V:
        s, r = call("POST", "/api/v1/budget/expenses", {"expenseType": "TOLL", "amount": 1000, "vehicleId": OTHER_V}, DRV)
        check("véhicule qui n'est pas le sien refusé", s == 403, f"({s})")
        if s in (200, 201) and isinstance(r, dict) and r.get("id"):
            expense_ids.append(r["id"])

    # ── 3. Rejet motivé ────────────────────────────────────────────────────
    if expense_ids:
        print("3. Rejet du péage par le gestionnaire")
        eid = expense_ids[0]
        s, r = call("PATCH", f"/api/v1/budget/expenses/{eid}/reject", {"rejectionReason": ""}, MGR)
        check("rejet sans motif refusé", s == 400, f"({s})")
        s, r = call("PATCH", f"/api/v1/budget/expenses/{eid}/reject", {"rejectionReason": "Reçu illisible (TEST-EXP)"}, MGR)
        check("le gestionnaire rejette avec un motif", s == 200 and (r or {}).get("status") == "REJECTED", f"({s})")
        n = notif(DRV, "EXPENSE_REJECTED", "expenseId", eid)
        check("chauffeuse notifiée EXPENSE_REJECTED avec le motif", n is not None and "Reçu illisible" in (n.get("message") or ""))
        s, mine = call("GET", f"/api/v1/budget/expenses/{eid}", token=DRV)
        check("statut « rejeté » et motif visibles chez la chauffeuse", s == 200 and (mine or {}).get("status") == "REJECTED"
              and "Reçu illisible" in ((mine or {}).get("rejectionReason") or ""))
        s, r = call("PUT", f"/api/v1/budget/expenses/{eid}/receipt", {"fileUrl": receipt}, DRV)
        check("une dépense tranchée n'est plus modifiable", s >= 400, f"({s})")

    # ── 4. Amende approuvée ────────────────────────────────────────────────
    print("4. Amende approuvée")
    s, e2 = call("POST", "/api/v1/budget/expenses", {
        "expenseType": "FINE", "amount": 5000, "vehicleId": V, "description": "Stationnement, Akwa (TEST-EXP)",
        "expenseDate": when, "receiptUrl": receipt, "receiptName": "avis.jpg", "receiptMime": "image/jpeg"}, DRV)
    if s == 201 and isinstance(e2, dict):
        expense_ids.append(e2["id"])
        s, r = call("PATCH", f"/api/v1/budget/expenses/{e2['id']}/approve", token=MGR)
        status = (r or {}).get("status") if isinstance(r, dict) else None
        if status == "APPROVED":
            check("le gestionnaire approuve l'amende", True, f"({s})")
            check("chauffeuse notifiée EXPENSE_APPROVED", notif(DRV, "EXPENSE_APPROVED", "expenseId", e2["id"]) is not None)
            s, mine = call("GET", f"/api/v1/budget/expenses/{e2['id']}", token=DRV)
            check("statut « approuvé » visible chez la chauffeuse", (mine or {}).get("status") == "APPROVED")
        else:
            # Seuil de l'entreprise ou budget du mois dépassé : la dépense monte chez l'administrateur.
            check("escalade administrateur (seuil ou budget) : dépense toujours en attente, niveau ADMIN",
                  s == 200 and status == "PENDING" and (r or {}).get("approvalLevel") == "ADMIN", f"({s} {status} {code(r)})")
    else:
        check("amende déclarée", False, f"({s} {code(e2)})")

    # ── 5. Justificatif ajouté après coup ──────────────────────────────────
    print("5. « Autre » sans justificatif, joint ensuite")
    s, e3 = call("POST", "/api/v1/budget/expenses", {
        "expenseType": "OTHER", "amount": 1500, "vehicleId": V, "description": "Lavage du véhicule (TEST-EXP)",
        "expenseDate": when}, DRV)
    if s == 201 and isinstance(e3, dict):
        expense_ids.append(e3["id"])
        s, r = call("PATCH", f"/api/v1/budget/expenses/{e3['id']}/approve", token=MGR)
        check("approbation sans justificatif refusée : BDG_016", s == 422 and code(r) == "BDG_016", f"({s} {code(r)})")
        s, r = call("PUT", f"/api/v1/budget/expenses/{e3['id']}/receipt",
                    {"fileUrl": receipt, "fileName": "ticket-lavage.jpg", "mimeType": "image/jpeg"}, DRV)
        check("la chauffeuse joint le justificatif ensuite", s == 200 and (r or {}).get("receiptUrl") == receipt, f"({s})")
        s, row = call("GET", f"/api/v1/budget/expenses/{e3['id']}", token=MGR)
        check("le gestionnaire voit le justificatif joint", s == 200 and (row or {}).get("receiptUrl") == receipt, f"({s})")
    else:
        check("dépense « Autre » déclarée", False, f"({s} {code(e3)})")
finally:
    print("Nettoyage")
    if plan_switched:
        plan_id, plan_status, plan_start, plan_end, plan_cycle = (saved_plan.split("|") + [""] * 5)[:5]
        sql(f"update fleet.companies set plan_id='{plan_id}', subscription_status='{plan_status}', "
            f"subscription_start=nullif('{plan_start}','')::date, subscription_end=nullif('{plan_end}','')::date, "
            f"billing_cycle=nullif('{plan_cycle}','') where id='{COMPANY}'")
    if expense_ids:
        sql(f"delete from fleet.expenses where id in ({','.join(repr(i) for i in expense_ids)})")
    sql(f"delete from fleet.notifications where created_at > '{marks['notifications']}'")
    sql(f"delete from fleet.budget_events where created_at > '{marks['budget_events']}'")
    for row in (saved_budgets.split(",") if saved_budgets else []):
        bid, consumed, level, a80, a100 = row.split(":")
        sql(f"update fleet.budgets set consumed={consumed}, alert_level='{level}', alert_80_sent={a80}, alert_100_sent={a100} where id='{bid}'")

ok = sum(results)
print(f"\n{ok}/{len(results)} vérifications réussies")
raise SystemExit(0 if ok == len(results) else 1)
