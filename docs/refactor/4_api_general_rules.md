# 🛡️ Règles Générales de l'API & Standards

**Projet :** Fleet Management System
**Version :** 2.0 (Consolidation)
**Framework :** Spring Boot WebFlux (Reactive)

Ce document définit les normes techniques, les formats d'échange, la sécurité et la gestion des erreurs pour tous les endpoints du microservice.

---

## 1. Principes Fondamentaux

*   **Protocole :** REST sur HTTP(S).
*   **Encodage :** UTF-8.
*   **Format de données :** `application/json` (Sauf pour l'upload de fichiers : `multipart/form-data`).
*   **Dates :** Format ISO 8601 UTC (ex: `2026-01-18T14:30:00Z`).
*   **Identifiants :** UUID v4 pour toutes les ressources (ex: `550e8400-e29b-41d4-a716-446655440000`).
*   **Stateless :** Aucune session serveur (utilisation de JWT).

---

## 2. Sécurité & Contrôle d'Accès

### 2.1. Authentification
L'API est sécurisée par **Bearer Token (JWT)**.
Le token doit être présent dans le header de chaque requête protégée :
```http
Authorization: Bearer <votre_token_jwt>
```

### 2.2. Classification des Routes

#### 🟢 Routes Publiques (Accessibles sans token)
Ces routes sont ouvertes pour permettre l'accès initial ou le monitoring.
*   `POST /api/v1/auth/login` : Connexion.
*   `POST /api/v1/auth/register` : Inscription.
*   `POST /api/v1/auth/refresh` : Rafraîchissement de token.
*   `GET /actuator/health` : Vérification de l'état du système (Health Check).
*   `GET /swagger-ui/**` & `/v3/api-docs/**` : Documentation API.

#### 🔒 Routes Protégées (Token Requis)
**Toutes les autres routes sont protégées par défaut.**
L'accès est ensuite affiné par **Rôle (RBAC)** :

*   **ADMIN** : Accès complet aux routes `/api/v1/admin/**`.
*   **FLEET_MANAGER** : Accès aux routes de gestion (`/fleets`, `/drivers`, `/vehicles`, `/geofence`).
    *   *Règle métier :* Un manager ne peut voir/modifier **que** les ressources de ses propres flottes.
*   **DRIVER** : Accès restreint aux routes opérationnelles (`/api/v1/driver/**`, `/trips`).

---

## 3. Codes de Statut HTTP (Standards)

L'API utilise les codes HTTP standards pour indiquer le succès ou l'échec d'une requête.

| Code | Signification | Contexte d'utilisation |
| :--- | :--- | :--- |
| **200** | `OK` | Requête traitée avec succès (Lecture, Modification). |
| **201** | `Created` | Ressource créée avec succès (ex: Création véhicule). |
| **204** | `No Content` | Action réussie mais pas de contenu à renvoyer (ex: Suppression). |
| **400** | `Bad Request` | Erreur client (Validation, format JSON invalide). |
| **401** | `Unauthorized` | Token manquant, invalide ou expiré. |
| **403** | `Forbidden` | Token valide, mais droits insuffisants pour cette action. |
| **404** | `Not Found` | Ressource introuvable (ID inexistant). |
| **409** | `Conflict` | Conflit métier (ex: Email déjà utilisé, Véhicule déjà assigné). |
| **422** | `Unprocessable` | Erreur sémantique (ex: Date de fin avant date de début). |
| **500** | `Server Error` | Bug serveur non géré (NullPointer, DB down). |

---

## 4. Gestion des Erreurs (Robustesse)

Pour faciliter le travail du Frontend, **toutes les erreurs** (4xx et 500) renverront un corps de réponse JSON uniformisé.

### 4.1. Structure de l'objet Erreur

```json
{
  "timestamp": "2026-01-18T14:45:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Ce véhicule est déjà assigné à un autre chauffeur.",
  "path": "/api/v1/drivers/assign-vehicle",
  "code": "VEHICLE_ALREADY_ASSIGNED" // (Optionnel) Code métier interne
}
```

### 4.2. Stratégie de Message

1.  **Erreur Métier Gérée (Business Exception) :**
    *   Si le code lève une exception métier (ex: `VehicleAlreadyAssignedException`), le `message` contiendra l'explication claire pour l'utilisateur.
    *   *Exemple :* "Impossible de supprimer cette flotte car elle contient encore des véhicules."

2.  **Erreur de Validation (Validation Exception) :**
    *   Si les données d'entrée sont invalides (`@Valid`), le `message` listera les champs en erreur.
    *   *Exemple :* "L'email est obligatoire, La plaque d'immatriculation est invalide."

3.  **Erreur Serveur Non Gérée (Internal Server Error) :**
    *   Si le serveur plante (bug, timeout DB), on **ne renvoie jamais** la stacktrace au client (sécurité).
    *   Le `message` sera générique et constant.
    *   *Exemple :* **"Un problème technique est survenu. Veuillez réessayer plus tard ou contacter le support."**

---

## 5. Formats de Réponse (Succès)

### 5.1. Ressource Unique
Retourne directement l'objet JSON.
```json
// GET /api/v1/vehicles/{id}
{
  "id": "uuid...",
  "licensePlate": "LT 123",
  "brand": "Toyota"
}
```

### 5.2. Liste (Collection)
Retourne un tableau JSON (si pas de pagination) ou un objet paginé.

**Format Paginé (Standard Spring Data) :**
```json
// GET /api/v1/vehicles?page=0&size=10
{
  "content": [
    { "id": "1", "model": "Yaris" },
    { "id": "2", "model": "Corolla" }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10
  },
  "totalElements": 45,
  "totalPages": 5,
  "last": false
}
```

---

## 6. Bonnes Pratiques pour les Développeurs

1.  **Validation :** Ne jamais faire confiance au frontend. Valider toutes les entrées (DTOs) avec des annotations (`@NotNull`, `@Email`, etc.).
2.  **Logging :**
    *   Logguer les erreurs 500 avec la stacktrace complète (pour nous).
    *   Ne pas logguer de données sensibles (mots de passe).
3.  **Idempotence :** Les requêtes `GET`, `PUT`, `DELETE` doivent pouvoir être relancées sans effet de bord indésirable.
4.  **Performance :** Utiliser les types réactifs (`Mono`, `Flux`) de bout en bout. Ne jamais bloquer un thread (`Thread.sleep`, JDBC classique dans le flux principal).