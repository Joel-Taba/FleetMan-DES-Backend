#!/usr/bin/env python3
"""Données de démonstration réalistes pour l'entreprise de manager.dupont (flottes Yaoundé, Douala, Garoua).

Ajoute des véhicules et des chauffeurs, cinq mois d'historique (trajets, pleins, entretiens, dépenses,
incidents, indicateurs mensuels, scores), des trajets et postes à venir, et remplace les pièces de démo
factices (local://) par de vrais fichiers PDF / PNG servis par /api/v1/files/.

Rejouable : tout ce qui est créé porte un identifiant commençant par 5eed ; --reset le supprime d'abord.
Les chauffeurs ajoutés n'ont pas de compte Kernel (affichage seulement, pas de connexion).
Usage : python3 scripts/seed_demo_data.py [--reset-only]
"""
import datetime as dt
import os
import random
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UPLOADS = os.path.join(ROOT, "uploads")
random.seed(2026)

MANAGER = "e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb"
COMPANY = "96b87460-6179-483d-a6d5-9cbcacd9d06d"
TENANT = "f5b814d9-766e-4c87-91ec-d6c8e32cb56c"
ENTREPRISE_PLAN = "55000003-0000-4000-8000-000000000003"
YDE, DLA, GAR = ("b0000001-0000-4000-8000-000000000001", "b0000002-0000-4000-8000-000000000002",
                 "b0000003-0000-4000-8000-000000000003")
TYPES = {"TRUCK": "f5394639-dd66-4f33-9eb3-f9cce88be586", "PICKUP": "a3f1743c-54ff-4cce-a20e-88547c66d7ec",
         "VAN": "f01c41bf-2a4c-49b9-ac6a-b2b0c461eb79", "SUV": "12fc216a-4ad9-49e7-aac5-76320f55f2fe",
         "BUS": "08ff3d4a-d5f6-4252-ab93-f63ad81ba7c1"}
TODAY = dt.date(2026, 11, 6)
HISTORY_START = dt.date(2026, 6, 2)
DIESEL_PRICE = 828

_counters = {}


def sid(kind):
    """Identifiant de démo : 5eedKKKK-NNNN-4000-8000-NNNNNNNNNNNN."""
    _counters[kind] = _counters.get(kind, 0) + 1
    n = _counters[kind]
    return f"5eed{kind:04x}-{n:04x}-4000-8000-{n:012x}"


def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "TRUE" if v else "FALSE"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


def insert(table, row):
    cols = ", ".join(row.keys())
    vals = ", ".join(q(v) for v in row.values())
    return f"INSERT INTO fleet.{table} ({cols}) VALUES ({vals});"


def psql(sql_text):
    out = subprocess.run(["docker", "exec", "-i", "fleet-management-db", "sh", "-c",
                          'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'],
                         input=sql_text, capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(out.stderr.strip()[-2000:])
    return out.stdout.strip()


RESET_SQL = """
BEGIN;
DELETE FROM fleet.incident_cases WHERE incident_id::text LIKE '5eed%';
DELETE FROM fleet.expenses WHERE id::text LIKE '5eed%';
DELETE FROM fleet.incidents WHERE id::text LIKE '5eed%';
DELETE FROM fleet.fuel_recharges WHERE id::text LIKE '5eed%';
DELETE FROM fleet.maintenances WHERE id::text LIKE '5eed%';
DELETE FROM fleet.assignments WHERE id::text LIKE '5eed%';
DELETE FROM fleet.trips WHERE id::text LIKE '5eed%';
DELETE FROM fleet.kpi_snapshots WHERE id::text LIKE '5eed%';
DELETE FROM fleet.driver_scores WHERE id::text LIKE '5eed%';
DELETE FROM fleet.budgets WHERE id::text LIKE '5eed%';
DELETE FROM fleet.documents WHERE id::text LIKE '5eed%';
DELETE FROM fleet.operational_parameters WHERE id::text LIKE '5eed%';
UPDATE fleet.vehicles SET current_driver_id = NULL WHERE current_driver_id::text LIKE '5eed%';
DELETE FROM fleet.drivers WHERE user_id::text LIKE '5eed%';
DELETE FROM fleet.users WHERE id::text LIKE '5eed%';
DELETE FROM fleet.vehicles WHERE id::text LIKE '5eed%';
COMMIT;
"""

# ── Fichiers de pièces (vrais PNG / PDF) ─────────────────────────────────────

TITLES = {"INSURANCE": "ATTESTATION D'ASSURANCE", "TECHNICAL_CONTROL": "CERTIFICAT DE VISITE TECHNIQUE",
          "REGISTRATION": "CARTE GRISE", "DRIVING_LICENSE": "PERMIS DE CONDUIRE"}
ISSUERS = {"INSURANCE": ["Activa Assurances", "AXA Cameroun", "Chanas Assurances", "Saham Assurance"],
           "TECHNICAL_CONTROL": ["Centre de contrôle technique de Douala", "SGS Contrôle Yaoundé", "Contrôle technique Garoua"],
           "REGISTRATION": ["Ministère des Transports"], "DRIVING_LICENSE": ["MINTRANSPORT"]}


def font(size, bold=False):
    for path in ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                 "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"]:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def make_document_file(name, doc_type, lines, as_pdf):
    """Pièce lisible : bandeau, titre, champs, cachet. Enregistrée en PDF ou en PNG dans uploads/."""
    w, h = 1240, 1754 if as_pdf else 900
    img = Image.new("RGB", (w, h), "white")
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w, 150], fill=(31, 111, 176))
    d.text((60, 45), "RÉPUBLIQUE DU CAMEROUN", fill="white", font=font(28, True))
    d.text((60, 90), "Paix - Travail - Patrie", fill=(220, 235, 250), font=font(22))
    d.text((60, 210), TITLES[doc_type], fill=(16, 36, 58), font=font(46, True))
    d.line([60, 285, w - 60, 285], fill=(31, 111, 176), width=4)
    y = 330
    for label, value in lines:
        d.text((60, y), label, fill=(90, 105, 120), font=font(26))
        d.text((480, y), str(value), fill=(16, 36, 58), font=font(30, True))
        y += 70
    cx, cy = w - 230, y + 80
    d.ellipse([cx - 120, cy - 120, cx + 120, cy + 120], outline=(200, 40, 40), width=6)
    d.text((cx - 80, cy - 18), "CERTIFIÉ", fill=(200, 40, 40), font=font(34, True))
    d.text((60, h - 90), "Document de démonstration FleetMan", fill=(150, 160, 170), font=font(22))
    os.makedirs(UPLOADS, exist_ok=True)
    path = os.path.join(UPLOADS, name)
    if as_pdf:
        img.save(path, "PDF", resolution=150)
    else:
        img.save(path, "PNG", optimize=True)
    return os.path.getsize(path)


def doc_row(target_type, target_id, doc_type, number, issue, expiry, label_lines, as_pdf, categories=None, review="APPROVED"):
    ext = "pdf" if as_pdf else "png"
    doc_id = sid(0x0D0C)
    name = f"demo-{doc_type.lower()}-{doc_id[-6:]}.{ext}"
    size = make_document_file(name, doc_type, label_lines, as_pdf)
    now = dt.datetime.combine(issue, dt.time(9, 0))
    return insert("documents", {
        "id": doc_id, "target_type": target_type, "target_id": target_id, "doc_type": doc_type, "doc_number": number,
        "license_categories": categories, "issuer": random.choice(ISSUERS[doc_type]), "issue_date": issue.isoformat(),
        "expiry_date": expiry.isoformat() if expiry else None, "file_url": f"/api/v1/files/{name}",
        "file_original_name": f"{TITLES[doc_type].title()}.{ext}", "file_mime_type": "application/pdf" if as_pdf else "image/png",
        "file_size_bytes": size, "review_status": review, "reviewed_by": MANAGER if review == "APPROVED" else None,
        "reviewed_at": (now + dt.timedelta(days=1)).isoformat(sep=" ") if review == "APPROVED" else None,
        "version": 1, "is_current": review == "APPROVED", "submitted_by": MANAGER, "created_at": now.isoformat(sep=" "),
        "updated_at": now.isoformat(sep=" "), "tenant_id": TENANT})


# ── Référentiel de démo ─────────────────────────────────────────────────────

CITIES = {
    "Douala": (4.0511, 9.7679), "Yaoundé": (3.8480, 11.5021), "Kribi": (2.9373, 9.9077), "Limbé": (4.0186, 9.2043),
    "Bafoussam": (5.4778, 10.4176), "Edéa": (3.8000, 10.1333), "Ebolowa": (2.9000, 11.1500), "Bertoua": (4.5773, 13.6846),
    "Mbalmayo": (3.5167, 11.5000), "Garoua": (9.3017, 13.3921), "Maroua": (10.5910, 14.3159),
    "Ngaoundéré": (7.3277, 13.5847), "Guider": (9.9333, 13.9500), "Nkongsamba": (4.9547, 9.9404),
}
ROUTES = {
    YDE: [("Yaoundé", "Mbalmayo", 50), ("Yaoundé", "Ebolowa", 160), ("Yaoundé", "Bafoussam", 295), ("Yaoundé", "Bertoua", 340),
          ("Yaoundé", "Douala", 245), ("Yaoundé", "Edéa", 180)],
    DLA: [("Douala", "Edéa", 65), ("Douala", "Limbé", 75), ("Douala", "Kribi", 160), ("Douala", "Nkongsamba", 145),
          ("Douala", "Yaoundé", 245), ("Douala", "Bafoussam", 270)],
    GAR: [("Garoua", "Guider", 100), ("Garoua", "Maroua", 210), ("Garoua", "Ngaoundéré", 290)],
}
MISSIONS = ["Livraison de ciment", "Approvisionnement en boissons", "Transport de matériel BTP", "Livraison de produits pharmaceutiques",
            "Tournée de distribution", "Transfert d'équipements", "Collecte de marchandises", "Livraison agroalimentaire"]

NEW_VEHICLES = [
    (GAR, "GR-301-AA", "Mercedes", "Actros 2643", 2022, "Blanc", "TRUCK", 400, 32.0, 2, 41250),
    (GAR, "GR-302-AB", "Renault", "Kerax 440", 2020, "Jaune", "TRUCK", 350, 34.5, 2, 98740),
    (GAR, "GR-303-AC", "Toyota", "Hilux 2.8", 2023, "Gris", "PICKUP", 80, 9.8, 5, 18320),
    (GAR, "GR-304-AD", "Toyota", "Land Cruiser 79", 2021, "Beige", "SUV", 130, 12.5, 5, 64410),
    (YDE, "CE-781-YA", "Hino", "500 Series", 2022, "Blanc", "TRUCK", 200, 22.0, 3, 52300),
    (YDE, "CE-782-YB", "Toyota", "Coaster", 2019, "Blanc", "BUS", 95, 14.0, 30, 143900),
    (DLA, "LT-551-DA", "Volvo", "FH 460", 2023, "Bleu", "TRUCK", 450, 30.5, 2, 27600),
    (DLA, "LT-552-DB", "Mitsubishi", "L200", 2022, "Rouge", "PICKUP", 75, 9.2, 5, 39850),
]
NEW_DRIVERS = [
    (GAR, "Moussa", "Abdoulaye", "CM-GAR-48211", "1986-03-14", "Quartier Roumdé Adjia, Garoua"),
    (GAR, "Hamidou", "Bello", "CM-GAR-51902", "1990-11-02", "Quartier Lopéré, Garoua"),
    (GAR, "Aïssatou", "Oumarou", "CM-GAR-60377", "1993-07-21", "Quartier Marouaré, Garoua"),
    (GAR, "Ibrahim", "Saïdou", "CM-GAR-44718", "1984-01-09", "Quartier Yelwa, Garoua"),
    (YDE, "Christelle", "Ngono", "CM-YDE-72530", "1991-05-30", "Bastos, Yaoundé"),
    (YDE, "Rodrigue", "Atangana", "CM-YDE-69044", "1988-09-17", "Mvog-Mbi, Yaoundé"),
    (DLA, "Josiane", "Ekane", "CM-DLA-80412", "1994-12-03", "Bonamoussadi, Douala"),
    (DLA, "Thierry", "Ndjock", "CM-DLA-77365", "1987-04-26", "Akwa, Douala"),
]


def main():
    print("Réinitialisation des données de démo précédentes…")
    psql(RESET_SQL)
    if "--reset-only" in sys.argv:
        return
    sql = ["BEGIN;"]

    # Entreprise en offre Entreprise : tous les modules (opérations, indicateurs) sont visibles.
    sql.append(f"UPDATE fleet.companies SET plan_id = '{ENTREPRISE_PLAN}', subscription_status = 'ACTIVE', "
               f"subscription_start = '2026-06-01', subscription_end = '2027-06-01', billing_cycle = 'ANNUAL', "
               f"legal_name = COALESCE(legal_name, 'Taba Logistique SARL'), sector = COALESCE(sector, 'Transport et logistique'), "
               f"city = COALESCE(city, 'Douala'), country = COALESCE(country, 'Cameroun') WHERE id = '{COMPANY}';")
    for fleet, phone, budget in [(YDE, "+237 222 21 45 10", 2500000), (DLA, "+237 233 42 18 77", 2200000), (GAR, "+237 222 27 31 05", 1600000)]:
        sql.append(f"UPDATE fleet.fleets SET phone_number = '{phone}', monthly_budget = {budget}, "
                   f"tenant_id = COALESCE(tenant_id, '{TENANT}') WHERE id = '{fleet}';")

    # Véhicules existants : caractéristiques complétées.
    existing = [r.split("|") for r in psql(
        "SELECT v.id, v.license_plate, v.fleet_id, v.status, COALESCE(op.mileage, 0) FROM fleet.vehicles v "
        "LEFT JOIN fleet.operational_parameters op ON op.vehicle_id = v.id "
        f"WHERE v.deleted_at IS NULL AND v.fleet_id IN ('{YDE}','{DLA}') ORDER BY v.license_plate").splitlines()]
    specs = {"Hilux": (80, 9.8, 5), "Transit": (80, 10.5, 3), "Corolla": (50, 6.5, 5), "Hiace": (70, 11.0, 15),
             "H1": (75, 10.0, 9), "Master": (100, 11.5, 3), "Actros": (400, 32.0, 2), "Sprinter": (75, 10.8, 3), "NPR": (100, 14.0, 3)}
    vehicles = []  # (id, plate, fleet, status, tank, conso, odometer)
    for vid, plate, fleet, status, mileage in existing:
        model = psql(f"SELECT model FROM fleet.vehicles WHERE id = '{vid}'")
        tank, conso, seats = specs.get(model, (80, 10.0, 3))
        sql.append(f"UPDATE fleet.vehicles SET fuel_type = COALESCE(fuel_type, 'DIESEL'), transmission_type = COALESCE(transmission_type, 'MANUAL'), "
                   f"tank_capacity = COALESCE(tank_capacity, {tank}), average_fuel_consumption = COALESCE(average_fuel_consumption, {conso}), "
                   f"total_seat_number = COALESCE(total_seat_number, {seats}) WHERE id = '{vid}';")
        start_km = max(float(mileage or 0) - 9000, 15000 + random.randint(0, 60000))
        vehicles.append([vid, plate, fleet, status, tank, conso, start_km])

    # Véhicules ajoutés.
    for fleet, plate, brand, model, year, color, vtype, tank, conso, seats, km in NEW_VEHICLES:
        vid = sid(0x0E01)
        status = "AVAILABLE"
        reason = None
        if plate == "GR-302-AB":
            status, reason = "IMMOBILIZED", "Boîte de vitesses hors service, en attente de pièce"
        sql.append(insert("vehicles", {
            "id": vid, "fleet_id": fleet, "manager_id": MANAGER, "vehicle_type_id": TYPES[vtype], "license_plate": plate,
            "brand": brand, "model": model, "manufacturing_year": year, "color": color, "status": status,
            "status_reason": reason, "status_changed_at": "2026-11-03 10:00:00" if reason else None,
            "created_at": "2026-05-20 09:00:00", "updated_at": "2026-11-01 09:00:00", "tenant_id": TENANT,
            "vehicle_serial_number": f"VF{random.randint(10**12, 10**13 - 1)}", "fuel_type": "DIESEL",
            "transmission_type": "MANUAL", "tank_capacity": tank, "total_seat_number": seats, "average_fuel_consumption": conso}))
        vehicles.append([vid, plate, fleet, status, tank, conso, km - 9000])

    # Chauffeurs ajoutés (sans compte Kernel : affichage seulement).
    drivers_by_fleet = {YDE: [], DLA: [], GAR: []}
    for row in psql("SELECT d.user_id, d.fleet_id, u.first_name || ' ' || u.last_name FROM fleet.drivers d JOIN fleet.users u ON u.id = d.user_id "
                    f"WHERE d.deleted_at IS NULL AND d.status = 'ACTIVE' AND d.fleet_id IN ('{YDE}','{DLA}') "
                    "AND u.username NOT LIKE 'test%' AND u.username NOT LIKE 'essai%'").splitlines():
        uid, fleet, name = row.split("|")
        drivers_by_fleet[fleet].append((uid, name))
    new_vehicle_ids = {v[1]: v[0] for v in vehicles}
    assign_plate = {"Moussa": "GR-301-AA", "Hamidou": "GR-303-AC", "Aïssatou": "GR-304-AD", "Christelle": "CE-781-YA",
                    "Rodrigue": "CE-782-YB", "Josiane": "LT-552-DB", "Thierry": "LT-551-DA"}
    driver_docs = []
    for i, (fleet, first, last, licence, birth, address) in enumerate(NEW_DRIVERS):
        uid = sid(0x0D01)
        username = f"demo.{first.lower().replace('ï', 'i')}.{last.lower()}"
        phone = f"+2376{77 + i % 3}{random.randint(100000, 999999)}"
        sql.append(insert("users", {"id": uid, "username": username, "email": f"{username}@fleetman.local", "first_name": first,
                                    "last_name": last, "is_active": True, "approval_status": "APPROVED", "tenant_id": TENANT, "phone": phone}))
        plate = assign_plate.get(first)
        vid = new_vehicle_ids.get(plate)
        sql.append(insert("drivers", {
            "user_id": uid, "fleet_id": fleet, "licence_number": licence, "status": "ACTIVE", "assigned_vehicle_id": vid,
            "created_at": "2026-05-25 09:00:00", "updated_at": "2026-05-25 09:00:00", "created_by": MANAGER, "tenant_id": TENANT,
            "hire_date": f"2026-0{random.randint(1, 5)}-{random.randint(10, 28)}", "birth_date": birth, "address": address,
            "emergency_contact_name": random.choice(["Marie", "Jean", "Fatima", "Paul", "Awa"]) + " " + last,
            "emergency_contact_phone": f"+2376{random.randint(50000000, 99999999)}", "onboarding_completed_at": "2026-05-26 10:00:00"}))
        if vid:
            sql.append(f"UPDATE fleet.vehicles SET current_driver_id = '{uid}' WHERE id = '{vid}';")
        drivers_by_fleet[fleet].append((uid, f"{first} {last}"))
        issue = dt.date(2019 + i % 5, 1 + i % 12, 10)
        driver_docs.append(doc_row("DRIVER", uid, "DRIVING_LICENSE", licence, issue, issue.replace(year=issue.year + 10),
                                   [("Nom", f"{last.upper()} {first}"), ("Né(e) le", birth), ("N° de permis", licence),
                                    ("Catégories", "B, C, CE"), ("Délivré le", issue.isoformat())], as_pdf=i % 2 == 0, categories="B,C,CE"))
    sql.extend(driver_docs)

    # Pièces des véhicules ajoutés (vraies pièces, deux cas d'alerte volontaires).
    for idx, (vid, plate, fleet, status, *_rest) in enumerate(vehicles):
        if not vid.startswith("5eed"):
            continue
        issue = dt.date(2026, 3 + idx % 5, 5 + idx)
        ins_exp = TODAY + dt.timedelta(days=18) if plate == "LT-552-DB" else issue + dt.timedelta(days=365)
        tc_exp = TODAY - dt.timedelta(days=9) if plate == "GR-302-AB" else issue + dt.timedelta(days=180 if idx % 2 else 365)
        common = [("Immatriculation", plate)]
        sql.append(doc_row("VEHICLE", vid, "INSURANCE", f"ASS-{plate.replace('-', '')}", issue, ins_exp,
                           common + [("Police n°", f"POL-{random.randint(100000, 999999)}"), ("Valable du", issue.isoformat()),
                                     ("Au", ins_exp.isoformat()), ("Garantie", "Responsabilité civile, vol, incendie")], as_pdf=idx % 2 == 0))
        sql.append(doc_row("VEHICLE", vid, "TECHNICAL_CONTROL", f"VT-{plate.replace('-', '')}", tc_exp - dt.timedelta(days=180), tc_exp,
                           common + [("Résultat", "Favorable"), ("Prochain contrôle", tc_exp.isoformat())], as_pdf=idx % 2 == 1))
        sql.append(doc_row("VEHICLE", vid, "REGISTRATION", f"CG-{plate.replace('-', '')}", dt.date(2023, 2, 14), None,
                           common + [("Genre", "Véhicule utilitaire"), ("Énergie", "Gazole"), ("Propriétaire", "Taba Logistique SARL")], as_pdf=True))

    # Pièces de démo existantes (local://) : un vrai fichier à la place.
    for row in psql("SELECT d.id, d.doc_type, d.target_type, d.target_id, COALESCE(d.doc_number, ''), d.issue_date, d.expiry_date, "
                    "COALESCE(v.license_plate, u.last_name || ' ' || u.first_name, '') FROM fleet.documents d "
                    "LEFT JOIN fleet.vehicles v ON v.id = d.target_id LEFT JOIN fleet.users u ON u.id = d.target_id "
                    "WHERE d.file_url LIKE 'local://%'").splitlines():
        did, doc_type, target_type, target_id, number, issue, expiry, who = row.split("|")
        as_pdf = random.random() < 0.6
        ext = "pdf" if as_pdf else "png"
        name = f"demo-{doc_type.lower()}-{did[:8]}.{ext}"
        label = "Immatriculation" if target_type == "VEHICLE" else "Titulaire"
        size = make_document_file(name, doc_type, [(label, who), ("Numéro", number or "-"), ("Délivré le", issue or "-"),
                                                   ("Expire le", expiry or "-")], as_pdf)
        sql.append(f"UPDATE fleet.documents SET file_url = '/api/v1/files/{name}', file_mime_type = '{'application/pdf' if as_pdf else 'image/png'}', "
                   f"file_size_bytes = {size}, file_original_name = '{TITLES.get(doc_type, 'Document').title().replace(chr(39), ' ')}.{ext}' WHERE id = '{did}';")

    # ── Historique : trajets, pleins, entretiens, dépenses ──────────────────
    vehicles_by_fleet = {YDE: [], DLA: [], GAR: []}
    for v in vehicles:
        vehicles_by_fleet[v[2]].append(v)
    fuel_since = {v[0]: 0.0 for v in vehicles}
    driver_name = {uid: name for lst in drivers_by_fleet.values() for uid, name in lst}
    monthly = {}  # (fleet, month) -> dict
    trip_no = 2000
    busy_until = {}
    expenses = []

    def month_key(fleet, day):
        return monthly.setdefault((fleet, day.replace(day=1)), {"km": 0, "trips": 0, "fuel_l": 0.0, "fuel_cost": 0, "maint": 0, "inc": 0, "inc_cost": 0})

    def add_expense(etype, amount, when, v, driver, desc, source="AUTO", source_id=None, status="APPROVED"):
        expenses.append(insert("expenses", {
            "id": sid(0x0E0E), "expense_type": etype, "amount": amount, "description": desc, "expense_date": when.isoformat(sep=" "),
            "status": status, "source_type": source, "source_id": source_id,
            "validated_at": (when + dt.timedelta(hours=5)).isoformat(sep=" ") if status != "PENDING" else None,
            "validated_by": MANAGER if status != "PENDING" else None,
            "rejection_reason": "Justificatif illisible, merci de renvoyer le ticket." if status == "REJECTED" else None,
            "vehicle_id": v[0], "vehicle_registration": v[1], "fleet_id": v[2], "manager_id": MANAGER,
            "driver_id": driver, "driver_full_name": driver_name.get(driver), "created_at": when.isoformat(sep=" "), "tenant_id": TENANT,
            "created_by": driver or MANAGER, "approval_level": "MANAGER", "over_budget": False}))

    day = HISTORY_START
    while day < TODAY:
        if day.weekday() < 6:  # du lundi au samedi
            for fleet, fleet_vehicles in vehicles_by_fleet.items():
                free_drivers = [d for d in drivers_by_fleet[fleet] if busy_until.get(d[0], dt.date.min) < day]
                random.shuffle(free_drivers)
                for v in fleet_vehicles:
                    vid, plate, _, status, tank, conso, odo = v
                    if busy_until.get(vid, dt.date.min) >= day or not free_drivers:
                        continue
                    if v[1] == "GR-302-AB" and day > dt.date(2026, 11, 2):
                        continue
                    if random.random() > (0.2 if fleet != GAR else 0.17):
                        continue
                    driver = free_drivers.pop()[0]
                    origin, dest, km_one = random.choice(ROUTES[fleet])
                    distance = km_one * 2 + random.randint(-6, 14)
                    hours = distance / 58 + random.uniform(1.0, 2.5)
                    start = dt.datetime.combine(day, dt.time(random.choice([5, 6, 6, 7, 7, 8]), random.choice([0, 15, 30, 45])))
                    end = start + dt.timedelta(hours=hours)
                    two_days = end.date() > day or hours > 13
                    if hours > 13:
                        end = dt.datetime.combine(day + dt.timedelta(days=1), dt.time(random.randint(15, 19), random.choice([0, 30])))
                    busy_until[vid] = busy_until[driver] = end.date()
                    consumed = round(distance * conso / 100, 1)
                    dep_fuel = round(min(tank, max(tank * 0.55, consumed + tank * 0.2)), 1)
                    trip_no += 1
                    tid = sid(0x07A1)
                    arrived = start + dt.timedelta(hours=hours / 2)
                    o_lat, o_lng = CITIES[origin]
                    d_lat, d_lng = CITIES[dest]
                    sql.append(insert("trips", {
                        "id": tid, "vehicle_id": vid, "driver_id": driver, "start_date": day.isoformat(), "end_date": end.date().isoformat(),
                        "start_time": start.time().isoformat(), "end_time": end.time().strftime("%H:%M:%S"), "status": "COMPLETED",
                        "distance_km": distance, "duration_minutes": int((end - start).total_seconds() // 60),
                        "created_at": (start - dt.timedelta(days=2)).isoformat(sep=" "), "updated_at": end.isoformat(sep=" "),
                        "created_by": MANAGER, "trip_code": f"TRJ-2026-{trip_no}", "fleet_id": fleet, "mission_object": random.choice(MISSIONS),
                        "mission_cost_currency": "XAF", "departure_location": f"Dépôt de {origin}", "departure_km_index": round(odo),
                        "departure_fuel_index": dep_fuel, "return_location": f"Dépôt de {origin}", "return_km_index": round(odo + distance),
                        "return_fuel_index": round(dep_fuel - consumed, 1), "return_registered_at": end.isoformat(sep=" "),
                        "computed_distance_km": distance, "computed_fuel_consumed": consumed,
                        "scheduled_return_datetime": (end + dt.timedelta(minutes=random.choice([-30, 0, 20, 45]))).isoformat(sep=" "),
                        "departure_lat": o_lat, "departure_lng": o_lng, "return_lat": o_lat, "return_lng": o_lng,
                        "departure_registered_at": start.isoformat(sep=" "), "tenant_id": TENANT, "driver_vehicle_linked": True,
                        "destination_location": dest, "destination_lat": d_lat, "destination_lng": d_lng, "arrived_at": arrived.isoformat(sep=" "),
                        "arrival_location": dest, "arrival_lat": d_lat, "arrival_lng": d_lng}))
                    v[6] = odo + distance
                    m = month_key(fleet, day)
                    m["km"] += distance
                    m["trips"] += 1
                    fuel_since[vid] += consumed
                    # Plein au retour quand le réservoir a bien baissé.
                    if fuel_since[vid] > tank * 0.6:
                        liters = round(min(tank, fuel_since[vid]), 1)
                        cost = round(liters * DIESEL_PRICE)
                        fid = sid(0x0F1E)
                        when = end + dt.timedelta(minutes=40)
                        station = random.choice(["TOTAL", "TOTAL", "SHELL", "OILIBYA", "OTHER"])
                        sql.append(insert("fuel_recharges", {
                            "id": fid, "quantity": liters, "price": cost, "recharge_date_time": when.isoformat(sep=" "),
                            "latitude": o_lat, "longitude": o_lng, "station_name": station, "vehicle_id": vid, "vehicle_registration": plate,
                            "driver_id": driver, "driver_full_name": driver_name.get(driver), "updated_at": when.isoformat(sep=" "),
                            "created_by": driver, "tenant_id": TENANT, "odometer_km": round(v[6]),
                            "station_label": f"{station.title() if station != 'OTHER' else 'Station'} {origin}"}))
                        add_expense("FUEL", cost, when, v, driver, f"Plein de {liters} L à {origin}", source_id=fid)
                        m["fuel_l"] += liters
                        m["fuel_cost"] += cost
                        fuel_since[vid] = 0.0
                    # Péage sur les grands axes.
                    if km_one >= 145 and random.random() < 0.35:
                        add_expense("TOLL", random.choice([500, 1000, 1500, 2000]) * 2, end - dt.timedelta(hours=1), v, driver,
                                    f"Péages {origin} - {dest}", source="MANUAL",
                                    status="PENDING" if day > TODAY - dt.timedelta(days=4) else "APPROVED")
        day += dt.timedelta(days=1)

    # Entretiens.
    maint_types = [("Vidange et filtres", "PREVENTIVE", 85000), ("Remplacement des plaquettes de frein", "CORRECTIVE", 120000),
                   ("Pneumatiques (4 pneus)", "CORRECTIVE", 480000), ("Révision des 60 000 km", "PREVENTIVE", 210000),
                   ("Réparation de la climatisation", "CORRECTIVE", 95000)]
    garages = {YDE: "Garage CFAO Yaoundé", DLA: "Garage Tractafric Douala", GAR: "Atelier Nord Mécanique Garoua"}
    for v in vehicles:
        for k in range(random.choice([1, 2, 2])):
            subject, mtype, base = random.choice(maint_types)
            when = dt.datetime.combine(HISTORY_START + dt.timedelta(days=random.randint(5, 150)), dt.time(8, 30))
            cost = base + random.randint(-10, 20) * 1000
            mid = sid(0x0A17)
            sql.append(insert("maintenances", {
                "id": mid, "subject": subject, "cost": cost, "date_time": when.isoformat(sep=" "), "report": "Intervention réalisée, véhicule remis en service.",
                "location_name": garages[v[2]], "vehicle_id": v[0], "vehicle_registration": v[1], "updated_at": when.isoformat(sep=" "),
                "created_by": MANAGER, "tenant_id": TENANT, "status": "COMPLETED", "maintenance_type": mtype, "urgent": False,
                "planned_at": (when - dt.timedelta(days=3)).isoformat(sep=" "), "started_at": when.isoformat(sep=" "),
                "completed_at": (when + dt.timedelta(hours=6)).isoformat(sep=" "), "requested_by": MANAGER}))
            add_expense("MAINTENANCE", cost, when + dt.timedelta(hours=6), v, None, subject, source_id=mid)
            month_key(v[2], when.date())["maint"] += cost
    by_plate = {v[1]: v for v in vehicles}
    for plate, subject, status, days, urgent in [("GR-302-AB", "Remplacement de la boîte de vitesses", "IN_PROGRESS", -3, True),
                                                 ("CE-781-YA", "Vidange et filtres", "PLANNED", 6, False),
                                                 ("LT-551-DA", "Révision des 30 000 km", "PLANNED", 12, False),
                                                 ("GR-303-AC", "Contrôle du train avant", "REQUESTED", 3, False)]:
        v = by_plate[plate]
        when = dt.datetime.combine(TODAY + dt.timedelta(days=days), dt.time(8, 0))
        sql.append(insert("maintenances", {
            "id": sid(0x0A17), "subject": subject, "cost": 650000 if urgent else None, "date_time": when.isoformat(sep=" "),
            "location_name": garages[v[2]], "vehicle_id": v[0], "vehicle_registration": v[1], "updated_at": TODAY.isoformat(),
            "created_by": MANAGER, "tenant_id": TENANT, "status": status, "maintenance_type": "CORRECTIVE" if urgent else "PREVENTIVE",
            "urgent": urgent, "planned_at": when.isoformat(sep=" "), "started_at": when.isoformat(sep=" ") if status == "IN_PROGRESS" else None,
            "requested_by": MANAGER}))

    # Dépenses manuelles diverses (lavage, amendes), dont quelques-unes à valider.
    for i in range(14):
        v = random.choice(vehicles)
        drv = random.choice(drivers_by_fleet[v[2]])[0] if drivers_by_fleet[v[2]] else None
        when = dt.datetime.combine(HISTORY_START + dt.timedelta(days=random.randint(0, 155)), dt.time(14, 0))
        etype, amount, desc = random.choice([("OTHER", 5000, "Lavage du véhicule"), ("FINE", 25000, "Amende pour stationnement gênant"),
                                             ("OTHER", 15000, "Achat de sangles d'arrimage"), ("OTHER", 8000, "Remplacement d'ampoule")])
        recent = when.date() > TODAY - dt.timedelta(days=10)
        add_expense(etype, amount, when, v, drv, desc, source="MANUAL", status="PENDING" if recent else ("REJECTED" if i == 3 else "APPROVED"))
    for desc, amount in [("Réparation de bâche", 35000), ("Frais de parking au port", 12000)]:
        v = random.choice(vehicles)
        add_expense("OTHER", amount, dt.datetime.combine(TODAY - dt.timedelta(days=1), dt.time(16, 0)), v,
                    random.choice(drivers_by_fleet[v[2]])[0], desc, source="MANUAL", status="PENDING")

    # Incidents.
    incidents = [("BREAKDOWN", "MEDIUM", "Crevaison sur la nationale, roue de secours montée.", "RESOLVED", 40, "CE-782-YB", 45000, "NONE"),
                 ("ACCIDENT", "HIGH", "Accrochage léger avec un taxi au carrefour Ndokoti, pare-choc avant endommagé.", "CLOSED", 75, "LT-552-DB", 280000, "THIRD_PARTY"),
                 ("TRAFFIC_VIOLATION", "LOW", "Excès de vitesse relevé par la gendarmerie à Edéa.", "CLOSED", 100, "DL-202-DD", 25000, "DRIVER"),
                 ("BREAKDOWN", "HIGH", "Boîte de vitesses bloquée en 3e, véhicule remorqué au garage.", "UNDER_INVESTIGATION", 4, "GR-302-AB", 650000, "UNDETERMINED"),
                 ("OTHER", "LOW", "Rétroviseur cassé pendant un chargement.", "REPORTED", 1, "GR-301-AA", 30000, "UNDETERMINED")]
    for itype, sev, desc, status, ago, plate, cost, resp in incidents:
        v = by_plate.get(plate)
        if not v:
            continue
        drv = next((d for d in drivers_by_fleet[v[2]]), (None, None))[0]
        when = dt.datetime.combine(TODAY - dt.timedelta(days=ago), dt.time(11, 20))
        iid = sid(0x01C1)
        lat, lng = CITIES[{YDE: "Yaoundé", DLA: "Douala", GAR: "Garoua"}[v[2]]]
        sql.append(insert("incidents", {
            "id": iid, "type": itype, "description": desc, "severity": sev, "incident_date_time": when.isoformat(sep=" "),
            "latitude": lat, "longitude": lng, "cost": cost if status in ("RESOLVED", "CLOSED") else None, "status": status,
            "reported_by": driver_name.get(drv), "resolved_at": (when + dt.timedelta(days=2)).isoformat(sep=" ") if status in ("RESOLVED", "CLOSED") else None,
            "vehicle_id": v[0], "vehicle_registration": v[1], "driver_id": drv, "driver_full_name": driver_name.get(drv),
            "updated_at": when.isoformat(sep=" "), "created_by": drv, "tenant_id": TENANT,
            "vehicle_immobilized": status == "UNDER_INVESTIGATION", "vehicle_drivable": status != "UNDER_INVESTIGATION"}))
        sql.append(insert("incident_cases", {
            "incident_id": iid, "location_label": f"Axe {v[1]}", "reported_by_id": drv, "handler_id": MANAGER if status != "REPORTED" else None,
            "estimated_cost": cost, "injuries": False, "injuries_count": 0, "third_party_involved": itype == "ACCIDENT",
            "third_party_name": "Taxi n° 4521" if itype == "ACCIDENT" else None, "insurance_status": "ACCEPTED" if itype == "ACCIDENT" else "NONE",
            "insurer_name": "Activa Assurances" if itype == "ACCIDENT" else None, "refund_amount": 200000 if itype == "ACCIDENT" else None,
            "root_cause": "Usure" if itype == "BREAKDOWN" and status != "UNDER_INVESTIGATION" else None,
            "corrective_action": "Contrôle des pneus avant chaque départ" if status in ("RESOLVED", "CLOSED") else None,
            "responsibility": resp, "acknowledged_at": (when + dt.timedelta(hours=2)).isoformat(sep=" ") if status != "REPORTED" else None,
            "closed_at": (when + dt.timedelta(days=5)).isoformat(sep=" ") if status == "CLOSED" else None, "tenant_id": TENANT}))
        if status in ("RESOLVED", "CLOSED"):
            add_expense("INCIDENT", cost, when + dt.timedelta(days=2), v, drv, f"Incident : {desc[:60]}", source_id=iid)
        m = month_key(v[2], when.date())
        m["inc"] += 1
        m["inc_cost"] += cost if status in ("RESOLVED", "CLOSED") else 0
    sql.extend(expenses)

    # Trajets à venir et postes de la semaine pour Garoua.
    for k, v in enumerate([x for x in vehicles if x[2] == GAR and x[3] == "AVAILABLE"]):
        drv = drivers_by_fleet[GAR][k % len(drivers_by_fleet[GAR])][0]
        for offset in range(0, 5):
            start_day = TODAY + dt.timedelta(days=offset)
            if start_day.weekday() == 6:
                continue
            sql.append(insert("assignments", {
                "id": sid(0x0A55), "fleet_id": GAR, "vehicle_id": v[0], "driver_id": drv,
                "start_datetime": f"{start_day} 07:00:00", "end_datetime": f"{start_day} 16:00:00", "status": "PENDING",
                "notes": "Tournée Garoua - Guider" if offset % 2 else "Livraisons en ville", "created_at": TODAY.isoformat(),
                "updated_at": TODAY.isoformat(), "tenant_id": TENANT, "acknowledged_at": f"{TODAY} 08:00:00" if offset < 2 else None}))
        origin, dest, km_one = ROUTES[GAR][k % 3]
        trip_no += 1
        when = TODAY + dt.timedelta(days=2 + k)
        if when.weekday() == 6:
            when += dt.timedelta(days=1)
        d_lat, d_lng = CITIES[dest]
        sql.append(insert("trips", {
            "id": sid(0x07A1), "vehicle_id": v[0], "driver_id": drv, "start_date": when.isoformat(), "start_time": "07:30:00",
            "status": "SCHEDULED", "created_at": TODAY.isoformat(), "updated_at": TODAY.isoformat(), "created_by": MANAGER,
            "trip_code": f"TRJ-2026-{trip_no}", "fleet_id": GAR, "mission_object": random.choice(MISSIONS), "mission_cost_currency": "XAF",
            "departure_location": f"Dépôt de {origin}", "scheduled_return_datetime": f"{when} 15:30:00", "tenant_id": TENANT,
            "driver_vehicle_linked": True, "destination_location": dest, "destination_lat": d_lat, "destination_lng": d_lng}))

    # Compteurs des véhicules ajoutés.
    for v in vehicles:
        if v[0].startswith("5eed"):
            sql.append(insert("operational_parameters", {"id": sid(0x0B0D), "vehicle_id": v[0], "status": True, "fuel_level": "60",
                                                         "mileage": round(v[6]), "odometer_reading": round(v[6]),
                                                         "timestamp": f"{TODAY} 07:00:00", "tenant_id": TENANT}))

    # Indicateurs mensuels par flotte (graphiques des pages flotte et indicateurs).
    for (fleet, month), m in sorted(monthly.items()):
        if month >= TODAY.replace(day=1) and m["trips"] == 0:
            continue
        nxt = (month + dt.timedelta(days=32)).replace(day=1)
        end = min(nxt - dt.timedelta(days=1), TODAY)
        total_cost = m["fuel_cost"] + m["maint"] + m["inc_cost"]
        sql.append(f"DELETE FROM fleet.kpi_snapshots WHERE entity_type = 'FLEET' AND entity_id = '{fleet}' AND period_type = 'MONTHLY' AND period_start = '{month}';")
        sql.append(insert("kpi_snapshots", {
            "id": sid(0x0C01), "fleet_id": fleet, "entity_type": "FLEET", "entity_id": fleet, "period_type": "MONTHLY",
            "period_start": month.isoformat(), "period_end": end.isoformat(), "total_km": m["km"], "total_trips": m["trips"],
            "total_driving_hours": round(m["km"] / 55, 1), "availability_rate": round(random.uniform(78, 94), 2),
            "total_fuel_cost": m["fuel_cost"], "total_fuel_liters": round(m["fuel_l"], 1), "total_maintenance_cost": m["maint"],
            "total_incident_cost": m["inc_cost"], "cost_per_km": round(total_cost / m["km"], 2) if m["km"] else None,
            "fuel_per_100km": round(m["fuel_l"] * 100 / m["km"], 2) if m["km"] else None, "total_incidents": m["inc"],
            "incident_rate": round(m["inc"] * 1000 / m["km"], 3) if m["km"] else None, "avg_driver_score": round(random.uniform(74, 88), 1),
            "doc_compliance_rate": round(random.uniform(86, 100), 1), "calculated_at": f"{end} 23:00:00", "tenant_id": TENANT}))

    # Budget du mois par flotte.
    for fleet, budget in [(YDE, 2500000), (DLA, 2200000), (GAR, 1600000)]:
        sql.append(f"DELETE FROM fleet.budgets WHERE scope = 'FLEET' AND entity_id = '{fleet}' AND budget_month = '{TODAY.replace(day=1)}';")
        sql.append(insert("budgets", {"id": sid(0x0B06), "scope": "FLEET", "entity_id": fleet, "manager_id": MANAGER,
                                      "budget_month": TODAY.replace(day=1).isoformat(), "amount": budget, "consumed": 0,
                                      "alert_level": "NORMAL", "alert_80_sent": False, "alert_100_sent": False,
                                      "created_at": f"{TODAY.replace(day=1)} 00:05:00", "updated_at": TODAY.isoformat(), "tenant_id": TENANT,
                                      "created_by": MANAGER}))

    # Scores des chauffeurs (octobre).
    for fleet, lst in drivers_by_fleet.items():
        for uid, _ in lst:
            parts = [round(random.uniform(60, 100), 1) for _ in range(5)]
            final = round(sum(parts) / 5, 1)
            badge = "EXCELLENCE" if final >= 90 else "GOOD" if final >= 80 else "SATISFACTORY" if final >= 70 else "WARNING"
            sql.append(f"DELETE FROM fleet.driver_scores WHERE driver_id = '{uid}' AND period_type = 'MONTHLY' AND period_start = '2026-10-01';")
            sql.append(insert("driver_scores", {
                "id": sid(0x05C0), "driver_id": uid, "fleet_id": fleet, "manager_id": MANAGER, "period_type": "MONTHLY",
                "period_start": "2026-10-01", "period_end": "2026-10-31", "incident_count": random.choice([0, 0, 0, 1]),
                "total_trips": random.randint(8, 18), "fuel_per_100km": round(random.uniform(10, 30), 1),
                "fleet_avg_fuel_per_100km": 18.5, "doc_compliance_rate": round(random.uniform(85, 100), 1),
                "abnormal_maintenance_count": 0, "completed_assignments": random.randint(14, 22), "no_show_assignments": random.choice([0, 0, 1]),
                "incident_score": parts[0], "fuel_score": parts[1], "compliance_score": parts[2], "punctuality_score": parts[3],
                "maintenance_score": parts[4], "final_score": final, "badge": badge, "calculated_at": "2026-11-01 02:00:00", "tenant_id": TENANT}))

    sql.append("COMMIT;")
    psql("\n".join(sql))
    # Budget consommé du mois = dépenses approuvées du mois sur la flotte.
    psql(f"""UPDATE fleet.budgets b SET consumed = COALESCE((SELECT SUM(e.amount) FROM fleet.expenses e
              WHERE e.fleet_id = b.entity_id AND e.status = 'APPROVED' AND e.expense_date >= b.budget_month), 0)
             WHERE b.id::text LIKE '5eed%';""")
    summary = psql("""SELECT (SELECT COUNT(*) FROM fleet.vehicles WHERE id::text LIKE '5eed%'), (SELECT COUNT(*) FROM fleet.drivers WHERE user_id::text LIKE '5eed%'),
                      (SELECT COUNT(*) FROM fleet.trips WHERE id::text LIKE '5eed%'), (SELECT COUNT(*) FROM fleet.fuel_recharges WHERE id::text LIKE '5eed%'),
                      (SELECT COUNT(*) FROM fleet.maintenances WHERE id::text LIKE '5eed%'), (SELECT COUNT(*) FROM fleet.expenses WHERE id::text LIKE '5eed%'),
                      (SELECT COUNT(*) FROM fleet.incidents WHERE id::text LIKE '5eed%'), (SELECT COUNT(*) FROM fleet.documents WHERE id::text LIKE '5eed%'),
                      (SELECT COUNT(*) FROM fleet.documents WHERE file_url LIKE 'local://%')""")
    v, d, t, f, m, e, i, docs, fake = summary.split("|")
    print(f"Véhicules +{v}, chauffeurs +{d}, trajets +{t}, pleins +{f}, entretiens +{m}, dépenses +{e}, incidents +{i}, pièces +{docs}; "
          f"pièces factices restantes : {fake}")


if __name__ == "__main__":
    main()
