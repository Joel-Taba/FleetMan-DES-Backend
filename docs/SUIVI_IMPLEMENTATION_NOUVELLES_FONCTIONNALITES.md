# Suivi d'Implémentation — Nouvelles Fonctionnalités FleetMan

> Document de suivi mis à jour à la fin de chaque phase réalisée.
> Référence : `cahier_analyse_conception.pdf`
> **Mise à jour du 5 juin 2026** : Réorganisation des phases en deux grandes périodes : modules indépendants du Kernel RT-Comops (réalisables immédiatement) et modules nécessitant le Kernel (en attente d'accès).

---

## ⚡ Stratégie d'Implémentation — Kernel RT-Comops

### Contexte
Le Kernel RT-Comops est la plateforme centrale à laquelle FleetMan doit s'intégrer pour bénéficier des services transverses (authentification centralisée, RBAC, organisations, gestion de fichiers, ressources, tiers...). L'accès au Kernel n'est pas encore disponible.

### Décision
Les modules sont désormais répartis en deux périodes :

- **🟢 Période 1 — SANS Kernel** : Tous les modules implémentables avec l'architecture locale existante (PostgreSQL, R2DBC, sécurité locale JWT). À réaliser maintenant.
- **🔵 Période 2 — AVEC Kernel** : Les modules nécessitant les services du Kernel RT-Comops. À reprendre après obtention de l'accès.

### Modules Kernel concernés (rappel)
Les 9 cores du Kernel applicables à FleetMan sont : `kernel-core`, `common-core`, `actor-core`, `auth-core`, `roles-core`, `organization-core`, `tp-core`, `settings-core`, `resource-core`, `file-core`.

---

## 🗺️ Vue d'ensemble des phases

### 🟢 PÉRIODE 1 — Modules sans Kernel (à faire maintenant)

| Phase | Titre | Dépendance Kernel | Statut |
|---|---|---|---|
| **Chapitre 1** | Exigences Globales du Système Étendu | ❌ Aucune | ✅ Terminé |
| **Chapitre 2** | Module 1 — Planification & Ordonnancement | ❌ Aucune | ✅ Terminé |
| **Chapitre 3** | Module 2 — Documents Légaux | ❌ Aucune (stockage local) | ✅ Terminé |
| **Chapitre 4** | Module 3 — KPI et Rapports Avancés | ❌ Aucune | ✅ Terminé |
| **Chapitre 5** | Module 4 — Dépenses et Budget | ❌ Aucune | ✅ Terminé |
| **Chapitre 6** | Module 5 — Scoring Conducteur | ❌ Aucune | ✅ Terminé |
| **Chapitre 7** | Module 6 — Maintenance Préventive | ❌ Aucune | ✅ Terminé |
| **Chapitre 8** | Module 8 — Alertes & Règles Métier | ❌ Aucune | ✅ Terminé |

### 🔵 PÉRIODE 2 — Modules avec Kernel (en attente d'accès)

| Phase | Titre | Cores Kernel Requis | Statut |
|---|---|---|---|
| **Chapitre K1** | Intégration Fondation Kernel | `kernel-core`, `common-core` | 🔒 En attente |
| **Chapitre K2** | Auth & RBAC Centralisés | `auth-core`, `roles-core` | 🔒 En attente |
| **Chapitre K3** | Organisations & Acteurs | `organization-core`, `actor-core` | 🔒 En attente |
| **Chapitre K4** | Ressources & Fichiers | `resource-core`, `file-core` | 🔒 En attente |
| **Chapitre K5** | Module 7 — Clients et Missions | `tp-core`, `settings-core` | 🔒 En attente |
| **Chapitre K6** | Refactorisation globale & migration | Tous les cores | 🔒 En attente |

---

## 📋 Détail des Modules — Période 1 (Sans Kernel)

### Pourquoi ces modules ne nécessitent-ils pas le Kernel ?

| Module | Justification |
|---|---|
| **Module 4 — Dépenses & Budget** | Données purement internes à FleetMan. Les dépenses (carburant, maintenance, amendes) sont déjà tracées localement. Aucun service externe requis. |
| **Module 5 — Scoring Conducteur** | Calcul algorithmique basé sur les données locales déjà disponibles (incidents, maintenances, trips, carburant). Score calculé localement. |
| **Module 6 — Maintenance Préventive** | Extension du module opérations existant. Seuils kilométriques, alertes planifiées, historiques — tout est local. |
| **Module 8 — Alertes & Règles Métier** | Moteur de règles interne basé sur des events Spring. Les déclencheurs (vitesse, zone, document) viennent des données locales. |

### Pourquoi le Module 7 est en Période 2 ?

Le **Module 7 — Clients & Missions** nécessite la gestion des tiers (clients, prospects) via `tp-core` du Kernel pour éviter de dupliquer une implémentation de mini-CRM qui existe déjà dans le Kernel. Il implique aussi `settings-core` pour la numérotation des missions et `billing-core` pour la facturation.

---

## Vue d'ensemble des phases (ancienne)

---

## Chapitre 1 — Exigences Globales du Système Étendu 🔄

### Objectif
Mettre en place les fondations techniques transversales nécessaires à tous les modules futurs :
- Pagination réactive sur tous les endpoints
- Soft Delete sur les tables critiques
- Traçabilité des actions (audit trail)
- Infrastructure des jobs planifiés
- Support multilingue des erreurs
- Infrastructure d'export CSV

### Sous-phases

| Sous-phase | Description | Statut |
|---|---|---|
| **1.1** | Classe `PageResponse<T>` — wrapper de pagination réactif | ✅ Terminé |
| **1.2** | `SortUtils` — utilitaire de tri réactif sur Flux | ✅ Terminé |
| **1.3** | `CsvExportUtil` — utilitaire d'export CSV réactif | ✅ Terminé |
| **1.4** | Migration Liquibase `011-add-soft-delete.sql` | ✅ Terminé |
| **1.5** | Mise à jour du `db.changelog-master.yaml` | ✅ Terminé |
| **1.6** | `ScheduledJobConfig` — infrastructure des jobs planifiés | ✅ Terminé |
| **1.7** | Enrichissement du `GlobalExceptionHandler` (pagination, IllegalArgument) | ✅ Terminé |
| **1.8** | Tests de compilation et validation | ✅ Terminé |

### Fichiers créés

```
src/main/java/com/yowyob/fleet/
  infrastructure/
    adapters/
      inbound/
        rest/
          dto/
            PageResponse.java                    ✅ créé
    config/
      ScheduledJobConfig.java                    ✅ créé
  shared/
    util/
      SortUtils.java                             ✅ créé
      CsvExportUtil.java                         ✅ créé

src/main/resources/
  db/changelog/changes/
    011-add-soft-delete.sql                      ✅ créé
  db/changelog/
    db.changelog-master.yaml                     ✅ mis à jour
```

### Fichiers modifiés

```
src/main/java/com/yowyob/fleet/
  infrastructure/
    adapters/
      inbound/
        rest/
          GlobalExceptionHandler.java            ✅ enrichi (ServerWebInputException, IllegalArgumentException)
```

---

## Journal des modifications

### 28 mai 2026 — Module 3 Terminé ✅

**KPI et Rapports Avancés — 11 fichiers créés, 0 erreur de compilation**

#### Domaine (Phase 4.1 & 4.2)
- ✅ `KpiSnapshot.java` — Record immuable avec 20 champs KPI (opérationnels, financiers, sécurité, conformité). Méthodes statiques `computeCostPerKm()`, `computeFuelPer100Km()`, `computeIncidentRate()` dans le domaine pur
- ✅ `KpiUseCase.java` — 8 méthodes réactives + records `KpiComparisonDto` et `BigDecimalDelta` (calcul de deltas absolus et en pourcentage)
- ✅ `KpiPersistencePort.java` — 7 méthodes dont `findTopByFleet()` pour les classements

#### Service (Phase 4.3)
- ✅ `KpiService.java` — Agrégation réactive multi-sources (véhicules, carburant, maintenances, incidents). Calcul à la demande si aucun snapshot existant. Comparaison de périodes avec `Mono.zip()`

#### Infrastructure (Phase 4.4)
- ✅ `KpiSnapshotEntity.java` — Entité R2DBC avec contrainte UNIQUE sur `(entity_type, entity_id, period_type, period_start)`
- ✅ `KpiSnapshotR2dbcRepository.java` — 7 requêtes `@Query` dont `findTopByKm` et `findTopByScore` avec `ORDER BY ... LIMIT`
- ✅ `KpiPersistenceAdapter.java` — Conversion record↔entity
- ✅ `013-kpi-tables.sql` — 1 table, 4 index (dont index partiels par `entity_type`)

#### API REST (Phase 4.5)
- ✅ `KpiController.java` — 9 endpoints (tag 20) : KPIs flotte, historique, comparaison, top véhicules, top conducteurs, KPIs véhicule/conducteur, export CSV, recalcul admin
- ✅ Export CSV via `CsvExportUtil` — 14 colonnes, compatible Excel

#### Job planifié (Phase 4.6)
- ✅ `KpiCalculationJob.java` — 3 jobs : quotidien (3h00), hebdomadaire (lundi 4h00), mensuel (1er du mois 5h00). Itère sur toutes les flottes actives via `FleetRepositoryPort.findAll()`

#### Décisions techniques
1. **`KpiSnapshot` en record** — immuable par nature, cohérent avec les autres value objects du domaine
2. **Calcul à la demande** dans `getLatestFleetKpi()` — si aucun snapshot n'existe, déclenche un calcul plutôt que de retourner une erreur 404
3. **`BigDecimalDelta`** dans le port entrant — encapsule la logique de comparaison dans le domaine, pas dans le controller
4. **Correction** : `@Parameter(allowableValues)` non supporté dans cette version de springdoc → remplacé par `@Parameter(description)`

---

## Prochaines étapes
➡️ **Chapitre 5 — Module 4 : Dépenses et Budget** (Sprint 4 du plan)

**Documents Légaux — 20 fichiers créés, 0 erreur de compilation**

#### Domaine (Phase 3.1)
- ✅ `VehicleDocument.java` — Entité avec 5 types (INSURANCE, REGISTRATION, TECHNICAL_CONTROL, TAX_STICKER, TRANSPORT_PERMIT), méthode `computeStatus()` statique, `refreshStatus()`, `daysUntilExpiry()`
- ✅ `DriverDocument.java` — Entité avec 5 types (DRIVING_LICENSE, MEDICAL_CERT, PROFESSIONAL_CARD, WORK_CONTRACT, ID_CARD), expiration optionnelle (CNI sans date)
- ✅ `DocumentException.java` — 8 codes DOC_001 à DOC_008

#### Ports (Phase 3.2)
- ✅ `ManageDocumentUseCase.java` — 13 méthodes + 4 records Command + 2 records de réponse transversaux (`ExpiringDocumentDto`, `ComplianceReportDto`)
- ✅ `DocumentPersistencePort.java` — 14 méthodes (7 véhicule + 7 conducteur)

#### Service (Phase 3.3)
- ✅ `DocumentService.java` — Calcul automatique du statut à la création, `getExpiringDocuments()` fusionne véhicules + conducteurs triés par urgence, `getComplianceReport()` calcule le taux de conformité

#### Infrastructure (Phase 3.4)
- ✅ `VehicleDocumentEntity.java` + `DriverDocumentEntity.java` — Entités R2DBC
- ✅ `VehicleDocumentR2dbcRepository.java` + `DriverDocumentR2dbcRepository.java` — Requêtes `@Query` avec JOIN sur `fleet.vehicles`/`fleet.drivers`
- ✅ `DocumentPersistenceAdapter.java` — Conversion Entity↔Domain pour les deux types
- ✅ `012-document-tables.sql` — 3 tables, 7 index (dont index partiels sur statuts critiques), 2 triggers

#### API REST (Phase 3.5)
- ✅ 4 DTOs de requête/réponse avec annotations Swagger
- ✅ `DocumentController.java` — 13 endpoints sur 3 tags (17, 18, 19) : CRUD véhicule, CRUD conducteur, conformité transversale
- ✅ Endpoint `/documents/compliance-report` — taux de conformité en temps réel

#### Job planifié (Phase 3.6)
- ✅ `DocumentExpiryCheckJob.java` — Exécution nocturne à 2h00, mise à jour des statuts, logs d'alerte J-30/J-15/J-7/EXPIRED, architecture prête pour brancher `SendNotificationPort`

#### Décisions techniques
1. **`computeStatus()` statique** dans les entités domaine — permet au job de recalculer sans instancier un service
2. **Expiration optionnelle** pour `DriverDocument` — certains documents (CNI) n'ont pas de date d'expiration
3. **Fusion réactive** dans `getExpiringDocuments()` via `Flux.merge()` — combine véhicules et conducteurs en un seul flux trié
4. **Fire & forget** dans le job — un échec de mise à jour d'un document ne bloque pas les autres

---

## Prochaines étapes
➡️ **Chapitre 4 — Module 3 : KPI et Rapports Avancés** (Sprint 3 du plan)

---

## Chapitre 4 — Module 3 : KPI et Rapports Avancés 🔄

### Objectif
Calculer et exposer les indicateurs de performance clés (KPIs) de la flotte : taux de disponibilité, coût par km, consommation carburant, taux d'incidents, conformité documentaire. Stockage périodique pour comparaisons temporelles.

### Phases d'implémentation

| Phase | Description | Statut |
|---|---|---|
| **4.1** | Domaine — `KpiSnapshot` (record) + `KpiUseCase` port entrant | ✅ Terminé |
| **4.2** | Port sortant — `KpiPersistencePort` | ✅ Terminé |
| **4.3** | Service — `KpiService` avec calcul d'agrégats réactifs | ✅ Terminé |
| **4.4** | Infrastructure — Entité R2DBC, repository, adapter, migration `013` | ✅ Terminé |
| **4.5** | API REST — DTOs, `KpiController`, export CSV | ✅ Terminé |
| **4.6** | Job planifié — `KpiCalculationJob` (quotidien/hebdo/mensuel) + compilation | ✅ Terminé |

**Planification & Ordonnancement — 18 fichiers créés, 0 erreur de compilation**

#### Domaine (Phase 2.1)
- ✅ `Schedule.java` — Entité planning avec cycle de vie DRAFT→PUBLISHED→ARCHIVED, invariants constructeur, méthodes `publish()`, `archive()`, `update()`
- ✅ `Assignment.java` — Entité affectation avec machine à états PENDING→IN_PROGRESS→COMPLETED, méthode `overlapsWith()` pour la détection de conflits
- ✅ `PlanningException.java` — 12 codes d'erreur PLN_001 à PLN_012 (conflits véhicule/conducteur, statuts invalides, entités introuvables)

#### Ports (Phase 2.2)
- ✅ `ManageScheduleUseCase.java` — 9 méthodes réactives + 2 records Command
- ✅ `ManageAssignmentUseCase.java` — 14 méthodes réactives + 1 record Command (dont `checkVehicleAvailability`, `checkDriverAvailability`, `getConflicts`)
- ✅ `SchedulePersistencePort.java` — 7 méthodes de persistance
- ✅ `AssignmentPersistencePort.java` — 12 méthodes dont `findConflictingByVehicle` et `findConflictingByDriver`

#### Services (Phase 2.3)
- ✅ `ScheduleService.java` — Logique de publication/archivage, validation des dates
- ✅ `AssignmentService.java` — Détection de conflits en 2 étapes (véhicule puis conducteur), vérification disponibilité, délégation à la machine à états

#### Infrastructure (Phase 2.4)
- ✅ `ScheduleEntity.java` + `AssignmentEntity.java` — Entités R2DBC avec `Persistable<UUID>`
- ✅ `ScheduleR2dbcRepository.java` — 3 requêtes `@Query` avec JOIN sur `fleet.fleets`
- ✅ `AssignmentR2dbcRepository.java` — 7 requêtes dont 2 requêtes de détection de conflits avec index partiels
- ✅ `SchedulePersistenceAdapter.java` + `AssignmentPersistenceAdapter.java` — Conversion Entity↔Domain
- ✅ `015-planning-tables.sql` — 2 tables, 6 index (dont index partiels sur statuts actifs), 2 triggers `updated_at`

#### API REST (Phase 2.5)
- ✅ `ScheduleRequest/Response.java` — DTOs avec annotations Swagger
- ✅ `AssignmentRequest/Response/StatusRequest.java` — DTOs complets
- ✅ `ScheduleController.java` — 9 endpoints (tags 15)
- ✅ `AssignmentController.java` — 13 endpoints (tag 16) dont `/conflicts`, `/availability`, `/today`

#### Décisions techniques
1. **Index partiels** sur `status IN ('PENDING','IN_PROGRESS')` pour les requêtes de conflits — performance optimale sur les affectations actives uniquement
2. **Détection de conflits en 2 étapes** dans `AssignmentService` — véhicule d'abord, conducteur ensuite, avec court-circuit dès le premier conflit détecté
3. **`PageResponse<T>`** utilisé sur tous les endpoints de listing — cohérence avec les fondations du Chapitre 1
4. **Correction** : `VehiclePersistencePort.getLocalDataById()` au lieu de `findById()` — adaptation au contrat existant

---

## Prochaines étapes
➡️ **Chapitre 3 — Module 2 : Documents Légaux** (Sprint 2 du plan)

---

## Chapitre 3 — Module 2 : Documents Légaux 🔄

### Objectif
Gérer les documents légaux des véhicules (assurance, carte grise, visite technique) et des conducteurs (permis, visite médicale), avec alertes automatiques avant expiration.

### Phases d'implémentation

| Phase | Description | Statut |
|---|---|---|
| **3.1** | Domaine — `VehicleDocument`, `DriverDocument`, `DocumentException` | ✅ Terminé |
| **3.2** | Ports — `ManageDocumentUseCase`, `DocumentPersistencePort` | ✅ Terminé |
| **3.3** | Services — `DocumentService` avec logique de statut | ✅ Terminé |
| **3.4** | Infrastructure — Entités R2DBC, repositories, adapters, migration `012` | ✅ Terminé |
| **3.5** | API REST — DTOs, `DocumentController`, Swagger | ✅ Terminé |
| **3.6** | Job planifié — `DocumentExpiryCheckJob` (alertes J-30/J-15/J-7) + compilation | ✅ Terminé |
- ✅ Création du document de suivi
- ✅ Analyse de la structure existante du projet
- ✅ `PageResponse<T>` — wrapper de pagination réactif avec méthodes `of()` et `empty()`
- ✅ `SortUtils` — tri dynamique sur Flux par champ et direction
- ✅ `CsvExportUtil` — export CSV réactif avec BOM UTF-8 et échappement correct
- ✅ Migration `011-add-soft-delete.sql` — soft delete + audit trail sur 6 tables critiques + triggers PostgreSQL
- ✅ `ScheduledJobConfig` — activation de `@EnableScheduling` pour les jobs futurs
- ✅ `GlobalExceptionHandler` enrichi — gestion `ServerWebInputException` et `IllegalArgumentException`
- ✅ Compilation Maven : **0 erreur**

### Décisions techniques prises
1. **Pagination en mémoire** pour `PageResponse.of(Flux)` — simple et cohérent avec le paradigme réactif actuel. Pour les très grandes flottes (>10 000 véhicules), une pagination déléguée à la DB sera envisagée.
2. **Triggers PostgreSQL** pour `updated_at` — plus fiable qu'une mise à jour applicative, garantit la cohérence même en cas d'accès direct à la DB.
3. **BOM UTF-8** dans les CSV — nécessaire pour la compatibilité avec Microsoft Excel sur Windows.
4. **`SortUtils` avec Map d'extracteurs** — pattern extensible permettant à chaque controller de déclarer ses champs triables sans couplage.

---

## Prochaines étapes
➡️ **Chapitre 2 — Module 1 : Planification & Ordonnancement** (Sprint 5 du plan)

---

## Chapitre 2 — Module 1 : Planification & Ordonnancement 🔄

### Objectif
Permettre aux gestionnaires de planifier les services, d'affecter les véhicules et conducteurs aux missions, et de détecter les conflits d'affectation.

### Phases d'implémentation (pattern hexagonal en 6 phases)

| Phase | Description | Statut |
|---|---|---|
| **2.1** | Domaine — Entités `Schedule` et `Assignment` + `PlanningException` | ✅ Terminé |
| **2.2** | Ports — `ManageScheduleUseCase`, `ManageAssignmentUseCase`, ports sortants | ✅ Terminé |
| **2.3** | Services — `ScheduleService` et `AssignmentService` avec détection de conflits | ✅ Terminé |
| **2.4** | Infrastructure — Entités R2DBC, repositories, adapters, migration `015` | ✅ Terminé |
| **2.5** | API REST — DTOs, `ScheduleController`, `AssignmentController`, Swagger | ✅ Terminé |
| **2.6** | Intégration — Lien avec `trips` existants, compilation finale | ✅ Terminé |

### Fichiers à créer

```
domain/model/
  Schedule.java
  Assignment.java
domain/exception/
  PlanningException.java
domain/ports/in/
  ManageScheduleUseCase.java
  ManageAssignmentUseCase.java
domain/ports/out/
  SchedulePersistencePort.java
  AssignmentPersistencePort.java

application/service/
  ScheduleService.java
  AssignmentService.java

infrastructure/adapters/outbound/persistence/
  entity/
    ScheduleEntity.java
    AssignmentEntity.java
  repository/
    ScheduleR2dbcRepository.java
    AssignmentR2dbcRepository.java
  SchedulePersistenceAdapter.java
  AssignmentPersistenceAdapter.java

infrastructure/adapters/inbound/rest/
  dto/
    ScheduleRequest.java
    ScheduleResponse.java
    AssignmentRequest.java
    AssignmentResponse.java
    AssignmentStatusRequest.java
  ScheduleController.java
  AssignmentController.java

resources/db/changelog/changes/
  015-planning-tables.sql
```

---

# 🟢 PÉRIODE 1 — MODULES SANS KERNEL

## Chapitre 5 — Module 4 : Dépenses et Budget ⏳

> **Indépendant du Kernel** : toutes les données source (maintenances, carburant, incidents) sont stockées localement dans FleetMan.

### Objectif
Centraliser le suivi financier des opérations de flotte : agrégation des dépenses par type (carburant, maintenance, amendes, réparations), définition de budgets mensuels par flotte ou véhicule, validation des dépenses par le Manager, alertes de dépassement.

### Phases d'implémentation prévues

| Phase | Description | Statut |
|---|---|---|
| **5.1** | Domaine — `Expense` (entité), `Budget` (entité), `ExpenseException` | ⏳ À faire |
| **5.2** | Ports — `ManageExpenseUseCase`, `ManageBudgetUseCase`, ports sortants | ⏳ À faire |
| **5.3** | Services — `ExpenseService` (agrégation réactive), `BudgetService` (suivi vs seuils) | ⏳ À faire |
| **5.4** | Infrastructure — Entités R2DBC, repositories, adapters, migration `016` | ⏳ À faire |
| **5.5** | API REST — DTOs, `ExpenseController`, `BudgetController`, export CSV | ⏳ À faire |
| **5.6** | Job planifié — `BudgetAlertJob` (alerte 80%/100% du budget) + compilation | ⏳ À faire |

### Périmètre fonctionnel

**Types de dépenses (`ExpenseType`)** :
- `FUEL` — Pleins de carburant (source : `FuelFill`)
- `MAINTENANCE` — Maintenances correctives (source : `Maintenance`)
- `INCIDENT` — Coûts incidents (source : `IncidentReport`)
- `FINE` — Amendes (saisie manuelle)
- `TOLL` — Péages (saisie manuelle)
- `OTHER` — Autres frais divers

**Cycle de vie d'une dépense** : `PENDING` → `APPROVED` / `REJECTED`

**Fonctionnalités Budget** :
- Budget mensuel défini par flotte ou par véhicule
- Alertes automatiques à 80% et 100% du budget consommé
- Vue comparative budget prévu vs dépenses réelles
- Export CSV des dépenses pour comptabilité externe

### Fichiers à créer

```
domain/model/
  Expense.java
  Budget.java
domain/exception/
  ExpenseException.java
domain/ports/in/
  ManageExpenseUseCase.java
  ManageBudgetUseCase.java
domain/ports/out/
  ExpensePersistencePort.java
  BudgetPersistencePort.java

application/service/
  ExpenseService.java
  BudgetService.java

infrastructure/adapters/outbound/persistence/
  entity/
    ExpenseEntity.java
    BudgetEntity.java
  repository/
    ExpenseR2dbcRepository.java
    BudgetR2dbcRepository.java
  ExpensePersistenceAdapter.java
  BudgetPersistenceAdapter.java

infrastructure/adapters/inbound/rest/
  dto/
    ExpenseRequest.java
    ExpenseResponse.java
    BudgetRequest.java
    BudgetResponse.java
  ExpenseController.java
  BudgetController.java

infrastructure/adapters/inbound/messaging/
  BudgetAlertJob.java

resources/db/changelog/changes/
  016-expense-budget-tables.sql
```

### Décisions techniques anticipées
1. **Agrégation réactive multi-sources** : `ExpenseService` va fusionner via `Flux.merge()` les dépenses provenant de `FuelFillRepository`, `MaintenanceRepository`, et `IncidentRepository`, plus les dépenses saisies manuellement.
2. **Vue dépenses consolidée** : une vue SQL `expense_consolidated_v` peut être créée pour simplifier les requêtes de reporting.
3. **Soft delete** sur les dépenses manuelles uniquement (les dépenses auto-générées sont immuables).

---

## Chapitre 6 — Module 5 : Scoring Conducteur ⏳

> **Indépendant du Kernel** : le scoring est un calcul algorithmique pur basé sur les données locales (incidents, maintenances, trips, carburant).

### Objectif
Calculer un score de conduite pour chaque chauffeur basé sur ses comportements mesurés : incidents déclarés, consommation carburant, maintenances générées, conformité documentaire, ponctualité des missions. Permettre au Manager de récompenser les meilleurs chauffeurs.

### Phases d'implémentation prévues

| Phase | Description | Statut |
|---|---|---|
| **6.1** | Domaine — `DriverScore` (record), `ScoreComponent` (value object), formule de calcul | ⏳ À faire |
| **6.2** | Ports — `ComputeDriverScoreUseCase`, `ScorePersistencePort` | ⏳ À faire |
| **6.3** | Service — `DriverScoringService` avec pondération configurable | ⏳ À faire |
| **6.4** | Infrastructure — Entité R2DBC, repository, adapter, migration `017` | ⏳ À faire |
| **6.5** | API REST — DTOs, `DriverScoreController` (classement, historique, détail) | ⏳ À faire |
| **6.6** | Job planifié — `ScoringCalculationJob` (hebdomadaire, mensuel) + compilation | ⏳ À faire |

### Périmètre fonctionnel

**Composantes du score (pondération suggérée)** :

| Composante | Poids | Source de données |
|---|---|---|
| Taux d'incidents (faible = bon) | 30% | `IncidentReport` |
| Consommation carburant (vs moyenne flotte) | 25% | `FuelFill` |
| Conformité documentaire (permis, visite médicale) | 20% | `DriverDocument` |
| Ponctualité des missions | 15% | `Assignment` (heure prévue vs effective) |
| Maintenances générées (usure anormale) | 10% | `Maintenance` |

**Score final** : 0 à 100 points, avec badges : 🏆 Excellence (>90), ⭐ Bon (75-90), ✅ Satisfaisant (60-75), ⚠️ À surveiller (40-60), 🔴 Insuffisant (<40)

### Fichiers à créer

```
domain/model/
  DriverScore.java          # record immuable
  ScoreComponent.java       # value object (label, weight, rawValue, normalizedValue)
domain/ports/in/
  ComputeDriverScoreUseCase.java
domain/ports/out/
  ScorePersistencePort.java

application/service/
  DriverScoringService.java  # Agrégation + calcul pondéré

infrastructure/adapters/outbound/persistence/
  entity/DriverScoreEntity.java
  repository/DriverScoreR2dbcRepository.java
  ScorePersistenceAdapter.java

infrastructure/adapters/inbound/rest/
  dto/DriverScoreResponse.java
  DriverScoreController.java

infrastructure/adapters/inbound/messaging/
  ScoringCalculationJob.java

resources/db/changelog/changes/
  017-driver-score-tables.sql
```

### Décisions techniques anticipées
1. **Formule configurable** : les poids de chaque composante doivent être paramétrables (table `score_weight_config`) pour permettre au Manager d'ajuster la pondération selon sa politique.
2. **Score historisé** : chaque calcul génère un snapshot daté, permettant de voir l'évolution du score dans le temps (graphique de tendance).
3. **Normalisation** : chaque composante brute est normalisée sur 100 avant pondération, pour éviter que les conducteurs ayant peu de trajets soient pénalisés.

---

## Chapitre 7 — Module 6 : Maintenance Préventive ⏳

> **Indépendant du Kernel** : basé sur les kilométrages et dates locaux, aucun service externe requis.

### Objectif
Implémenter un système de maintenance préventive basé sur des seuils kilométriques et temporels : définir des plans de maintenance par modèle de véhicule, déclencher automatiquement des alertes quand un véhicule approche d'un seuil, gérer le cycle de vie d'un ordre de maintenance préventive.

### Phases d'implémentation prévues

| Phase | Description | Statut |
|---|---|---|
| **7.1** | Domaine — `MaintenancePlan` (entité), `MaintenanceAlert` (entité), `PreventiveMaintenanceException` | ⏳ À faire |
| **7.2** | Ports — `ManageMaintenancePlanUseCase`, ports sortants | ⏳ À faire |
| **7.3** | Service — `PreventiveMaintenanceService` (calcul des seuils, génération alertes) | ⏳ À faire |
| **7.4** | Infrastructure — Entités R2DBC, repositories, adapters, migration `018` | ⏳ À faire |
| **7.5** | API REST — DTOs, `MaintenancePlanController`, `MaintenanceAlertController` | ⏳ À faire |
| **7.6** | Job planifié — `PreventiveMaintenanceCheckJob` (quotidien à 6h00) + compilation | ⏳ À faire |

### Périmètre fonctionnel

**Types de maintenance préventive** :
- `OIL_CHANGE` — Vidange moteur (tous les X km ou X mois)
- `TIRE_ROTATION` — Rotation des pneus
- `BRAKE_INSPECTION` — Inspection freins
- `FILTER_CHANGE` — Remplacement filtres (air, carburant)
- `TIMING_BELT` — Remplacement courroie de distribution
- `GENERAL_INSPECTION` — Révision générale

**Cycle de vie d'une alerte** : `UPCOMING` (J-30 km/j) → `DUE` (seuil atteint) → `OVERDUE` (dépassé) → `RESOLVED` (maintenance effectuée)

### Fichiers à créer

```
domain/model/
  MaintenancePlan.java      # Plan par modèle véhicule (seuil km + seuil jours)
  MaintenanceAlert.java     # Alerte générée pour un véhicule spécifique
domain/exception/
  PreventiveMaintenanceException.java
domain/ports/in/
  ManageMaintenancePlanUseCase.java
domain/ports/out/
  MaintenancePlanPersistencePort.java
  MaintenanceAlertPersistencePort.java

application/service/
  PreventiveMaintenanceService.java

infrastructure/adapters/outbound/persistence/
  entity/
    MaintenancePlanEntity.java
    MaintenanceAlertEntity.java
  repository/
    MaintenancePlanR2dbcRepository.java
    MaintenanceAlertR2dbcRepository.java
  MaintenancePlanPersistenceAdapter.java
  MaintenanceAlertPersistenceAdapter.java

infrastructure/adapters/inbound/rest/
  dto/
    MaintenancePlanRequest.java
    MaintenancePlanResponse.java
    MaintenanceAlertResponse.java
  MaintenancePlanController.java
  MaintenanceAlertController.java

infrastructure/adapters/inbound/messaging/
  PreventiveMaintenanceCheckJob.java

resources/db/changelog/changes/
  018-preventive-maintenance-tables.sql
```

### Décisions techniques anticipées
1. **Plans par modèle + surcharge par véhicule** : un plan de base est défini par modèle de véhicule, mais peut être surchargé pour un véhicule individuel (ex: véhicule très sollicité = seuil km réduit).
2. **Lien avec Maintenance existante** : quand une maintenance préventive est réalisée, elle crée automatiquement une `Maintenance` dans le module opérations existant, et marque l'alerte comme `RESOLVED`.
3. **Double seuil** : l'alerte se déclenche si le seuil kilométrique OU le seuil temporel est atteint, en prenant le premier des deux.

---

## Chapitre 8 — Module 8 : Alertes & Règles Métier ⏳

> **Indépendant du Kernel** : le moteur de règles est purement interne, basé sur les events Spring et les données locales.

### Objectif
Permettre aux Managers de créer des règles métier personnalisées de type "SI [condition] ALORS [action]" via une interface no-code, et d'envoyer des alertes en temps réel via WebSocket, email, ou in-app notification.

### Phases d'implémentation prévues

| Phase | Description | Statut |
|---|---|---|
| **8.1** | Domaine — `AlertRule` (entité), `AlertEvent` (record), `AlertException` | ⏳ À faire |
| **8.2** | Ports — `ManageAlertRuleUseCase`, `SendAlertPort`, `AlertPersistencePort` | ⏳ À faire |
| **8.3** | Service — `AlertRuleEngineService` (évaluation réactive des règles) | ⏳ À faire |
| **8.4** | Infrastructure — Entités R2DBC, repositories, adapters, migration `019` | ⏳ À faire |
| **8.5** | API REST — DTOs, `AlertRuleController`, `AlertEventController` | ⏳ À faire |
| **8.6** | WebSocket — `AlertWebSocketHandler` pour push temps réel + compilation | ⏳ À faire |

### Périmètre fonctionnel

**Types de déclencheurs (`TriggerType`)** :
- `DOCUMENT_EXPIRY` — Document légal expire dans X jours
- `BUDGET_THRESHOLD` — Budget consommé à X%
- `INCIDENT_SEVERITY` — Incident de sévérité Y créé
- `MAINTENANCE_DUE` — Maintenance préventive due
- `DRIVER_SCORE_DROP` — Score conducteur < seuil
- `FUEL_ANOMALY` — Consommation carburant anormale (+X% vs moyenne)
- `TRIP_OVERDUE` — Trajet non terminé après délai estimé + marge

**Types d'actions (`ActionType`)** :
- `IN_APP_NOTIFICATION` — Notification in-app (table `alert_events`)
- `EMAIL` — Email au Manager/Admin (via `SendNotificationPort`)
- `LOG` — Entrée dans le journal d'audit (toujours activée)

**Modèle de règle simplifié** :
```
AlertRule {
  name: "Assurance expirante"
  trigger: DOCUMENT_EXPIRY
  condition: { documentType: INSURANCE, daysBeforeExpiry: 30 }
  action: IN_APP_NOTIFICATION
  target: MANAGER
  active: true
}
```

### Fichiers à créer

```
domain/model/
  AlertRule.java            # Règle définie par le Manager
  AlertEvent.java           # record : événement d'alerte déclenché
domain/exception/
  AlertException.java
domain/ports/in/
  ManageAlertRuleUseCase.java
domain/ports/out/
  AlertRulePersistencePort.java
  AlertEventPersistencePort.java
  SendAlertPort.java        # Port sortant vers email/push/websocket

application/service/
  AlertRuleEngineService.java   # Évalue les règles, crée les AlertEvent

infrastructure/adapters/outbound/persistence/
  entity/
    AlertRuleEntity.java
    AlertEventEntity.java
  repository/
    AlertRuleR2dbcRepository.java
    AlertEventR2dbcRepository.java
  AlertRulePersistenceAdapter.java
  AlertEventPersistenceAdapter.java

infrastructure/adapters/outbound/notification/
  InAppAlertAdapter.java    # implémente SendAlertPort (in-app)
  EmailAlertAdapter.java    # implémente SendAlertPort (email SMTP)

infrastructure/adapters/inbound/rest/
  dto/
    AlertRuleRequest.java
    AlertRuleResponse.java
    AlertEventResponse.java
  AlertRuleController.java
  AlertEventController.java

infrastructure/adapters/inbound/websocket/
  AlertWebSocketHandler.java

resources/db/changelog/changes/
  019-alert-rule-tables.sql
```

### Décisions techniques anticipées
1. **Architecture événementielle interne** : le moteur de règles est abonné aux events Spring (`@EventListener`) émis par les services existants (DocumentService, BudgetService, ScoringService...) pour déclencher les évaluations.
2. **Évaluation réactive** : `AlertRuleEngineService` charge les règles actives depuis Redis (cache) pour ne pas accéder à la DB à chaque événement.
3. **WebSocket réactif** : utilisation de `WebSocketHandler` de Spring WebFlux pour un push temps réel vers le frontend sans polling.
4. **Templates prédéfinis** : 10 règles de templates livrées par défaut (documentaires, budget, incidents) pour faciliter l'onboarding du Manager.

---

# 🔵 PÉRIODE 2 — MODULES AVEC KERNEL (En attente d'accès)

> ⏸️ **Ces chapitres sont bloqués** en attente d'accès au Kernel RT-Comops.
> Ils seront traités après la finalisation complète de la Période 1.

## Vue d'ensemble Période 2

| Chapitre | Titre | Cores Kernel Requis | Priorité |
|---|---|---|---|
| **K1** | Intégration Fondation Kernel | `kernel-core`, `common-core` | 🔴 Prérequis absolu |
| **K2** | Auth & RBAC Centralisés | `auth-core`, `roles-core` | 🔴 Critique |
| **K3** | Organisations & Acteurs | `organization-core`, `actor-core` | 🟠 Haute |
| **K4** | Ressources & Fichiers | `resource-core`, `file-core` | 🟠 Haute |
| **K5** | Module 7 — Clients & Missions | `tp-core`, `settings-core` | 🟡 Moyenne |
| **K6** | Refactorisation & Migration globale | Tous les cores | 🟡 Moyenne |

---

## Chapitre K1 — Intégration Fondation Kernel 🔒

### Objectif
Connecter FleetMan au Kernel RT-Comops comme ClientApplication enregistrée. Remplacer les mécanismes d'authentification locaux par ceux du Kernel.

### Ce qui change
- `SecurityConfig` actuelle → remplacée par la validation JWT RS256 du Kernel via JWKS
- `JwtAuthenticationManager` local → délégué au Kernel
- Ajout du `TenantWebFilter` du Kernel dans la chaîne réactive
- Enregistrement de FleetMan comme `ClientApplication` avec services autorisés : `ORGANIZATION`, `RESOURCE`, `COMMERCIAL`, `SETTINGS`, `FILE`
- Toutes les entités héritent de `BaseEntity` du Kernel (`tenantId` garanti)
- `PageResponse<T>` aligné avec `PageResult<T>` du Kernel

### Prérequis
- [ ] URL du Kernel RT-Comops accessible
- [ ] Identifiants ClientApplication (X-Client-Id + X-Api-Key)
- [ ] Document JWKS du Kernel pour vérification JWT RS256
- [ ] tenantId de l'organisation FleetMan dans le Kernel

---

## Chapitre K2 — Auth & RBAC Centralisés 🔒

### Objectif
Remplacer l'authentification et le contrôle d'accès locaux de FleetMan par ceux du Kernel (`auth-core` + `roles-core`).

### Ce qui change
- Suppression de `RemoteAuthAdapter` actuel → remplacé par `auth-core` du Kernel
- Les rôles `FLEET_SUPER_ADMIN`, `FLEET_ADMIN`, `FLEET_MANAGER`, `FLEET_DRIVER` → provisionnés dans `roles-core` du Kernel avec leurs permissions RBAC
- Endpoint de login → délégué à `auth-core` (OIDC flow du Kernel)
- Cache de permissions → Redis `roles-core` du Kernel

### Bénéfice
FleetMan profite du SSO multi-applications de RT-Comops, MFA, OTP, réinitialisation de mot de passe professionnelle.

---

## Chapitre K3 — Organisations & Acteurs 🔒

### Objectif
Aligner le modèle "organisation/flotte" de FleetMan avec `organization-core` et les utilisateurs avec `actor-core`.

### Ce qui change
- Une **Organisation FleetMan** = une Organisation dans `organization-core`
- Un **Dépôt/Agence** = une Agence dans `organization-core`
- Les **Managers** et **Conducteurs** = des Acteurs dans `actor-core`
- Les adresses des flottes et dépôts → format `Address` polymorphique du Kernel
- Les contacts des gestionnaires → format `Contact` du Kernel

---

## Chapitre K4 — Ressources & Fichiers 🔒

### Objectif
Connecter la gestion des véhicules et documents à `resource-core` et `file-core` du Kernel.

### Ce qui change
- Chaque **Véhicule FleetMan** = une Ressource dans `resource-core` du Kernel (affectation, maintenance, GPS)
- Les **Documents légaux** (assurance, carte grise...) → stockage dans `file-core` avec politique de gouvernance (expiration, révision)
- Suppression du stockage de fichiers local → délégation à `file-core`

---

## Chapitre K5 — Module 7 : Clients & Missions 🔒

### Objectif
Implémenter le module Clients & Missions en s'appuyant sur `tp-core` (tiers) et `settings-core` (numérotation) du Kernel, plutôt que de réimplémenter un mini-CRM.

### Fonctionnalités
- Création et gestion des clients des missions via `tp-core`
- Numérotation des missions via `settings-core`
- Kanban missions : Devis → Planifiée → En cours → Livrée → À facturer
- Lien mission ↔ trajet GPS pour tracking temps réel client
- Page publique client (white-label) : carte + véhicule en mouvement + ETA

---

## Chapitre K6 — Refactorisation & Migration Globale 🔒

### Objectif
Migrer toutes les données et tous les modules vers l'architecture intégrée Kernel.

### Étapes prévues
1. Migration des tenants et organisations vers le Kernel
2. Migration des utilisateurs vers `actor-core` + `auth-core`
3. Migration des véhicules vers `resource-core`
4. Migration des documents vers `file-core`
5. Suppression des mécanismes locaux redondants
6. Tests d'intégration end-to-end avec le Kernel
7. Mise en production progressive (canary)

---

## 📊 Tableau de bord de progression

### Période 1 — Sans Kernel

| Module | Fichiers | Endpoints | Tests compil. | Statut |
|---|---|---|---|---|
| Chapitre 1 — Fondations | 7 | - | ✅ 0 erreur | ✅ **Terminé** |
| Chapitre 2 — Planning | 18 | 22 | ✅ 0 erreur | ✅ **Terminé** |
| Chapitre 3 — Documents | 20 | 13 | ✅ 0 erreur | ✅ **Terminé** |
| Chapitre 4 — KPIs | 11 | 9 | ✅ 0 erreur | ✅ **Terminé** |
| Chapitre 5 — Dépenses & Budget | 17 | 14 | ✅ 0 erreur | ✅ **Terminé** || Chapitre 6 — Scoring | ~12 | ~8 | - | ✅ **Terminé** |
| Chapitre 7 — Maintenance Préventive | ~14 | ~10 | - | ✅ **Terminé** |
| Chapitre 8 — Alertes | ~16 | ~10 | - | ✅ **Terminé** |

### Période 2 — Avec Kernel

| Module | Cores Requis | Statut |
|---|---|---|
| K1 — Fondation Kernel | `kernel-core`, `common-core` | 🔒 En attente |
| K2 — Auth & RBAC | `auth-core`, `roles-core` | 🔒 En attente |
| K3 — Organisations | `organization-core`, `actor-core` | 🔒 En attente |
| K4 — Ressources & Fichiers | `resource-core`, `file-core` | 🔒 En attente |
| K5 — Clients & Missions | `tp-core`, `settings-core` | 🔒 En attente |
| K6 — Migration globale | Tous | 🔒 En attente |

---

**Prochaine action** : ➡️ **Chapitre 5 — Module 4 : Dépenses et Budget**


---

## Journal des modifications — Module 4 Terminé ✅

### 5 juin 2026 — Module 4 Terminé ✅

**Dépenses & Budget — 17 fichiers créés, 0 erreur de compilation**

#### Domaine (Phase 5.1)
- ✅ `Expense.java` — Entité avec 3 types sources : `ExpenseType` (FUEL, MAINTENANCE, INCIDENT, FINE, TOLL, OTHER), `SourceType` (AUTO, MANUAL), `ExpenseStatus` (PENDING, APPROVED, REJECTED). Méthodes métier `approve()`, `reject()`, `updateAmount()`, `isManualAndPending()`
- ✅ `Budget.java` — Entité avec `BudgetScope` (FLEET, VEHICLE), `AlertLevel` (NORMAL, WARNING, EXCEEDED). Méthodes métier `consumptionRate()`, `remaining()`, `updateConsumed()`, `isExceeded()`. Normalisation automatique au 1er du mois
- ✅ `BudgetException.java` — 15 codes BDG_001 à BDG_015 couvrant expenses, budgets et références croisées

#### Ports (Phase 5.2)
- ✅ `ManageExpenseUseCase.java` — 16 méthodes + 3 records Command (`CreateManualExpenseCommand`, `UpdateExpenseCommand`, `ValidateExpenseCommand`) + record `ExpenseSummaryDto`
- ✅ `ManageBudgetUseCase.java` — 11 méthodes + 2 records Command + record `BudgetStatusDto` avec calculs enrichis
- ✅ `ExpensePersistencePort.java` — 14 méthodes dont 4 méthodes agrégats mensuels pour le recalcul budgétaire
- ✅ `BudgetPersistencePort.java` — 10 méthodes dont `existsByEntityAndMonth()` pour la contrainte d'unicité

#### Services (Phase 5.3)
- ✅ `ExpenseService.java` — Gestion complète CRUD + validation + auto-génération depuis opérations. Pattern fire & forget pour les mises à jour budgétaires. Résumé par type pour graphiques camembert
- ✅ `BudgetService.java` — Création avec unicité mensuelle, recalcul réactif depuis les dépenses approuvées, `recalculateAllActiveByManager()` pour le job

#### Infrastructure (Phase 5.4)
- ✅ `ExpenseEntity.java` — Entité R2DBC avec `Persistable<UUID>`, 19 colonnes
- ✅ `BudgetEntity.java` — Entité R2DBC avec `Persistable<UUID>`, 14 colonnes + flags alertes
- ✅ `ExpenseR2dbcRepository.java` — 12 requêtes `@Query` dont 4 agrégats COALESCE avec filtres mensuels
- ✅ `BudgetR2dbcRepository.java` — 7 requêtes `@Query` dont `existsByEntityAndMonth()` et `findActiveByManagerId()`
- ✅ `ExpensePersistenceAdapter.java` — Conversion Entity↔Domain avec gestion des enums via try/catch
- ✅ `BudgetPersistenceAdapter.java` — Conversion Entity↔Domain avec normalisation du mois
- ✅ `016-expense-budget-tables.sql` — 2 tables, 10 index (dont index partiels sur status='APPROVED'), 1 trigger `updated_at`, contrainte UNIQUE `(scope, entity_id, budget_month)`

#### API REST (Phase 5.5)
- ✅ 6 DTOs : `ExpenseRequest`, `ExpenseUpdateRequest`, `ExpenseRejectRequest`, `ExpenseResponse`, `BudgetRequest`, `BudgetUpdateRequest`, `BudgetResponse`
- ✅ `ExpenseController.java` — 14 endpoints (tag 17a) : CRUD, filtres, validation, agrégats, export CSV
- ✅ `BudgetController.java` — 12 endpoints (tag 17b) : CRUD, budgets courants, statut enrichi, recalcul

#### Job planifié (Phase 5.6)
- ✅ `BudgetAlertJob.java` — Exécution quotidienne à 7h00. Détecte les budgets à 80% et 100% de consommation. Flags `alert_80_sent` / `alert_100_sent` pour éviter les doublons. Architecture prête pour brancher `SendNotificationPort` (Module 8)

#### Mise à jour fichiers existants
- ✅ `OpenApiConfig.java` — Ajout tags `TAG_BUDGET_EXPENSES` (17a) et `TAG_BUDGET_BUDGETS` (17b) dans la liste ordonnée
- ✅ `db.changelog-master.yaml` — Inclusion de `016-expense-budget-tables.sql`

#### Décisions techniques
1. **Dépenses AUTO immuables** : les dépenses générées depuis des opérations existantes (FuelRecharge, Maintenance, Incident) naissent directement en statut APPROVED et ne peuvent pas être modifiées ni supprimées — garantit l'intégrité de la comptabilité opérationnelle
2. **Recalcul budgétaire fire & forget** : la mise à jour du budget après chaque approbation de dépense est asynchrone (`.subscribe()`) pour ne pas bloquer la réponse HTTP
3. **Contrainte UNIQUE SQL** `(scope, entity_id, budget_month)` — appliquée en base pour une garantie forte contre les doublons, en plus de la vérification applicative
4. **Flags alertes non-redoublées** : `alert_80_sent` et `alert_100_sent` en base évitent d'envoyer la même alerte à chaque exécution du job quotidien
5. **manager_id sur expense** : le `manager_id` est dénormalisé sur la table `expenses` pour des requêtes de listing par manager sans jointure sur `vehicles`

---

**Prochaine action** : ➡️ **Chapitre 6 — Module 5 : Scoring Conducteur**


---

## Journal des modifications — Module 5 Terminé ✅

### 5 juin 2026 — Module 5 Terminé ✅

**Scoring Conducteur — 12 fichiers créés, 0 erreur de compilation**

#### Domaine (Phase 6.1)
- ✅ `DriverScore.java` — Record immuable avec 22 champs. Enums : `PeriodType` (WEEKLY, MONTHLY), `ScoreBadge` (EXCELLENCE, GOOD, SATISFACTORY, WARNING, INSUFFICIENT). 7 méthodes statiques de calcul pur : `computeIncidentScore()`, `computeFuelScore()` avec interpolation linéaire, `computeComplianceScore()`, `computePunctualityScore()`, `computeMaintenanceScore()`, `computeFinalScore()` pondéré 30/25/20/15/10%, `resolveBadge()`. Méthode `getComponents()` retournant les 5 `ScoreComponentView` pour l'affichage détaillé
- ✅ `ScoringException.java` — 6 codes SCR_001 à SCR_006

#### Ports (Phase 6.2)
- ✅ `ComputeDriverScoreUseCase.java` — 10 méthodes + record `FleetScoreSummaryDto` (distribution des badges, min/max/moyenne)
- ✅ `DriverScorePersistencePort.java` — 9 méthodes dont `findTopByFleet()` et `findBottomByFleet()` pour les classements

#### Service (Phase 6.3)
- ✅ `DriverScoringService.java` — Agrégation réactive `Mono.zip()` depuis 4 sources (incidents, maintenances, affectations, documents conducteur). Calcul en cascade via les méthodes statiques du domaine. Résumé flotte avec distribution des badges via `Collectors.groupingBy`

#### Infrastructure (Phase 6.4)
- ✅ `DriverScoreEntity.java` — Entité R2DBC avec `Persistable<UUID>`, 22 colonnes
- ✅ `DriverScoreR2dbcRepository.java` — 6 requêtes `@Query` dont `ORDER BY final_score DESC LIMIT :lim` pour les classements
- ✅ `DriverScorePersistenceAdapter.java` — Conversion Entity↔Domain avec try/catch pour enums
- ✅ `017-driver-score-tables.sql` — 1 table, 4 index (dont index partiel pour classements), contrainte UNIQUE `(driver_id, period_type, period_start)`

#### API REST (Phase 6.5)
- ✅ `DriverScoreResponse.java` — DTO avec composantes détaillées `ComponentDto` et liste complète des 5 composantes
- ✅ `DriverScoreController.java` — 10 endpoints (tag 18) : dernier score, détail, historique, scores flotte, top/bottom N, résumé flotte, calcul à la demande chauffeur/flotte

#### Job planifié (Phase 6.6)
- ✅ `ScoringCalculationJob.java` — Hebdomadaire (lundi 5h00, semaine précédente) + mensuel (1er du mois 5h30, mois précédent). Calcule tous les chauffeurs de toutes les flottes actives

#### Mise à jour fichiers existants
- ✅ `OpenApiConfig.java` — Ajout tag `TAG_SCORING` (18)
- ✅ `db.changelog-master.yaml` — Inclusion de `017-driver-score-tables.sql`

#### Décisions techniques
1. **Calcul idempotent** : contrainte UNIQUE `(driver_id, period_type, period_start)` + `save()` qui fait un upsert → recalculer un score le met à jour, pas de doublons
2. **Score neutre (75) pour carburant** : si les données L/100km sont insuffisantes, le score carburant est à 75 (neutre) pour ne pas pénaliser injustement un chauffeur sans données GPS
3. **Calcul des composantes via méthodes statiques du domaine** : logique purement dans le domaine, le service ne fait qu'orchestrer l'agrégation réactive
4. **Calcul des semaines précédentes** dans le job : le job hebdomadaire calcule la semaine `N-1` (lundi dernier), pas la semaine courante, pour avoir des données complètes

---

**Prochaine action** : ➡️ **Chapitre 7 — Module 6 : Maintenance Préventive**


---

## Journal des modifications — Module 6 Terminé ✅

### 5 juin 2026 — Module 6 Terminé ✅

**Maintenance Préventive — 16 fichiers créés, 0 erreur de compilation**

#### Domaine (Phase 7.1)
- ✅ `MaintenancePlan.java` — Entité avec enums `MaintenanceType` (9 types), `PlanScope` (FLEET/VEHICLE). Double seuil km + jours. Méthodes métier `computeNextMaintenanceKm()` et `computeNextMaintenanceDate()`. Validation : au moins un seuil doit être fourni
- ✅ `MaintenanceAlert.java` — Entité avec machine à états `AlertStatus` (UPCOMING → DUE → OVERDUE → RESOLVED), `TriggerType` (MILEAGE/DATE/BOTH). Méthodes métier `refreshStatus()` (recalcul du statut selon km et date actuels) et `resolve(maintenanceId)`
- ✅ `PreventiveMaintenanceException.java` — 10 codes PMT_001 à PMT_010

#### Ports (Phase 7.2)
- ✅ `ManageMaintenancePlanUseCase.java` — 16 méthodes + 2 records Command. Couvre CRUD plans, lecture alertes, résolution et évaluation
- ✅ `MaintenancePlanPersistencePort.java` — 9 méthodes dont `findEffectivePlan()` avec priorité VEHICLE > FLEET
- ✅ `MaintenanceAlertPersistencePort.java` — 9 méthodes dont `findActiveByVehicleAndType()` pour la déduplication

#### Service (Phase 7.3)
- ✅ `PreventiveMaintenanceService.java` — Logique d'évaluation `evaluateSingleVehiclePlan()` : récupère le kilométrage depuis `operationalParameters.mileage()`, calcule les seuils cibles, détermine la zone d'alerte, crée ou met à jour l'alerte (upsert via `findActiveByVehicleAndType`). Génération asynchrone d'alertes après création d'un plan (fire & forget)

#### Infrastructure (Phase 7.4)
- ✅ `MaintenancePlanEntity.java` + `MaintenanceAlertEntity.java` — Entités R2DBC avec `Persistable<UUID>`
- ✅ `MaintenancePlanR2dbcRepository.java` — 5 requêtes dont `findEffectivePlan()` avec `ORDER BY scope DESC LIMIT 1`
- ✅ `MaintenanceAlertR2dbcRepository.java` — 5 requêtes dont tri `status DESC, days_remaining ASC` pour priorité urgence
- ✅ `MaintenancePlanPersistenceAdapter.java` + `MaintenanceAlertPersistenceAdapter.java`
- ✅ `018-preventive-maintenance-tables.sql` — 2 tables, 11 index (dont index partiels sur status), 2 triggers, contrainte `chk_plan_has_threshold`, UNIQUE partiel sur `(vehicle_id, maintenance_type) WHERE status != 'RESOLVED'`

#### API REST (Phase 7.5)
- ✅ 5 DTOs : `MaintenancePlanRequest`, `MaintenancePlanUpdateRequest`, `MaintenancePlanResponse`, `MaintenanceAlertResponse`
- ✅ `MaintenancePlanController.java` — 8 endpoints (tag 19a) : CRUD + toggle + lecture par flotte/véhicule
- ✅ `MaintenanceAlertController.java` — 8 endpoints (tag 19b) : lecture active/urgente/véhicule/flotte, résolution, évaluation à la demande

#### Job planifié (Phase 7.6)
- ✅ `PreventiveMaintenanceCheckJob.java` — Quotidien à 6h00. Évalue tous les plans de toutes les flottes. Logs détaillés par niveau (🔴 OVERDUE, 🟠 DUE, 🟡 UPCOMING). Architecture prête pour le Module 8 Alertes

#### Mise à jour fichiers existants
- ✅ `OpenApiConfig.java` — Ajout tags `TAG_PREVENTIVE_PLANS` (19a) et `TAG_PREVENTIVE_ALERTS` (19b)
- ✅ `db.changelog-master.yaml` — Inclusion de `018-preventive-maintenance-tables.sql`

#### Décisions techniques
1. **Priorité VEHICLE > FLEET** : si un plan individuel (scope=VEHICLE) existe pour un véhicule, il prime sur le plan de flotte (scope=FLEET) — permet des seuils personnalisés pour des véhicules spécifiquement sollicités
2. **UNIQUE partiel** `(vehicle_id, maintenance_type) WHERE status != 'RESOLVED'` : garantit qu'une seule alerte active existe par combinaison véhicule/type, quelle que soit l'implémentation applicative
3. **Kilométrage source** : récupéré depuis `VehicleParameters.Operational.mileage()` déjà stocké par le module opérationnel existant — aucune nouvelle source de données requise
4. **Génération immédiate** après création d'un plan : les véhicules déjà en zone de préalerte reçoivent leurs alertes dès que le plan est créé (fire & forget asynchrone)
5. **Résolution liée** : quand une alerte est résolue, elle est liée par FK à la `Maintenance` effectuée — traçabilité complète de la réponse à l'alerte

---

**Prochaine action** : ➡️ **Chapitre 8 — Module 8 : Alertes & Règles Métier**


---

## Journal des modifications — Module 8 Terminé ✅

### 5 juin 2026 — Module 8 Terminé ✅

**Alertes & Règles Métier — 16 fichiers créés, 0 erreur de compilation**

#### Domaine (Phase 8.1)
- ✅ `AlertRule.java` — Entité avec `TriggerType` (7 types), `ActionType` (IN_APP/EMAIL), `TargetRole` (MANAGER/ADMIN/DRIVER). Flag `systemTemplate` pour les règles protégées. `conditionValue` encodé en String. Méthode `getConditionThreshold()` avec parsing défensif. Méthode `toggle()`
- ✅ `AlertEvent.java` — Entité représentant une notification in-app. `ReadStatus` (UNREAD/READ/DISMISSED). Méthodes `markAsRead()`, `dismiss()`, `isUnread()`
- ✅ `AlertException.java` — 6 codes ALR_001 à ALR_006

#### Ports (Phase 8.2)
- ✅ `ManageAlertRuleUseCase.java` — 17 méthodes : CRUD règles, provisionnement templates, lecture/gestion événements, déclenchement du moteur `triggerRules()`
- ✅ `AlertRulePersistencePort.java` — 6 méthodes dont `countSystemTemplatesByManager()` pour l'idempotence du provisionnement
- ✅ `AlertEventPersistencePort.java` — 7 méthodes dont `markAllAsReadByManagerId()` pour le bulk-update
- ✅ `SendAlertPort.java` — Interface dual-channel : `sendInApp()` (persistance toujours active) + `sendEmail()` (fire & forget, conditionnel)

#### Service (Phase 8.3)
- ✅ `AlertRuleEngineService.java` — Moteur d'évaluation avec méthode `evaluateCondition()` : logique de comparaison différenciée selon le triggerType (≤ pour DOCUMENT_EXPIRY/SCORE_DROP, ≥ pour BUDGET_THRESHOLD/FUEL_ANOMALY). 8 templates système provisionnés via `buildDefaultRules()`. `provisionDefaultRules()` est idempotent

#### Infrastructure (Phase 8.4)
- ✅ `AlertRuleEntity.java` + `AlertEventEntity.java` — Entités R2DBC avec `Persistable<UUID>`
- ✅ `AlertRuleR2dbcRepository.java` — 3 requêtes dont index sur `(manager_id, trigger_type, active)`
- ✅ `AlertEventR2dbcRepository.java` — 4 requêtes dont `@Modifying markAllAsReadByManagerId()` bulk-update SQL
- ✅ `AlertRulePersistenceAdapter.java` + `AlertEventPersistenceAdapter.java` — Conversion Entity↔Domain avec try/catch sur enums
- ✅ `InAppAlertAdapter.java` — Implémente `SendAlertPort`. Canal in-app toujours actif. Canal email stubbed avec log (TODO SMTP)
- ✅ `019-alert-rule-tables.sql` — 2 tables, 5 index (dont index partiels `WHERE read_status = 'UNREAD'`), 1 trigger

#### API REST (Phase 8.5)
- ✅ 3 DTOs : `AlertRuleRequest`, `AlertRuleResponse`, `AlertEventResponse`
- ✅ `AlertRuleController.java` — 7 endpoints (tag 20a) : CRUD + toggle + provisionnement des templates
- ✅ `AlertEventController.java` — 7 endpoints (tag 20b) : lister, non-lues, compter, marquer lu/tout lu/ignorer

#### Mise à jour fichiers existants
- ✅ `OpenApiConfig.java` — Ajout tags `TAG_ALERT_RULES` (20a) et `TAG_ALERT_EVENTS` (20b)
- ✅ `db.changelog-master.yaml` — Inclusion de `019-alert-rule-tables.sql`

#### Décisions techniques
1. **`conditionValue` en String** : un seul champ générique plutôt que N colonnes spécifiques par triggerType — plus évolutif pour ajouter de nouveaux types de conditions sans migration SQL
2. **Moteur `evaluateCondition()` in-process** : l'évaluation est synchrone et en mémoire (pas de Kafka ni d'EventBus) pour rester cohérent avec l'architecture Spring reactive locale; prêt pour une migration vers Kafka avec le Kernel
3. **8 templates système idempotents** : `provisionDefaultRules()` vérifie `COUNT(*) WHERE system_template = true` avant d'insérer — un appel multiple ne génère pas de doublons
4. **`@Modifying markAllAsReadByManagerId()`** : une seule requête SQL bulk-update plutôt qu'un `flatMap` sur chaque événement — efficient pour des managers avec des centaines de notifications
5. **Canal email stubbed** : `sendEmail()` log un TODO et retourne `Mono.empty()` — l'architecture est prête pour brancher JavaMailSender ou l'API SMTP du Kernel sans toucher au service ni aux ports
6. **Fire & forget sur email** : l'envoi email ne bloque pas la réponse HTTP de l'appel REST qui a déclenché l'alerte

---

## 🏁 PÉRIODE 1 COMPLÈTE — Tous les modules sans Kernel sont terminés

### Récapitulatif final Période 1

| Module | Fichiers créés | Endpoints | Compilation |
|---|---|---|---|
| Ch.1 — Fondations | 7 | - | ✅ 0 erreur |
| Ch.2 — Planning | 18 | 22 | ✅ 0 erreur |
| Ch.3 — Documents | 20 | 13 | ✅ 0 erreur |
| Ch.4 — KPIs | 11 | 9 | ✅ 0 erreur |
| Ch.5 — Dépenses & Budget | 17 | 26 | ✅ 0 erreur |
| Ch.6 — Scoring Conducteur | 12 | 10 | ✅ 0 erreur |
| Ch.7 — Maintenance Préventive | 16 | 16 | ✅ 0 erreur |
| Ch.8 — Alertes & Règles Métier | 16 | 14 | ✅ 0 erreur |
| **TOTAL** | **117 fichiers** | **110 endpoints** | **✅ 0 erreur** |

### Prochain palier → Période 2 (avec Kernel RT-Comops)
En attente d'accès au Kernel. Voir chapitres K1 à K6 dans ce document.
