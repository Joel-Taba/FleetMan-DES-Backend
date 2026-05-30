# Plan d'Intégration — Module Opérations Terrain
## Apports du projet `Fleetman Backend` vers `fleet-management-back`

> **Objectif :** Enrichir le projet principal `fleet-management-back` (architecture hexagonale réactive) en y intégrant les concepts métier solides identifiés dans le projet `Fleetman Backend`, tout en respectant scrupuleusement l'architecture hexagonale réactive déjà en place (WebFlux, R2DBC, Mono/Flux, UUID).

---

## Analyse des points positifs identifiés dans `Fleetman Backend`

Avant de détailler les phases, voici les atouts concrets du projet `Fleetman Backend` qui justifient leur intégration.

### ✅ Domaine métier riche et absent du projet principal

Le projet `Fleetman Backend` couvre trois agrégats métier **complètement absents** de `fleet-management-back` :

| Agrégat | Valeur métier |
|---|---|
| **Maintenance** | Suivi des interventions techniques sur les véhicules (objet, coût, rapport, localisation GPS, chauffeur impliqué) |
| **Incident** | Gestion des événements terrain (accidents, pannes, vols) avec cycle de vie complet (REPORTED → RESOLVED → CLOSED) |
| **Recharge carburant** | Traçabilité des pleins (quantité, prix, station, localisation) avec calcul automatique du coût unitaire |

### ✅ Modèles de domaine purs et bien contraints

Les entités `Maintenance`, `Incident` et `FuelRecharge` appliquent des **invariants métier dans le constructeur** (validation à la création, jamais dans un service externe), ce qui est la bonne pratique DDD :
- Sujet de maintenance obligatoire et non vide
- Véhicule obligatoire pour toute opération
- Quantité de carburant strictement positive
- Prix non négatif

### ✅ Value Object `Coordinates` avec validation GPS

Le record `Coordinates(longitude, latitude)` valide les bornes géographiques (`-180/+180`, `-90/+90`) et fournit un `toString()` au format WKT (`POINT(lon lat)`). Ce concept est absent du domaine de `fleet-management-back` qui stocke les coordonnées de façon dispersée.

### ✅ Use Cases granulaires avec Commands typées

Les interfaces `ManageMaintenanceUseCase`, `ManageIncidentUseCase` et `ManageFuelRechargeUseCase` utilisent des **records Command imbriqués** (`CreateMaintenanceCommand`, `UpdateIncidentCommand`, etc.) — pattern propre qui évite les méthodes à longue liste de paramètres.

### ✅ Ports de sortie riches avec requêtes métier

Les ports `IncidentRepositoryPort` et `MaintenanceRepositoryPort` exposent des méthodes de requête métier avancées :
- `findByType()`, `findBySeverity()`, `findByStatus()`, `findOpenIncidents()`
- `getTotalCostByDriverId()`, `getTotalCostByVehicleId()`
- `countByDriverId()`, `countByVehicleId()`

Ces méthodes permettent des **tableaux de bord et KPIs opérationnels** directement depuis le domaine.

### ✅ Port événementiel découplé (`OperationEventPort`)

Le port `OperationEventPort` avec ses records `MaintenanceCreatedEvent` et `IncidentReportedEvent` permet de **publier des événements métier sans dépendre de Spring**. Ces événements transportent le `fleetManagerId` pour router les notifications — intégration naturelle avec le module Notification déjà présent dans `fleet-management-back`.

### ✅ Anti-Corruption Layer (`VehicleReferencePort`)

Le `VehicleReferencePort` isole le module Opérations du module Fleet via un record `VehicleRef(vehicleId, registrationNumber, fleetManagerId)`. Ce pattern évite le couplage direct entre bounded contexts et sera réutilisé tel quel.

### ✅ Cycle de vie des incidents bien modélisé

L'entité `Incident` implémente une **machine à états** claire avec des méthodes métier expressives :
- `resolve(report)` — interdit sur un incident déjà clôturé
- `close()` — horodate automatiquement la résolution
- `addWitness(name, contact)` — enrichissement progressif
- `isCritical()` — règle métier encapsulée

---

## Contraintes d'adaptation

Avant les phases, les points suivants doivent être adaptés pour respecter l'architecture de `fleet-management-back` :

| Point `Fleetman Backend` | Adaptation requise |
|---|---|
| IDs de type `Long` | → Remplacer par `UUID` (standard du projet principal) |
| Modèles avec getters/setters | → Convertir en **records Java immuables** (standard du projet principal) ou conserver des classes avec méthodes métier uniquement |
| Paradigme synchrone (`List`, `Optional`) | → Adapter en **réactif** (`Flux`, `Mono`) dans les ports et services |
| Package `com.polytechnique` | → Migrer vers `com.yowyob.fleet` |
| Migrations SQL absentes | → Créer les changesets Liquibase dans le schéma `fleet` |

---

## Phases d'implémentation

---

### 🔵 Phase 1 — Fondations du domaine Opérations

> **Objectif :** Poser les bases du nouveau bounded context `operations` dans le domaine de `fleet-management-back`, sans toucher à l'infrastructure.

**Durée estimée :** 1 session

#### 1.1 — Value Object `Coordinates`

Créer le record `Coordinates` dans `domain/model/` avec validation des bornes GPS et format WKT.

```
domain/model/Coordinates.java
```

**Contenu :**
- Record immuable `(double longitude, double latitude)`
- Validation dans le bloc compact (`-180/+180`, `-90/+90`)
- `toString()` → `"POINT(lon lat)"`

#### 1.2 — Entité de domaine `Maintenance`

Créer la classe `Maintenance` dans `domain/model/` adaptée au projet principal.

**Adaptations par rapport à `Fleetman Backend` :**
- IDs `UUID` au lieu de `Long`
- Conserver les invariants métier dans le constructeur
- Méthodes métier : `addReport(String)`, `updateCost(BigDecimal)`
- Références croisées par ID uniquement (`vehicleId: UUID`, `driverId: UUID`)

#### 1.3 — Entité de domaine `Incident`

Créer la classe `Incident` dans `domain/model/` avec la machine à états complète.

**Contenu :**
- Enums imbriqués : `Type`, `Severity`, `Status`
- Invariants constructeur (type et vehicleId obligatoires)
- Méthodes métier : `resolve()`, `close()`, `addWitness()`, `updateStatus()`, `isCritical()`

#### 1.4 — Entité de domaine `FuelRecharge`

Créer la classe `FuelRecharge` dans `domain/model/`.

**Contenu :**
- Enum `StationName` (TOTAL, SHELL, OILIBYA, CAMRAIL, OTHER)
- Invariants constructeur (quantité > 0, prix ≥ 0, véhicule obligatoire)
- Méthode métier : `unitCost()` — calcul du coût par litre

#### 1.5 — Exception métier `OperationException`

Créer `OperationException` dans `domain/exception/` en suivant le pattern des exceptions existantes (`VehicleException`, `DriverException`, etc.) avec des codes d'erreur typés (OPR_001 à OPR_015).

**Codes prévus :**
```
OPR_001 — Maintenance introuvable
OPR_002 — Incident introuvable
OPR_003 — Recharge introuvable
OPR_004 — Véhicule non trouvé pour l'opération
OPR_005 — Chauffeur non trouvé pour l'opération
OPR_006 — Coût négatif interdit
OPR_007 — Quantité de carburant invalide
OPR_008 — Incident déjà clôturé
OPR_009 — Transition de statut invalide
OPR_010 — Sujet de maintenance obligatoire
```

**Livrable Phase 1 :**
```
domain/
  model/
    Coordinates.java          ← nouveau
    Maintenance.java          ← nouveau
    Incident.java             ← nouveau
    FuelRecharge.java         ← nouveau
  exception/
    OperationException.java   ← nouveau
```

---

### 🔵 Phase 2 — Ports du domaine Opérations (Use Cases & Repository Ports)

> **Objectif :** Définir les contrats d'entrée (use cases) et de sortie (persistence, événements) du module Opérations, en réactif.

**Durée estimée :** 1 session

#### 2.1 — Port entrant : `ManageMaintenanceUseCase`

Interface dans `domain/ports/in/` avec records Command imbriqués.

**Méthodes (réactives) :**
```java
Mono<Maintenance> createMaintenance(CreateMaintenanceCommand cmd);
Mono<Maintenance> getById(UUID id);
Flux<Maintenance> getAll(UUID managerId);
Flux<Maintenance> getByVehicleId(UUID vehicleId);
Flux<Maintenance> getByDriverId(UUID driverId);
Flux<Maintenance> getByDateRange(LocalDateTime start, LocalDateTime end);
Mono<Long> countByDriverId(UUID driverId);
Mono<Maintenance> update(UpdateMaintenanceCommand cmd);
Mono<Void> delete(UUID id);
```

**Records Command :**
- `CreateMaintenanceCommand(String subject, BigDecimal cost, String report, Double longitude, Double latitude, String locationName, UUID vehicleId, UUID driverId)`
- `UpdateMaintenanceCommand(UUID maintenanceId, String subject, BigDecimal cost, String report, Double longitude, Double latitude, String locationName)`

#### 2.2 — Port entrant : `ManageIncidentUseCase`

Interface dans `domain/ports/in/` avec use cases avancés.

**Méthodes (réactives) :**
```java
Mono<Incident> createIncident(CreateIncidentCommand cmd);
Mono<Incident> getById(UUID id);
Flux<Incident> getAll(UUID managerId);
Flux<Incident> getByVehicleId(UUID vehicleId);
Flux<Incident> getByDriverId(UUID driverId);
Flux<Incident> getByType(Incident.Type type);
Flux<Incident> getBySeverity(Incident.Severity severity);
Flux<Incident> getByStatus(Incident.Status status);
Flux<Incident> getOpenIncidents(UUID managerId);
Mono<Incident> update(UpdateIncidentCommand cmd);
Mono<Incident> updateStatus(UUID id, Incident.Status status);
Mono<Long> countByVehicleId(UUID vehicleId);
Mono<BigDecimal> getTotalCostByVehicleId(UUID vehicleId);
Mono<Void> delete(UUID id);
```

#### 2.3 — Port entrant : `ManageFuelRechargeUseCase`

Interface dans `domain/ports/in/`.

**Méthodes (réactives) :**
```java
Mono<FuelRecharge> createFuelRecharge(CreateFuelRechargeCommand cmd);
Mono<FuelRecharge> getById(UUID id);
Flux<FuelRecharge> getAll(UUID managerId);
Flux<FuelRecharge> getByVehicleId(UUID vehicleId);
Flux<FuelRecharge> getByDriverId(UUID driverId);
Flux<FuelRecharge> getByDateRange(LocalDateTime start, LocalDateTime end);
Mono<FuelRecharge> update(UpdateFuelRechargeCommand cmd);
Mono<Void> delete(UUID id);
```

#### 2.4 — Port sortant : `MaintenancePersistencePort`

Interface dans `domain/ports/out/` — contrat de persistance réactif.

#### 2.5 — Port sortant : `IncidentPersistencePort`

Interface dans `domain/ports/out/` avec requêtes métier avancées (findByType, findBySeverity, getTotalCost...).

#### 2.6 — Port sortant : `FuelRechargePersistencePort`

Interface dans `domain/ports/out/`.

#### 2.7 — Port sortant événementiel : `OperationEventPort`

Interface dans `domain/ports/out/` avec records événements imbriqués.

**Events :**
- `MaintenanceCreatedEvent(UUID maintenanceId, String subject, UUID vehicleId, String vehicleRegistration, UUID driverId, UUID fleetManagerId)`
- `IncidentReportedEvent(UUID incidentId, String incidentType, String severity, UUID vehicleId, String vehicleRegistration, UUID driverId, UUID fleetManagerId, boolean isCritical)`

**Méthodes :**
```java
Mono<Void> publishMaintenanceCreated(MaintenanceCreatedEvent event);
Mono<Void> publishIncidentReported(IncidentReportedEvent event);
```

**Livrable Phase 2 :**
```
domain/ports/
  in/
    ManageMaintenanceUseCase.java    ← nouveau
    ManageIncidentUseCase.java       ← nouveau
    ManageFuelRechargeUseCase.java   ← nouveau
  out/
    MaintenancePersistencePort.java  ← nouveau
    IncidentPersistencePort.java     ← nouveau
    FuelRechargePersistencePort.java ← nouveau
    OperationEventPort.java          ← nouveau
```

---

### 🔵 Phase 3 — Services applicatifs (couche application)

> **Objectif :** Implémenter la logique métier des trois agrégats dans la couche `application/service/`, en réactif, en s'appuyant sur les ports définis en Phase 2.

**Durée estimée :** 1-2 sessions

#### 3.1 — `MaintenanceService`

Service `@Service` implémentant `ManageMaintenanceUseCase`.

**Logique métier :**
- Vérification de l'existence du véhicule via `VehiclePersistencePort` avant création
- Résolution du nom du chauffeur via `DriverPersistencePort` si `driverId` fourni
- Construction de l'objet `Maintenance` avec invariants
- Persistance via `MaintenancePersistencePort`
- Publication de `MaintenanceCreatedEvent` via `OperationEventPort` → déclenche une notification au Fleet Manager

#### 3.2 — `IncidentService`

Service `@Service` implémentant `ManageIncidentUseCase`.

**Logique métier :**
- Vérification véhicule et chauffeur
- Construction de l'objet `Incident` avec sévérité par défaut `MEDIUM`
- Persistance et publication de `IncidentReportedEvent`
- Pour les incidents `CRITICAL` ou `HIGH` : publication prioritaire (flag `isCritical = true`)
- Méthode `updateStatus` : délégation à la machine à états de l'entité (`resolve()`, `close()`)

#### 3.3 — `FuelRechargeService`

Service `@Service` implémentant `ManageFuelRechargeUseCase`.

**Logique métier :**
- Vérification véhicule et chauffeur
- Construction de `FuelRecharge` avec invariants
- Mise à jour du `fuelLevel` dans `operational_parameters` du véhicule après chaque recharge (intégration avec `OperationalParameterR2dbcRepository` existant)

**Livrable Phase 3 :**
```
application/service/
  MaintenanceService.java     ← nouveau
  IncidentService.java        ← nouveau
  FuelRechargeService.java    ← nouveau
```

---

### 🔵 Phase 4 — Infrastructure : Persistance (adapters outbound)

> **Objectif :** Implémenter les adapters de persistance R2DBC pour les trois agrégats, avec migrations Liquibase.

**Durée estimée :** 1-2 sessions

#### 4.1 — Migration Liquibase : tables opérations

Créer un nouveau changeset Liquibase (ex: `010-operations-tables.sql`) dans `resources/db/changelog/changes/`.

**Tables à créer dans le schéma `fleet` :**

```sql
-- Maintenances
CREATE TABLE fleet.maintenances (
  id UUID PRIMARY KEY,
  subject VARCHAR(255) NOT NULL,
  cost NUMERIC,
  date_time TIMESTAMP NOT NULL DEFAULT now(),
  report TEXT,
  longitude NUMERIC,
  latitude NUMERIC,
  location_name VARCHAR(255),
  vehicle_id UUID NOT NULL REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
  vehicle_registration VARCHAR(50),
  driver_id UUID REFERENCES fleet.drivers(user_id) ON DELETE SET NULL,
  driver_full_name VARCHAR(255)
);

-- Incidents
CREATE TABLE fleet.incidents (
  id UUID PRIMARY KEY,
  type VARCHAR(50) NOT NULL CHECK (type IN ('ACCIDENT','BREAKDOWN','THEFT','VANDALISM','TRAFFIC_VIOLATION','OTHER')),
  description TEXT,
  severity VARCHAR(50) DEFAULT 'MEDIUM' CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
  incident_date_time TIMESTAMP NOT NULL DEFAULT now(),
  longitude NUMERIC,
  latitude NUMERIC,
  cost NUMERIC,
  status VARCHAR(50) DEFAULT 'REPORTED' CHECK (status IN ('REPORTED','UNDER_INVESTIGATION','RESOLVED','CLOSED')),
  report TEXT,
  witness_name VARCHAR(255),
  witness_contact VARCHAR(255),
  police_report_number VARCHAR(100),
  insurance_claim_number VARCHAR(100),
  reported_by VARCHAR(255),
  resolved_at TIMESTAMP,
  vehicle_id UUID NOT NULL REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
  vehicle_registration VARCHAR(50),
  driver_id UUID REFERENCES fleet.drivers(user_id) ON DELETE SET NULL,
  driver_full_name VARCHAR(255)
);

-- Recharges carburant
CREATE TABLE fleet.fuel_recharges (
  id UUID PRIMARY KEY,
  quantity NUMERIC NOT NULL,
  price NUMERIC NOT NULL,
  recharge_date_time TIMESTAMP NOT NULL DEFAULT now(),
  longitude NUMERIC,
  latitude NUMERIC,
  station_name VARCHAR(50) CHECK (station_name IN ('TOTAL','SHELL','OILIBYA','CAMRAIL','OTHER')),
  vehicle_id UUID NOT NULL REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
  vehicle_registration VARCHAR(50),
  driver_id UUID REFERENCES fleet.drivers(user_id) ON DELETE SET NULL,
  driver_full_name VARCHAR(255)
);
```

#### 4.2 — Entités R2DBC

Créer les entités R2DBC dans `infrastructure/adapters/outbound/persistence/entity/` :
- `MaintenanceEntity`
- `IncidentEntity`
- `FuelRechargeEntity`

#### 4.3 — Repositories R2DBC

Créer les interfaces Spring Data R2DBC dans `infrastructure/adapters/outbound/persistence/repository/` :
- `MaintenanceR2dbcRepository`
- `IncidentR2dbcRepository`
- `FuelRechargeR2dbcRepository`

Avec les requêtes `@Query` nécessaires pour les filtres métier (par type, sévérité, statut, plage de dates, coût total).

#### 4.4 — Adapters de persistance

Créer les adapters implémentant les ports de sortie :
- `MaintenancePersistenceAdapter implements MaintenancePersistencePort`
- `IncidentPersistenceAdapter implements IncidentPersistencePort`
- `FuelRechargePersistenceAdapter implements FuelRechargePersistencePort`

Chaque adapter assure la conversion `Entity ↔ Domain` (sans MapStruct pour les entités opérations, la conversion étant simple).

#### 4.5 — Adapter événementiel : `KafkaOperationEventAdapter`

Implémenter `OperationEventPort` via Kafka (réutiliser la config Kafka existante) :
- `publishMaintenanceCreated` → topic `operation-events-topic`
- `publishIncidentReported` → topic `operation-events-topic`

En fallback, un `LogOperationEventAdapter` (simple log) pour les environnements sans Kafka.

**Livrable Phase 4 :**
```
infrastructure/adapters/outbound/
  persistence/
    entity/
      MaintenanceEntity.java        ← nouveau
      IncidentEntity.java           ← nouveau
      FuelRechargeEntity.java       ← nouveau
    repository/
      MaintenanceR2dbcRepository.java   ← nouveau
      IncidentR2dbcRepository.java      ← nouveau
      FuelRechargeR2dbcRepository.java  ← nouveau
    MaintenancePersistenceAdapter.java  ← nouveau
    IncidentPersistenceAdapter.java     ← nouveau
    FuelRechargePersistenceAdapter.java ← nouveau
  messaging/
    KafkaOperationEventAdapter.java     ← nouveau

resources/db/changelog/changes/
  010-operations-tables.sql             ← nouveau
```

---

### 🔵 Phase 5 — Infrastructure : API REST (adapters inbound)

> **Objectif :** Exposer les trois agrégats via des controllers WebFlux documentés Swagger, en suivant les conventions de nommage et de sécurité du projet principal.

**Durée estimée :** 1 session

#### 5.1 — DTOs de requête et réponse

Créer dans `infrastructure/adapters/inbound/rest/dto/` :

**Requêtes :**
- `MaintenanceRequest` — création/mise à jour d'une maintenance
- `IncidentRequest` — création d'un incident
- `IncidentUpdateRequest` — mise à jour partielle (statut, rapport, numéros police/assurance)
- `FuelRechargeRequest` — création/mise à jour d'une recharge

**Réponses :**
- `MaintenanceResponse` — avec `locationDto` (longitude, latitude, locationName)
- `IncidentResponse` — avec statut, sévérité, coût total, dates
- `FuelRechargeResponse` — avec `unitCost` calculé

#### 5.2 — `MaintenanceController`

Controller WebFlux dans `infrastructure/adapters/inbound/rest/`.

**Tag Swagger :** `"12. Operations | Maintenance"`

**Endpoints :**
```
POST   /operations/maintenances                          → MANAGER/DRIVER
GET    /operations/maintenances                          → MANAGER
GET    /operations/maintenances/{id}                     → MANAGER/DRIVER
GET    /operations/maintenances/vehicle/{vehicleId}      → MANAGER
GET    /operations/maintenances/driver/{driverId}        → MANAGER
PUT    /operations/maintenances/{id}                     → MANAGER
DELETE /operations/maintenances/{id}                     → MANAGER
```

#### 5.3 — `IncidentController`

**Tag Swagger :** `"13. Operations | Incidents"`

**Endpoints :**
```
POST   /operations/incidents                             → MANAGER/DRIVER
GET    /operations/incidents                             → MANAGER
GET    /operations/incidents/{id}                        → MANAGER/DRIVER
GET    /operations/incidents/vehicle/{vehicleId}         → MANAGER
GET    /operations/incidents/driver/{driverId}           → MANAGER
GET    /operations/incidents/open                        → MANAGER
PATCH  /operations/incidents/{id}/status                 → MANAGER
PUT    /operations/incidents/{id}                        → MANAGER
DELETE /operations/incidents/{id}                        → MANAGER
```

#### 5.4 — `FuelRechargeController`

**Tag Swagger :** `"14. Operations | Carburant"`

**Endpoints :**
```
POST   /operations/fuel-recharges                        → DRIVER
GET    /operations/fuel-recharges                        → MANAGER
GET    /operations/fuel-recharges/{id}                   → MANAGER/DRIVER
GET    /operations/fuel-recharges/vehicle/{vehicleId}    → MANAGER
GET    /operations/fuel-recharges/driver/{driverId}      → MANAGER
PUT    /operations/fuel-recharges/{id}                   → MANAGER
DELETE /operations/fuel-recharges/{id}                   → MANAGER
```

**Livrable Phase 5 :**
```
infrastructure/adapters/inbound/rest/
  dto/
    MaintenanceRequest.java       ← nouveau
    MaintenanceResponse.java      ← nouveau
    IncidentRequest.java          ← nouveau
    IncidentUpdateRequest.java    ← nouveau
    IncidentResponse.java         ← nouveau
    FuelRechargeRequest.java      ← nouveau
    FuelRechargeResponse.java     ← nouveau
  MaintenanceController.java      ← nouveau
  IncidentController.java         ← nouveau
  FuelRechargeController.java     ← nouveau
```

---

### 🔵 Phase 6 — KPIs Opérationnels & Intégration avec les modules existants

> **Objectif :** Connecter le module Opérations aux modules existants (Véhicules, Notifications, Fleet Manager) pour enrichir les tableaux de bord.

**Durée estimée :** 1 session

#### 6.1 — Enrichissement des KPIs Fleet Manager

Mettre à jour `FleetManagerService` pour inclure dans le endpoint `/kpis` :
- Nombre total de maintenances en cours (mois courant)
- Nombre d'incidents ouverts
- Coût total des incidents par flotte
- Coût total des maintenances par flotte
- Consommation carburant totale (litres) par flotte

#### 6.2 — Enrichissement des paramètres de maintenance du véhicule

Après chaque création de `Maintenance`, mettre à jour automatiquement `maintenance_parameters.last_maintenance_at` du véhicule concerné.

#### 6.3 — Mise à jour du niveau carburant après recharge

Après chaque `FuelRecharge`, mettre à jour `operational_parameters.fuel_level` du véhicule (calcul basé sur `tankCapacity` et `quantity`).

#### 6.4 — Intégration Notifications

Consommer les événements `MaintenanceCreatedEvent` et `IncidentReportedEvent` dans le module Notification existant pour envoyer des alertes au Fleet Manager concerné.

**Livrable Phase 6 :**
```
application/service/
  FleetManagerService.java    ← mise à jour (ajout KPIs opérations)
  MaintenanceService.java     ← mise à jour (sync maintenance_parameters)
  FuelRechargeService.java    ← mise à jour (sync operational_parameters)
  
infrastructure/adapters/inbound/rest/
  FleetManagerController.java ← mise à jour (endpoint /kpis enrichi)
```

---

## Récapitulatif des phases

| Phase | Contenu | Couche | Priorité |
|---|---|---|---|
| **Phase 1** | Modèles de domaine (Maintenance, Incident, FuelRecharge, Coordinates, OperationException) | Domain | 🔴 Critique |
| **Phase 2** | Ports entrants (Use Cases) et sortants (Persistence, Events) | Domain | 🔴 Critique |
| **Phase 3** | Services applicatifs avec logique métier réactive | Application | 🔴 Critique |
| **Phase 4** | Adapters R2DBC, migrations Liquibase, adapter Kafka | Infrastructure | 🟠 Haute |
| **Phase 5** | Controllers WebFlux, DTOs, documentation Swagger | Infrastructure | 🟠 Haute |
| **Phase 6** | KPIs enrichis, synchronisation inter-modules, notifications | Transverse | 🟡 Normale |

---

## Règles d'architecture à respecter tout au long de l'implémentation

1. **Le domaine ne dépend de rien** — aucune annotation Spring, JPA ou Jackson dans `domain/model/` et `domain/ports/`
2. **Les IDs sont des UUID** — cohérence avec le reste du projet
3. **Tout est réactif** — `Mono<T>` et `Flux<T>` dans les ports et services, jamais de types bloquants
4. **Les invariants métier vivent dans le constructeur** — pas de validation dans les services
5. **Les Commands sont des records imbriqués** dans les interfaces Use Case
6. **Les exceptions sont typées** — utiliser `OperationException` avec codes OPR_xxx
7. **Les adapters ne contiennent pas de logique métier** — ils convertissent et délèguent
8. **Chaque nouvelle table est dans le schéma `fleet`** — cohérence avec les migrations existantes
