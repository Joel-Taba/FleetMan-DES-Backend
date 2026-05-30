# 📋 Roadmap Finalisation - 05 Février 2026

## 🦁 Cluster Gabriel (Chef de projet Backend)
- [x] **Tâche 1 :** Réorganisation finale Swagger (Config faite).
    - [x] stats publiques pour la landing page
    - [x] mise a jour de checkhealt pour verifier la sante reelle des services
- [x] **Tâche 2 :** Test et finalisation module Monitoring.
    - [x] **2.1 :** stats publiques pour la landing page
    - [x] **2.2 :** mise a jour de checkhealt pour verifier la sante reelle des services
- [x] **Tâche 3 :** Test et finalisation module Auth.
    - [x] **3.1 :** Migration SQL : Déplacer `users` vers le schéma `fleet` et ajouter `is_active` (boolean) et `deleted_at` (timestamp).
    - [x] **3.2 :** Refactoring technique : Adapter l'entité `UserEntity` et son repository pour pointer sur `fleet.users`.
    - [x] **3.3 :** Implémentation du flux `Refresh Token` (Route + Service + Port).
    - [x] **3.4 :** Raffinement du flux `Login` : Intégrer le "Pull" (sync à chaque login) et la vérification du statut `is_active`.
    - [x] **3.5 :** Gestion des erreurs : Créer un mapper d'exceptions pour traduire proprement les codes d'erreur du service distant (401, 409, etc.).
    - [x] **3.6 :** Validation Swagger : Scénario d'inscription, login, accès bloqué (en forçant `is_active: false` en DB) et rafraîchissement de token.

- [x] **Tâche 4 :** Test et finalisation module Account.
    - [x] **Tâche 4.1 :** **Agrégation & Synchro Profile** : Garantir un `pullSync` systématique sur le `GET /me` et enrichir l'objet avec les données métier complètes (Manager/Driver).
    - [x] **Tâche 4.2 :** **Mise à jour Identité** : Implémenter le `PUT /account` avec propagation immédiate des modifications vers le cache local `fleet.users`.
    - [x] **Tâche 4.3 :** **Soft Delete & Nettoyage** : Implémenter la suppression logique (is_active/deleted_at) et créer le service de libération automatique du véhicule pour les Chauffeurs.
    - [x] **Tâche 4.4 :** **Tests & Debug Médias** : Valider l'upload de photo (`POST /picture`) et le changement de mot de passe avec le service distant.
    - [x] **Tâche 4.5 :** **Blindage des Erreurs** : Mapper les codes d'erreur spécifiques du service Auth (401, 403, 409) vers nos `DomainException` pour des retours API clairs.



- [x] **Tâche 5 :** Test et finalisation module Super Admin.
    - [x] **Tâche 5.1 :** Création du Port & Service pour le filtrage spécifique des utilisateurs ayant le rôle `FLEET_ADMIN`.
    - [x] **Tâche 5.2 :** Implémentation du "Toggle Status" (Activer/Désactiver) modifiant la colonne `is_active` dans `fleet.users`.
    - [x] **Tâche 5.3 :** Implémentation de la suppression logique (`deleted_at`) pour les comptes Admin.
    - [x] **Tâche 5.4 :** Sécurisation et mise à jour du `AdminUserController` (Accès restreint au Super Admin).

- [x] **Tâche 6 :** Test et finalisation module Admin.
    **6.1 Gestion des Managers**
    - [x] **Tâche 6.1.1 :** Extension du service Manager pour inclure la désactivation de compte (`is_active = false`).
    - [x] **Tâche 6.1.2 :** Ajout de la suppression logique des managers.
    - [x] **Tâche 6.1.3 :** Listing global des managers avec état du compte (Actif/Suspendu).

    **6.2 Gestion des Ressources (Nouveau Tag)**
    - [x] **Tâche 6.2.1 :** Finalisation du CRUD complet pour `VehicleType` (Create, Read, Update, Delete).
    - [x] **Tâche 6.2.2 :** Réorganisation Swagger : Création du tag **"05. Admin | Gestion des Ressources"** et déplacement des endpoints de référence.
    - [x] **Tâche 6.2.3 :** Validation des contraintes (Interdire la suppression d'un type utilisé par un véhicule).

- [x] **Tâche 7 :** Test et finalisation module Fleet Managers.
    - [x] **Tâche 7.1 :** **Dynamisation du Profil** : Afficher le nombre réel de flottes gérées dans le endpoint `/me` (Remplacement de la valeur hardcodée).
    - [x] **Tâche 7.2 :** **Vue KPIs (Tableau de bord)** : Créer l'endpoint `/kpis` regroupant les métriques clés (Total Flottes, Total Véhicules, Total Chauffeurs).
    - [x] **Tâche 7.3 :** **Validation Entreprise** : Sécuriser la modification du nom d'entreprise (interdire les valeurs vides/nulles).
- [x] **Tâche 8 :** Test et finalisation module Drivers.
    - [x] **Tâche 8 :** Test et finalisation module Drivers.
    - [x] **Tâche 8.1 :** **Recrutement Intelligent** : Vérifier que l'utilisateur recruté possède bien le rôle `FLEET_DRIVER` avant de le lier.
    - [x] **Tâche 8.2 :** **Smart Assignment** : Implémenter la logique de permutation automatique (libérer l'ancien chauffeur si le véhicule est réassigné).
    - [x] **Tâche 8.3 :** **Verrouillage de Course** : Interdire le détachement d'un chauffeur ou d'un véhicule si un trajet (`Trip`) est marqué `ONGOING`.
    - [x] **Tâche 8.4 :** **Listing Filtré** : Permettre au manager de voir séparément ses chauffeurs "Libres" et "En course".
- [x] **Tâche 9 :** Test et finalisation module Vehicles (Debug PUT et ressources initiales).
     - [x] **Tâche 9.1 :** **Souveraineté des Ressources** : Créer le référentiel local (Marques, Carburants) géré par l'Admin.
    - [x] **Tâche 9.2 :** **Lookup local** : Rediriger les routes `/lookup` pour consommer les données de notre base `fleet` au lieu du service externe.
    - [x] **Tâche 9.3 :** **Gestion Opérationnelle** : Implémenter la route `/operational` pour permettre aux drivers de consulter/ajuster l'état du véhicule.
    - [x] **Tâche 9.4 :** **Nettoyage Swagger** : Appliquer le découpage en 4 sous-tags (9a, 9b, 9c, 9d) et uniformiser les erreurs.
- [x] **Tâche 10 : Test et finalisation module Fleets (Gestion Intégrale).**
    - [x] **10.1 : Fondations & Robustesse**
        - [x] Création de `FleetException` (Codes FLT_001 à FLT_006) pour une gestion d'erreur granulaire.
        - [x] Implémentation des DTOs documentés (`FleetAssignVehicleRequest`, `RecruitDriverRequest`).
    - [x] **10.2 : Administration & CRUD (10a)**
        - [x] Finalisation des endpoints de base (Create, Read, Update, Delete).
        - [x] Sécurisation de la suppression : Interdire `DELETE` si la flotte contient encore des véhicules ou chauffeurs actifs.
        - [x] Validation des statistiques de flotte (KPIs temps réel).
    - [x] **10.3 : Gestion du Parc Véhicules (10b)**
        - [x] Migration et adaptation de la route d'assignation (Véhicule -> Flotte).
        - [x] Implémentation du retrait de véhicule (Détachement de la flotte).
        - [x] **Trigger Geofence** : Automatiser l'ajout/retrait du véhicule dans les zones liées à la flotte lors du changement d'affectation.
    - [x] **10.4 : Gestion des Chauffeurs (10c)**
        - [x] Endpoint de création directe : `POST /fleets/{id}/drivers/register` (Nouveau compte + Profil métier).
        - [x] Endpoint de recrutement : `POST /fleets/{id}/drivers` (Lier un chauffeur existant via email/username).
        - [x] Logique de détachement : Retirer un chauffeur d'une flotte sans supprimer son compte.
    - [x] **10.5 : Finalisation Swagger & Clean-up**
        - [x] Application du découpage en 3 sous-tags (10a, 10b, 10c).
        - [x] Nettoyage définitif des routes redondantes dans `VehicleController` et `DriverController`.
- [x] **Tâche 11 :** Test et finalisation module Trips (Courses).
    - [x] **Tâche 11 : Test et finalisation module Trips (Courses & Télémétrie).**
    - [x] **11.1 : Fondations**
        - [x] Création de `TripException` (TRP_001 à TRP_006).
        - [x] Implémentation du filtrage strict : Un driver ne peut démarrer que sur SON véhicule assigné.
    - [x] **11.2 : Logique de Course (11a)**
        - [x] Sécurisation du `startTrip` : Vérification multi-niveaux (Driver dispo, Véhicule dispo, Assignation valide).
        - [x] Optimisation `sendTelemetry` : Pipeline Redis pour performance.
        - [x] Finalisation `endTrip` : Calcul de distance et mise à jour de l'odomètre global du véhicule dans `operational_parameters`.
    - [x] **11.3 : Monitoring & Historique (11b)**
        - [x] Implémentation du listing paginé pour le Manager.
        - [x] Ajout de la route `/active` pour le monitoring temps réel du parc.
    - [x] **11.4 : Intégration Geofence Live**
        - [x] Trigger automatique de détection de zone lors de la télémétrie.
    - [x] **11.5 : Nettoyage Swagger**
        - [x] Application des tags `11a` et `11b` et documentation des DTOs.
## 🦅 Cluster Hassana
- [x] **Tâche 12 :** Finalisation module Geofence.

## 🐢 Cluster Raphaël
- [ ] **Tâche 13 :** Finalisation module Payments.