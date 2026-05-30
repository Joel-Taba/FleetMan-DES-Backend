# Suivi d'Implémentation — Module Opérations Terrain
## Intégration des apports de `Fleetman Backend` dans `fleet-management-back`

> Ce document est mis à jour à la fin de chaque phase pour tracer précisément ce qui a été réalisé.

---

## Vue d'ensemble des phases

| Phase | Titre | Statut |
|---|---|---|
| **Phase 1** | Fondations du domaine | ✅ Terminée |
| **Phase 2** | Ports du domaine (Use Cases & Repository Ports) | ✅ Terminée |
| **Phase 3** | Services applicatifs | ✅ Terminée |
| **Phase 4** | Infrastructure : Persistance (R2DBC + Liquibase) | ✅ Terminée |
| **Phase 5** | Infrastructure : API REST (Controllers + DTOs) | ✅ Terminée |
| **Phase 6** | KPIs Opérationnels & Intégration inter-modules | ✅ Terminée |

---

---

## ✅ Phase 1 — Fondations du domaine

**Date de réalisation :** 27 mai 2026
**Couche impactée :** `domain/model/` et `domain/exception/`

### Objectif

Poser les bases du bounded context **Opérations Terrain** dans le domaine pur de `fleet-management-back`, sans toucher à l'infrastructure. Tous les fichiers créés sont 100% Java pur, sans aucune annotation Spring, JPA ou Jackson.

---

### Fichiers créés

#### 1. `domain/model/Coordinates.java` — Value Object GPS

**Chemin :** `src/main/java/com/yowyob/fleet/domain/model/Coordinates.java`

Record Java immuable représentant une position géographique (longitude, latitude).

**Points clés :**
- Validation des bornes dans le bloc compact du record : longitude ∈ [-180, 180], latitude ∈ [-90, 90]
- Méthode `toString()` retournant le format WKT `POINT(lon lat)` compatible PostGIS
- Aucune dépendance externe (les librairies `ugeojson` du dossier `libs` ne sont pas utilisées ici — le domaine reste pur)

---

#### 2. `domain/model/Maintenance.java` — Entité Maintenance

**Chemin :** `src/main/java/com/yowyob/fleet/domain/model/Maintenance.java`

Classe Java représentant une intervention technique sur un véhicule.

**Points clés :**
- IDs de type `UUID` (cohérence avec le reste du projet principal)
- Invariants métier validés dans le constructeur : sujet obligatoire et non vide, véhicule obligatoire
- Références croisées par ID uniquement (`vehicleId: UUID`, `driverId: UUID`) — pas d'entités JPA dans le domaine
- Données dénormalisées pour l'affichage : `vehicleRegistrationNumber`, `driverFullName`
- Méthodes métier : `addReport(String)`, `updateCost(BigDecimal)` (avec validation coût ≥ 0), `updateLocation(Coordinates, String)`
- Un seul setter exposé : `setId(UUID)` réservé à la couche persistance après sauvegarde

---

#### 3. `domain/model/Incident.java` — Entité Incident

**Chemin :** `src/main/java/com/yowyob/fleet/domain/model/Incident.java`

Classe Java représentant un événement imprévu sur un véhicule avec cycle de vie complet.

**Points clés :**
- IDs de type `UUID`
- 3 enums imbriqués dans la classe :
  - `Type` : ACCIDENT, BREAKDOWN, THEFT, VANDALISM, TRAFFIC_VIOLATION, OTHER
  - `Severity` : LOW, MEDIUM, HIGH, CRITICAL
  - `Status` : REPORTED, UNDER_INVESTIGATION, RESOLVED, CLOSED
- Invariants constructeur : type obligatoire, véhicule obligatoire, sévérité par défaut `MEDIUM`
- Statut initial automatique : `REPORTED`
- Machine à états avec méthodes métier expressives :
  - `resolve(String report)` — interdit sur un incident déjà `CLOSED`
  - `close()` — horodate automatiquement `resolvedAt` si absent
  - `addWitness(String name, String contact)` — enrichissement progressif
  - `updateStatus(Status)` — horodate si statut terminal
- Méthodes de requête métier : `isCritical()` (HIGH ou CRITICAL), `isOpen()` (REPORTED ou UNDER_INVESTIGATION)

---

#### 4. `domain/model/FuelRecharge.java` — Entité Recharge Carburant

**Chemin :** `src/main/java/com/yowyob/fleet/domain/model/FuelRecharge.java`

Classe Java représentant un plein de carburant effectué sur un véhicule.

**Points clés :**
- IDs de type `UUID`
- Enum imbriqué `StationName` : TOTAL, SHELL, OILIBYA, CAMRAIL, OTHER
- Invariants constructeur : quantité > 0, prix ≥ 0, véhicule obligatoire
- Méthode métier : `unitCost()` — calcule le coût par litre (`price / quantity`, arrondi à 2 décimales, `HALF_UP`), protégée contre la division par zéro

---

#### 5. `domain/exception/OperationException.java` — Exception métier

**Chemin :** `src/main/java/com/yowyob/fleet/domain/exception/OperationException.java`

Exception typée héritant de `DomainException` (pattern identique à `VehicleException`, `TripException`, etc.).

**Codes définis (OPR_001 à OPR_012) :**

| Code | Statut HTTP | Description |
|---|---|---|
| OPR_001 | 404 | Maintenance introuvable |
| OPR_002 | 400 | Sujet de maintenance obligatoire |
| OPR_003 | 400 | Coût négatif interdit |
| OPR_004 | 404 | Incident introuvable |
| OPR_005 | 400 | Type d'incident obligatoire |
| OPR_006 | 422 | Incident déjà clôturé |
| OPR_007 | 422 | Transition de statut invalide |
| OPR_008 | 404 | Recharge de carburant introuvable |
| OPR_009 | 400 | Quantité de carburant invalide |
| OPR_010 | 400 | Prix de recharge invalide |
| OPR_011 | 404 | Véhicule introuvable pour l'opération |
| OPR_012 | 404 | Chauffeur introuvable pour l'opération |

---

### Récapitulatif des fichiers créés en Phase 1

```
src/main/java/com/yowyob/fleet/
  domain/
    model/
      Coordinates.java       ✅ créé
      Maintenance.java       ✅ créé
      Incident.java          ✅ créé
      FuelRecharge.java      ✅ créé
    exception/
      OperationException.java ✅ créé
```

**Aucun fichier existant modifié.**

---

### Décisions techniques prises

1. **Pas d'utilisation des librairies `ugeojson`** dans le domaine — le value object `Coordinates` est un record Java pur. Les librairies `ugeojson` seront évaluées pour la couche infrastructure (adapters, services) si nécessaire.
2. **IDs `UUID`** sur toutes les entités — cohérence avec le reste du projet (`Vehicle`, `Driver`, `Fleet`, `Trip`).
3. **Pas de records pour `Maintenance`, `Incident`, `FuelRecharge`** — ces entités ont un état mutable (machine à états pour `Incident`, mise à jour du rapport/coût pour `Maintenance`) ce qui les rend incompatibles avec les records Java immuables. Seul `Coordinates` est un record car c'est un vrai value object immuable.
4. **Données dénormalisées** (`vehicleRegistrationNumber`, `driverFullName`) — évite des jointures supplémentaires pour l'affichage, pattern déjà utilisé dans le projet principal.

---

### Prochaine étape

**Phase 2 — Ports du domaine** : définition des interfaces Use Cases (ports entrants) et des contrats de persistance/événements (ports sortants) pour les trois agrégats.

---

## ✅ Phase 2 — Ports du domaine (Use Cases & Repository Ports)

**Date de réalisation :** 27 mai 2026
**Couche impactée :** `domain/ports/in/` et `domain/ports/out/`

### Objectif

Définir tous les contrats d'entrée (use cases) et de sortie (persistance, événements) du module Opérations Terrain. Ces interfaces constituent la **colonne vertébrale hexagonale** : elles isolent le domaine de toute implémentation technique et seront implémentées en Phase 3 (services) et Phase 4 (adapters infrastructure).

---

### Fichiers créés

#### Ports entrants — `domain/ports/in/`

##### 1. `ManageMaintenanceUseCase.java`

Interface définissant tous les cas d'utilisation pour la gestion des maintenances.

**Records Command imbriqués :**
- `CreateMaintenanceCommand` — subject, cost, report, longitude, latitude, locationName, vehicleId, driverId (optionnel)
- `UpdateMaintenanceCommand` — maintenanceId, subject, cost, report, longitude, latitude, locationName

**Méthodes réactives (9) :**
- `createMaintenance(CreateMaintenanceCommand)` → `Mono<Maintenance>`
- `getById(UUID)` → `Mono<Maintenance>`
- `getAllByManager(UUID managerId)` → `Flux<Maintenance>`
- `getByVehicleId(UUID)` → `Flux<Maintenance>`
- `getByDriverId(UUID)` → `Flux<Maintenance>`
- `getByDateRange(LocalDateTime, LocalDateTime, UUID managerId)` → `Flux<Maintenance>`
- `countByDriverId(UUID)` → `Mono<Long>`
- `update(UpdateMaintenanceCommand)` → `Mono<Maintenance>`
- `delete(UUID)` → `Mono<Void>`

---

##### 2. `ManageIncidentUseCase.java`

Interface la plus riche du module — couvre le cycle de vie complet des incidents et les KPIs financiers.

**Records Command imbriqués :**
- `CreateIncidentCommand` — type, description, severity (optionnel), cost (optionnel), longitude, latitude, witnessName, witnessContact, reportedBy, vehicleId, driverId (optionnel)
- `UpdateIncidentCommand` — incidentId, description, severity, cost, report, witnessName, witnessContact, policeReportNumber, insuranceClaimNumber, longitude, latitude

**Méthodes réactives (15) :**
- `createIncident(CreateIncidentCommand)` → `Mono<Incident>`
- `getById(UUID)` → `Mono<Incident>`
- `getAllByManager(UUID)` → `Flux<Incident>`
- `getByVehicleId(UUID)` → `Flux<Incident>`
- `getByDriverId(UUID)` → `Flux<Incident>`
- `getByType(Incident.Type, UUID managerId)` → `Flux<Incident>`
- `getBySeverity(Incident.Severity, UUID managerId)` → `Flux<Incident>`
- `getByStatus(Incident.Status, UUID managerId)` → `Flux<Incident>`
- `getOpenIncidents(UUID managerId)` → `Flux<Incident>`
- `getByDateRange(LocalDateTime, LocalDateTime, UUID managerId)` → `Flux<Incident>`
- `update(UpdateIncidentCommand)` → `Mono<Incident>`
- `updateStatus(UUID, Incident.Status)` → `Mono<Incident>`
- `countByVehicleId(UUID)` → `Mono<Long>`
- `getTotalCostByVehicleId(UUID)` → `Mono<BigDecimal>`
- `getTotalCostByDriverId(UUID)` → `Mono<BigDecimal>`
- `delete(UUID)` → `Mono<Void>`

---

##### 3. `ManageFuelRechargeUseCase.java`

Interface pour la gestion des recharges carburant avec KPIs de consommation.

**Records Command imbriqués :**
- `CreateFuelRechargeCommand` — quantity, price, longitude, latitude, stationName (optionnel), vehicleId, driverId (optionnel)
- `UpdateFuelRechargeCommand` — rechargeId, quantity, price, longitude, latitude, stationName, driverId

**Méthodes réactives (10) :**
- `createFuelRecharge(CreateFuelRechargeCommand)` → `Mono<FuelRecharge>`
- `getById(UUID)` → `Mono<FuelRecharge>`
- `getAllByManager(UUID)` → `Flux<FuelRecharge>`
- `getByVehicleId(UUID)` → `Flux<FuelRecharge>`
- `getByDriverId(UUID)` → `Flux<FuelRecharge>`
- `getByDateRange(LocalDateTime, LocalDateTime, UUID managerId)` → `Flux<FuelRecharge>`
- `getTotalQuantityByVehicleId(UUID)` → `Mono<BigDecimal>`
- `getTotalCostByVehicleId(UUID)` → `Mono<BigDecimal>`
- `update(UpdateFuelRechargeCommand)` → `Mono<FuelRecharge>`
- `delete(UUID)` → `Mono<Void>`

---

#### Ports sortants — `domain/ports/out/`

##### 4. `MaintenancePersistencePort.java`

Contrat de persistance réactif pour les maintenances. Sera implémenté par `MaintenancePersistenceAdapter` (R2DBC) en Phase 4.

**Méthodes (10) :** save, findById, findAll, findAllByManagerId, findByVehicleId, findByDriverId, findByDateRange, countByDriverId, existsById, deleteById.

---

##### 5. `IncidentPersistencePort.java`

Contrat de persistance le plus riche — expose des requêtes métier avancées pour les tableaux de bord.

**Méthodes (16) :** save, findById, findAll, findAllByManagerId, findByVehicleId, findByDriverId, findByType, findBySeverity, findByStatus, findOpenIncidents, findByDateRange, countByVehicleId, countByDriverId, getTotalCostByVehicleId, getTotalCostByDriverId, existsById, deleteById.

---

##### 6. `FuelRechargePersistencePort.java`

Contrat de persistance pour les recharges carburant avec agrégats KPIs.

**Méthodes (11) :** save, findById, findAll, findAllByManagerId, findByVehicleId, findByDriverId, findByDateRange, getTotalQuantityByVehicleId, getTotalCostByVehicleId, existsById, deleteById.

---

##### 7. `OperationEventPort.java`

Port événementiel découplé — le domaine publie des événements sans dépendre de Spring ni de Kafka.

**Records événements imbriqués :**
- `MaintenanceCreatedEvent(maintenanceId, subject, vehicleId, vehicleRegistration, driverId, fleetManagerId)` — déclenche une notification au Fleet Manager
- `IncidentReportedEvent(incidentId, incidentType, severity, vehicleId, vehicleRegistration, driverId, fleetManagerId, isCritical)` — notification prioritaire si `isCritical = true`

**Méthodes réactives (2) :**
- `publishMaintenanceCreated(MaintenanceCreatedEvent)` → `Mono<Void>`
- `publishIncidentReported(IncidentReportedEvent)` → `Mono<Void>`

---

### Récapitulatif des fichiers créés en Phase 2

```
src/main/java/com/yowyob/fleet/
  domain/ports/
    in/
      ManageMaintenanceUseCase.java    ✅ créé
      ManageIncidentUseCase.java       ✅ créé
      ManageFuelRechargeUseCase.java   ✅ créé
    out/
      MaintenancePersistencePort.java  ✅ créé
      IncidentPersistencePort.java     ✅ créé
      FuelRechargePersistencePort.java ✅ créé
      OperationEventPort.java          ✅ créé
```

**Aucun fichier existant modifié.**
**Zéro erreur de compilation sur les 7 fichiers.**

---

### Décisions techniques prises

1. **`managerId` systématiquement présent** dans les méthodes de listing — cohérence avec le reste du projet (un manager ne voit que ses propres ressources).
2. **Méthodes de listing par date** incluent `managerId` pour le filtrage de sécurité — évite les fuites de données inter-managers.
3. **Agrégats KPIs dans les ports de persistance** (`getTotalCost`, `getTotalQuantity`, `count`) — délégués à la base de données plutôt que calculés en mémoire, plus performant en réactif.
4. **`OperationEventPort` réactif** (`Mono<Void>`) — cohérence avec le paradigme WebFlux, la publication d'événement ne bloque pas le flux principal.
5. **Pas de port `DriverReferencePort` séparé** — les ports `DriverPersistencePort` et `VehiclePersistencePort` déjà existants dans le projet seront réutilisés directement dans les services (Phase 3), évitant la duplication.

---

### Prochaine étape

**Phase 3 — Services applicatifs** : implémentation de `MaintenanceService`, `IncidentService` et `FuelRechargeService` dans la couche `application/service/`, en réactif, en s'appuyant sur les ports définis en Phase 2.

---

## ✅ Phase 3 — Services applicatifs

**Date de réalisation :** 27 mai 2026
**Couche impactée :** `application/service/`

### Objectif

Implémenter la logique métier des trois agrégats dans la couche `application/service/`, en réactif (Mono/Flux), en s'appuyant exclusivement sur les ports définis en Phase 2. Aucune dépendance directe vers des classes d'infrastructure dans la logique métier — seuls les repositories déjà utilisés par le projet principal (`MaintenanceParameterR2dbcRepository`, `OperationalParameterR2dbcRepository`) sont injectés pour les synchronisations inter-modules.

---

### Fichiers créés

#### 1. `MaintenanceService.java`

**Chemin :** `src/main/java/com/yowyob/fleet/application/service/MaintenanceService.java`

Implémente `ManageMaintenanceUseCase`. Annoté `@Service @RequiredArgsConstructor @Slf4j`.

**Dépendances injectées :**
- `MaintenancePersistencePort` — persistance des maintenances
- `VehiclePersistencePort` — vérification existence véhicule + récupération `managerId`
- `DriverPersistencePort` — vérification existence chauffeur (optionnel)
- `OperationEventPort` — publication événement notification
- `MaintenanceParameterR2dbcRepository` — synchronisation `maintenance_parameters`

**Logique métier de `createMaintenance` :**
1. Vérification existence du véhicule via `VehiclePersistencePort` → `OperationException.vehicleNotFound` si absent
2. Vérification optionnelle du chauffeur via `DriverPersistencePort` → `OperationException.driverNotFound` si absent
3. Construction de l'objet `Maintenance` avec invariants (les validations du constructeur s'appliquent)
4. Construction du `Coordinates` uniquement si longitude ET latitude sont fournis
5. Persistance via `MaintenancePersistencePort`
6. **Synchronisation `maintenance_parameters`** : mise à jour de `last_maintenance_at` et `maintenance_status = UP_TO_DATE` dans la table existante du véhicule
7. **Publication événement** (fire & forget via `.subscribe()`) : `MaintenanceCreatedEvent` avec `fleetManagerId` résolu depuis le véhicule → déclenche une notification

**Logique de `update` :** application sélective des champs non-null via les méthodes métier de l'entité (`addReport`, `updateCost`, `updateLocation`).

---

#### 2. `IncidentService.java`

**Chemin :** `src/main/java/com/yowyob/fleet/application/service/IncidentService.java`

Implémente `ManageIncidentUseCase`. Annoté `@Service @RequiredArgsConstructor @Slf4j`.

**Dépendances injectées :**
- `IncidentPersistencePort`
- `VehiclePersistencePort`
- `DriverPersistencePort`
- `OperationEventPort`

**Logique métier de `createIncident` :**
1. Vérification véhicule et chauffeur (même pattern que `MaintenanceService`)
2. Construction de l'objet `Incident` — sévérité par défaut `MEDIUM` si non fournie (géré dans le constructeur de l'entité)
3. Ajout du témoin si `witnessName` ou `witnessContact` fournis
4. Persistance
5. **Publication événement prioritaire** (fire & forget) : `IncidentReportedEvent` avec `isCritical = incident.isCritical()` — log `WARN` supplémentaire si critique

**Logique de `updateStatus` :** délégation à la machine à états de l'entité :
- `RESOLVED` → appel `incident.resolve(null)`
- `CLOSED` → appel `incident.close()`
- Autres → appel `incident.updateStatus(newStatus)`
- Guard : retourne `OperationException.incidentAlreadyClosed` si statut courant est `CLOSED`

**Logique de `update` :** application sélective des champs non-null avec guard sur `CLOSED`.

**KPIs** : `getTotalCostByVehicleId`, `getTotalCostByDriverId` retournent `BigDecimal.ZERO` par défaut si aucun résultat (`.defaultIfEmpty`).

---

#### 3. `FuelRechargeService.java`

**Chemin :** `src/main/java/com/yowyob/fleet/application/service/FuelRechargeService.java`

Implémente `ManageFuelRechargeUseCase`. Annoté `@Service @RequiredArgsConstructor @Slf4j`.

**Dépendances injectées :**
- `FuelRechargePersistencePort`
- `VehiclePersistencePort`
- `DriverPersistencePort`
- `OperationalParameterR2dbcRepository` — synchronisation `fuel_level`

**Logique métier de `createFuelRecharge` :**
1. Vérification véhicule + récupération `tankCapacity` (pour le calcul du niveau)
2. Vérification optionnelle du chauffeur
3. Construction de l'objet `FuelRecharge` avec invariants
4. Persistance
5. **Synchronisation `operational_parameters.fuel_level`** : calcul du niveau en pourcentage `(quantity / tankCapacity) * 100`, plafonné à `"FULL"` si ≥ 100%. Mise à jour du `timestamp`. Dégradé gracieux si `tankCapacity` inconnue (log warn, pas d'erreur).

**Logique de `update`** : les champs `quantity` et `price` étant `final` dans l'entité (invariants), la mise à jour crée une nouvelle instance `FuelRecharge` avec les valeurs fusionnées (existant + nouvelles valeurs non-null).

**KPIs** : `getTotalQuantityByVehicleId`, `getTotalCostByVehicleId` retournent `BigDecimal.ZERO` par défaut.

---

### Récapitulatif des fichiers créés en Phase 3

```
src/main/java/com/yowyob/fleet/
  application/service/
    MaintenanceService.java    ✅ créé
    IncidentService.java       ✅ créé
    FuelRechargeService.java   ✅ créé
```

**Aucun fichier existant modifié.**
**Zéro erreur de compilation sur les 3 fichiers.**

---

### Décisions techniques prises

1. **Fire & forget pour les événements** — la publication Kafka/notification ne bloque pas le flux principal. Un échec de publication est loggué en `WARN` mais ne fait pas échouer la création de l'opération.
2. **Synchronisation inter-modules via repositories directs** — `MaintenanceParameterR2dbcRepository` et `OperationalParameterR2dbcRepository` sont injectés directement (déjà utilisés dans `VehicleService` et `TripService`). Pas de nouveau port créé pour éviter la sur-ingénierie.
3. **`buildCoordinates` helper privé** — factorisé dans chaque service pour éviter la duplication. Retourne `null` si longitude ou latitude est absent, ce qui est valide (localisation optionnelle).
4. **Guard `CLOSED` dans `IncidentService`** — toute tentative de modification d'un incident clôturé retourne `OperationException.incidentAlreadyClosed` (OPR_006, HTTP 422).
5. **Reconstruction d'objet pour `FuelRecharge.update`** — les champs `quantity` et `price` étant `final` (invariants métier), la mise à jour crée une nouvelle instance plutôt que de muter l'existant.

---

### Prochaine étape

**Phase 4 — Infrastructure : Persistance** : création des entités R2DBC, repositories Spring Data, adapters de persistance, migration Liquibase, et adapter Kafka pour les événements.

---

## ✅ Phase 4 — Infrastructure : Persistance (R2DBC + Liquibase + Kafka)

**Date de réalisation :** 27 mai 2026
**Couche impactée :** `infrastructure/adapters/outbound/persistence/`, `infrastructure/adapters/outbound/messaging/`, `resources/db/changelog/`

### Objectif

Implémenter toute la couche infrastructure du module Opérations : entités R2DBC, repositories Spring Data, adapters de persistance (implémentant les ports de sortie), adapter Kafka pour les événements, et migration Liquibase pour les trois nouvelles tables.

---

### Fichiers créés

#### Entités R2DBC — `infrastructure/adapters/outbound/persistence/entity/`

##### 1. `MaintenanceEntity.java`
Entité R2DBC mappée sur `fleet.maintenances`. Implémente `Persistable<UUID>` avec flag `isNew` (pattern identique à `TripEntity`, `MaintenanceParameterEntity`). Champs : id, subject, cost, dateTime, report, longitude, latitude, locationName, vehicleId, vehicleRegistration, driverId, driverFullName.

##### 2. `IncidentEntity.java`
Entité R2DBC mappée sur `fleet.incidents`. Enums stockés en `String` (VARCHAR en base). Champs complets incluant : type, severity, status, incidentDateTime, resolvedAt, policeReportNumber, insuranceClaimNumber, witnessName, witnessContact, reportedBy.

##### 3. `FuelRechargeEntity.java`
Entité R2DBC mappée sur `fleet.fuel_recharges`. Enum `StationName` stocké en `String`. Champs : id, quantity, price, rechargeDateTime, longitude, latitude, stationName, vehicleId, vehicleRegistration, driverId, driverFullName.

---

#### Repositories Spring Data R2DBC — `infrastructure/adapters/outbound/persistence/repository/`

##### 4. `MaintenanceR2dbcRepository.java`
Étend `ReactiveCrudRepository<MaintenanceEntity, UUID>`. Méthodes dérivées + requêtes `@Query` :
- `findByVehicleId`, `findByDriverId` — dérivées Spring Data
- `findAllByManagerId` — JOIN sur `fleet.vehicles` filtré par `manager_id`, trié par `date_time DESC`
- `findByDateRange` — filtre `BETWEEN :start AND :end`
- `countByDriverId` — agrégat `COUNT(*)`

##### 5. `IncidentR2dbcRepository.java`
Le plus riche — 12 méthodes dont 8 requêtes `@Query` avec JOIN sur `fleet.vehicles` :
- `findAllByManagerId`, `findByTypeAndManagerId`, `findBySeverityAndManagerId`, `findByStatusAndManagerId`
- `findOpenIncidentsByManagerId` — filtre `status IN ('REPORTED', 'UNDER_INVESTIGATION')`
- `findByDateRangeAndManagerId`
- `countByVehicleId`, `countByDriverId`
- `getTotalCostByVehicleId`, `getTotalCostByDriverId` — `COALESCE(SUM(cost), 0)` pour éviter les NULL

##### 6. `FuelRechargeR2dbcRepository.java`
Étend `ReactiveCrudRepository<FuelRechargeEntity, UUID>`. Méthodes :
- `findByVehicleId`, `findByDriverId` — dérivées
- `findAllByManagerId`, `findByDateRangeAndManagerId` — JOIN sur `fleet.vehicles`
- `getTotalQuantityByVehicleId`, `getTotalCostByVehicleId` — `COALESCE(SUM(...), 0)`

---

#### Adapters de persistance — `infrastructure/adapters/outbound/persistence/`

##### 7. `MaintenancePersistenceAdapter.java`
Implémente `MaintenancePersistencePort`. Conversion bidirectionnelle `Entity ↔ Domain` :
- `toDomain` : reconstruit `Coordinates` si longitude ET latitude présents
- `toEntity` : décompose `Coordinates` en deux colonnes séparées
- `save` : génère un UUID et marque `isNew = true` si l'entité est nouvelle

##### 8. `IncidentPersistenceAdapter.java`
Implémente `IncidentPersistencePort`. Conversion la plus complexe :
- `toDomain` : conversion `String → Enum` pour Type, Severity, Status ; restauration complète de l'état (report, policeReportNumber, insuranceClaimNumber, witness) via les setters/méthodes de l'entité domaine
- `toEntity` : conversion `Enum → String` pour tous les enums ; statut par défaut `REPORTED` si null

##### 9. `FuelRechargePersistenceAdapter.java`
Implémente `FuelRechargePersistencePort`. Conversion `StationName` avec fallback `OTHER` si valeur inconnue en base (robustesse).

---

#### Adapter événementiel — `infrastructure/adapters/outbound/messaging/`

##### 10. `KafkaOperationEventAdapter.java`
Implémente `OperationEventPort` via `ReactiveKafkaProducerTemplate<String, Object>` (bean existant dans `KafkaConfig`).
- Topic configurable via `${application.kafka.topics.operation-events:operation-events-topic}` (valeur par défaut si non défini dans `application.yml`)
- `publishMaintenanceCreated` → envoie sur le topic avec `maintenanceId` comme clé
- `publishIncidentReported` → log `🚨 CRITIQUE` si `isCritical = true`
- Logs `DEBUG` sur le succès (offset Kafka), `ERROR` sur l'échec

---

#### Migration Liquibase — `resources/db/changelog/changes/`

##### 11. `010-operations-tables.sql`
Changeset `operations-team:create-operations-tables-v1`. Crée les 3 tables dans le schéma `fleet` :

**`fleet.maintenances`** : 12 colonnes, FK vers `fleet.vehicles` (CASCADE) et `fleet.drivers` (SET NULL). Index sur `vehicle_id`, `driver_id`, `date_time DESC`.

**`fleet.incidents`** : 20 colonnes, contraintes CHECK sur `type`, `severity`, `status`. FK vers `fleet.vehicles` (CASCADE) et `fleet.drivers` (SET NULL). Index sur `vehicle_id`, `driver_id`, `status`, `severity`, `incident_date_time DESC`.

**`fleet.fuel_recharges`** : 11 colonnes, contrainte CHECK sur `station_name`. FK vers `fleet.vehicles` (CASCADE) et `fleet.drivers` (SET NULL). Index sur `vehicle_id`, `driver_id`, `recharge_date_time DESC`.

##### 12. `db.changelog-master.yaml` — mis à jour
Ajout de l'entrée `010-operations-tables.sql` en position 10 dans la chaîne de migration.

---

### Récapitulatif des fichiers créés/modifiés en Phase 4

```
src/main/java/com/yowyob/fleet/infrastructure/
  adapters/outbound/
    persistence/
      entity/
        MaintenanceEntity.java              ✅ créé
        IncidentEntity.java                 ✅ créé
        FuelRechargeEntity.java             ✅ créé
      repository/
        MaintenanceR2dbcRepository.java     ✅ créé
        IncidentR2dbcRepository.java        ✅ créé
        FuelRechargeR2dbcRepository.java    ✅ créé
      MaintenancePersistenceAdapter.java    ✅ créé
      IncidentPersistenceAdapter.java       ✅ créé
      FuelRechargePersistenceAdapter.java   ✅ créé
    messaging/
      KafkaOperationEventAdapter.java       ✅ créé

src/main/resources/db/changelog/
  changes/
    010-operations-tables.sql              ✅ créé
  db.changelog-master.yaml                ✅ mis à jour (ajout entrée 010)
```

**Zéro erreur de compilation sur les 10 fichiers Java.**

---

### Décisions techniques prises

1. **Enums stockés en VARCHAR** — pas d'ENUMs PostgreSQL natifs (cohérence avec le reste du projet qui évite les ENUMs PG pour simplifier les migrations R2DBC).
2. **Coordonnées en deux colonnes séparées** (`longitude`, `latitude`) — pas de type PostGIS `POINT` pour éviter la dépendance à l'extension PostGIS dans les entités R2DBC. La conversion vers `Coordinates` est faite dans l'adapter.
3. **`COALESCE(SUM(...), 0)` dans les agrégats** — évite les `null` dans les `Mono<BigDecimal>` des KPIs, cohérent avec le `.defaultIfEmpty(ZERO)` des services.
4. **Topic Kafka avec valeur par défaut** — `${application.kafka.topics.operation-events:operation-events-topic}` permet au projet de démarrer sans configuration supplémentaire dans `application.yml`.
5. **Pas de MapStruct pour les entités opérations** — la conversion Entity ↔ Domain est simple et directe, pas besoin d'un mapper généré. Cohérent avec la décision de garder les adapters lisibles.

---

### Prochaine étape

**Phase 5 — Infrastructure : API REST** : création des controllers WebFlux, DTOs de requête/réponse, et documentation Swagger pour les trois agrégats.

---

## ✅ Phase 5 — Infrastructure : API REST (Controllers + DTOs + Swagger)

**Date de réalisation :** 27 mai 2026
**Couche impactée :** `infrastructure/adapters/inbound/rest/`, `infrastructure/adapters/inbound/rest/dto/`, `infrastructure/config/OpenApiConfig.java`
**Compilation Maven :** ✅ `mvn compile` — Exit Code 0, zéro erreur

### Objectif

Exposer les trois agrégats du module Opérations via des controllers WebFlux documentés Swagger, avec DTOs de requête/réponse validés, sécurité par rôle, et intégration dans l'ordre des tags Swagger existant.

---

### Fichiers créés

#### DTOs — `infrastructure/adapters/inbound/rest/dto/`

##### 1. `MaintenanceRequest.java`
Record de requête pour la création et la mise à jour d'une maintenance. Annotations de validation Jakarta : `@NotBlank` sur `subject`, `@NotNull` sur `vehicleId`, `@PositiveOrZero` sur `cost`. Annotations `@Schema` Swagger sur tous les champs avec exemples.

##### 2. `MaintenanceResponse.java`
Record de réponse avec DTO imbriqué `LocationDto(longitude, latitude, locationName)`. Méthode de fabrique statique `from(Maintenance)` qui reconstruit la localisation depuis le `Coordinates` du domaine.

##### 3. `IncidentRequest.java`
Record de requête pour la déclaration d'un incident. Champs : type (obligatoire), description, severity (optionnel — défaut MEDIUM), cost, vehicleId (obligatoire), driverId, coordonnées GPS, witnessName, witnessContact, reportedBy. Annotations `allowableValues` sur les enums pour Swagger.

##### 4. `IncidentUpdateRequest.java`
Record de mise à jour partielle — tous les champs optionnels. Couvre : description, severity, cost, report, witness, policeReportNumber, insuranceClaimNumber, coordonnées GPS.

##### 5. `IncidentStatusRequest.java`
Record minimal pour la transition de statut : un seul champ `status` avec `@NotNull`.

##### 6. `IncidentResponse.java`
Record de réponse complet avec deux DTOs imbriqués : `LocationDto` (réutilisé depuis `MaintenanceResponse`) et `WitnessDto(name, contact)`. Expose les champs calculés `isCritical` et `isOpen` depuis les méthodes métier de l'entité. Méthode de fabrique `from(Incident)`.

##### 7. `FuelRechargeRequest.java`
Record de requête avec `@Positive` sur `quantity` et `@PositiveOrZero` sur `price`. Enum `StationName` avec `allowableValues` Swagger.

##### 8. `FuelRechargeResponse.java`
Record de réponse qui expose le champ calculé `unitCost` (résultat de `f.unitCost()` — méthode métier de l'entité domaine). Méthode de fabrique `from(FuelRecharge)`.

---

#### Controllers WebFlux — `infrastructure/adapters/inbound/rest/`

##### 9. `MaintenanceController.java`

**Base URL :** `/api/v1/operations/maintenances`
**Tag Swagger :** `14. Operations | Maintenance`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| POST | `/` | MANAGER, DRIVER | Déclarer une maintenance |
| GET | `/` | MANAGER | Lister mes maintenances |
| GET | `/{id}` | MANAGER, DRIVER | Détail d'une maintenance |
| GET | `/vehicle/{vehicleId}` | MANAGER | Maintenances d'un véhicule |
| GET | `/driver/{driverId}` | MANAGER | Maintenances d'un chauffeur |
| GET | `/range?start=&end=` | MANAGER | Maintenances par plage de dates |
| PUT | `/{id}` | MANAGER | Mettre à jour une maintenance |
| DELETE | `/{id}` | MANAGER | Supprimer une maintenance |

##### 10. `IncidentController.java`

**Base URL :** `/api/v1/operations/incidents`
**Tag Swagger :** `15. Operations | Incidents`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| POST | `/` | MANAGER, DRIVER | Déclarer un incident |
| GET | `/` | MANAGER | Lister mes incidents |
| GET | `/{id}` | MANAGER, DRIVER | Détail d'un incident |
| GET | `/vehicle/{vehicleId}` | MANAGER | Incidents d'un véhicule |
| GET | `/driver/{driverId}` | MANAGER | Incidents d'un chauffeur |
| GET | `/open` | MANAGER | Incidents ouverts (tableau de bord) |
| GET | `/filter?type=&severity=&status=` | MANAGER | Filtrage multi-critères |
| GET | `/range?start=&end=` | MANAGER | Incidents par plage de dates |
| GET | `/vehicle/{vehicleId}/cost` | MANAGER | Coût total des incidents d'un véhicule |
| PUT | `/{id}` | MANAGER | Mettre à jour un incident |
| PATCH | `/{id}/status` | MANAGER | Changer le statut (machine à états) |
| DELETE | `/{id}` | MANAGER | Supprimer un incident |

##### 11. `FuelRechargeController.java`

**Base URL :** `/api/v1/operations/fuel-recharges`
**Tag Swagger :** `16. Operations | Recharges Carburant`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| POST | `/` | DRIVER, MANAGER | Enregistrer une recharge |
| GET | `/` | MANAGER | Lister mes recharges |
| GET | `/{id}` | MANAGER, DRIVER | Détail d'une recharge |
| GET | `/vehicle/{vehicleId}` | MANAGER | Recharges d'un véhicule |
| GET | `/driver/{driverId}` | MANAGER | Recharges d'un chauffeur |
| GET | `/range?start=&end=` | MANAGER | Recharges par plage de dates |
| GET | `/vehicle/{vehicleId}/stats` | MANAGER | Stats carburant (litres + coût total) |
| PUT | `/{id}` | MANAGER | Mettre à jour une recharge |
| DELETE | `/{id}` | MANAGER | Supprimer une recharge |

---

#### Fichier modifié — `OpenApiConfig.java`

Ajout de 3 nouvelles constantes de tags :
```java
TAG_OPS_MAINTENANCE = "14. Operations | Maintenance"
TAG_OPS_INCIDENTS   = "15. Operations | Incidents"
TAG_OPS_FUEL        = "16. Operations | Recharges Carburant"
```
Ajout dans la liste d'ordre `sortTagsAlphabetically` après `TAG_PAYMENTS` — les nouveaux tags apparaissent en positions 14, 15, 16 dans Swagger UI.

---

### Récapitulatif des fichiers créés/modifiés en Phase 5

```
src/main/java/com/yowyob/fleet/infrastructure/
  adapters/inbound/rest/
    dto/
      MaintenanceRequest.java      ✅ créé
      MaintenanceResponse.java     ✅ créé
      IncidentRequest.java         ✅ créé
      IncidentUpdateRequest.java   ✅ créé
      IncidentStatusRequest.java   ✅ créé
      IncidentResponse.java        ✅ créé
      FuelRechargeRequest.java     ✅ créé
      FuelRechargeResponse.java    ✅ créé
    MaintenanceController.java     ✅ créé
    IncidentController.java        ✅ créé
    FuelRechargeController.java    ✅ créé
  config/
    OpenApiConfig.java             ✅ mis à jour (3 nouveaux tags)
```

**Compilation Maven :** `mvn compile` → Exit Code 0, zéro erreur.

---

### Décisions techniques prises

1. **Méthodes de fabrique `from()` dans les DTOs de réponse** — évite les mappers externes, la conversion est colocalisée avec la structure de la réponse. Pattern cohérent avec `FleetStatsResponse` existant.
2. **`LocationDto` partagé** — `IncidentResponse` réutilise `MaintenanceResponse.LocationDto` pour éviter la duplication. Les deux agrégats ont la même structure de localisation.
3. **Endpoint `/filter` unifié pour les incidents** — un seul endpoint avec paramètres optionnels plutôt que 3 endpoints séparés (`/by-type`, `/by-severity`, `/by-status`). Plus propre côté API.
4. **`FuelStatsResponse` comme record interne** du controller — DTO simple utilisé uniquement par cet endpoint, pas besoin d'un fichier séparé.
5. **`@Valid` sur tous les `@RequestBody`** — active la validation Jakarta automatiquement, les erreurs sont gérées par le `GlobalExceptionHandler` existant (`WebExchangeBindException`).

---

### Prochaine étape

**Phase 6 — KPIs Opérationnels & Intégration inter-modules** : enrichissement des KPIs Fleet Manager, synchronisation automatique des paramètres véhicule, et branchement sur le module Notification existant.

---

## ✅ Phase 6 — KPIs Opérationnels & Intégration inter-modules

**Date de réalisation :** 27 mai 2026
**Couche impactée :** `application/service/`, `infrastructure/adapters/inbound/messaging/`, `infrastructure/adapters/inbound/rest/dto/`, `resources/application.yml`
**Compilation Maven :** ✅ `mvn compile` — Exit Code 0, zéro erreur

### Objectif

Connecter le module Opérations aux modules existants (Fleet Manager, Notifications) pour enrichir les tableaux de bord et déclencher des alertes automatiques lors de la création de maintenances et d'incidents.

---

### Fichiers modifiés

#### 1. `ManagerKpiResponse.java` — enrichi avec 5 nouveaux champs

Ajout des KPIs opérations terrain dans le record de réponse existant :

| Champ | Type | Description |
|---|---|---|
| `maintenancesThisMonth` | `long` | Maintenances enregistrées ce mois-ci |
| `openIncidents` | `long` | Incidents encore ouverts (REPORTED ou UNDER_INVESTIGATION) |
| `totalIncidentCost` | `BigDecimal` | Coût total de tous les incidents (tous véhicules du manager) |
| `totalFuelLitersThisMonth` | `BigDecimal` | Litres rechargés ce mois-ci |
| `totalFuelCostThisMonth` | `BigDecimal` | Coût total des recharges ce mois-ci |

Ajout d'une méthode de fabrique `legacy(...)` pour la compatibilité ascendante (les 4 champs existants avec valeurs par défaut à zéro pour les nouveaux).

#### 2. `FleetManagerService.java` — `getManagerKpis` enrichi

Refactoring complet de la méthode `getManagerKpis` :

**Nouveaux repositories injectés :**
- `MaintenanceR2dbcRepository`
- `IncidentR2dbcRepository`
- `FuelRechargeR2dbcRepository`

**Logique ajoutée :**
- `maintenancesThisMonth` : filtre applicatif sur `findAllByManagerId` avec `isAfter(startOfMonth)` — compte les maintenances du mois courant
- `openIncidents` : appel direct à `findOpenIncidentsByManagerId` (requête SQL déjà optimisée)
- `totalIncidentCost` : itération sur les véhicules du manager → `getTotalCostByVehicleId` pour chacun → `reduce(BigDecimal.ZERO, BigDecimal::add)`
- `totalFuelLitersThisMonth` / `totalFuelCostThisMonth` : filtre applicatif sur `findAllByManagerId` avec `isAfter(startOfMonth)` → `reduce`

La méthode utilise un `Mono.zip` à 6 éléments pour les compteurs, puis un second `Mono.zip` à 3 éléments pour les agrégats financiers (chaîne `flatMap`).

#### 3. `application.yml` — 2 ajouts

- Topic Kafka : `application.kafka.topics.operation-events: operation-events-topic`
- Templates de notification : `maintenance-created: 8`, `incident-reported: 9`, `incident-critical: 10`

---

### Fichier créé

#### 4. `OperationEventConsumer.java`

**Chemin :** `infrastructure/adapters/inbound/messaging/OperationEventConsumer.java`

Consumer Kafka qui écoute le topic `operation-events-topic` et traite les deux types d'événements publiés par `KafkaOperationEventAdapter`.

**Activation conditionnelle :** `@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")` — le consumer ne démarre que si Kafka est configuré, évitant les erreurs au démarrage en environnement de développement sans Kafka.

**Dispatcher `handleOperationEvent`** : reçoit un `Object` et route via `instanceof` vers le bon handler selon le type d'événement.

**`handleMaintenanceCreated`** :
1. Vérifie que `fleetManagerId` est présent (log warn + skip si absent)
2. Persiste une notification in-app via `NotificationHistoryRepositoryPort.save()` avec `Notification.builder()`
3. Envoie une notification push (template 8) via `NotificationApiClient` en fire & forget

**`handleIncidentReported`** :
1. Même vérification `fleetManagerId`
2. Titre adapté selon `isCritical` : `"🚨 Incident CRITIQUE signalé"` vs `"Incident signalé"`
3. Template adapté : 10 (critique) vs 9 (normal)
4. Notification in-app + push en fire & forget

**Dégradé gracieux** : toutes les erreurs (persistance, push) sont loggées et absorbées via `.onErrorResume(e -> Mono.empty())` — un échec de notification ne fait jamais échouer le traitement de l'événement.

---

### Récapitulatif des fichiers créés/modifiés en Phase 6

```
src/main/java/com/yowyob/fleet/
  application/service/
    FleetManagerService.java                          ✅ mis à jour (KPIs enrichis)
  infrastructure/
    adapters/inbound/
      messaging/
        OperationEventConsumer.java                   ✅ créé
      rest/dto/
        ManagerKpiResponse.java                       ✅ mis à jour (5 nouveaux champs)

src/main/resources/
  application.yml                                     ✅ mis à jour (topic + templates)
```

**Compilation Maven :** `mvn compile` → Exit Code 0, zéro erreur.

---

### Décisions techniques prises

1. **Filtre applicatif pour les KPIs mensuels** — les filtres `isAfter(startOfMonth)` sont appliqués en mémoire sur le flux réactif plutôt qu'en SQL. Acceptable pour des volumes de données de flotte (quelques centaines d'enregistrements par manager). Si les volumes augmentent, les requêtes `@Query` des repositories peuvent être enrichies avec des paramètres de date.
2. **`@ConditionalOnProperty` sur le consumer** — le consumer Kafka ne démarre que si `spring.kafka.bootstrap-servers` est défini. Évite les erreurs de démarrage en environnement de développement sans Kafka.
3. **Dispatcher `instanceof`** — un seul `@KafkaListener` avec dispatch par type plutôt que deux listeners séparés. Simplifie la configuration Kafka et évite la duplication de la configuration du consumer group.
4. **Compatibilité ascendante de `ManagerKpiResponse`** — la méthode `legacy()` permet aux tests ou appels existants de continuer à fonctionner sans les nouveaux champs opérations.
5. **Templates de notification numérotés 8, 9, 10** — continuité avec les templates existants (1-7) définis dans `application.yml`.

---

## 🎉 Intégration complète — Toutes les phases terminées

### Bilan global

| Phase | Fichiers créés | Fichiers modifiés | Erreurs |
|---|---|---|---|
| Phase 1 — Domaine | 5 | 0 | 0 |
| Phase 2 — Ports | 7 | 0 | 0 |
| Phase 3 — Services | 3 | 0 | 0 |
| Phase 4 — Infrastructure Persistance | 10 | 2 | 0 |
| Phase 5 — API REST | 11 | 1 | 0 |
| Phase 6 — Intégration | 1 | 4 | 0 |
| **TOTAL** | **37** | **7** | **0** |

### Architecture finale respectée

✅ Domaine 100% pur (aucune annotation Spring/JPA dans `domain/`)
✅ Tous les IDs en `UUID`
✅ Paradigme réactif partout (`Mono<T>`, `Flux<T>`)
✅ Invariants métier dans les constructeurs des entités
✅ Commands en records imbriqués dans les Use Cases
✅ Exceptions typées avec codes `OPR_xxx`
✅ Adapters sans logique métier
✅ Tables dans le schéma `fleet` (migration Liquibase)
✅ Compilation Maven sans erreur
