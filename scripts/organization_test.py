"""Essai : organisation de l'entreprise (migration 063) — fiche légale, sites, organigramme.

Backend en mode Kernel (port 8081), comptes réels : adminfleet (administrateur) et manager.dupont
(gestionnaire). Mot de passe : DEMO_PASSWORD du fichier .env. Le logo reste en stockage local.

1. Fiche : modifiable par l'admin seul, valeurs contrôlées, complétude, nom recopié sur les gestionnaires.
2. Sites : création, siège unique, nom unique, désactivation et suppression refusées s'il reste une flotte.
3. Organigramme : rattachement d'une flotte, gestionnaire limité à ses flottes, isolation entre entreprises.
4. Les anciens endpoints du gestionnaire sur l'entreprise ont disparu.
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
PNG = bytes.fromhex("89504e470d0a1a0a0000000d4948445200000001000000010806000000" "1f15c4890000000d49444154789c6360f8cf"
                    "c000000301010018dd8db40000000049454e44ae426082")


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


def upload(path, filename, mime, content, token):
    boundary = "fleetmanorgtest"
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\n"
            f"Content-Type: {mime}\r\n\r\n").encode() + content + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(B + path, method="POST", data=body, headers={
        "Authorization": "Bearer " + token, "Content-Type": f"multipart/form-data; boundary={boundary}"})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, json.loads(r.read() or b"{}")
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


ADMIN = login("adminfleet")
MGR = login("manager.dupont")
uid = lambda u: sql(f"select id from fleet.users where username='{u}'")
COMPANY, DUPONT = uid("adminfleet"), uid("manager.dupont")
FLEET = sql(f"select id from fleet.fleets where manager_id='{DUPONT}' and deleted_at is null order by name limit 1")
OTHER_FLEET = sql(f"select id from fleet.fleets where company_id<>'{COMPANY}' and deleted_at is null limit 1")
OTHER_MGR_FLEETS = int(sql(f"select count(*) from fleet.fleets where company_id='{COMPANY}' and manager_id<>'{DUPONT}' and deleted_at is null"))
saved = sql(f"select row_to_json(c) from (select name, logo_url, legal_name, legal_form, registration_number, tax_id, sector, address, city, country, "
            f"contact_phone, contact_email, website from fleet.companies where id='{COMPANY}') c")
saved_names = sql(f"select json_agg(json_build_object('id', user_id, 'n', company_name)) from fleet.fleet_managers where created_by_admin_id='{COMPANY}'")
FULL = {"organizationName": "Fret Express Essai", "legalName": "Fret Express SARL", "legalForm": "SARL",
        "registrationNumber": "RC/YAO/2024/B/1234", "taxId": "M012345678901A", "sector": "FREIGHT",
        "address": "Rue 1.234, Bastos", "city": "Yaoundé", "country": "CM", "companyPhone": "+237 699 00 00 00",
        "companyEmail": "contact@fret-essai.cm", "website": "www.fret-essai.cm"}

try:
    print("1. Fiche de l'entreprise")
    s, p = call("GET", "/api/v1/organization/profile", token=MGR)
    check("le gestionnaire lit la fiche de son entreprise", s == 200 and p.get("adminId") == COMPANY, f"({s})")
    s, r = call("PUT", "/api/v1/organization/profile", dict(FULL), token=MGR)
    check("le gestionnaire ne peut pas la modifier", s == 403, f"({s})")
    for field, value, label in (("organizationName", " ", "nom vide"), ("companyEmail", "pas-un-mail", "email invalide"),
                                ("companyPhone", "abc", "téléphone invalide"), ("website", "pas un site", "site web invalide"),
                                ("legalForm", "XXX", "forme juridique inconnue"), ("sector", "XXX", "secteur inconnu")):
        s, r = call("PUT", "/api/v1/organization/profile", {**FULL, field: value}, token=ADMIN)
        check(f"{label} : refusé", s == 400 and code(r) == "ORG_001", f"({s} {code(r)})")
    s, p = call("PUT", "/api/v1/organization/profile", dict(FULL), token=ADMIN)
    check("l'administrateur enregistre la fiche", s == 200 and p.get("registrationNumber") == FULL["registrationNumber"], f"({s})")
    check("il manque encore le logo et/ou le siège", "HEAD_OFFICE" in p.get("missingFields", []) and p.get("completeness", 100) < 100,
          f"({p.get('completeness')} % : {p.get('missingFields')})")
    check("le nom suit sur les gestionnaires",
          sql(f"select company_name from fleet.fleet_managers where user_id='{DUPONT}'") == FULL["organizationName"])
    s, r = call("PUT", "/api/v1/fleet-managers/me/company", {"companyName": "X"}, token=MGR)
    check("ancien endpoint du gestionnaire : supprimé", s in (404, 405), f"({s})")
    s, r = upload("/api/v1/organization/logo", "note.pdf", "application/pdf", b"%PDF-1.4", ADMIN)
    check("logo : un PDF est refusé", s == 400, f"({s} {code(r)})")
    s, r = upload("/api/v1/organization/logo", "logo.png", "image/png", PNG, MGR)
    check("logo : refusé au gestionnaire", s == 403, f"({s})")
    s, r = upload("/api/v1/organization/logo", "logo.png", "image/png", PNG, ADMIN)
    check("logo : accepté pour l'administrateur", s == 200 and r.get("logoUrl"), f"({s})")

    print("2. Sites")
    site = {"name": "Siège Bastos", "siteType": "HEAD_OFFICE", "address": "Rue 1.234", "city": "Yaoundé", "phone": "+237 699 11 11 11"}
    s, r = call("POST", "/api/v1/organization/sites", site, token=MGR)
    check("création refusée au gestionnaire", s == 403, f"({s})")
    s, head = call("POST", "/api/v1/organization/sites", site, token=ADMIN)
    check("siège créé", s == 200 and head.get("siteType") == "HEAD_OFFICE", f"({s})")
    s, r = call("POST", "/api/v1/organization/sites", {**site, "name": "Deuxième siège"}, token=ADMIN)
    check("un seul siège", s == 409 and code(r) == "ORG_004", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/organization/sites", {**site, "siteType": "DEPOT", "name": "siège bastos"}, token=ADMIN)
    check("nom de site unique (sans la casse)", s == 409 and code(r) == "ORG_003", f"({s} {code(r)})")
    s, r = call("POST", "/api/v1/organization/sites", {**site, "siteType": "GARAGE", "name": "X site"}, token=ADMIN)
    check("type de site inconnu : refusé", s == 400, f"({s})")
    s, r = call("POST", "/api/v1/organization/sites", {**site, "siteType": "DEPOT", "name": "Sans ville", "city": " "}, token=ADMIN)
    check("ville obligatoire", s == 400, f"({s})")
    s, depot = call("POST", "/api/v1/organization/sites", {"name": "Dépôt Bonabéri", "siteType": "DEPOT", "city": "Douala"}, token=ADMIN)
    check("dépôt créé", s == 200 and depot.get("id"), f"({s})")
    s, r = call("PUT", f"/api/v1/organization/sites/{depot['id']}", {"name": "Dépôt de Bonabéri", "siteType": "DEPOT", "city": "Douala"}, token=ADMIN)
    check("site modifié", s == 200 and r.get("name") == "Dépôt de Bonabéri", f"({s})")
    s, p = call("GET", "/api/v1/organization/profile", token=ADMIN)
    check("la fiche est complète avec logo et siège", p.get("completeness") == 100 and not p.get("missingFields"),
          f"({p.get('completeness')} % {p.get('missingFields')})")
    s, sites = call("GET", "/api/v1/organization/sites", token=MGR)
    check("le gestionnaire voit les sites, le siège en premier", s == 200 and sites[0]["siteType"] == "HEAD_OFFICE", f"({s})")

    print("3. Organigramme")
    s, r = call("PUT", f"/api/v1/organization/fleets/{FLEET}/site", {"siteId": depot["id"]}, token=MGR)
    check("rattachement refusé au gestionnaire", s == 403, f"({s})")
    s, r = call("PUT", f"/api/v1/organization/fleets/{FLEET}/site", {"siteId": depot["id"]}, token=ADMIN)
    check("flotte rattachée au dépôt", s == 200 and sql(f"select site_id from fleet.fleets where id='{FLEET}'") == depot["id"], f"({s})")
    if OTHER_FLEET:
        s, r = call("PUT", f"/api/v1/organization/fleets/{OTHER_FLEET}/site", {"siteId": depot["id"]}, token=ADMIN)
        check("flotte d'une autre entreprise : introuvable", s == 404 and code(r) == "ORG_007", f"({s} {code(r)})")
    FOREIGN_SITE = sql(f"insert into fleet.company_sites (id, company_id, name, site_type, city) select gen_random_uuid(), id, 'Site étranger', 'DEPOT', 'Kribi' "
                       f"from fleet.companies where id<>'{COMPANY}' limit 1 returning id").splitlines()[0]
    s, r = call("PUT", f"/api/v1/organization/fleets/{FLEET}/site", {"siteId": FOREIGN_SITE}, token=ADMIN)
    check("site d'une autre entreprise : introuvable", s == 404 and code(r) == "ORG_002", f"({s} {code(r)})")
    s, r = call("PUT", f"/api/v1/organization/sites/{FOREIGN_SITE}", {"name": "Volé", "siteType": "DEPOT", "city": "Kribi"}, token=ADMIN)
    check("site d'une autre entreprise : non modifiable", s == 404 and code(r) == "ORG_002", f"({s} {code(r)})")
    sql(f"delete from fleet.company_sites where id='{FOREIGN_SITE}'")
    s, r = call("PATCH", f"/api/v1/organization/sites/{depot['id']}/active", {"active": False}, token=ADMIN)
    check("désactivation refusée : il reste une flotte", s == 409 and code(r) == "ORG_005", f"({s} {code(r)})")
    s, r = call("DELETE", f"/api/v1/organization/sites/{depot['id']}", token=ADMIN)
    check("suppression refusée : il reste une flotte", s == 409 and code(r) == "ORG_005", f"({s} {code(r)})")
    s, st = call("GET", "/api/v1/organization/structure", token=ADMIN)
    row = next((f for f in st.get("fleets", []) if f["id"] == FLEET), {})
    check("organigramme admin : flottes, site et gestionnaire", s == 200 and row.get("siteId") == depot["id"] and row.get("managerId") == DUPONT and len(st.get("managers", [])) > 0,
          f"({len(st.get('fleets', []))} flottes, {len(st.get('managers', []))} gestionnaires)")
    check("véhicules et conducteurs comptés", row.get("vehicleCount", -1) >= 0 and row.get("driverCount", -1) >= 0,
          f"({row.get('vehicleCount')} / {row.get('driverCount')})")
    s, sm = call("GET", "/api/v1/organization/structure", token=MGR)
    check("gestionnaire : uniquement ses flottes", s == 200 and all(f["managerId"] == DUPONT for f in sm["fleets"]) and sm["managers"] == [],
          f"({len(sm['fleets'])} flottes ; {OTHER_MGR_FLEETS} autres masquées)")
    s, r = call("PUT", f"/api/v1/organization/fleets/{FLEET}/site", {"siteId": None}, token=ADMIN)
    check("flotte détachée", s == 200 and sql(f"select site_id is null from fleet.fleets where id='{FLEET}'") == "t", f"({s})")
    s, r = call("PATCH", f"/api/v1/organization/sites/{depot['id']}/active", {"active": False}, token=ADMIN)
    check("site vide : désactivation acceptée", s == 200 and r.get("active") is False, f"({s})")
    s, r = call("PUT", f"/api/v1/organization/fleets/{FLEET}/site", {"siteId": depot["id"]}, token=ADMIN)
    check("rattachement à un site désactivé : refusé", s == 409 and code(r) == "ORG_006", f"({s} {code(r)})")
    s, r = call("DELETE", f"/api/v1/organization/sites/{depot['id']}", token=ADMIN)
    check("site vide : suppression acceptée", s == 200, f"({s})")
finally:
    sql(f"update fleet.fleets set site_id = null where company_id='{COMPANY}'")
    sql(f"delete from fleet.company_sites where company_id='{COMPANY}'")
    c = json.loads(saved)
    esc = lambda v: "null" if v is None else "'" + str(v).replace("'", "''") + "'"
    sql(f"update fleet.companies set name={esc(c['name'])}, logo_url={esc(c['logo_url'])}, legal_name={esc(c['legal_name'])}, legal_form={esc(c['legal_form'])}, "
        f"registration_number={esc(c['registration_number'])}, tax_id={esc(c['tax_id'])}, sector={esc(c['sector'])}, address={esc(c['address'])}, city={esc(c['city'])}, "
        f"country={esc(c['country'])}, contact_phone={esc(c['contact_phone'])}, contact_email={esc(c['contact_email'])}, website={esc(c['website'])} where id='{COMPANY}'")
    for row in json.loads(saved_names or "[]"):
        sql(f"update fleet.fleet_managers set company_name={esc(row['n'])} where user_id='{row['id']}'")

print(f"\n{sum(results)}/{len(results)} vérifications réussies")
raise SystemExit(0 if all(results) else 1)
