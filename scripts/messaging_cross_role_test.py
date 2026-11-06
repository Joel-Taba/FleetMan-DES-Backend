#!/usr/bin/env python3
"""Essai croisé : messagerie gestionnaire <-> chauffeur (migration 071) et notifications de fiche chauffeur.

1. Le gestionnaire écrit à la chauffeuse : notifiée (MESSAGE_RECEIVED), message lu → accusé visible du gestionnaire.
2. La chauffeuse répond : gestionnaire notifié, conversation non lue, lecture → compteur remis à zéro.
3. Message à toute la flotte : chaque chauffeur actif le reçoit.
4. Cloisonnement : un chauffeur d'une autre flotte ne lit pas le fil, un chauffeur n'écrit pas au nom du gestionnaire.
5. Fiche chauffeur modifiée par le gestionnaire : notifiée seulement en cas de changement réel.
Usage : python3 scripts/messaging_cross_role_test.py (backend en mode Kernel sur :8081).
"""
import json
import os
import subprocess
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8081"
ENV = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".env")
PW = next(l.split("=", 1)[1].strip() for l in open(ENV) if l.startswith("DEMO_PASSWORD="))
results = []


def call(method, path, body=None, token=None):
    headers = {"content-type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, method=method, headers=headers,
                                 data=json.dumps(body).encode() if body is not None else None)
    try:
        with urllib.request.urlopen(req, timeout=90) as r:
            raw = r.read()
            data = json.loads(raw) if raw else None
            status = r.status
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            data = json.loads(raw) if raw else None
        except ValueError:
            data = None
        status = e.code
    if isinstance(data, dict) and "success" in data and "data" in data:
        data = data["data"]
    return status, data


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


def login(user):
    for _ in range(6):
        s, d = call("POST", "/api/v1/auth/login", {"identifier": user, "password": PW})
        if s == 200 and isinstance(d, dict) and d.get("accessToken"):
            return d["accessToken"]
        time.sleep(15)  # Kernel parfois indisponible (AUTH_006)
    raise SystemExit(f"connexion impossible pour {user}")


def notifs(token, event, **keys):
    s, page = call("GET", "/api/v1/notifications?page=0&size=100", token=token)
    items = page.get("content", []) if isinstance(page, dict) else (page or [])
    out = []
    for n in items:
        data = n.get("data") if isinstance(n.get("data"), dict) else json.loads(n.get("dataJson") or n.get("data") or "{}")
        if (n.get("event") or data.get("type")) == event and all(str(data.get(k)) == str(v) for k, v in keys.items()):
            out.append(n)
    return out


MGR, DRV, OTHER = login("manager.dupont"), login("driver.bella"), login("driver.nana")
BELLA = sql("select id from fleet.users where username='driver.bella'")
DOUALA = sql("select id from fleet.fleets where name='Flotte Douala'")
mark = sql("select coalesce(max(created_at), 'epoch') from fleet.notifications")
mark_msg = sql("select coalesce(max(created_at), 'epoch') from fleet.driver_messages")
saved_phone = sql(f"select coalesce(phone, '') from fleet.users where id='{BELLA}'")
try:
    print("1. Le gestionnaire écrit à la chauffeuse")
    s, m1 = call("POST", f"/api/v1/messages/drivers/{BELLA}", {"body": "Pensez à prendre le badge du dépôt demain."}, MGR)
    check("message envoyé", s == 201 and m1.get("fromDriver") is False, f"({s})")
    n = notifs(DRV, "MESSAGE_RECEIVED", messageId=m1.get("id"), driverId=BELLA)
    check("chauffeuse notifiée (MESSAGE_RECEIVED, messageId)", len(n) == 1 and "badge" in n[0].get("message", ""), f"({len(n)})")
    s, unread = call("GET", "/api/v1/messages/mine/unread-count", token=DRV)
    check("compteur de messages non lus côté chauffeuse", s == 200 and unread.get("unread", 0) >= 1, f"({s} {unread})")
    s, mine = call("GET", "/api/v1/messages/mine", token=DRV)
    check("message visible dans le fil de la chauffeuse", s == 200 and any(x["id"] == m1["id"] for x in mine), f"({s})")
    s, thread = call("GET", f"/api/v1/messages/drivers/{BELLA}", token=MGR)
    seen = next((x for x in thread if x["id"] == m1["id"]), {})
    check("le gestionnaire voit que son message est lu", s == 200 and seen.get("readAt"), f"({s})")

    print("2. La chauffeuse répond")
    s, m2 = call("POST", "/api/v1/messages/mine", {"body": "Bien reçu, je le récupère à 7 h."}, DRV)
    check("réponse envoyée", s == 201 and m2.get("fromDriver") is True, f"({s})")
    n = notifs(MGR, "MESSAGE_RECEIVED", messageId=m2.get("id"), driverId=BELLA)
    check("gestionnaire notifié de la réponse", len(n) == 1 and "Claire" in n[0].get("title", ""), f"({len(n)})")
    s, convs = call("GET", "/api/v1/messages/conversations", token=MGR)
    conv = next((c for c in convs if c["driverId"] == BELLA), {})
    check("conversation en tête, non lue, dernier message de la chauffeuse",
          s == 200 and convs and convs[0]["driverId"] == BELLA and conv.get("unread") == 1 and conv.get("lastFromDriver") is True,
          f"({s} {conv.get('unread')})")
    call("GET", f"/api/v1/messages/drivers/{BELLA}", token=MGR)
    s, convs = call("GET", "/api/v1/messages/conversations", token=MGR)
    conv = next((c for c in convs if c["driverId"] == BELLA), {})
    check("lecture par le gestionnaire : compteur remis à zéro", conv.get("unread") == 0, f"({conv.get('unread')})")
    s, mine = call("GET", "/api/v1/messages/mine", token=DRV)
    own = next((x for x in mine if x["id"] == m2["id"]), {})
    check("la chauffeuse voit sa réponse lue", own.get("readAt"), "")

    print("3. Message à toute la flotte")
    active = int(sql(f"select count(*) from fleet.drivers where fleet_id='{DOUALA}' and coalesce(status,'ACTIVE') <> 'LEFT'"))
    s, r = call("POST", f"/api/v1/messages/fleets/{DOUALA}", {"body": "Réunion de sécurité vendredi à 8 h au dépôt."}, MGR)
    check("message envoyé à chaque chauffeur actif de la flotte", s == 201 and r.get("recipients") == active, f"({s} {r} / {active})")
    s, mine = call("GET", "/api/v1/messages/mine", token=DRV)
    check("la chauffeuse le reçoit, marqué « toute la flotte »",
          any(x.get("broadcast") and "Réunion de sécurité" in x["body"] for x in mine), "")

    print("4. Cloisonnement et validations")
    s, r = call("GET", f"/api/v1/messages/drivers/{BELLA}", token=OTHER)
    check("un chauffeur d'une autre flotte ne lit pas le fil", s == 403, f"({s})")
    s, r = call("POST", f"/api/v1/messages/drivers/{BELLA}", {"body": "Usurpation"}, DRV)
    check("un chauffeur n'écrit pas au nom du gestionnaire", s == 403, f"({s})")
    s, r = call("POST", "/api/v1/messages/mine", {"body": "   "}, DRV)
    check("message vide refusé", s == 400, f"({s})")

    print("5. Fiche chauffeur modifiée par le gestionnaire")
    new_phone = "+237677000999" if saved_phone != "+237677000999" else "+237677000998"
    s, d = call("PUT", f"/api/v1/drivers/{BELLA}", {"phone": new_phone}, MGR)
    check("téléphone modifié par le gestionnaire", s == 200, f"({s} {d.get('code') if isinstance(d, dict) else ''})")
    n = notifs(DRV, "DRIVER_PROFILE_UPDATED", driverId=BELLA)
    check("chauffeuse prévenue (DRIVER_PROFILE_UPDATED, téléphone)", len(n) == 1 and "téléphone" in n[0].get("message", ""), f"({len(n)})")
    s, d = call("PUT", f"/api/v1/drivers/{BELLA}", {"phone": new_phone}, MGR)
    n = notifs(DRV, "DRIVER_PROFILE_UPDATED", driverId=BELLA)
    check("réenregistrement sans changement : pas de nouvelle notification", s == 200 and len(n) == 1, f"({s} {len(n)})")
finally:
    sql(f"delete from fleet.driver_messages where fleet_id='{DOUALA}' and created_at > '{mark_msg}'")
    sql(f"delete from fleet.notifications where created_at > '{mark}' and (data like '%MESSAGE_RECEIVED%' or data like '%DRIVER_PROFILE_UPDATED%')")
    if saved_phone:
        sql(f"update fleet.users set phone='{saved_phone}' where id='{BELLA}'")
    print(f"\n{sum(results)}/{len(results)} vérifications réussies")
raise SystemExit(0 if all(results) else 1)
