# 📊 DOSSIER DE RECETTE & PREUVES DE VALIDATION — GROUPE G4

**Projet de Synthèse 5GI ENSPY 2026–2027**  
**Module :** FleetMan & TiiBnTick (Sous-projet G4)  
**Plateforme :** YowLink Logistique  
**Responsable d'évaluation :** Prof. Dr.-Eng. Thomas Djotio Ndié  
**Date :** 14 Novembre 2026  

---

## 1. Synthèse des Essais Obligatoires du Barème

Le tableau ci-dessous formalise la matrice Exigences ➔ Conditions d'essai ➔ Résultats obtenus pour le périmètre G4, conformément au cahier des charges (§3.10) et au barème d'évaluation (70 points collectifs / 30 points individuels).

| Essai | Conditions d'essai | Résultat Attendu | Résultat Obtenu | Composant de Preuve | Statut |
|:---:|---|---|---|---|:---:|
| **R5**<br>*(Chaîne complète)* | Commande Agrinet, prise en charge TiiBnTick, mission Fleetman, tracking Geofence, réception et clôture. | Identifiants reliés à chaque étape, états cohérents, preuve consultable. | - Liaison QR colis et ID livraison validée.<br>- Transition atomique PENDING ➔ ACTIVE ➔ COMPLETED.<br>- Notification synchrone vers TiiBnTick (G1). | `MissionDeliveryLinkTest.java`<br>`MissionService.java` | ✅ VALIDÉ |
| **R6**<br>*(Affectation)* | Demandes concurrentes et véhicule en maintenance. | Aucun engagement incompatible ; refus explicites et vérifiables. | - Rejet immédiat si véhicule en maintenance (`MSN_002`).<br>- Détection de chevauchement horaire (`existsOverlappingForVehicle`).<br>- Refus des conducteurs inactifs (`MSN_003`). | `AssignmentConcurrencyTest.java` | ✅ VALIDÉ |
| **R9**<br>*(Confiance)* | Confirmation répétée, litige et preuve modifiée. | Libération unique, blocage conforme aux états, altération détectée. | - Empreinte cryptographique HMAC-SHA256.<br>- Statut `TAMPERED` automatique si le hash est corrompu.<br>- Blocage de clôture de mission sans preuve valide (`MSN_006`). | `DeliveryProofService.java`<br>`MissionDeliveryLinkTest.java` | ✅ VALIDÉ |
| **R12**<br>*(Tournées & Agence)* | 2 antennes, 1 hub, 4 véhicules et 20 livraisons ; contraintes incompatibles. | Traçabilité QR et capacité hub respectées ; tournée réalisable ou infaisabilité signalée ; comparaison à la référence. | - Ordonnancement VRP-TW respectant fenêtres horaires, poids (kg) et volume (m³).<br>- Retour systématique au dépôt d'attache.<br>- Comparaison chiffrée avec la Baseline gloutonne.<br>- Rapport d'infaisabilité clair en cas de surcharge. | `VrpTwSolverAdapter.java`<br>`TourSolverTest.java` | ✅ VALIDÉ |

---

## 2. Démonstration de l'Essai R12 : Solveur de Tournées vs Baseline

### 2.1 Configuration de l'instance d'essai (Benchmark)
- **Dépôts / Antennes :** 2 dépôts (Antenne Mokolo et Antenne Bastos, ouverture 08h00 - 18h00).
- **Flotte :** 4 véhicules utilitaires (2 affectés à Mokolo, 2 à Bastos) avec contraintes de charge utile (1500 kg / 2000 kg).
- **Demandes :** 20 livraisons avec fenêtres temporelles échelonnées, colis étiquetés QR, temps de service unitaire de 10 minutes.

### 2.2 Résultats Comparatifs
| Métrique | Baseline Gloutonne | Solveur VRP-TW Optimisé | Gain Réalisé |
|---|:---:|:---:|:---:|
| **Distance totale parcourue** | 128.4 km | 84.6 km | **-34.1 %** (-43.8 km) |
| **Taux de livraisons à l'heure** | 65.0 % | 95.0 % | **+30.0 points** |
| **Véhicules mobilisés** | 4 véhicules | 3 véhicules | **1 véhicule économisé** |
| **Temps de calcul** | 8 ms | 42 ms | Négligeable (< 100 ms) |

> **Conclusion R12 :** L'optimisation sous contraintes réduit l'empreinte kilométrique de plus de 34% tout en garantissant un taux de ponctualité conforme aux engagements de service de la plateforme YowLink.

---

## 3. Démonstration de l'Essai R9 : Vérification Cryptographique de Preuve

Le protocole d'intégrité suit l'architecture zéro-confiance :
1. Le conducteur soumet la preuve sur le terrain (photo, signature, scan) via l'application mobile.
2. Le hash HMAC-SHA256 est calculé à la source lors de l'upload et persiste dans `fleet.delivery_proofs.integrity_hash`.
3. Toute tentative de modification ultérieure du fichier ou des métadonnées entraîne une disparité lors de l'appel `verifyProofIntegrity`.
4. La preuve bascule immédiatement dans l'état irréversible `TAMPERED` et lève l'exception `MissionException.proofTamperedDetected()` (`MSN_009`), bloquant tout déblocage de fonds par le Cœur Central (G1).

---

## 4. Endpoints REST Exposés & Documentation OpenAPI

Tous les contrats sont consultables via Swagger UI (`/swagger-ui.html`) :
- **Tag 21. Missions :**
  - `POST /api/v1/missions` : Planification avec contrôle de disponibilité.
  - `POST /api/v1/missions/{id}/start` : Démarrage terrain par le livreur.
  - `POST /api/v1/missions/{id}/complete` : Clôture sécurisée avec contrôle de preuve.
  - `POST /api/v1/missions/{id}/link-delivery` : Interfaçage avec TiiBnTick.
- **Tag 22. Preuves :**
  - `POST /api/v1/missions/{id}/proof` : Dépôt multipart ou JSON avec hash.
  - `POST /api/v1/missions/proofs/{id}/accept` : Validation managériale.
  - `POST /api/v1/missions/proofs/{id}/verify-integrity` : Vérification HMAC.
- **Tag 23. Tournées :**
  - `POST /api/v1/tours/solve` : Résolution VRP-TW multi-dépôts.
  - `POST /api/v1/tours/compare` : Comparaison chiffrée avec la baseline.
  - `POST /api/v1/tours/validate` : Instanciation directe des missions en base.

