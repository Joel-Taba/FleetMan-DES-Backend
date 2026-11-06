"""Essai bout en bout : opérations terrain entre chauffeur et gestionnaire + centres de notifications
(migration 070).

Backend en mode Kernel (port 8081, APPLICATION_FILE_MODE=local), comptes réels : manager.dupont
(gestionnaire), driver.bella (chauffeuse, véhicule attitré), adminfleet (administrateur).
Mot de passe : ligne DEMO_PASSWORD= du fichier .env (lu ici, jamais affiché).

Chaque étape vérifie les deux côtés : l'action d'un rôle, puis ce que voit l'autre (liste / détail)
et la notification qu'il reçoit (GET /api/v1/notifications : `type` + identifiant dans `data`).

1. Plein déclaré par la chauffeuse → gestionnaire notifié (FUEL_DECLARED), plein visible avec
   relevé, station, ticket, position ; dépense en attente dans le circuit de validation.
2. Rejet de la dépense → chauffeuse notifiée (EXPENSE_REJECTED, motif), statut visible chez elle.
3. Second plein approuvé (ou validé par l'admin au-delà du seuil) → EXPENSE_APPROVED.
4. Plein saisi par le gestionnaire pour son véhicule → chauffeuse notifiée (FUEL_RECORDED).
5. Demande de maintenance → MAINTENANCE_REQUESTED ; planification, atelier (véhicule MAINTENANCE),
   clôture (véhicule disponible), annulation motivée : la chauffeuse est notifiée à chaque étape
   et voit statut, date prévue et garage ; elle ne peut pas faire avancer la maintenance.
6. Maintenance saisie par le gestionnaire → MAINTENANCE_RECORDED.
7. Centre de notifications : ordre, compteur de non lues, lecture d'une / de toutes, cloisonnement.
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


def notifications(token, size=50):
    s, page = call("GET", f"/api/v1/notifications?page=0&size={size}", token=token)
    return page if s == 200 and isinstance(page, dict) else {"content": []}


def notif(token, event, key, value):
    """Notification reçue : même code d'événement et identifiant attendu dans data."""
    for n in notifications(token)["content"]:
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


MGR, DRV, ADMIN = login("manager.dupont"), login("driver.bella"), login("adminfleet")
assert MGR and DRV and ADMIN, "connexion impossible (backend démarré ? DEMO_PASSWORD ?)"
BELLA = sql("select id from fleet.users where username='driver.bella'")
MANAGER = sql("select id from fleet.users where username='manager.dupont'")
V = sql(f"select assigned_vehicle_id from fleet.drivers where user_id='{BELLA}'")
assert V, "driver.bella n'a pas de véhicule attitré"
PLATE = sql(f"select license_plate from fleet.vehicles where id='{V}'")

# Repères de nettoyage pris AVANT l'essai (horloges base / application décalées d'une heure).
marks = {t: sql(f"select coalesce(max(created_at), 'epoch') from fleet.{t}") for t in ("notifications", "budget_events")}
saved_vehicle = sql(f"select status || '|' || coalesce(status_reason, '') from fleet.vehicles where id='{V}'")
saved_ops = sql(f"select coalesce(fuel_level, '') || '|' || coalesce(odometer_reading::text, '') from fleet.operational_parameters where vehicle_id='{V}'")
saved_mp = sql(f"select coalesce(last_maintenance_at::text, '') || '|' || coalesce(last_maintenance_km::text, '') || '|' || coalesce(maintenance_status, '') "
               f"from fleet.maintenance_parameters where vehicle_id='{V}'")
month = datetime.date.today().replace(day=1).isoformat()
saved_budgets = sql(f"select string_agg(id::text || ':' || consumed || ':' || alert_level || ':' || alert_80_sent || ':' || alert_100_sent, ',') "
                    f"from fleet.budgets where budget_month = '{month}'")
odo = float(saved_ops.split("|")[1] or 0) if saved_ops else 0.0
if saved_vehicle.split("|")[0] != "AVAILABLE":
    sql(f"update fleet.vehicles set status='AVAILABLE' where id='{V}'")

fuel_ids, maintenance_ids = [], []
receipt = "/api/v1/files/expense-receipt/TEST-070-ticket.jpg"

# « Opérations terrain » n'est pas dans l'offre Découverte de la démo (refus SUB_006) :
# offre Entreprise le temps de l'essai, puis offre d'origine restaurée.
COMPANY = sql("select company_id from fleet.fleets where name='Flotte Douala'")
saved_plan = sql(f"select plan_id || '|' || subscription_status || '|' || coalesce(subscription_start::text, '') || '|' || "
                 f"coalesce(subscription_end::text, '') || '|' || coalesce(billing_cycle, '') from fleet.companies where id='{COMPANY}'")
sql(f"update fleet.companies set plan_id='55000003-0000-4000-8000-000000000003', subscription_status='ACTIVE', "
    f"subscription_start=current_date, subscription_end=current_date + 30 where id='{COMPANY}'")

try:
    # ── 1. Plein déclaré par la chauffeuse ─────────────────────────────────
    print("1. Plein déclaré par la chauffeuse")
    s, f1 = call("POST", "/api/v1/operations/fuel-recharges", {
        "quantity": 25, "price": 18500, "vehicleId": V, "stationName": "Total Bastos",
        "odometerKm": odo + 120, "latitude": 4.0511, "longitude": 9.7679,
        "receiptUrl": receipt, "receiptName": "ticket.jpg", "receiptMime": "image/jpeg"}, DRV)
    ok = s == 201 and isinstance(f1, dict)
    check("la chauffeuse déclare le plein", ok, f"({s})")
    if ok:
        fuel_ids.append(f1["id"])
        check("plein attribué à la chauffeuse, dépense en attente", f1.get("driverId") == BELLA and f1.get("declaredByDriver")
              and f1.get("expenseStatus") == "PENDING", f"({f1.get('driverId')}, {f1.get('expenseStatus')})")
        s, lst = call("GET", "/api/v1/operations/fuel-recharges", token=MGR)
        row = find(lst, f1["id"])
        check("le gestionnaire voit le plein avec relevé, station, ticket, position",
              row is not None and row.get("stationLabel") == "Total Bastos" and row.get("stationName") == "TOTAL"
              and float(row.get("odometerKm") or 0) == odo + 120 and row.get("receiptUrl") == receipt
              and (row.get("location") or {}).get("latitude") is not None, f"({s})")
        check("nom de la chauffeuse affiché (pas son identifiant)",
              row is not None and row.get("driverFullName") and row.get("driverFullName") != BELLA, f"({row and row.get('driverFullName')})")
        n = notif(MGR, "FUEL_DECLARED", "fuelRechargeId", f1["id"])
        check("gestionnaire notifié FUEL_DECLARED (fuelRechargeId, expenseId)", n is not None and (n.get("data") or {}).get("expenseId") == f1.get("expenseId"))
        check("la chauffeuse n'est pas notifiée de sa propre déclaration", notif(DRV, "FUEL_DECLARED", "fuelRechargeId", f1["id"]) is None)
        s, pend = call("GET", "/api/v1/budget/expenses/pending", token=MGR)
        exp = find(pend, f1.get("expenseId"))
        check("dépense en attente dans le circuit budget, ticket joint", exp is not None and exp.get("receiptUrl") == receipt, f"({s})")
        s, mine = call("GET", "/api/v1/operations/fuel-recharges/mine", token=DRV)
        check("la chauffeuse retrouve le plein dans ses déclarations", s == 200 and find(mine, f1["id"]) is not None, f"({s})")
        cur_odo = sql(f"select odometer_reading from fleet.operational_parameters where vehicle_id='{V}'")
        check("compteur du véhicule mis à jour par le relevé", cur_odo and float(cur_odo) == odo + 120, f"({cur_odo})")

        # ── 2. Rejet de la dépense ─────────────────────────────────────────
        print("2. Rejet du plein par le gestionnaire")
        s, r = call("PATCH", f"/api/v1/budget/expenses/{f1['expenseId']}/reject", {"rejectionReason": "Ticket illisible (TEST-070)"}, MGR)
        check("le gestionnaire rejette la dépense", s == 200 and (r or {}).get("status") == "REJECTED", f"({s})")
        n = notif(DRV, "EXPENSE_REJECTED", "fuelRechargeId", f1["id"])
        check("chauffeuse notifiée EXPENSE_REJECTED avec le motif", n is not None and "Ticket illisible" in (n.get("message") or ""))
        s, mine = call("GET", "/api/v1/operations/fuel-recharges/mine", token=DRV)
        row = find(mine, f1["id"])
        check("statut « rejeté » et motif visibles chez la chauffeuse", row is not None and row.get("expenseStatus") == "REJECTED"
              and "Ticket illisible" in (row.get("expenseRejectionReason") or ""))

    # ── 3. Second plein approuvé ────────────────────────────────────────────
    print("3. Plein approuvé")
    s, f2 = call("POST", "/api/v1/operations/fuel-recharges",
                 {"quantity": 10, "price": 7400, "vehicleId": V, "stationName": "SHELL"}, DRV)
    if s == 201:
        fuel_ids.append(f2["id"])
        s, r = call("PATCH", f"/api/v1/budget/expenses/{f2['expenseId']}/approve", token=MGR)
        if s != 200:
            # Seuil de l'entreprise ou budget dépassé : la dépense monte chez l'administrateur.
            check("escalade : administrateur notifié EXPENSE_ADMIN_APPROVAL",
                  notif(ADMIN, "EXPENSE_ADMIN_APPROVAL", "expenseId", f2["expenseId"]) is not None)
            s, r = call("PATCH", f"/api/v1/budget/expenses/{f2['expenseId']}/approve", token=ADMIN)
        check("dépense approuvée", s == 200 and (r or {}).get("status") == "APPROVED", f"({s})")
        check("chauffeuse notifiée EXPENSE_APPROVED", notif(DRV, "EXPENSE_APPROVED", "expenseId", f2["expenseId"]) is not None)
        s, mine = call("GET", "/api/v1/operations/fuel-recharges/mine", token=DRV)
        check("statut « approuvé » visible chez la chauffeuse", (find(mine, f2["id"]) or {}).get("expenseStatus") == "APPROVED")
    else:
        check("second plein déclaré", False, f"({s} {f2})")

    # ── 4. Plein saisi par le gestionnaire ─────────────────────────────────
    print("4. Plein saisi par le gestionnaire")
    s, f3 = call("POST", "/api/v1/operations/fuel-recharges", {"quantity": 5, "price": 3700, "vehicleId": V, "stationName": "OILIBYA"}, MGR)
    if s == 201:
        fuel_ids.append(f3["id"])
        check("chauffeuse du véhicule notifiée FUEL_RECORDED", notif(DRV, "FUEL_RECORDED", "fuelRechargeId", f3["id"]) is not None)
        check("le gestionnaire n'est pas notifié de sa propre saisie", notif(MGR, "FUEL_RECORDED", "fuelRechargeId", f3["id"]) is None)
    else:
        check("plein saisi par le gestionnaire", False, f"({s})")

    # ── 5. Demande de maintenance et son cycle de vie ──────────────────────
    print("5. Demande de maintenance")
    s, m1 = call("POST", "/api/v1/operations/maintenances", {
        "subject": "Bruit au freinage (TEST-070)", "report": "Grincement à l'avant droit", "vehicleId": V,
        "maintenanceType": "CORRECTIVE", "urgent": True, "status": "COMPLETED"}, DRV)
    ok = s == 201 and isinstance(m1, dict)
    check("la chauffeuse demande une maintenance (statut forcé à « demandée »)", ok and m1.get("status") == "REQUESTED"
          and m1.get("requestedBy") == BELLA and m1.get("urgent"), f"({s} {m1.get('status') if ok else m1})")
    if ok:
        mid = m1["id"]
        maintenance_ids.append(mid)
        s, lst = call("GET", "/api/v1/operations/maintenances", token=MGR)
        check("le gestionnaire voit la demande", (find(lst, mid) or {}).get("status") == "REQUESTED", f"({s})")
        n = notif(MGR, "MAINTENANCE_REQUESTED", "maintenanceId", mid)
        check("gestionnaire notifié MAINTENANCE_REQUESTED (urgente : WARNING)", n is not None and n.get("type") == "WARNING")
        s, mine = call("GET", "/api/v1/operations/maintenances/mine", token=DRV)
        check("la chauffeuse suit sa demande", (find(mine, mid) or {}).get("status") == "REQUESTED", f"({s})")
        s, _ = call("POST", f"/api/v1/operations/maintenances/{mid}/transition", {"action": "PLAN", "plannedAt": "2030-01-01T09:00:00"}, DRV)
        check("la chauffeuse ne peut pas faire avancer la maintenance", s == 403, f"({s})")

        planned = (datetime.datetime.now() + datetime.timedelta(days=2)).replace(hour=9, minute=0, second=0, microsecond=0)
        s, r = call("POST", f"/api/v1/operations/maintenances/{mid}/transition",
                    {"action": "PLAN", "plannedAt": planned.strftime("%Y-%m-%dT%H:%M:%S"), "garage": "Garage Central (TEST-070)"}, MGR)
        check("le gestionnaire planifie (date + garage)", s == 200 and (r or {}).get("status") == "PLANNED", f"({s})")
        n = notif(DRV, "MAINTENANCE_PLANNED", "maintenanceId", mid)
        check("chauffeuse notifiée MAINTENANCE_PLANNED avec date et garage",
              n is not None and planned.strftime("%d/%m/%Y") in (n.get("message") or "") and "Garage Central" in (n.get("message") or ""))
        s, mine = call("GET", "/api/v1/operations/maintenances/mine", token=DRV)
        row = find(mine, mid) or {}
        check("statut, date prévue et garage visibles chez la chauffeuse", row.get("status") == "PLANNED"
              and (row.get("plannedAt") or "").startswith(planned.strftime("%Y-%m-%dT%H:%M")) and "Garage Central" in (row.get("garage") or ""))

        s, r = call("POST", f"/api/v1/operations/maintenances/{mid}/transition", {"action": "START"}, MGR)
        check("le gestionnaire démarre l'intervention", s == 200 and (r or {}).get("status") == "IN_PROGRESS", f"({s})")
        check("véhicule passé en MAINTENANCE", sql(f"select status from fleet.vehicles where id='{V}'") == "MAINTENANCE")
        check("chauffeuse notifiée MAINTENANCE_STARTED", notif(DRV, "MAINTENANCE_STARTED", "maintenanceId", mid) is not None)
        s, veh = call("GET", f"/api/v1/vehicles/{V}", token=DRV)
        if s == 200 and isinstance(veh, dict):
            check("la chauffeuse voit son véhicule en maintenance", veh.get("status") == "MAINTENANCE", f"({veh.get('status')})")

        s, r = call("POST", f"/api/v1/operations/maintenances/{mid}/transition",
                    {"action": "COMPLETE", "cost": 45000, "report": "Plaquettes changées (TEST-070)"}, MGR)
        check("le gestionnaire clôt l'intervention (coût, rapport)", s == 200 and (r or {}).get("status") == "COMPLETED", f"({s})")
        check("véhicule de nouveau disponible", sql(f"select status from fleet.vehicles where id='{V}'") == "AVAILABLE")
        check("chauffeuse notifiée MAINTENANCE_COMPLETED", notif(DRV, "MAINTENANCE_COMPLETED", "maintenanceId", mid) is not None)
        s, mine = call("GET", "/api/v1/operations/maintenances/mine", token=DRV)
        row = find(mine, mid) or {}
        check("la chauffeuse voit « terminée » et le coût", row.get("status") == "COMPLETED" and float(row.get("cost") or 0) == 45000)

    s, m2 = call("POST", "/api/v1/operations/maintenances",
                 {"subject": "Voyant moteur (TEST-070)", "report": "Voyant orange", "vehicleId": V, "maintenanceType": "PREVENTIVE"}, DRV)
    if s == 201:
        maintenance_ids.append(m2["id"])
        s, _ = call("POST", f"/api/v1/operations/maintenances/{m2['id']}/transition", {"action": "CANCEL"}, MGR)
        check("annulation sans motif refusée", s == 422, f"({s})")
        s, r = call("POST", f"/api/v1/operations/maintenances/{m2['id']}/transition", {"action": "CANCEL", "reason": "Doublon (TEST-070)"}, MGR)
        check("annulation motivée", s == 200 and (r or {}).get("status") == "CANCELLED", f"({s})")
        n = notif(DRV, "MAINTENANCE_CANCELLED", "maintenanceId", m2["id"])
        check("chauffeuse notifiée MAINTENANCE_CANCELLED avec le motif", n is not None and "Doublon" in (n.get("message") or ""))
        s, mine = call("GET", "/api/v1/operations/maintenances/mine", token=DRV)
        check("motif d'annulation visible chez la chauffeuse", "Doublon" in ((find(mine, m2["id"]) or {}).get("cancelReason") or ""))
        s, _ = call("POST", f"/api/v1/operations/maintenances/{m2['id']}/transition", {"action": "START"}, MGR)
        check("une maintenance annulée est figée", s == 409, f"({s})")

    # ── 6. Maintenance saisie par le gestionnaire ──────────────────────────
    print("6. Maintenance saisie par le gestionnaire")
    s, m3 = call("POST", "/api/v1/operations/maintenances", {"subject": "Vidange (TEST-070)", "cost": 30000, "vehicleId": V}, MGR)
    if s == 201:
        maintenance_ids.append(m3["id"])
        check("intervention enregistrée terminée", m3.get("status") == "COMPLETED")
        check("chauffeuse notifiée MAINTENANCE_RECORDED", notif(DRV, "MAINTENANCE_RECORDED", "maintenanceId", m3["id"]) is not None)
        check("pas de notification « demande » au gestionnaire", notif(MGR, "MAINTENANCE_REQUESTED", "maintenanceId", m3["id"]) is None)
    else:
        check("maintenance saisie par le gestionnaire", False, f"({s})")

    # ── 7. Centre de notifications ─────────────────────────────────────────
    print("7. Centre de notifications")
    page = notifications(DRV)
    items = page["content"]
    dates = [n.get("createdAt") or "" for n in items]
    check("liste paginée, plus récentes d'abord", dates == sorted(dates, reverse=True) and page.get("totalElements", 0) >= len(items))
    check("champs read / event / data exposés", bool(items) and all("read" in n and "data" in n for n in items)
          and any(n.get("event") == (n.get("data") or {}).get("type") for n in items))
    s, cnt = call("GET", "/api/v1/notifications/unread-count", token=DRV)
    unread = (cnt or {}).get("count")
    check("compteur de non lues cohérent avec la liste", s == 200 and unread == page.get("unreadCount"), f"({unread} / {page.get('unreadCount')})")
    target = next((n for n in items if not n.get("read")), None)
    if target:
        s, _ = call("PATCH", f"/api/v1/notifications/{target['id']}/read", token=MGR)
        check("le gestionnaire ne peut pas lire la notification de la chauffeuse", s == 404, f"({s})")
        s, r = call("PATCH", f"/api/v1/notifications/{target['id']}/read", token=DRV)
        check("lecture d'une notification", s == 200 and (r or {}).get("read") is True, f"({s})")
        s, cnt = call("GET", "/api/v1/notifications/unread-count", token=DRV)
        check("compteur décrémenté", (cnt or {}).get("count") == unread - 1, f"({(cnt or {}).get('count')})")
    s, r = call("PATCH", "/api/v1/notifications/read-all", token=DRV)
    s2, cnt = call("GET", "/api/v1/notifications/unread-count", token=DRV)
    check("« Tout marquer comme lu »", s == 200 and (cnt or {}).get("count") == 0, f"({s} {cnt})")
    s, r = call("GET", "/api/v1/notifications?page=0&size=5", token=ADMIN)
    check("l'administrateur dispose aussi de son centre de notifications", s == 200 and len(r.get("content", [])) <= 5, f"({s})")
finally:
    plan_id, plan_status, plan_start, plan_end, plan_cycle = (saved_plan.split("|") + [""] * 5)[:5]
    sql(f"update fleet.companies set plan_id='{plan_id}', subscription_status='{plan_status}', "
        f"subscription_start=nullif('{plan_start}','')::date, subscription_end=nullif('{plan_end}','')::date, "
        f"billing_cycle=nullif('{plan_cycle}','') where id='{COMPANY}'")
    print("Nettoyage")
    ids = fuel_ids + maintenance_ids
    if ids:
        quoted = ",".join(f"'{i}'" for i in ids)
        sql(f"delete from fleet.expenses where source_id in ({quoted})")
    if fuel_ids:
        sql(f"delete from fleet.fuel_recharges where id in ({','.join(repr(i) for i in fuel_ids)})")
    if maintenance_ids:
        sql(f"delete from fleet.maintenances where id in ({','.join(repr(i) for i in maintenance_ids)})")
    sql(f"delete from fleet.notifications where created_at > '{marks['notifications']}'")
    sql(f"delete from fleet.budget_events where created_at > '{marks['budget_events']}'")
    st, reason = saved_vehicle.split("|", 1)
    sql(f"update fleet.vehicles set status='{st}', status_reason={repr(reason) if reason else 'NULL'} where id='{V}'")
    if saved_ops:
        fuel, odometer = saved_ops.split("|", 1)
        sql(f"update fleet.operational_parameters set fuel_level={repr(fuel) if fuel else 'NULL'}, "
            f"odometer_reading={odometer if odometer else 'NULL'} where vehicle_id='{V}'")
    if saved_mp:
        last_at, last_km, mstatus = saved_mp.split("|", 2)
        sql(f"update fleet.maintenance_parameters set last_maintenance_at={repr(last_at) if last_at else 'NULL'}, "
            f"last_maintenance_km={last_km if last_km else 'NULL'}, maintenance_status={repr(mstatus) if mstatus else 'NULL'} where vehicle_id='{V}'")
    for row in (saved_budgets.split(",") if saved_budgets else []):
        bid, consumed, level, a80, a100 = row.split(":")
        sql(f"update fleet.budgets set consumed={consumed}, alert_level='{level}', alert_80_sent={a80}, alert_100_sent={a100} where id='{bid}'")

ok = sum(results)
print(f"\n{ok}/{len(results)} vérifications réussies")
raise SystemExit(0 if ok == len(results) else 1)
