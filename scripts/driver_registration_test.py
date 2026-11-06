#!/usr/bin/env python3
"""Inscription des chauffeurs : demande publique, décision du gestionnaire, code d'activation,
activation par le chauffeur, finalisation du profil et des pièces obligatoires.

Prérequis : backend sur :8081 (APPLICATION_FILE_MODE=local), DEMO_PASSWORD dans .env.
Le compte créé à l'activation existe aussi dans le Kernel (inscription réelle) ; la fiche locale,
ses pièces, les demandes et les notifications de l'essai sont supprimées à la fin.
"""
import datetime
import json
import os
import random
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

B = "http://localhost:8081"
ENV = os.path.join(os.path.dirname(__file__), "..", ".env")
PW = next(l.split("=", 1)[1].strip() for l in open(ENV) if l.startswith("DEMO_PASSWORD="))
results = []


def call(method, path, body=None, token=None, raw=None, content_type="application/json"):
    headers = {"Content-Type": content_type}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(B + path, method=method, headers=headers, data=data)
    try:
        with urllib.request.urlopen(req, timeout=90) as r:
            payload = r.read()
            out = json.loads(payload) if payload else None
            if isinstance(out, dict) and "data" in out and "success" in out:
                out = out["data"]
            return r.status, out
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


def login(identifier, password=PW):
    s, d = call("POST", "/api/v1/auth/login", {"identifier": identifier, "password": password})
    return (d or {}).get("accessToken") if s == 200 else None


def notif(user_id, event, since):
    return int(sql(f"select count(*) from fleet.notifications where user_id='{user_id}' "
                   f"and data like '%\"{event}\"%' and created_at > '{since}'"))


MGR, DRV = login("manager.dupont"), login("driver.bella")
DUPONT = sql("select id from fleet.users where username='manager.dupont'")
DOUALA = sql("select id from fleet.fleets where name='Flotte Douala'")
since = sql("select now()::timestamp")
suffix = f"{random.randint(0, 999999):06d}"
PHONE = f"+2376{random.randint(10000000, 99999999)}"
LICENCE = f"ESSAI-REG-{suffix}"
PASSWORD = f"Essai-{suffix}x"
created_driver = None
skipped_driver = None
reg_ids = []
# La démo est sur l'offre Découverte (5 chauffeurs, déjà atteinte) : la limite est d'abord vérifiée,
# puis l'offre Entreprise est appliquée le temps de l'essai et l'offre d'origine restaurée.
COMPANY = sql(f"select company_id from fleet.fleets where id='{DOUALA}'")
saved_plan = sql(f"select plan_id || '|' || subscription_status || '|' || coalesce(subscription_start::text, '') || '|' || "
                 f"coalesce(subscription_end::text, '') || '|' || coalesce(billing_cycle, '') from fleet.companies where id='{COMPANY}'")

try:
    print("1. Code d'inscription des flottes")
    s, codes = call("GET", "/api/v1/driver-registrations/fleet-codes", token=MGR)
    douala = next((c for c in (codes or []) if c["fleetId"] == DOUALA), None)
    check("le gestionnaire voit le code de chaque flotte", s == 200 and douala and len(douala["code"]) == 6, f"({s})")
    s, again = call("GET", "/api/v1/driver-registrations/fleet-codes", token=MGR)
    check("le code reste le même d'un affichage à l'autre",
          s == 200 and next(c for c in again if c["fleetId"] == DOUALA)["code"] == douala["code"])
    s, r = call("GET", "/api/v1/driver-registrations/fleet-codes", token=DRV)
    check("un chauffeur n'y a pas accès", s == 403, f"({s})")
    FLEET_CODE = douala["code"]

    print("2. Demande du chauffeur (sans session)")
    base = {"fleetCode": FLEET_CODE, "firstName": "Essai", "lastName": "Inscription", "licenceNumber": LICENCE}
    s, r = call("POST", "/api/v1/public/driver-registrations", base)
    check("sans téléphone ni e-mail → refusée", s == 400 and code(r) == "REG_002", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/public/driver-registrations", {**base, "phone": "12"})
    check("téléphone invalide → refusée", s == 400 and code(r) == "REG_003", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/public/driver-registrations", {**base, "phone": PHONE, "fleetCode": "ZZZZZZ"})
    check("code de flotte inconnu → refusée", s == 404 and code(r) == "REG_005", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/public/driver-registrations", {**base, "phone": PHONE, "licenceNumber": "B2000004CM"})
    check("permis déjà enregistré → refusée", s == 409 and code(r) == "REG_007", f"({s} {code(r)})")
    s, sub = call("POST", "/api/v1/public/driver-registrations", {**base, "phone": PHONE.replace("+237", "+237 ")})
    check("demande valide enregistrée", s == 201 and sub["status"] == "PENDING" and sub["fleetName"] == "Flotte Douala",
          f"({s} {code(sub)})")
    reg_ids.append(sub["id"])
    s, r = call("POST", "/api/v1/public/driver-registrations", {**base, "phone": PHONE})
    check("deuxième demande pour le même chauffeur → refusée", s == 409 and code(r) == "REG_006", f"({s} {code(r)})")
    check("le gestionnaire est notifié", notif(DUPONT, "DRIVER_REGISTRATION_REQUESTED", since) == 1)
    s, r = call("GET", "/api/v1/driver-registrations/pending-count", token=MGR)
    check("compteur des demandes en attente", s == 200 and r["count"] >= 1, f"({s} {r})")
    s, r = call("POST", "/api/v1/public/driver-registrations/activate", {"contact": PHONE, "code": "ABCD2345", "password": PASSWORD})
    check("activation avant décision → refusée", s == 400 and code(r) == "REG_015", f"({s} {code(r)})")

    print("3. Décision du gestionnaire et code d'activation")
    s, r = call("POST", f"/api/v1/driver-registrations/{sub['id']}/approve", token=DRV)
    check("un chauffeur ne peut pas accepter", s == 403, f"({s})")
    sql(f"update fleet.companies set plan_id='55000001-0000-4000-8000-000000000001', subscription_status='ACTIVE' where id='{COMPANY}'")
    s, r = call("POST", f"/api/v1/driver-registrations/{sub['id']}/approve", token=MGR)
    check("limite de chauffeurs du plan atteinte → acceptation refusée", s == 403 and code(r) == "SUB_005", f"({s} {code(r)})")
    sql(f"update fleet.companies set plan_id='55000003-0000-4000-8000-000000000003', subscription_status='ACTIVE', "
        f"subscription_start=current_date, subscription_end=current_date + 30 where id='{COMPANY}'")
    s, reg = call("POST", f"/api/v1/driver-registrations/{sub['id']}/approve", token=MGR)
    first_code = (reg or {}).get("activationCode") or ""
    check("demande acceptée, code généré", s == 200 and reg["status"] == "APPROVED" and len(first_code) == 9, f"({s} {code(reg)})")
    check("le téléphone est enregistré sans espaces", reg.get("phone") == PHONE, f"({reg.get('phone')})")
    s, r = call("POST", f"/api/v1/driver-registrations/{sub['id']}/approve", token=MGR)
    check("accepter deux fois → refusé", s == 409 and code(r) == "REG_012", f"({s} {code(r)})")
    s, lst = call("GET", "/api/v1/driver-registrations", token=MGR)
    mine = next((x for x in (lst or []) if x["id"] == sub["id"]), None)
    check("le code reste visible dans la liste du gestionnaire", s == 200 and mine and mine["activationCode"] == first_code)
    s, sent = call("POST", f"/api/v1/driver-registrations/{sub['id']}/send-code", token=MGR)
    sms = next((d for d in (sent or {}).get("deliveries", []) if d["channel"] == "SMS"), None)
    check("envoi par SMS tenté (aucun fournisseur : non remis, numéro masqué)",
          s == 200 and sms and sms["delivered"] is False and sms["destination"].startswith("***"), f"({s} {sent and sent.get('deliveries')})")
    s, r = call("POST", "/api/v1/public/driver-registrations/activate", {"contact": PHONE, "code": "ZZZZ-ZZZZ", "password": PASSWORD})
    check("mauvais code → refusé", s == 400 and code(r) == "REG_015", f"({s} {code(r)})")
    check("l'essai raté est compté", sql(f"select failed_attempts from fleet.driver_registrations where id='{sub['id']}'") == "1")
    s, reg2 = call("POST", f"/api/v1/driver-registrations/{sub['id']}/regenerate-code", token=MGR)
    new_code = reg2["activationCode"]
    check("nouveau code généré, compteur remis à zéro", s == 200 and new_code != first_code
          and sql(f"select failed_attempts from fleet.driver_registrations where id='{sub['id']}'") == "0")
    s, r = call("POST", "/api/v1/public/driver-registrations/activate", {"contact": PHONE, "code": first_code, "password": PASSWORD})
    check("l'ancien code ne fonctionne plus", s == 400 and code(r) == "REG_015", f"({s} {code(r)})")
    sql(f"update fleet.driver_registrations set failed_attempts=5 where id='{sub['id']}'")
    s, r = call("POST", "/api/v1/public/driver-registrations/activate", {"contact": PHONE, "code": new_code, "password": PASSWORD})
    check("après 5 essais le code est bloqué", s == 429 and code(r) == "REG_016", f"({s} {code(r)})")
    sql(f"update fleet.driver_registrations set failed_attempts=0, code_expires_at=now() - interval '1 minute' where id='{sub['id']}'")
    s, r = call("POST", "/api/v1/public/driver-registrations/activate", {"contact": PHONE, "code": new_code, "password": PASSWORD})
    check("code expiré → refusé", s == 410 and code(r) == "REG_017", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/driver-registrations/{sub['id']}/send-code", token=MGR)
    check("on n'envoie pas un code expiré", s == 409 and code(r) == "REG_014", f"({s} {code(r)})")
    s, reg3 = call("POST", f"/api/v1/driver-registrations/{sub['id']}/regenerate-code", token=MGR)
    final_code = reg3["activationCode"]

    print("4. Activation du compte par le chauffeur")
    s, r = call("POST", "/api/v1/public/driver-registrations/activate", {"contact": PHONE, "code": final_code, "password": "court"})
    check("mot de passe trop faible → refusé", s == 400 and code(r) == "REG_010", f"({s} {code(r)})")
    s, act = call("POST", "/api/v1/public/driver-registrations/activate",
                  {"contact": PHONE.replace("+237", "+237 "), "code": final_code.lower().replace("-", " "), "password": PASSWORD})
    ok = s == 200 and act and act.get("auth", {}).get("accessToken")
    check("compte activé, session ouverte", ok, f"({s} {code(act)})")
    if not ok:
        raise SystemExit("activation impossible")
    NEWTOK = act["auth"]["accessToken"]
    created_driver = sql(f"select driver_user_id from fleet.driver_registrations where id='{sub['id']}'")
    check("identifiant de connexion renvoyé", act["username"].startswith("essai.inscription."), f"({act['username']})")
    check("demande close, code effacé", sql(f"select status || ':' || coalesce(activation_code,'') from fleet.driver_registrations where id='{sub['id']}'") == "ACTIVATED:")
    check("le gestionnaire est notifié de l'activation", notif(DUPONT, "DRIVER_ACCOUNT_ACTIVATED", since) == 1)
    s, r = call("POST", "/api/v1/public/driver-registrations/activate", {"contact": PHONE, "code": final_code, "password": PASSWORD})
    check("le code ne sert qu'une fois", s == 400 and code(r) == "REG_015", f"({s} {code(r)})")
    check("connexion avec le numéro de téléphone", login(PHONE, PASSWORD) is not None)
    check("connexion avec l'identifiant", login(act["username"], PASSWORD) is not None)

    print("5. Finalisation : profil et pièces obligatoires")
    s, onb = call("GET", "/api/v1/drivers/me/onboarding", token=NEWTOK)
    check("inscription à terminer", s == 200 and onb["completed"] is False and not onb["canComplete"], f"({s} {code(onb)})")
    check("profil manquant listé", set(onb["missingProfile"]) == {"PHOTO", "BIRTH_DATE", "ADDRESS", "EMERGENCY_CONTACT_NAME",
                                                                  "EMERGENCY_CONTACT_PHONE"}, f"({onb['missingProfile']})")
    check("permis de conduire à déposer", "DRIVING_LICENSE" in onb["missingDocuments"], f"({onb['missingDocuments']})")
    s, r = call("POST", "/api/v1/drivers/me/onboarding/complete", token=NEWTOK)
    check("terminer trop tôt → refusé", s == 409 and code(r) == "ONB_002", f"({s} {code(r)})")
    s, r = call("PUT", "/api/v1/drivers/me/onboarding/profile", {"birthDate": "2015-01-01"}, NEWTOK)
    check("chauffeur mineur → refusé", s == 400 and code(r) == "ONB_001", f"({s} {code(r)})")
    s, onb = call("PUT", "/api/v1/drivers/me/onboarding/profile",
                  {"birthDate": "1990-05-12", "address": "Akwa, Douala", "emergencyContactName": "Marie Essai",
                   "emergencyContactPhone": "+237 699 00 00 00"}, NEWTOK)
    check("profil complété (reste la photo)", s == 200 and onb["missingProfile"] == ["PHOTO"], f"({s} {onb and onb.get('missingProfile')})")
    png = bytes.fromhex("89504E470D0A1A0A0000000D4948445200000001000000010806000000"
                        "1F15C4890000000D49444154789C6360000002000154A24F5D0000000049454E44AE426082")
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\n"
            f"Content-Type: image/png\r\n\r\n").encode() + png + f"\r\n--{boundary}--\r\n".encode()
    s, r = call("POST", "/api/v1/account/picture", token=NEWTOK, raw=body, content_type=f"multipart/form-data; boundary={boundary}")
    check("photo de profil envoyée", s == 200, f"({s})")
    today = datetime.date.today()
    s, doc = call("POST", f"/api/v1/drivers/{created_driver}/documents", {
        "docType": "DRIVING_LICENSE", "docNumber": LICENCE, "licenseCategories": "B,C", "issuer": "MINT",
        "issueDate": (today - datetime.timedelta(days=300)).isoformat(),
        "expiryDate": (today + datetime.timedelta(days=900)).isoformat(),
        "fileUrl": f"/api/v1/files/essai-reg-{suffix}.pdf", "fileOriginalName": "permis.pdf",
        "fileMimeType": "application/pdf", "fileSizeBytes": 1000}, NEWTOK)
    check("le chauffeur dépose son permis", s == 201, f"({s} {code(doc)})")
    s, onb = call("GET", "/api/v1/drivers/me/onboarding", token=NEWTOK)
    required_left = [d for d in onb["missingDocuments"]]
    check("permis en attente de contrôle : compte comme fourni",
          "DRIVING_LICENSE" not in onb["missingDocuments"], f"({onb['documents']})")
    # Pièces obligatoires propres à l'entreprise (selon sa politique) : déposées elles aussi.
    for d in required_left:
        call("POST", f"/api/v1/drivers/{created_driver}/documents", {
            "docType": d, "docNumber": f"ESSAI-{d}", "issuer": "Essai",
            "issueDate": (today - datetime.timedelta(days=30)).isoformat(),
            "expiryDate": (today + datetime.timedelta(days=400)).isoformat(),
            "fileUrl": f"/api/v1/files/essai-reg-{suffix}-{d}.pdf", "fileOriginalName": "piece.pdf",
            "fileMimeType": "application/pdf", "fileSizeBytes": 1000}, NEWTOK)
    s, onb = call("POST", "/api/v1/drivers/me/onboarding/complete", token=NEWTOK)
    check("inscription terminée", s == 200 and onb["completed"] is True, f"({s} {code(onb)})")
    check("le gestionnaire est invité à contrôler le dossier", notif(DUPONT, "DRIVER_ONBOARDING_COMPLETED", since) == 1)
    s, lst = call("GET", "/api/v1/driver-registrations", token=MGR)
    mine = next((x for x in lst if x["id"] == sub["id"]), None)
    check("la demande apparaît comme terminée côté gestionnaire", mine and mine["status"] == "ACTIVATED" and mine["onboardingCompleted"] is True)

    print("6. Refus d'une demande")
    s, sub2 = call("POST", "/api/v1/public/driver-registrations",
                   {**base, "licenceNumber": LICENCE + "B", "phone": None, "email": f"essai.{suffix}@fleetman.local"})
    reg_ids.append(sub2["id"])
    s, r = call("POST", f"/api/v1/driver-registrations/{sub2['id']}/reject", {"reason": " "}, MGR)
    check("refus sans motif → refusé", s == 400 and code(r) == "REG_013", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/driver-registrations/{sub2['id']}/reject", {"reason": "Inconnu de l'agence"}, MGR)
    check("demande refusée avec motif", s == 200 and r["status"] == "REJECTED" and r["activationCode"] is None, f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/driver-registrations/{sub2['id']}/approve", token=MGR)
    check("une demande refusée ne peut plus être acceptée", s == 409, f"({s})")
    s, sub3 = call("POST", "/api/v1/public/driver-registrations",
                   {**base, "licenceNumber": LICENCE + "B", "email": f"essai.{suffix}@fleetman.local"})
    check("après un refus, une nouvelle demande est possible", s == 201, f"({s} {code(sub3)})")
    if s == 201:
        reg_ids.append(sub3["id"])

    print("6b. Passer la finalisation (pour tester le reste de l'application)")
    if s == 201:
        s, r3 = call("POST", f"/api/v1/driver-registrations/{sub3['id']}/approve", token=MGR)
        s, act3 = call("POST", "/api/v1/public/driver-registrations/activate",
                       {"contact": f"essai.{suffix}@fleetman.local", "code": r3["activationCode"], "password": PASSWORD})
        check("activation par e-mail", s == 200, f"({s} {code(act3)})")
        if s == 200:
            skipped_driver = sql(f"select driver_user_id from fleet.driver_registrations where id='{sub3['id']}'")
            tok3 = act3["auth"]["accessToken"]
            s, o = call("GET", "/api/v1/drivers/me/onboarding", token=tok3)
            check("inscription par e-mail : le téléphone est demandé au profil", s == 200 and "PHONE" in o.get("missingProfile", []),
                  f"({s} {o.get('missingProfile') if s == 200 else (o.get('code'), (o.get('detail') or '')[:120])})")
            s, o = call("POST", "/api/v1/drivers/me/onboarding/skip", token=tok3)
            check("le chauffeur passe l'étape : espace ouvert", s == 200 and o["completed"] is True and not o["canComplete"], f"({s} {code(o)})")
            check("le gestionnaire est prévenu du dossier incomplet", notif(DUPONT, "DRIVER_ONBOARDING_SKIPPED", since) == 1)

    print("7. Fin de l'ancien circuit")
    s, r = call("POST", "/api/v1/drivers", {"fleetId": DOUALA, "firstName": "X", "lastName": "Y",
                                            "licenceNumber": "Z" + suffix, "phone": "+237600000009"}, MGR)
    check("le gestionnaire ne crée plus de compte chauffeur", s == 403, f"({s})")
finally:
    plan_id, plan_status, plan_start, plan_end, plan_cycle = (saved_plan.split("|") + [""] * 5)[:5]
    sql(f"update fleet.companies set plan_id='{plan_id}', subscription_status='{plan_status}', "
        f"subscription_start=nullif('{plan_start}','')::date, subscription_end=nullif('{plan_end}','')::date, "
        f"billing_cycle=nullif('{plan_cycle}','') where id='{COMPANY}'")
    if skipped_driver:
        sql(f"delete from fleet.notifications where user_id='{skipped_driver}'")
        s2, _ = call("DELETE", f"/api/v1/drivers/{skipped_driver}", token=MGR)
        if s2 != 204:
            sql(f"update fleet.drivers set deleted_at=now(), status='LEFT', left_at=current_date, left_reason='Essai' where user_id='{skipped_driver}'")
    if created_driver:
        sql(f"delete from fleet.documents where target_id='{created_driver}'")
        sql(f"delete from fleet.notifications where user_id='{created_driver}'")
        s, _ = call("DELETE", f"/api/v1/drivers/{created_driver}", token=MGR)
        if s != 204:
            sql(f"update fleet.drivers set deleted_at=now(), status='LEFT', left_at=current_date, left_reason='Essai' where user_id='{created_driver}'")
    for rid in reg_ids:
        sql(f"delete from fleet.driver_registrations where id='{rid}'")
    sql(f"delete from fleet.notifications where created_at > '{since}' and (data like '%DRIVER_REGISTRATION_REQUESTED%' "
        f"or data like '%DRIVER_ACCOUNT_ACTIVATED%' or data like '%DRIVER_ONBOARDING_COMPLETED%' or data like '%DRIVER_ONBOARDING_SKIPPED%' or data like '%DOCUMENT_SUBMITTED%')")

print(f"\n{sum(results)}/{len(results)} vérifications réussies")
sys.exit(0 if all(results) else 1)
