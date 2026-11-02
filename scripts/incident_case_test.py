"""Essai : dossier d'incident (migration 062).

Backend en mode Kernel (port 8081), comptes réels : adminfleet, manager.dupont (flotte Douala),
driver.bella, driver.nana. Mot de passe : DEMO_PASSWORD du fichier .env. Offre gratuite conservée :
déclarer un incident doit y rester possible. Aucun fichier n'est envoyé au Kernel.

1. Déclaration : après coup, tiers, blessés ; date future refusée ; nom du chauffeur lisible ;
   trajet en cours rattaché.
2. Immobilisation : non roulant, vol, gravité ; décision motivée du gestionnaire.
3. Étapes : ordre imposé, responsable, historique, notifications, annulation motivée, pas de suppression.
4. Coûts : estimation hors budget, coût réel en dépense à approuver, remboursement hors budget, net.
5. Clôture : dossier complet exigé selon le type ; dossier figé ensuite ; responsabilité et score.
Les données touchées sont restaurées à la fin.
"""
import datetime
import json
import os
import subprocess
import time
import urllib.error
import urllib.parse
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


MGR, DRV, OTHER = login("manager.dupont"), login("driver.bella"), login("driver.nana")
BELLA = sql("select id from fleet.users where username='driver.bella'")
V = sql("select id from fleet.vehicles where license_plate='DL-203-FF'")
BASE = "/api/v1/operations/incidents"
saved_vehicle = sql(f"select status from fleet.vehicles where id='{V}'")
saved_bella = sql(f"select coalesce(assigned_vehicle_id::text,'') from fleet.drivers where user_id='{BELLA}'")
saved_plan = sql("select p.name from fleet.companies c join fleet.subscription_plans p on p.id=c.plan_id "
                 "join fleet.users u on u.id=c.id where u.username='adminfleet'")
marks = {t: sql(f"select coalesce(max(created_at), 'epoch') from fleet.{t}") for t in ("notifications", "maintenances") if t == "notifications"}
sql(f"update fleet.drivers set assigned_vehicle_id='{V}' where user_id='{BELLA}'")
ids = []
files = 0


def declare(token=DRV, **body):
    base = {"type": "BREAKDOWN", "severity": "LOW", "description": "Essai 062", "vehicleId": V, "reportedBy": "Essai"}
    s, r = call("POST", BASE, {**base, **body}, token)
    if s == 201:
        ids.append(r["id"])
    return s, r


def case(i, token=MGR):
    return call("GET", f"{BASE}/{i}/case", token=token)


def step(i, status, token=MGR, **extra):
    return call("POST", f"{BASE}/{i}/transition", {"status": status, **extra}, token)


def attach(i, token=MGR):
    global files
    files += 1
    return call("POST", f"{BASE}/{i}/attachments", {"kind": "PHOTO", "fileUrl": f"/api/v1/files/essai-062-{files}.jpg",
                                                    "fileName": "photo.jpg", "mimeType": "image/jpeg"}, token)


def vstatus():
    return sql(f"select status from fleet.vehicles where id='{V}'")


def close_out(i):
    step(i, "ACKNOWLEDGED")
    step(i, "UNDER_INVESTIGATION")
    return step(i, "RESOLVED", report="Réparé")


try:
    print("1. Déclaration")
    check("essai sur l'offre gratuite", saved_plan == "Découverte", f"({saved_plan})")
    past = (datetime.datetime.now() - datetime.timedelta(days=2)).strftime("%Y-%m-%dT%H:%M:%S")
    future = (datetime.datetime.now() + datetime.timedelta(days=2)).strftime("%Y-%m-%dT%H:%M:%S")
    s, r = declare(occurredAt=future)
    check("date dans le futur → refusée", s == 422 and code(r) == "OPS_INC_105", f"({s} {code(r)})")
    s, i1 = declare(type="ACCIDENT", occurredAt=past, driverId=BELLA, locationLabel="Carrefour Ndokoti", injuries=True, injuriesCount=1,
                    thirdPartyInvolved=True, thirdPartyName="M. Essono", thirdPartyPlate="LT-555-AA", estimatedCost=250000)
    check("la chauffeuse déclare, même en offre gratuite", s == 201 and i1.get("status") == "REPORTED", f"({s} {code(i1)})")
    check("date réelle conservée", i1["incidentDateTime"].startswith(past[:16]), f"({i1['incidentDateTime']})")
    check("nom du chauffeur lisible", i1.get("driverFullName") == "Claire Bella", f"({i1.get('driverFullName')})")
    s, c = case(i1["id"], DRV)
    check("dossier lisible par la chauffeuse, tiers et blessés repris", s == 200 and c["details"]["thirdPartyName"] == "M. Essono"
          and c["details"]["injuries"] and c["reportedByName"] == "Claire Bella", f"({s})")
    check("déclaration dans l'historique", [e["type"] for e in c["timeline"]] == ["DECLARED"], f"({[e['type'] for e in c['timeline']]})")
    s, r = case(i1["id"], OTHER)
    check("un autre chauffeur ne lit pas le dossier", s == 403, f"({s})")
    s, mine = call("GET", f"{BASE}/mine", token=DRV)
    check("« mes incidents » de la chauffeuse", s == 200 and i1["id"] in [x["id"] for x in mine], f"({s})")
    budget_before = sql(f"select coalesce(sum(amount),0) from fleet.expenses where source_id='{i1['id']}'")
    check("l'estimation ne crée aucune dépense", float(budget_before) == 0, f"({budget_before})")

    print("2. Immobilisation")
    check("gravité faible, véhicule roulant : disponible", vstatus() == "AVAILABLE", f"({vstatus()})")
    s, i2 = declare(type="BREAKDOWN", driverId=BELLA, vehicleDrivable=False)
    check("véhicule non roulant : immobilisé", s == 201 and vstatus() == "MAINTENANCE", f"({s} {vstatus()})")
    s, r = call("PATCH", f"{BASE}/{i2['id']}/immobilization", {"immobilized": False, "reason": " "}, MGR)
    check("décision sans motif → refusée", s in (400, 422), f"({s})")
    s, r = call("PATCH", f"{BASE}/{i2['id']}/immobilization", {"immobilized": False, "reason": "Réparé sur place"}, MGR)
    check("le gestionnaire libère, motif tracé", s == 200 and vstatus() == "AVAILABLE"
          and any(e["type"] == "RELEASED" and e["note"] == "Réparé sur place" for e in r["timeline"]), f"({s} {vstatus()})")
    s, r = call("PATCH", f"{BASE}/{i2['id']}/immobilization", {"immobilized": True, "reason": "Contrôle demandé"}, MGR)
    check("il peut aussi immobiliser", s == 200 and vstatus() == "MAINTENANCE", f"({s} {vstatus()})")
    s, r = call("PATCH", f"{BASE}/{i2['id']}/immobilization", {"immobilized": False, "reason": "Fin du contrôle"}, DRV)
    check("pas la chauffeuse", s == 403, f"({s})")
    s, r = step(i2["id"], "CANCELLED", note="Doublon")
    check("annulé : le véhicule est libéré", s == 200 and vstatus() == "AVAILABLE" and r["details"]["cancelReason"] == "Doublon", f"({s} {vstatus()})")
    s, i3 = declare(type="THEFT", severity="LOW")
    check("vol, même de faible gravité : immobilisé", s == 201 and vstatus() == "MAINTENANCE", f"({s} {vstatus()})")
    step(i3["id"], "CANCELLED", note="Fausse alerte")

    print("3. Étapes")
    I = i1["id"]
    s, r = step(I, "RESOLVED")
    check("sauter des étapes → refusé", s == 409 and code(r) == "OPS_INC_101", f"({s} {code(r)})")
    s, r = step(I, "ACKNOWLEDGED", token=DRV)
    check("la chauffeuse n'instruit pas", s == 403, f"({s})")
    s, r = step(I, "CANCELLED")
    check("annulation sans motif → refusée", s == 422 and code(r) == "OPS_INC_103", f"({s} {code(r)})")
    s, r = step(I, "ACKNOWLEDGED")
    check("prise en charge : responsable désigné", s == 200 and r["incident"]["status"] == "ACKNOWLEDGED" and r["handlerName"] == "Jean Dupont"
          and r["details"]["acknowledgedAt"], f"({s} {r.get('handlerName')})")
    notif = sql(f"select count(*) from fleet.notifications where user_id='{BELLA}' and data like '%{I}%'")
    check("la chauffeuse est informée", int(notif) >= 1, f"({notif})")
    s, r = call("DELETE", f"{BASE}/{I}", token=MGR)
    check("suppression impossible", s in (404, 405), f"({s})")
    step(I, "UNDER_INVESTIGATION")

    print("4. Coûts et assurance")
    s, r = call("POST", f"{BASE}/{I}/costs", {"nature": "REPAIR", "amount": 180000, "description": "Carrosserie"}, MGR)
    check("coût sans justificatif → refusé", s >= 400 and code(r) == "BDG_016", f"({s} {code(r)})")
    s, r = call("POST", f"{BASE}/{I}/costs", {"nature": "REPAIR", "amount": 180000, "description": "Carrosserie", "receiptUrl": "/api/v1/files/essai-062-devis.pdf",
                                              "receiptName": "devis.pdf", "receiptMime": "application/pdf"}, DRV)
    check("la chauffeuse ne saisit pas les coûts", s == 403, f"({s})")
    s, r = call("POST", f"{BASE}/{I}/costs", {"nature": "REPAIR", "amount": 180000, "description": "Carrosserie", "receiptUrl": "/api/v1/files/essai-062-devis.pdf",
                                              "receiptName": "devis.pdf", "receiptMime": "application/pdf"}, MGR)
    check("coût réel : dépense en attente, hors consommé", s == 200 and r["costs"][0]["status"] == "PENDING" and float(r["approvedCosts"]) == 0
          and float(r["pendingCosts"]) == 180000, f"({s} {code(r)})")
    cost_id = r["costs"][0]["id"]
    check("l'estimation reste à part", float(r["details"]["estimatedCost"]) == 250000, "")
    s, r = call("PATCH", f"{BASE}/{I}/case", {"insuranceStatus": "ACCEPTED", "insurerName": "AXA", "refundAmount": 100000, "refundDate": datetime.date.today().isoformat()}, MGR)
    check("assurance et remboursement saisis", s == 200 and r["details"]["insurerName"] == "AXA", f"({s} {code(r)})")
    level = r["costs"][0]["approvalLevel"]
    approver = login("adminfleet") if level == "ADMIN" else MGR
    s, x = call("PATCH", f"/api/v1/budget/expenses/{cost_id}/approve", token=approver)
    check("la dépense suit le circuit d'approbation", s == 200, f"({s} {code(x)} {level})")
    s, r = case(I)
    check("coût net = approuvé − remboursement", float(r["approvedCosts"]) == 180000 and float(r["netCost"]) == 80000, f"({r['approvedCosts']} {r['netCost']})")
    cost = sql(f"select cost from fleet.incidents where id='{I}'")
    check("le coût de l'incident suit les coûts approuvés", float(cost) == 180000, f"({cost})")
    consumed = sql(f"select amount from fleet.expenses where id='{cost_id}'")
    check("le remboursement ne réduit pas la dépense au budget", float(consumed) == 180000, f"({consumed})")

    print("5. Clôture")
    step(I, "RESOLVED", report="Véhicule réparé")
    s, r = step(I, "CLOSED")
    gaps = r.get("detail", "") if isinstance(r, dict) else ""
    check("dossier incomplet : clôture refusée, ce qui manque est dit", s == 422 and code(r) == "OPS_INC_102"
          and all(w in gaps for w in ("cause", "corrective", "responsabilité", "photo", "procès-verbal", "assurance")), f"({s} {gaps[:150]})")
    s, r = attach(I, DRV)
    check("la chauffeuse ajoute une photo", s == 200 and len(r["attachments"]) == 1, f"({s} {code(r)})")
    photo = r["attachments"][0]["fileUrl"].rsplit("/", 1)[1]
    s, x = call("GET", f"/api/v1/files/{photo}", token=OTHER)
    check("la photo est fermée aux autres chauffeurs", s == 403, f"({s})")
    # 404 : droit accordé, le fichier d'essai n'existe pas sur le disque.
    s, x = call("GET", f"/api/v1/files/{photo}", token=MGR)
    check("ouverte au gestionnaire", s == 404, f"({s})")
    s, r = call("PATCH", f"{BASE}/{I}/case", {"policeReportNumber": "PV-2026-77", "insuranceClaimNumber": "SIN-42"}, MGR)
    check("constat et assurance renseignés", s == 200 and r["incident"]["policeReportNumber"] == "PV-2026-77", f"({s})")
    s, r = step(I, "UNDER_INVESTIGATION", note="Fuite constatée")
    resolved_cleared = sql(f"select resolved_at is null from fleet.incidents where id='{I}'")
    check("réouverture possible avant clôture", s == 200 and r["incident"]["status"] == "UNDER_INVESTIGATION" and resolved_cleared in ("t", "true"), f"({s} {resolved_cleared})")
    step(I, "RESOLVED", report="Véhicule réparé")
    resolved_1 = sql(f"select resolved_at from fleet.incidents where id='{I}'")
    case(I)
    time.sleep(1)
    case(I)
    step(I, "CLOSED", rootCause="Refus de priorité du tiers")
    resolved_2 = sql(f"select resolved_at from fleet.incidents where id='{I}'")
    check("la date de résolution n'est pas réécrite par les lectures", resolved_1 == resolved_2, f"({resolved_1} / {resolved_2})")
    score_before = sql(f"select count(*) from fleet.incidents i join fleet.incident_cases c on c.incident_id=i.id where i.driver_id='{BELLA}' and c.responsibility='DRIVER'")
    s, r = step(I, "CLOSED", rootCause="Refus de priorité du tiers", correctiveAction="Rappel de conduite défensive", responsibility="THIRD_PARTY")
    check("dossier complet : clôturé", s == 200 and r["incident"]["status"] == "CLOSED" and r["closureGaps"] == [] and r["details"]["closedAt"], f"({s} {code(r)} {r.get('closureGaps')})")
    statuses = [e["toStatus"] for e in r["timeline"] if e["type"] == "STATUS_CHANGED"]
    check("historique complet des étapes", statuses == ["ACKNOWLEDGED", "UNDER_INVESTIGATION", "RESOLVED", "UNDER_INVESTIGATION", "RESOLVED", "CLOSED"], f"({statuses})")
    s, x = call("PATCH", f"{BASE}/{I}/case", {"rootCause": "Autre"}, MGR)
    check("dossier clôturé : figé", s >= 400, f"({s} {code(x)})")
    s, x = attach(I)
    check("plus de pièce ajoutée", s >= 400, f"({s})")
    resp = sql(f"select count(*) from fleet.incidents i join fleet.incident_cases c on c.incident_id=i.id where i.driver_id='{BELLA}' and c.responsibility='DRIVER'")
    check("responsabilité d'un tiers : rien sur le score du chauffeur", resp == score_before, f"({resp})")

    s, b = declare(type="BREAKDOWN", driverId=BELLA)
    close_out(b["id"])
    attach(b["id"])
    s, r = step(b["id"], "CLOSED", rootCause="Usure", correctiveAction="Remplacement", responsibility="NONE")
    check("panne sans ordre de réparation → clôture refusée", s == 422 and "réparation" in r.get("detail", ""), f"({s})")
    s, r = call("POST", f"{BASE}/{b['id']}/repair-order", {"subject": "Essai 062 réparation"}, MGR)
    check("ordre de réparation créé et rattaché", s == 200 and r["details"]["maintenanceId"], f"({s} {code(r)})")
    s, r = step(b["id"], "CLOSED", rootCause="Usure", correctiveAction="Remplacement", responsibility="DRIVER")
    resp2 = sql(f"select count(*) from fleet.incidents i join fleet.incident_cases c on c.incident_id=i.id where i.driver_id='{BELLA}' and c.responsibility='DRIVER'")
    check("responsabilité du chauffeur : comptée pour le score", s == 200 and int(resp2) == int(resp) + 1, f"({s} {resp2})")
finally:
    if ids:
        lst = ",".join(f"'{i}'" for i in ids)
        sql(f"""
            delete from fleet.alert_events where source_entity_id in ({lst})
                or source_entity_id in (select maintenance_id from fleet.incident_cases where incident_id in ({lst}));
            delete from fleet.expenses where source_id in ({lst});
            delete from fleet.expenses where description like '%Essai 062%';
            delete from fleet.maintenances where id in (select maintenance_id from fleet.incident_cases where incident_id in ({lst}));
            delete from fleet.notifications where data like any (array[{','.join(f"'%{i}%'" for i in ids)}]);
            delete from fleet.incidents where id in ({lst});
            delete from fleet.notifications where created_at > '{marks['notifications']}' and title like 'Incident%';
        """)
    sql(f"update fleet.vehicles set status='{saved_vehicle}' where id='{V}'")
    sql(f"update fleet.drivers set assigned_vehicle_id = {repr(saved_bella) if saved_bella else 'NULL'} where user_id='{BELLA}'")
    sql("""update fleet.budgets b set consumed = coalesce((select sum(e.amount) from fleet.expenses e where e.status='APPROVED'
           and e.expense_date >= b.budget_month and e.expense_date < b.budget_month + interval '1 month'
           and ((b.scope='FLEET' and e.fleet_id=b.entity_id) or (b.scope='VEHICLE' and e.vehicle_id=b.entity_id))), 0)
           where b.budget_month = date_trunc('month', current_date)::date""")
    print(f"  ↺ données restaurées — {sum(results)}/{len(results)} contrôles OK")
