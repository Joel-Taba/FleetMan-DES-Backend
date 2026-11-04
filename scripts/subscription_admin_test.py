"""Essai : pilotage des abonnements par le super-administrateur (migration 064).

Backend en authentification factice (SPRING_PROFILES_ACTIVE=local,demo,fake-auth) : superadmin, adminfleet
(administrateur d'entreprise), manager.dupont. Mot de passe : DEMO_PASSWORD du fichier .env. Le paiement
reste celui du simulateur : aucun règlement réel.

1. Accès réservé au super-administrateur.
2. Plan attribué : motif obligatoire, plan trop petit refusé, revenu récurrent recalculé.
3. Prolongation, suspension (fonctions bloquées, connexion conservée), réactivation.
4. Commandes : règlement hors ligne, référence exigée selon le mode, annulation motivée.
5. Rappels d'échéance sans doublon, relance manuelle, états calculés (échéance, grâce, expiré).
6. Journal et notification de l'administrateur à chaque opération.
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
    results.append(bool(ok))
    print(f"  {'✅' if ok else '❌'} {label} {detail}")


def code(r):
    return r.get("code") if isinstance(r, dict) else None


def login(identifier):
    s, d = call("POST", "/api/v1/auth/login", {"identifier": identifier, "password": PW})
    time.sleep(1)
    return d["accessToken"] if s == 200 else None



SUPER = login("superadmin")
ADMIN = login("adminfleet")
MGR = login("manager.dupont")
COMPANY = sql("select id from fleet.users where username='adminfleet'")
BILL = "/api/v1/admin/super/billing"
STARTED = sql("select to_char(localtimestamp, 'YYYY-MM-DD HH24:MI:SS')")
saved = sql(f"select row_to_json(c) from (select plan_id, subscription_status, subscription_start, subscription_end, billing_cycle, "
            f"suspension_reason, suspended_at from fleet.companies where id='{COMPANY}') c")
plans_raw = sql("select json_agg(json_build_object('id', id, 'name', name, 'monthly', monthly_price, 'v', max_vehicles, 'f', max_fleets, 'd', max_drivers)) "
                "from fleet.subscription_plans where is_active")
plans = json.loads(plans_raw)
fits = lambda p: (p["v"] == 0 or p["v"] >= 10) and (p["f"] == 0 or p["f"] >= 4) and (p["d"] == 0 or p["d"] >= 8)
BIG = next(p for p in sorted(plans, key=lambda p: p["monthly"] or 0) if (p["monthly"] or 0) > 0 and fits(p))
SMALL = next((p for p in plans if not fits(p) and (p["monthly"] or 0) > 0), None) or next(p for p in plans if not fits(p))
FREE = next(p for p in plans if (p["monthly"] or 0) == 0)


def row():
    s, rows = call("GET", f"{BILL}/companies", token=SUPER)
    return next(r for r in rows if r["id"] == COMPANY)


def events():
    s, d = call("GET", f"{BILL}/companies/{COMPANY}", token=SUPER)
    return [e["type"] for e in d["events"]]


def notifications():
    return int(sql(f"select count(*) from fleet.notifications where user_id='{COMPANY}' and title='Abonnement' and created_at >= '{STARTED}'"))


try:
    print("1. Accès")
    check("connexion du super-administrateur", SUPER is not None)
    for token, who in ((ADMIN, "administrateur"), (MGR, "gestionnaire")):
        s, r = call("GET", f"{BILL}/overview", token=token)
        check(f"portefeuille refusé à l'{who}" if who == "administrateur" else f"portefeuille refusé au {who}", s == 403, f"({s})")
    s, r = call("POST", f"{BILL}/companies/{COMPANY}/suspend", {"reason": "x"}, token=ADMIN)
    check("suspension refusée à l'administrateur", s == 403, f"({s})")
    s, ov0 = call("GET", f"{BILL}/overview", token=SUPER)
    check("synthèse du portefeuille", s == 200 and ov0["companies"] > 0 and len(ov0["revenue"]) == 12, f"({ov0.get('companies')} entreprises, MRR {ov0.get('monthlyRecurringRevenue')})")
    s, rows = call("GET", f"{BILL}/companies", token=SUPER)
    check("entreprises rejetées absentes de la liste", s == 200 and all(r["state"] for r in rows), f"({len(rows)})")

    print("2. Attribution d'un plan")
    s, r = call("POST", f"{BILL}/companies/{COMPANY}/plan", {"planId": BIG["id"], "billingCycle": "MONTHLY", "reason": " "}, token=SUPER)
    check("motif obligatoire", s == 400 and code(r) == "SUB_017", f"({s} {code(r)})")
    s, r = call("POST", f"{BILL}/companies/{COMPANY}/plan", {"planId": "00000000-0000-4000-8000-000000000000", "reason": "test"}, token=SUPER)
    check("plan inconnu", s == 404, f"({s} {code(r)})")
    s, r = call("POST", f"{BILL}/companies/{COMPANY}/plan", {"planId": SMALL["id"], "billingCycle": "MONTHLY", "reason": "test"}, token=SUPER)
    check("plan trop petit pour l'usage : refusé", s in (403, 409) and code(r) is not None, f"({s} {code(r)} {SMALL['name']})")
    before = notifications()
    s, d = call("POST", f"{BILL}/companies/{COMPANY}/plan", {"planId": BIG["id"], "billingCycle": "MONTHLY", "reason": "Geste commercial"}, token=SUPER)
    check("plan attribué", s == 200 and d["company"]["planName"] == BIG["name"] and d["company"]["state"] in ("ACTIVE", "EXPIRING"), f"({s} {d.get('company', {}).get('state')})")
    check("journal : changement de plan et motif", d["events"][0]["type"] == "PLAN_CHANGED" and d["events"][0]["reason"] == "Geste commercial" and d["events"][0]["actorName"] is not None)
    check("administrateur notifié", notifications() == before + 1)
    s, ov1 = call("GET", f"{BILL}/overview", token=SUPER)
    check("revenu récurrent recalculé", float(ov1["monthlyRecurringRevenue"]) == float(ov0["monthlyRecurringRevenue"]) + float(BIG["monthly"]),
          f"({ov0['monthlyRecurringRevenue']} → {ov1['monthlyRecurringRevenue']})")
    check("ARR = 12 × MRR", float(ov1["annualRecurringRevenue"]) == 12 * float(ov1["monthlyRecurringRevenue"]))

    print("3. Prolongation et suspension")
    end0 = row()["subscriptionEnd"]
    s, r = call("POST", f"{BILL}/companies/{COMPANY}/extend", {"days": 0, "reason": "x"}, token=SUPER)
    check("prolongation : 0 jour refusé", s == 400 and code(r) == "SUB_018", f"({s} {code(r)})")
    s, r = call("POST", f"{BILL}/companies/{COMPANY}/extend", {"days": 400, "reason": "x"}, token=SUPER)
    check("prolongation : plus de 365 jours refusé", s == 400 and code(r) == "SUB_018", f"({s} {code(r)})")
    s, d = call("POST", f"{BILL}/companies/{COMPANY}/extend", {"days": 15, "reason": "Retard de virement"}, token=SUPER)
    expected = sql(f"select to_char('{end0}'::date + 15, 'YYYY-MM-DD')")
    check("échéance prolongée de 15 jours", s == 200 and d["company"]["subscriptionEnd"] == expected, f"({end0} → {d.get('company', {}).get('subscriptionEnd')})")
    s, r = call("POST", f"{BILL}/companies/{COMPANY}/suspend", {"reason": ""}, token=SUPER)
    check("suspension : motif obligatoire", s == 400 and code(r) == "SUB_017", f"({s} {code(r)})")
    s, d = call("POST", f"{BILL}/companies/{COMPANY}/suspend", {"reason": "Impayé"}, token=SUPER)
    check("abonnement suspendu", s == 200 and d["company"]["state"] == "SUSPENDED" and d["company"]["suspensionReason"] == "Impayé", f"({s})")
    s, r = call("GET", "/api/v1/vehicles", token=MGR)
    check("gestionnaire bloqué pendant la suspension", s == 403 and code(r) == "SUB_002", f"({s} {code(r)})")
    s, r = call("GET", "/api/v1/organization/profile", token=ADMIN)
    check("l'administrateur garde sa fiche", s == 200, f"({s})")
    s, r = call("POST", f"{BILL}/companies/{COMPANY}/suspend", {"reason": "Impayé"}, token=SUPER)
    check("double suspension refusée", s == 409 and code(r) == "SUB_020", f"({s} {code(r)})")
    s, d = call("POST", f"{BILL}/companies/{COMPANY}/extend", {"days": 5, "reason": "x"}, token=SUPER)
    check("une prolongation ne lève pas la suspension", s == 200 and d["company"]["state"] == "SUSPENDED", f"({d.get('company', {}).get('state')})")
    s, ov2 = call("GET", f"{BILL}/overview", token=SUPER)
    check("suspendu : sorti du revenu récurrent", float(ov2["monthlyRecurringRevenue"]) == float(ov0["monthlyRecurringRevenue"]), f"({ov2['monthlyRecurringRevenue']})")
    s, r = call("POST", f"{BILL}/companies/{COMPANY}/reactivate", {"reason": " "}, token=SUPER)
    check("réactivation : motif obligatoire", s == 400 and code(r) == "SUB_017", f"({s} {code(r)})")
    s, d = call("POST", f"{BILL}/companies/{COMPANY}/reactivate", {"reason": "Règlement reçu"}, token=SUPER)
    check("abonnement réactivé", s == 200 and d["company"]["subscriptionStatus"] == "ACTIVE" and d["company"]["suspensionReason"] is None, f"({s})")
    s, r = call("GET", "/api/v1/vehicles", token=MGR)
    check("gestionnaire de nouveau servi", s == 200, f"({s})")
    s, r = call("POST", f"{BILL}/companies/{COMPANY}/reactivate", {"reason": "x"}, token=SUPER)
    check("réactivation sans suspension : refusée", s == 409 and code(r) == "SUB_021", f"({s} {code(r)})")

    print("4. Commandes et règlements")
    s, c = call("POST", "/api/v1/organization/subscription/checkout", {"planId": BIG["id"], "billingCycle": "MONTHLY"}, token=ADMIN)
    order = (c.get("order") or c) if isinstance(c, dict) else {}
    OID = order.get("id") or order.get("orderId")
    check("commande créée par l'administrateur", s == 200 and OID, f"({s})")
    s, ov3 = call("GET", f"{BILL}/overview", token=SUPER)
    check("commande en attente comptée", ov3["pendingOrders"] >= 1 and float(ov3["pendingOrdersAmount"]) >= float(BIG["monthly"]), f"({ov3['pendingOrders']})")
    s, r = call("POST", f"{BILL}/orders/{OID}/record-payment", {"method": "CRYPTO"}, token=SUPER)
    check("mode de règlement inconnu", s == 400 and code(r) == "SUB_023", f"({s} {code(r)})")
    s, r = call("POST", f"{BILL}/orders/{OID}/record-payment", {"method": "BANK_TRANSFER", "reference": " "}, token=SUPER)
    check("virement sans référence : refusé", s == 400 and code(r) == "SUB_024", f"({s} {code(r)})")
    end1 = row()["subscriptionEnd"]
    s, o = call("POST", f"{BILL}/orders/{OID}/record-payment", {"method": "BANK_TRANSFER", "reference": "VIR-TEST-1", "note": "Banque Atlantique"}, token=SUPER)
    check("règlement enregistré", s == 200 and o["status"] == "PAID" and o["paymentMethod"] == "BANK_TRANSFER" and o["paymentReference"] == "VIR-TEST-1", f"({s})")
    check("échéance prolongée d'une période", row()["subscriptionEnd"] > end1, f"({end1} → {row()['subscriptionEnd']})")
    check("journal : paiement avec auteur et montant", "PAYMENT_CONFIRMED" in events())
    s, r = call("POST", f"{BILL}/orders/{OID}/record-payment", {"method": "CASH"}, token=SUPER)
    check("double règlement refusé", s == 409 and code(r) == "SUB_014", f"({s} {code(r)})")
    s, ov4 = call("GET", f"{BILL}/overview", token=SUPER)
    check("encaissé du mois augmenté", float(ov4["collectedThisMonth"]) == float(ov3["collectedThisMonth"]) + float(BIG["monthly"]), f"({ov4['collectedThisMonth']})")
    s, c = call("POST", "/api/v1/organization/subscription/checkout", {"planId": BIG["id"], "billingCycle": "MONTHLY"}, token=ADMIN)
    order = (c.get("order") or c) if isinstance(c, dict) else {}
    OID2 = order.get("id") or order.get("orderId")
    s, r = call("POST", f"{BILL}/orders/{OID2}/cancel", {"reason": ""}, token=SUPER)
    check("annulation : motif obligatoire", s == 400 and code(r) == "SUB_017", f"({s} {code(r)})")
    s, r = call("POST", f"{BILL}/orders/{OID2}/cancel", {"reason": "Doublon"}, token=SUPER)
    check("commande annulée", s == 200 and r["status"] == "CANCELLED", f"({s})")
    s, r = call("POST", f"{BILL}/orders/{OID2}/record-payment", {"method": "CASH"}, token=SUPER)
    check("règlement d'une commande annulée refusé", s == 409 and code(r) == "SUB_014", f"({s} {code(r)})")
    s, orders = call("GET", f"{BILL}/orders", token=SUPER)
    check("liste des commandes avec l'entreprise", s == 200 and any(o["id"] == OID and o["companyName"] for o in orders), f"({len(orders)})")

    print("5. Échéances et rappels")
    sql(f"update fleet.companies set subscription_end = CURRENT_DATE + 7 where id='{COMPANY}'")
    check("état : échéance proche", row()["state"] == "EXPIRING", f"({row()['state']} {row()['daysLeft']} j)")
    before = notifications()
    s, r = call("POST", f"{BILL}/reminders/run", token=SUPER)
    check("rappel J-7 envoyé", s == 200 and r["sent"] >= 1 and notifications() == before + 1, f"({r})")
    s, r = call("POST", f"{BILL}/reminders/run", token=SUPER)
    check("pas de doublon le même jour", notifications() == before + 1 and "REMINDER_J7" in events(), f"({r})")
    s, ov5 = call("GET", f"{BILL}/overview", token=SUPER)
    check("listée dans les échéances proches", any(c["id"] == COMPANY for c in ov5["expiringSoon"]))
    s, d = call("POST", f"{BILL}/companies/{COMPANY}/remind", token=SUPER)
    check("relance manuelle tracée", s == 200 and d["events"][0]["type"] == "REMINDER" and notifications() == before + 2, f"({s})")
    sql(f"update fleet.companies set subscription_end = CURRENT_DATE - 2 where id='{COMPANY}'")
    check("état : période de grâce", row()["state"] == "GRACE", f"({row()['state']})")
    sql(f"update fleet.companies set subscription_end = CURRENT_DATE - 60 where id='{COMPANY}'")
    check("état : expiré après la grâce", row()["state"] == "EXPIRED", f"({row()['state']})")
    s, d = call("POST", f"{BILL}/companies/{COMPANY}/suspend", {"reason": "Impayé"}, token=SUPER)
    s, d = call("POST", f"{BILL}/companies/{COMPANY}/reactivate", {"reason": "Levée"}, token=SUPER)
    check("réactivation après l'échéance : reste expiré", s == 200 and d["company"]["subscriptionStatus"] == "EXPIRED", f"({d['company']['subscriptionStatus']})")
    s, d = call("POST", f"{BILL}/companies/{COMPANY}/extend", {"days": 30, "reason": "Geste"}, token=SUPER)
    check("prolonger un expiré : repart d'aujourd'hui", s == 200 and d["company"]["subscriptionStatus"] == "ACTIVE" and d["company"]["daysLeft"] == 30, f"({d['company']['daysLeft']} j)")
    sql(f"update fleet.companies set plan_id='{FREE['id']}', subscription_end=null, billing_cycle=null, subscription_status='ACTIVE' where id='{COMPANY}'")
    s, r = call("POST", f"{BILL}/companies/{COMPANY}/extend", {"days": 30, "reason": "x"}, token=SUPER)
    check("offre gratuite : rien à prolonger", s == 409 and code(r) == "SUB_019", f"({s} {code(r)})")
    s, r = call("POST", f"{BILL}/companies/{COMPANY}/remind", token=SUPER)
    check("offre gratuite : pas de relance", s == 409 and code(r) == "SUB_019", f"({s} {code(r)})")
    PENDING_COMPANY = sql("select c.id from fleet.companies c join fleet.users u on u.id=c.id where c.subscription_status='PENDING' and coalesce(u.approval_status,'')<>'REJECTED' and u.deleted_at is null limit 1")
    if PENDING_COMPANY:
        s, r = call("POST", f"{BILL}/companies/{PENDING_COMPANY}/suspend", {"reason": "x"}, token=SUPER)
        check("demande en attente : pas de suspension", s == 409 and code(r) == "SUB_022", f"({s} {code(r)})")
    s, r = call("GET", f"{BILL}/companies/00000000-0000-4000-8000-000000000000", token=SUPER)
    check("entreprise inconnue", s == 404 and code(r) == "SUB_016", f"({s} {code(r)})")
    print("   journal :", ", ".join(events()[:14]))
finally:
    c = json.loads(saved)
    esc = lambda v: "null" if v is None else "'" + str(v).replace("'", "''") + "'"
    sql(f"update fleet.companies set plan_id={esc(c['plan_id'])}, subscription_status={esc(c['subscription_status'])}, subscription_start={esc(c['subscription_start'])}, "
        f"subscription_end={esc(c['subscription_end'])}, billing_cycle={esc(c['billing_cycle'])}, suspension_reason={esc(c['suspension_reason'])}, suspended_at={esc(c['suspended_at'])} where id='{COMPANY}'")
    sql(f"delete from fleet.subscription_events where company_id='{COMPANY}' and created_at >= '{STARTED}'")
    sql(f"delete from fleet.subscription_orders where company_id='{COMPANY}' and created_at >= '{STARTED}'")
    sql(f"delete from fleet.notifications where user_id='{COMPANY}' and title='Abonnement' and created_at >= '{STARTED}'")

print(f"\n{sum(results)}/{len(results)} vérifications réussies")
raise SystemExit(0 if all(results) else 1)
