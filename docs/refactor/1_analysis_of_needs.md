# 📋 Analyse des Besoins : Fleet Management & Geofencing

**Projet :** TraEnSys - Module de Gestion de Flotte
**Date :** 18 Janvier 2026
**Version :** 1.0 (Validée)

---

## 1. Vision et Périmètre du Projet

### 1.1 Objectif Principal
Développer une plateforme B2B permettant aux entreprises de transport (taxis, logistique, livraisons) de **superviser** leurs flottes de véhicules en temps réel, de gérer leurs chauffeurs et de contrôler le respect des zones géographiques d'activité (**Geofencing**).

### 1.2 Clarification sur le "Client Final"
Conformément à l'analyse du métier, le rôle du **"Client Final"** (passager ou destinataire) est **exclu du périmètre fonctionnel principal**.
*   **Raison :** Le système est un outil de supervision interne (Manager <-> Chauffeur), et non une application de mise en relation de type VTC (Uber/Yango).
*   **Optionnel :** Une fonctionnalité de "Lien de suivi public" pourra être envisagée en bonus, mais aucune gestion de compte "Client" ne sera développée.

### 1.3 Matériel Cible
*   **Suivi GPS :** Assuré exclusivement par le **smartphone Android du chauffeur**. Aucun boîtier matériel (OBD/Tracker) n'est requis.
*   **Cartographie :** Utilisation de solutions Open Source (OpenStreetMap).

---

## 2. Acteurs du Système

| Acteur | Rôle | Plateforme |
| :--- | :--- | :--- |
| **Administrateur (Admin)** | Super-utilisateur technique. Gère l'accès au service pour les entreprises. | Web (Back-office) |
| **Gestionnaire (Fleet Manager)** | Utilisateur principal. Propriétaire d'une flotte. Supervise les opérations au quotidien. | Web (Dashboard) |
| **Chauffeur (Driver)** | Employé conduisant le véhicule. Source de la donnée GPS. | Mobile (Android) |

---

## 3. Besoins Fonctionnels par Acteur

### 👮‍♂️ 3.1 Pour l'Administrateur
1.  **Gestion des Comptes Managers :** Créer, modifier, suspendre ou supprimer les comptes des gestionnaires de flotte (Fleet Managers).
2.  **Supervision Globale :** Visualiser les statistiques d'utilisation de la plateforme (nombre total de flottes, véhicules connectés).
3.  **Diffusion de Messages :** Envoyer des notifications système (maintenance, infos) à tous les utilisateurs.

### 👔 3.2 Pour le Gestionnaire de Flotte (Fleet Manager)
*Le cœur de l'application métier.*

**A. Gestion des Ressources**
1.  **Véhicules :** Enregistrer les véhicules (Marque, Modèle, Plaque) et gérer leurs statuts (En service, En maintenance).
2.  **Chauffeurs :** Enregistrer les chauffeurs (Nom, Permis, Photo) et gérer leurs profils.
3.  **Assignation :** Associer un véhicule à un chauffeur pour une période donnée.

**B. Supervision & Géolocalisation**

4.  **Tracking Temps Réel :** Visualiser sur une carte interactive la position instantanée de tous les véhicules actifs.
5.  **Historique des Trajets :** Consulter les trajets passés (Tracé sur carte, Heure début/fin, Distance parcourue).

**C. Geofencing (Fonctionnalité Clé)**

6.  **Définition de Zones :** Dessiner des zones sur la carte (Polygones ou Cercles).
7.  **Règles d'Alerte :** Configurer des déclencheurs (ex: "Alerter si le véhicule sort de la zone Douala-Centre").
8.  **Réception d'Alertes :** Recevoir une notification (Push/In-App) immédiate en cas de violation d'une règle.

**D. Administratif**

9.  **Tableau de Bord :** Vue synthétique des performances (Km parcourus, temps de conduite).
10. **Abonnement :** Gérer son niveau de service (Premium) via un système de paiement.

### 🧢 3.3 Pour le Chauffeur (Driver)
*L'outil de terrain.*

1.  **Authentification :** Connexion sécurisée sur l'application mobile.
2.  **Gestion de la Course (Le "Switch") :**
    *   **Début de service (Start Trip) :** Active le GPS et commence la transmission des données au serveur.
    *   **Fin de service (End Trip) :** Arrête le GPS et clôture le trajet.
3.  **Mode Déconnecté (Offline) :** En cas de perte de réseau (fréquent), l'application doit stocker les points GPS localement et les synchroniser dès le retour de la connexion.
4.  **Réception d'Ordres :** Recevoir les notifications ou alertes envoyées par le Manager.

---

## 4. Exigences Non-Fonctionnelles (Contraintes)

1.  **Performance Temps Réel :** La latence entre la position réelle du véhicule et son affichage sur l'écran du manager ne doit pas excéder **5 secondes**.
2.  **Robustesse Réseau :** Le système doit être résilient aux coupures d'internet mobile (mécanisme de *Retry* et *Buffer*).
3.  **Sécurité des Données :** Les données de localisation sont sensibles. L'accès doit être strictement cloisonné (un Manager ne voit que SA flotte).
4.  **Intégration Externe :** Le système doit s'interfacer avec le Service d'Authentification Centralisé de l'écosystème TransEns (pas de gestion de mots de passe en local).

---

## 5. Synthèse des Priorités (Roadmap)

1.  **Priorité 1 (Core) :** Authentification, Gestion CRUD (Véhicules/Chauffeurs/Assignation), Démarrage/Fin de trajet simple.
2.  **Priorité 2 (Tracking) :** Remontée GPS fluide, Carte temps réel.
3.  **Priorité 3 (Geofencing) :** Dessin de zones, Moteur de détection Entrée/Sortie, Alertes.
4.  **Priorité 4 (Avancé) :** Statistiques, Mode Offline avancé, Paiements.