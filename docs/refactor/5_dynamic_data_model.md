# 🔄 Modèle des Objets Dynamiques & Statiques

**Projet :** Fleet Management System
**Version :** 1.1 (Renommage Rôles)
**Date :** 18 Janvier 2026

Ce document définit la stratégie de gestion des données de référence.

## 1. Principes Directeurs
*   **Données Dynamiques (Tables) :** Ce sont des données métier que l'Administrateur doit pouvoir enrichir via le Dashboard sans intervention technique (ex: ajouter un type de véhicule).
*   **Données Statiques (Enums) :** Ce sont des états liés à la logique algorithmique du code (ex: un trajet est "En cours"). Les modifier implique une modification du code Java.

---

## 2. Objets Dynamiques (Tables de Référence)

Ces objets seront gérés via des **CRUD** dans le module Administration.

### 2.1. Types de Véhicules (`fleet.vehicle_types`)
*   **Besoin :** Flexibilité totale pour définir le parc (Voiture, Camion, Moto, Engin de chantier...).
*   **Structure :**
    *   `id` (UUID) : Clé primaire.
    *   `code` (VARCHAR unique) : Identifiant technique (ex: `HEAVY_TRUCK`, `MOTO`).
    *   `label` (VARCHAR) : Libellé affiché (ex: "Poids Lourd", "Moto Taxi").
    *   `description` (TEXT) : Détails facultatifs.

---

## 3. Objets Statiques (Enums PostgreSQL)

Ces listes sont figées dans la structure de la base de données et dans le code Java (Classes `Enum`).

### 3.1. Rôles Utilisateurs (Namespace `FLEET_`)
*Pour éviter les conflits avec d'autres microservices de l'écosystème TransEns.*

| Code (Enum) | Description | Périmètre |
| :--- | :--- | :--- |
| **`FLEET_ADMIN`** | Super-administrateur du module Fleet. | Gestion globale des comptes managers. |
| **`FLEET_MANAGER`** | Client B2B (Gestionnaire). | Gestion de sa flotte, ses véhicules, ses zones. |
| **`FLEET_DRIVER`** | Chauffeur / Employé mobile. | Utilisation de l'App Mobile, exécution des trajets. |

### 3.2. Statuts Opérationnels

#### Véhicule (`vehicle_status_enum`)
*   `AVAILABLE` : Prêt à être assigné ou conduit.
*   `ON_TRIP` : Actuellement en course (verrouillé).
*   `MAINTENANCE` : Indisponible pour cause technique.

#### Chauffeur (`driver_status_enum`)
*   `ACTIVE` : Peut se connecter et prendre des courses.
*   `INACTIVE` : Compte suspendu ou en congé.

#### Trajet (`trip_status_enum`)
*   `SCHEDULED` : Planifié (Futur).
*   `ONGOING` : En cours (GPS actif).
*   `COMPLETED` : Terminé avec succès.
*   `CANCELLED` : Annulé avant la fin.

### 3.3. Indicateurs Techniques & Alertes

#### Moteur (`engine_status_enum`)
*   `OK`
*   `NEEDS_SERVICE`
*   `OUT_OF_SERVICE`

#### Maintenance (`maintenance_status_enum`)
*   `UP_TO_DATE`
*   `PENDING`
*   `OVERDUE`

#### Geofencing (`event_type_enum`)
*   `ENTRY` : Entrée dans une zone.
*   `EXIT` : Sortie d'une zone.

---

## 4. Impact sur le Schéma SQL

1.  **Table `vehicles`** :
    *   La colonne `type` ne sera plus un `ENUM`.
    *   Elle devient `vehicle_type_id` (UUID), une **Clé Étrangère** vers la table `fleet.vehicle_types`.

2.  **Table `trips`** :
    *   Pour garder l'historique, un trajet stockera aussi `vehicle_type_id` (FK) pour savoir quel type de véhicule a été utilisé à ce moment-là.

3.  **Gestion des Rôles** :
    *   Les rôles sont stockés dans le schéma `public` (tables partagées), mais nous insèrerons spécifiquement les valeurs `FLEET_ADMIN`, `FLEET_MANAGER`, `FLEET_DRIVER` lors du seeding initial.

---