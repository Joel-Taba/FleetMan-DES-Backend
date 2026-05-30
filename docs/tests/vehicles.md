# 🚜 Rapport de Test : Module Véhicules (V1.0)

Ce document décrit les étapes pour valider la fusion Gabriel (Ressources/Finance) + Hassana (Geofence Sync).

## 🛠️ PRÉ-REQUIS (Initialisation)
1. **Lancer l'app localement** (`./run_local.sh`).
2. **Login Super Admin** : Récupère le token pour les ressources Admin.
3. **Login Fleet Manager** : Récupère le token pour la gestion du parc.
4. **Créer une Flotte** : `POST /api/v1/fleets` -> Note l'`id` (ex: `FLEET_ID`).

---

## 🏗️ PHASE 1 : Souveraineté des Ressources (Admin)
*Vérifier que l'Admin peut préparer le catalogue local.*

1. **Ajouter une Marque** : `POST /api/v1/admin/resources/manufacturers`
   ```json
   { "code": "TOYOTA_M", "label": "Toyota Motors", "description": "Local Ref" }
   ```
2. **Ajouter un Carburant** : `POST /api/v1/admin/resources/fuel-types`
   ```json
   { "code": "SUPER_95", "label": "Super 95", "description": "Essence local" }
   ```
3. **Vérification en DB (Terminal)** :
   ```sql
   docker exec -it fleet-management-db psql -U fleet_admin -d yowyob_db -c "SELECT * FROM fleet.manufacturers;"
   ```

---

## 🚗 PHASE 2 : Cycle de Vie & Double Sync (Manager)
*Vérifier la création DISTANTE (Pynfi) + GEOFENCE + LOCALE.*

1. **Création du Véhicule** : `POST /api/v1/vehicles`
   * **Note :** Utilise l'`id` du `vehicle-type` généré au démarrage (ex: CAR).
   ```json
   {
     "brand": "Toyota",
     "model": "Yaris 2026",
     "licensePlate": "LT-001-YOW",
     "vehicleTypeId": "11111111-1111-1111-1111-111111111111",
     "manufacturerName": "Toyota Motors",
     "sizeName": "Compact",
     "typeName": "Personnel",
     "fuelType": "Super 95",
     "manufacturingYear": 2026,
     "color": "Rouge"
   }
   ```
2. **Vérification de l'Agrégation** : `GET /api/v1/vehicles/{id}`
   * **Attendu :** Un JSON massif contenant les infos de Pynfi ET les blocs `financialParameters`, `maintenanceParameters` (vides) et `operationalParameters`.
3. **Vérification DB (Lien Geofence)** :
   ```sql
   docker exec -it fleet-management-db psql -U fleet_admin -d yowyob_db -c "SELECT license_plate, geofence_remote_id FROM fleet.vehicles WHERE license_plate = 'LT-001-YOW';"
   ```
   * *Si `geofence_remote_id` n'est pas null, la synchro de Hassana a fonctionné !*

---

## 💰 PHASE 3 : Paramètres Métiers (Manager)
*Vérifier que nos tables locales 1-1 se remplissent.*

1. **Mise à jour Finance** : `PUT /api/v1/vehicles/{id}/financial-parameters`
   ```json
   {
     "insuranceNumber": "INS-999-DOUALA",
     "costPerKm": 125.5,
     "depreciationRate": 15
   }
   ```
2. **Mise à jour Maintenance** : `PUT /api/v1/vehicles/{id}/maintenance-parameters`
   ```json
   {
     "engineStatus": "OK",
     "batteryHealth": 98,
     "maintenanceStatus": "UP_TO_DATE"
   }
   ```
3. **Vérification DB** :
   ```sql
   docker exec -it fleet-management-db psql -U fleet_admin -d yowyob_db -c "SELECT * FROM fleet.financial_parameters;"
   ```

---

## 📡 PHASE 4 : Opérationnel (Driver)
*Vérifier que le chauffeur peut mettre à jour la télémétrie.*

1. **Mise à jour Télémétrie** : `PATCH /api/v1/vehicles/{id}/operational`
   ```json
   {
     "fuelLevel": "75%",
     "odometerReading": "12500.50"
   }
   ```
2. **Lecture Temps Réel** : `GET /api/v1/vehicles/{id}/operational`
   * **Attendu :** Doit retourner les 75% et le kilométrage avec le timestamp `now`.

---

## 🔍 PHASE 5 : Les Lookups (Souveraineté)
*Vérifier que le manager voit nos données locales.*

1. **Endpoint** : `GET /api/v1/vehicles/lookup/manufacturers`
   * **Attendu :** Doit retourner "Toyota Motors" (créé en Phase 1) et non les données de Pynfi.

---

## 🧹 PHASE 6 : Suppression
1. **Endpoint** : `DELETE /api/v1/vehicles/{id}`
2. **Vérification Cascade DB** :
   ```sql
   -- Cette requête doit renvoyer 0 lignes partout
   docker exec -it fleet-management-db psql -U fleet_admin -d yowyob_db -c "SELECT count(*) FROM fleet.vehicles; SELECT count(*) FROM fleet.financial_parameters;"
   ```

---
