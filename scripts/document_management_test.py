"""Essai : gestion documentaire (migration 059).

Backend en mode Kernel (port 8081), comptes réels : adminfleet (administrateur), manager.dupont
(gestionnaire de la flotte Douala), driver.bella (chauffeuse de cette flotte) et driver.nana (autre chauffeur).
Mot de passe : DEMO_PASSWORD du fichier .env. Aucun fichier n'est envoyé au Kernel : les pièces
pointent vers des noms de fichiers d'essai.

1. Dépôt : justificatif, dates et catégories contrôlés ; une pièce en attente ne compte pas.
2. Contrôle : rejet motivé, nouveau dépôt, approbation ; personne ne valide son propre dossier.
3. Renouvellement : l'ancienne version reste en vigueur jusqu'à l'approbation, l'historique reste.
4. Exploitation : pièce obligatoire manquante → trajet refusé ; socle légal et politique d'entreprise.
5. Lecture : dossiers et fichiers limités à la personne, son responsable et l'administrateur.
6. Dossier gestionnaire, archivage motivé, taux de conformité sur les pièces exigées.
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


ADMIN, MGR, DRV, OTHER = login("adminfleet"), login("manager.dupont"), login("driver.bella"), login("driver.nana")
uid = lambda u: sql(f"select id from fleet.users where username='{u}'")
BELLA, DUPONT = uid("driver.bella"), uid("manager.dupont")
COMPANY = uid("adminfleet")
DOUALA = sql("select id from fleet.fleets where name='Flotte Douala'")
V1 = sql("select id from fleet.vehicles where license_plate='DL-203-FF'")
saved_bella = sql(f"select coalesce(assigned_vehicle_id::text,'') from fleet.drivers where user_id='{BELLA}'")
sql(f"update fleet.drivers set assigned_vehicle_id=NULL where user_id='{BELLA}'")
last_notif = sql("select coalesce(max(created_at), 'epoch') from fleet.notifications")
today = datetime.date.today()
day = lambda n: (today + datetime.timedelta(days=n)).isoformat()
trips = []
n = 0


def piece(doc_type, **extra):
    global n
    n += 1
    return {"docType": doc_type, "docNumber": f"ESSAI-{n}", "issuer": "Essai", "issueDate": day(-30), "expiryDate": day(400),
            "fileUrl": f"/api/v1/files/essai-059-{n}.pdf", "fileOriginalName": "piece.pdf",
            "fileMimeType": "application/pdf", "fileSizeBytes": 1000, **extra}


def trip():
    s, r = call("POST", "/api/v1/trips", {
        "vehicleId": V1, "driverId": BELLA, "startDate": day(5), "startTime": "08:00:00",
        "scheduledReturnDatetime": f"{day(5)}T12:00:00", "departureKmIndex": 1000, "departureFuelIndex": 30,
        "destinationLocation": "Essai"}, MGR)
    if s in (200, 201):
        trips.append(r["id"])
    return s, r


def drop_trips():
    for t in trips:
        sql(f"delete from fleet.trips where id='{t}'")
    trips.clear()


def state_of(items, doc_type):
    return next((i["state"] for i in items if i["docType"] == doc_type), None)


DRIVER_DOCS = f"/api/v1/drivers/{BELLA}/documents"
VEHICLE_DOCS = f"/api/v1/vehicles/{V1}/documents"
MANAGER_DOCS = f"/api/v1/managers/{DUPONT}/documents"


# Données de démonstration (pièces en règle ajoutées le 03/11, plannings de démo) : écrites avant cet
# essai, elles faussent ses hypothèses. Mises de côté le temps de l'essai, restaurées dans finally.
parked_docs = sql("select coalesce(string_agg(id::text, ','), '') from fleet.documents where is_current "
                  "and notes in ('Document de démonstration','Permis de démonstration') and target_id in ('"+BELLA+"','"+V1+"')")
# Rattachées le temps de l'essai à une cible fictive : elles ne comptent ni dans les versions ni dans l'historique.
PARK_TARGET = "00000000-0000-4000-8000-0000000e5059"
parked_targets = {}
if parked_docs:
    for row in sql("select id::text || ':' || target_id::text from fleet.documents where id in ("
                   + ",".join(repr(x) for x in parked_docs.split(",")) + ")").splitlines():
        parked_targets[row.split(":")[0]] = row.split(":")[1]
    sql("update fleet.documents set is_current=false, target_id='" + PARK_TARGET + "' where id in ("
        + ",".join(repr(x) for x in parked_docs.split(",")) + ")")
parked_shifts = sql("select coalesce(string_agg(id::text || ':' || status, ','), '') from fleet.assignments "
                    "where status in ('PENDING','IN_PROGRESS') and (driver_id in ('"+BELLA+"') or vehicle_id in ('"+V1+"')) "
                    "and end_datetime > now() - interval '12 hours' and start_datetime < now() + interval '40 days'")
if parked_shifts:
    sql("update fleet.assignments set status='CANCELLED' where id in (" + ",".join(repr(x.split(':')[0]) for x in parked_shifts.split(",")) + ")")

try:
    print("1. Dépôt")
    s, r = call("POST", DRIVER_DOCS, {**piece("DRIVING_LICENSE", licenseCategories="B,C,D"), "fileUrl": ""}, DRV)
    check("sans justificatif → refusé", s == 422 and code(r) == "DOC_013", f"({s} {code(r)})")
    s, r = call("POST", DRIVER_DOCS, piece("DRIVING_LICENSE"), DRV)
    check("permis sans catégories → refusé", s == 422 and code(r) == "DOC_018", f"({s} {code(r)})")
    s, r = call("POST", DRIVER_DOCS, piece("DRIVING_LICENSE", licenseCategories="B", expiryDate=day(-1)), DRV)
    check("pièce déjà expirée → refusée", s >= 400 and code(r) == "DOC_007", f"({s} {code(r)})")
    s, r = call("POST", DRIVER_DOCS, piece("INSURANCE"), DRV)
    check("type étranger au dossier → refusé", s >= 400 and code(r) == "DOC_008", f"({s} {code(r)})")
    s, lic1 = call("POST", DRIVER_DOCS, piece("DRIVING_LICENSE", licenseCategories="B,C,D"), DRV)
    check("la chauffeuse dépose son permis : en attente", s == 201 and lic1.get("status") == "PENDING", f"({s} {code(lic1)})")
    notif = sql(f"select count(*) from fleet.notifications where user_id='{DUPONT}' and data like '%{lic1['id']}%'")
    check("le gestionnaire est prévenu du dépôt", notif == "1", f"({notif})")
    s, r = call("POST", DRIVER_DOCS, piece("DRIVING_LICENSE", licenseCategories="B"), DRV)
    check("second dépôt de la même pièce → refusé", s == 409 and code(r) == "DOC_015", f"({s} {code(r)})")
    s, r = trip()
    check("permis en attente : ne compte pas", s == 422 and code(r) == "DRV_009", f"({s} {code(r)})")

    print("2. Contrôle")
    s, r = call("POST", f"/api/v1/documents/{lic1['id']}/approve", token=DRV)
    check("la chauffeuse ne valide pas sa propre pièce", s == 403, f"({s})")
    s, q = call("GET", "/api/v1/documents/pending", token=MGR)
    check("file de contrôle du gestionnaire", s == 200 and lic1["id"] in [d["id"] for d in q], f"({s})")
    s, r = call("POST", f"/api/v1/documents/{lic1['id']}/reject", {"reason": " "}, MGR)
    check("rejet sans motif → refusé", s in (400, 422), f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/documents/{lic1['id']}/reject", {"reason": "Illisible"}, MGR)
    check("rejet motivé", s == 200 and r.get("status") == "REJECTED" and r.get("rejectionReason") == "Illisible", f"({s} {code(r)})")
    notif = sql(f"select message from fleet.notifications where user_id='{BELLA}' and data like '%{lic1['id']}%'")
    check("la chauffeuse connaît le motif", "Illisible" in notif, f"({notif})")
    s, items = call("GET", f"{DRIVER_DOCS}/checklist", token=DRV)
    check("checklist : permis rejeté", s == 200 and state_of(items, "DRIVING_LICENSE") == "REJECTED", f"({s})")
    s, lic2 = call("POST", DRIVER_DOCS, piece("DRIVING_LICENSE", licenseCategories="B"), DRV)
    check("nouveau dépôt : version 2", s == 201 and lic2.get("version") == 2, f"({s} {code(lic2)})")
    s, r = call("PUT", f"/api/v1/documents/{lic2['id']}", {"docNumber": "ESSAI-CORRIGE"}, DRV)
    check("correction tant qu'elle est en attente", s == 200 and r.get("docNumber") == "ESSAI-CORRIGE", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/documents/{lic2['id']}/approve", {"licenseCategories": "B,C,D"}, MGR)
    check("approuvée, catégories corrigées par le contrôleur", s == 200 and r.get("status") == "VALID"
          and r.get("current") and r.get("licenseCategories") == "B,C,D", f"({s} {code(r)})")
    s, r = call("PUT", f"/api/v1/documents/{lic2['id']}", {"docNumber": "TRICHE"}, DRV)
    check("pièce approuvée figée", s == 409 and code(r) == "DOC_016", f"({s} {code(r)})")
    s, r = call("POST", f"/api/v1/documents/{lic2['id']}/approve", token=MGR)
    check("double approbation → refusée", s == 409 and code(r) == "DOC_016", f"({s} {code(r)})")

    print("3. Renouvellement")
    s, lic3 = call("POST", DRIVER_DOCS, piece("DRIVING_LICENSE", licenseCategories="B,C,D", expiryDate=day(900)), DRV)
    s2, items = call("GET", f"{DRIVER_DOCS}/checklist", token=DRV)
    line = next(i for i in items if i["docType"] == "DRIVING_LICENSE")
    check("renouvellement en attente : l'ancien reste en vigueur", s == 201 and line["state"] == "OK" and line["renewalPending"],
          f"({s} {line['state']})")
    s, r = call("POST", f"/api/v1/documents/{lic3['id']}/approve", token=MGR)
    s2, old = call("GET", f"/api/v1/documents/{lic2['id']}", token=MGR)
    check("approuvé : l'ancienne version passe à l'historique", s == 200 and r.get("current") and old.get("status") == "REPLACED",
          f"({s} {old.get('status')})")
    s, hist = call("GET", f"{DRIVER_DOCS}/history", token=MGR)
    check("historique complet (3 versions)", s == 200 and len([d for d in hist if d["docType"] == "DRIVING_LICENSE"]) == 3, f"({s})")
    s, cur = call("GET", DRIVER_DOCS, token=DRV)
    check("le dossier ne montre que la version en vigueur",
          [d["id"] for d in cur["content"] if d["docType"] == "DRIVING_LICENSE"] == [lic3["id"]], f"({s})")

    print("4. Exploitation et politique d'entreprise")
    s, r = trip()
    check("véhicule sans pièces → trajet refusé", s == 422 and code(r) == "VHC_013", f"({s} {code(r)})")
    s, r = call("POST", VEHICLE_DOCS, piece("INSURANCE"), DRV)
    check("une chauffeuse ne dépose pas pour un véhicule", s == 403, f"({s})")
    vehicle_docs = {}
    for doc in ("INSURANCE", "TECHNICAL_CONTROL"):
        _, d = call("POST", VEHICLE_DOCS, piece(doc), MGR)
        call("POST", f"/api/v1/documents/{d['id']}/approve", token=MGR)
        vehicle_docs[doc] = d["id"]
    s, r = call("POST", VEHICLE_DOCS, piece("REGISTRATION", expiryDate=None), MGR)
    check("carte grise sans échéance acceptée", s == 201, f"({s} {code(r)})")
    s, x = trip()
    check("carte grise en attente : encore refusé", s == 422 and "carte grise" in str(x.get("detail")), f"({s} {x.get('detail')})")
    call("POST", f"/api/v1/documents/{r['id']}/approve", token=MGR)
    s, x = trip()
    check("dossier complet : trajet planifié", s == 201, f"({s} {code(x)})")
    drop_trips()
    s, r = call("PUT", "/api/v1/documents/policies/DRIVER/MEDICAL_CERT", {"mandatory": True}, MGR)
    check("le gestionnaire ne fixe pas la politique", s == 403, f"({s})")
    s, r = call("PUT", "/api/v1/documents/policies/VEHICLE/INSURANCE", {"mandatory": False}, ADMIN)
    check("socle légal non désactivable", s == 422 and code(r) == "DOC_020", f"({s} {code(r)})")
    s, r = call("PUT", "/api/v1/documents/policies/DRIVER/MEDICAL_CERT", {"mandatory": True}, ADMIN)
    check("l'administrateur exige la visite médicale", s == 204, f"({s} {code(r)})")
    s, cat = call("GET", "/api/v1/documents/catalog", token=DRV)
    entry = next(e for e in cat if e["targetType"] == "DRIVER" and e["docType"] == "MEDICAL_CERT")
    check("le catalogue reflète la politique", entry["mandatory"], f"({entry})")
    s, r = trip()
    check("visite médicale manquante → trajet refusé", s == 422 and code(r) == "DRV_021", f"({s} {code(r)})")
    _, med = call("POST", DRIVER_DOCS, piece("MEDICAL_CERT"), DRV)
    call("POST", f"/api/v1/documents/{med['id']}/approve", token=MGR)
    s, r = trip()
    check("visite médicale approuvée : trajet planifié", s == 201, f"({s} {code(r)})")
    drop_trips()

    print("5. Lecture restreinte")
    file_name = lic3["fileUrl"].rsplit("/", 1)[1]
    s, r = call("GET", DRIVER_DOCS, token=OTHER)
    check("un autre chauffeur ne lit pas le dossier", s == 403, f"({s})")
    s, r = call("POST", DRIVER_DOCS, piece("ID_CARD"), OTHER)
    check("un autre chauffeur ne dépose pas pour elle", s == 403, f"({s})")
    s, r = call("GET", f"/api/v1/files/{file_name}", token=OTHER)
    check("un autre chauffeur ne télécharge pas le fichier", s == 403, f"({s})")
    # 404 : droit accordé, le fichier d'essai n'existe pas sur le disque.
    s, r = call("GET", f"/api/v1/files/{file_name}", token=DRV)
    check("la chauffeuse accède à son fichier", s == 404, f"({s})")
    s, r = call("GET", f"/api/v1/files/{file_name}", token=MGR)
    check("son gestionnaire aussi", s == 404, f"({s})")
    s, r = call("POST", f"/api/v1/drivers/{uid('driver.nana')}/documents",
                {**piece("ID_CARD"), "fileUrl": lic3["fileUrl"]}, OTHER)
    check("rattacher le fichier d'autrui pour le lire → refusé", s == 409 and code(r) == "DOC_014", f"({s} {code(r)})")
    s, r = call("GET", VEHICLE_DOCS, token=DRV)
    check("papiers d'un véhicule qu'elle ne conduit pas → refusé", s == 403, f"({s})")
    sql(f"update fleet.drivers set assigned_vehicle_id='{V1}' where user_id='{BELLA}'")
    s, r = call("GET", VEHICLE_DOCS, token=DRV)
    check("papiers du véhicule qu'elle conduit → lisibles", s == 200 and len(r["content"]) == 3, f"({s})")
    sql(f"update fleet.drivers set assigned_vehicle_id=NULL where user_id='{BELLA}'")

    print("6. Dossier gestionnaire, archivage, conformité")
    s, mdoc = call("POST", MANAGER_DOCS, piece("ID_CARD"), MGR)
    check("le gestionnaire dépose sa pièce", s == 201, f"({s} {code(mdoc)})")
    s, r = call("POST", f"/api/v1/documents/{mdoc['id']}/approve", token=MGR)
    check("il ne valide pas son propre dossier", s == 403, f"({s})")
    s, r = call("GET", MANAGER_DOCS, token=DRV)
    check("un chauffeur ne lit pas le dossier d'un gestionnaire", s == 403, f"({s})")
    s, r = call("POST", f"/api/v1/documents/{mdoc['id']}/approve", token=ADMIN)
    check("l'administrateur le valide", s == 200 and r.get("status") == "VALID", f"({s} {code(r)})")
    s, r = call("DELETE", f"/api/v1/documents/{vehicle_docs['INSURANCE']}", token=MGR)
    check("archiver sans motif → refusé", s == 422 and code(r) == "DOC_017", f"({s} {code(r)})")
    s, r = call("DELETE", f"/api/v1/documents/{vehicle_docs['INSURANCE']}?reason={urllib.parse.quote('Contrat résilié')}", token=MGR)
    s2, x = trip()
    check("assurance archivée : trajet de nouveau refusé", s == 204 and s2 == 422 and code(x) == "VHC_013", f"({s} {s2} {code(x)})")
    s, hist = call("GET", f"{VEHICLE_DOCS}/history", token=MGR)
    arch = next(d for d in hist if d["id"] == vehicle_docs["INSURANCE"])
    check("elle reste dans l'historique avec son motif", arch["status"] == "ARCHIVED" and arch["archiveReason"] == "Contrat résilié", f"({arch['status']})")
    s, rep = call("GET", "/api/v1/documents/compliance-report", token=MGR)
    s2, miss = call("GET", "/api/v1/documents/missing", token=MGR)
    check("taux calculé sur les pièces exigées (pas 100 % avec des trous)", s == 200 and rep["missingDocuments"] > 0
          and rep["complianceRate"] < 100 and rep["totalTargets"] > 0, f"({rep})")
    check("liste des pièces manquantes", s2 == 200 and any(m["entityId"] == V1 and m["docType"] == "INSURANCE" for m in miss), f"({s2})")
    s, rep_admin = call("GET", "/api/v1/documents/compliance-report", token=ADMIN)
    check("l'administrateur voit aussi les dossiers des gestionnaires", s == 200 and rep_admin["totalTargets"] > rep["totalTargets"],
          f"({rep_admin['totalTargets']} / {rep['totalTargets']})")
finally:
    drop_trips()
    sql(f"""
        delete from fleet.notifications where created_at > '{last_notif}' and data like '%DOCUMENT%';
        update fleet.documents set superseded_by = null where file_url like '/api/v1/files/essai-059-%';
        delete from fleet.documents where file_url like '/api/v1/files/essai-059-%';
        delete from fleet.document_policies where company_id = '{COMPANY}';
        update fleet.drivers set assigned_vehicle_id = {f"'{saved_bella}'" if saved_bella else 'NULL'} where user_id='{BELLA}';
    """)
    print(f"  ↺ données restaurées — {sum(results)}/{len(results)} contrôles OK")
    for x in (parked_shifts.split(",") if parked_shifts else []):
        sql(f"update fleet.assignments set status='{x.split(':')[1]}' where id='{x.split(':')[0]}'")
    for doc_id, target in parked_targets.items():
        sql(f"update fleet.documents set target_id='{target}' where id='{doc_id}'")
    if parked_docs:
        sql("update fleet.documents d set is_current=true where d.id in (" + ",".join(repr(x) for x in parked_docs.split(","))
            + ") and not exists (select 1 from fleet.documents o where o.target_id=d.target_id and o.doc_type=d.doc_type and o.is_current)")
