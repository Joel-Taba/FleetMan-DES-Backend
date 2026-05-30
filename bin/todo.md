### 📋 Roadmap Macro : API Fleet & Geofence

#### Jalon 1 : Initialisation, Structure & Persistence (Fondations)
- [x] **Tâche 1.1 :** Nettoyage et Refactoring du template (Renommage packages, suppression de la logique "Product").
- [x] **Tâche 1.2 :** Implémentation du Schéma de données (Traduction du contrat v1.0 en SQL pour PostgreSQL).
- [x] **Tâche 1.3 :** Mise en place du mécanisme de **Seeding** (Génération automatique de données de test au démarrage).
- [x] **Tâche 1.4 :** **Validation swagger** : Vérification de l'état de la base de données via un endpoint de santé (Health Check).
- [x] **Tâche 1.5:** *Guide*: Elaboration d'un guide pour que les collaborateurs puissent initialiser la bd sans probleme et la seed en mode local.(le plus facilement possible) 

#### Jalon 2 : Authentification & Accès (CU1)
- [x] **Tâche 2.1 :** Configuration de la Sécurité Réactive (Spring Security).
- [ ] **Tâche 2.2 :** Intégration du Service d'Authentification externe (Adaptateur WebClient).
- [ ] **Tâche 2.3 :** Implémentation du **Mode Dégradé (Fake Auth)** pour le développement local.
- [ ] **Tâche 2.4 :** **Validation Postman** : Tests de login, génération de token et accès restreint par rôles (RBAC).
- [ ] **Tâche 2.5 :** Intégration des routes "mot de passe oublié" et autres via l'API d'authentification externe.

#### Jalon 3 : Gestion de la Flotte (CU17, CU18, CU19)
- [ ] **Tâche 3.1 :** Use-Case : Créer / Modifier / Supprimer une Flotte (Fleets).
- [ ] **Tâche 3.2 :** Use-Case : Créer / Modifier / Supprimer un Véhicule (Vehicles).
- [ ] **Tâche 3.3 :** Gestion des paramètres détaillés (Financial & Maintenance parameters).
- [ ] **Tâche 3.4 :** **Validation Postman** : CRUD complet des véhicules et flottes.

#### Jalon 4 : Gestion des Chauffeurs & Assignations (CU21, CU24)
- [ ] **Tâche 4.1 :** Use-Case : Créer / Gérer un profil Driver (lié à l'utilisateur distant).
- [ ] **Tâche 4.2 :** Use-Case : Assigner / Libérer un véhicule à un chauffeur.
- [ ] **Tâche 4.3 :** **Validation Postman** : Scénario complet d'enregistrement et d'affectation d'un chauffeur.

#### Jalon 5 : Gestion des Trajets & Temps Réel (CU4, CU5, CU2, CU14)
- [ ] **Tâche 5.1 :** Use-Case : Démarrer un trajet (Start Trip).
- [ ] **Tâche 5.2 :** Use-Case : Terminer un trajet (End Trip) et calcul des statistiques de fin de trajet.
- [ ] **Tâche 5.3 :** Mise à jour des paramètres opérationnels (Positions GPS, vitesse, fuel) en flux continu.
- [ ] **Tâche 5.4 :** **Validation Postman** : Simulation d'un trajet complet et vérification des logs.

#### Jalon 6 : Moteur de Geofencing & Alertes (CU10, CU11, CU12, CU13)
- [ ] **Tâche 6.1 :** Use-Case : Définir et gérer les zones (Geofence Zones).
- [ ] **Tâche 6.2 :** Moteur de détection réactif (Intersection position / zone).
- [ ] **Tâche 6.3 :** Publication des alertes dans Kafka.
- [ ] **Tâche 6.4 :** **Validation Postman** : Déclenchement manuel d'une alerte en injectant une position GPS hors zone.

#### Jalon 7 : Intégrations Services Périphériques (Fare, Payment, Medias, Notification)
- [ ] **Tâche 7.1 :** Adaptateurs pour Fare Calculator & Payment (avec mode Fake data).
- [ ] **Tâche 7.2 :** Adaptateur pour l'API Media (Gestion des images).
- [ ] **Tâche 7.3 :** Intégration finale du service de Notification.
- [ ] **Tâche 7.4 :** **Validation Postman** : Tests de bout en bout incluant les services externes.

