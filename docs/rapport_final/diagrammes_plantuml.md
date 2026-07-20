# Diagrammes PlantUML — FleetMan (à compiler)

Compilez chaque bloc avec [PlantUML](https://plantuml.com) et placez les PDF/PNG générés dans le dossier `figures/` avec le **nom de fichier indiqué**.

## Légende

| Statut | Signification |
|--------|---------------|
| **OBSOLÈTE** | Présent dans le rapport mais ne reflète plus l'implémentation actuelle — à régénérer |
| **MANQUANT** | Nouveau diagramme à ajouter au rapport |

## Diagrammes encore valides (pas de régénération obligatoire)

Les diagrammes suivants restent cohérents avec le code métier actuel (ajustements cosmétiques optionnels) :

- `DiagrammeClasses_Domaine`
- `DiagrammeEtats_Trip`
- `DiagrammeSequence_GPS`
- `DiagrammeEtats_Incident`
- `DiagrammeSequence_Incident`
- `DiagrammeEtats_Schedule`
- `DiagrammeSequence_Conflits`
- `DiagrammeSequence_JobDocuments`
- `DiagrammeActivite_KPI`

---

## 1. DiagrammeCasUtilisation — **OBSOLÈTE**

**Fichier de sortie :** `figures/DiagrammeCasUtilisation.pdf`

Ajouts requis : mode offline, synchronisation, inscription KYC gestionnaire, intégration Kernel.

```plantuml
@startuml DiagrammeCasUtilisation
left to right direction
skinparam packageStyle rectangle
skinparam actorStyle awesome

actor "Super-Admin" as SA
actor "Admin Flotte" as AD
actor "Gestionnaire" as MG
actor "Conducteur" as DR
actor "Visiteur" as VS

rectangle "FleetMan — Plateforme B2B" {
  usecase "S'inscrire (KYC)" as UC_REG
  usecase "Se connecter (Kernel)" as UC_LOGIN
  usecase "Rafraîchir session JWT" as UC_REFRESH
  usecase "Gérer flottes / véhicules / conducteurs" as UC_FLEET
  usecase "Planifier trajets et affectations" as UC_PLAN
  usecase "Suivre télémetrie GPS" as UC_GPS
  usecase "Déclarer incidents / maintenance" as UC_OPS
  usecase "Gérer documents légaux" as UC_DOC
  usecase "Consulter KPIs et alertes" as UC_KPI
  usecase "Gérer abonnements et plans" as UC_SUB
  usecase "Travailler hors ligne" as UC_OFF
  usecase "Synchroniser données locales" as UC_SYNC
  usecase "Résoudre conflits de sync" as UC_CONF
}

VS --> UC_REG
VS --> UC_LOGIN
SA --> UC_LOGIN
AD --> UC_LOGIN
MG --> UC_LOGIN
DR --> UC_LOGIN

SA --> UC_SUB
SA --> UC_KPI
AD --> UC_FLEET
AD --> UC_DOC
MG --> UC_FLEET
MG --> UC_PLAN
MG --> UC_GPS
MG --> UC_OPS
MG --> UC_DOC
MG --> UC_KPI
MG --> UC_OFF
MG --> UC_SYNC
MG --> UC_CONF
DR --> UC_GPS
DR --> UC_OPS

UC_OFF ..> UC_SYNC : <<include>>
UC_LOGIN ..> UC_REFRESH : <<extend>>
UC_REG ..> UC_DOC : pièces KYC

@enduml
```

---

## 2. DiagrammePackages — **OBSOLÈTE**

**Fichier de sortie :** `figures/DiagrammePackages.pdf`

```plantuml
@startuml DiagrammePackages
skinparam packageStyle rectangle
skinparam linetype ortho

package "fleet_management_front (Next.js 15)" {
  package "app" { package "dashboard" package "auth" package "offline" }
  package "components" { package "dashboard/views" package "offline" }
  package "lib" {
    package "api"
    package "auth"
    package "offline" {
      [sync-engine]
      [mutation-queue]
      [repositories]
      [api-client]
    }
  }
}

package "fleet-management-back" {
  package "domain" {
    package "model"
    package "ports.in"
    package "ports.out"
    package "exception"
  }
  package "application" { package "service" }
  package "infrastructure" {
    package "adapters.inbound.rest"
    package "adapters.outbound.persistence"
    package "adapters.outbound.external" {
      [KernelAuthAdapter]
      [KernelResourceAdapter]
      [KernelFileAdapter]
      [FakeAuthAdapter]
    }
    package "config"
  }
}

package "Kernel RT-Comops" {
  [auth-core]
  [organization-core]
  [resource-core]
  [file-core]
}

fleet_management_front --> fleet-management-back : HTTPS REST
fleet-management-back --> Kernel RT-Comops : WebClient
@enduml
```

---

## 3. DiagrammeComposants — **OBSOLÈTE**

**Fichier de sortie :** `figures/DiagrammeComposants.pdf`

```plantuml
@startuml DiagrammeComposants
skinparam componentStyle rectangle

package "Client" {
  [Navigateur Web] as BR
  [Service Worker Serwist] as SW
  [IndexedDB Dexie] as IDB
}

package "Frontend Next.js" {
  [AuthProvider] as AP
  [OfflineProvider] as OP
  [Dashboard Views] as DV
  [Offline Data Manager] as ODM
  [Sync Engine] as SE
}

package "Backend Spring WebFlux" {
  [Controllers REST] as CTL
  [Application Services] as SVC
  [JwtAuthenticationManager] as JWT
  [SyncPullService / SyncPushService] as SYNC
  [IdempotencyWebFilter] as IDEM
}

database "PostgreSQL + PostGIS" as PG
database "Redis" as RD
queue "Kafka" as KF

cloud "Kernel RT-Comops" as KC {
  [auth-core]
  [resource-core]
  [file-core]
  [organization-core]
}

BR --> AP
BR --> DV
BR --> SW
SW --> BR : cache assets
DV --> ODM
ODM --> IDB
ODM --> SE
SE --> CTL : /api/v1/sync/*
DV --> CTL : /api/v1/*
CTL --> JWT
CTL --> SVC
SVC --> PG
SVC --> RD
SVC --> KC : WebClient
SVC --> SYNC
SYNC --> PG
KF --> SVC : événements Kernel
@enduml
```

---

## 4. DiagrammeClasses_Ports — **OBSOLÈTE**

**Fichier de sortie :** `figures/DiagrammeClasses_Ports.pdf`

```plantuml
@startuml DiagrammeClasses_Ports
skinparam classAttributeIconSize 0

interface AuthPort {
  +login(identifier, password)
  +refresh(refreshToken)
  +getUserProfile(token)
  +discoverContexts(principal, password)
  +selectContext(selectionToken, contextId, orgId)
}

interface ExternalVehiclePort {
  +registerVehicle(...)
  +updateVehicleStatus(...)
}

interface ExternalFilePort {
  +uploadFile(...)
  +getFileUrl(...)
}

interface ExternalOrganizationPort {
  +createOrganization(...)
}

interface ExternalActorPort {
  +provisionDriver(...)
  +provisionManager(...)
}

class KernelAuthAdapter
class KernelResourceAdapter
class KernelFileAdapter
class KernelOrganizationAdapter
class KernelActorAdapter
class FakeAuthAdapter

class AuthController
class VehicleController
class SyncController
class AuthUseCase
class VehicleService
class SyncPushService

AuthController --> AuthUseCase
AuthUseCase --> AuthPort
KernelAuthAdapter ..|> AuthPort
FakeAuthAdapter ..|> AuthPort

VehicleController --> VehicleService
VehicleService --> ExternalVehiclePort
KernelResourceAdapter ..|> ExternalVehiclePort

SyncController --> SyncPushService
KernelFileAdapter ..|> ExternalFilePort
KernelOrganizationAdapter ..|> ExternalOrganizationPort
KernelActorAdapter ..|> ExternalActorPort
@enduml
```

---

## 5. DiagrammeDeploiement — **OBSOLÈTE**

**Fichier de sortie :** `figures/DiagrammeDeploiement.pdf`

```plantuml
@startuml DiagrammeDeploiement
skinparam nodeStyle rectangle

node "Poste utilisateur" {
  artifact "Chrome / Firefox" as BR
  artifact "Next.js (dev :3001 / prod)" as FE
  database "IndexedDB fleetman_offline" as IDB
  artifact "Service Worker sw.js" as SW
}

node "Serveur local Docker" {
  node "fleet-management-back :8081" as BE {
    component "Spring Boot WebFlux"
  }
  database "PostgreSQL 16 :5433" as PG
  database "Redis :6380" as RD
  queue "Kafka (optionnel)" as KF
}

cloud "kernel-core.yowyob.com" as KC {
  node "Kernel RT-Comops" as KAPI
}

BR --> FE : HTTPS
FE --> BE : REST /api/v1
FE --> IDB
FE --> SW
BE --> PG : R2DBC
BE --> RD
BE --> KF
BE --> KAPI : WebClient\nX-Tenant-Id / X-Client-Id
@enduml
```

---

## 6. DiagrammeSequence_Auth — **OBSOLÈTE**

**Fichier de sortie :** `figures/DiagrammeSequence_Auth.pdf`

Remplace l'ancien flux auth locale par le flux Kernel en 2 étapes.

```plantuml
@startuml DiagrammeSequence_Auth
autonumber
skinparam responseMessageBelowArrow true

actor Utilisateur as U
participant "Frontend\nNext.js" as FE
participant "AuthController" as AC
participant "KernelAuthAdapter" as KA
participant "KernelAuthApiClient" as KC
database "PostgreSQL\nusers" as DB
participant "JwtAuthenticationManager" as JWT

== Connexion ==
U -> FE : email + mot de passe
FE -> AC : POST /api/v1/auth/login
AC -> KA : login(identifier, password)
KA -> KC : POST /api/auth/discover-contexts\n(principal, password)
KC --> KA : selectionToken + contexts[]
KA -> KC : POST /api/auth/select-context\n(selectionToken, contextId, orgId?)
KC --> KA : accessToken + refreshToken (JWT RS256)
KA -> DB : findByKernelId / rebind UUID local
KA --> AC : AuthResponse
AC --> FE : ApiResponse { accessToken, user }
FE -> FE : saveSession(localStorage)

== Requête API protégée ==
U -> FE : action dashboard
FE -> AC : GET /api/v1/... + Bearer JWT
AC -> JWT : authenticate(token)
JWT -> KA : getUserProfile(token)\n(décodage claims permissions)
KA --> JWT : UserDetail + roles
JWT -> DB : rebind kernel_id → local id
JWT --> AC : SecurityContext
AC --> FE : 200 OK + données

== Token expiré ==
FE -> AC : POST /api/v1/auth/refresh
AC -> KA : refresh(refreshToken)
KA -> KC : POST /api/auth/refresh
KC --> KA : nouveaux tokens
KA --> FE : session mise à jour

@enduml
```

---

## 7. DiagrammeArchitecture_Offline — **MANQUANT**

**Fichier de sortie :** `figures/DiagrammeArchitecture_Offline.pdf`

```plantuml
@startuml DiagrammeArchitecture_Offline
skinparam componentStyle rectangle

package "Couche UI" {
  [Dashboard Manager/Admin] as UI
  [OfflineBanner / SyncStatusBadge] as BAN
  [ConflictResolverDialog] as CRD
}

package "Offline Data Manager" {
  [offlineApiFetch] as OAF
  [read cache-first] as READ
  [write optimistic] as WRITE
}

package "Persistance locale (Dexie)" {
  database "entities" as E
  database "mutations" as M
  database "fileUploads" as F
  database "syncMeta" as SM
  database "conflicts" as C
}

package "Sync Engine" {
  [pull.ts] as PULL
  [push.ts] as PUSH
  [sync-engine.ts] as ENG
}

package "Backend" {
  [GET /api/v1/sync/changes] as CHG
  [POST /api/v1/sync/mutations] as MUT
  [REST métier existant] as REST
}

UI --> OAF
OAF --> READ
OAF --> WRITE
READ --> E
WRITE --> M
WRITE --> E
ENG --> PULL
ENG --> PUSH
PULL --> CHG
PUSH --> MUT
MUT --> REST
ENG --> SM
ENG --> C
BAN --> ENG
CRD --> C
@enduml
```

---

## 8. DiagrammeSequence_Sync — **MANQUANT**

**Fichier de sortie :** `figures/DiagrammeSequence_Sync.pdf`

```plantuml
@startuml DiagrammeSequence_Sync
autonumber

actor Gestionnaire as U
participant "OfflineProvider" as OP
participant "Sync Engine" as SE
database "IndexedDB" as IDB
participant "SyncController" as SC
participant "SyncPullService" as SPULL
participant "SyncPushService" as SPUSH
participant "Services métier" as SVC

== Bootstrap (login online) ==
U -> OP : connexion réussie
OP -> SE : startBootstrap(role)
SE -> SC : GET /sync/changes?full=true&scope=manager
SC -> SPULL : buildSnapshot(role)
SPULL --> SC : entities + cursor
SC --> SE : delta
SE -> IDB : upsert entities + syncMeta

== Lecture hors ligne ==
U -> SE : consulter véhicules (offline)
SE -> IDB : read entities/vehicle
IDB --> U : données cache

== Mutation hors ligne ==
U -> SE : créer incident (offline)
SE -> IDB : enqueue mutation\nclientMutationId
SE -> IDB : upsert optimiste entity
SE --> U : UI immédiate (queued)

== Retour réseau ==
OP -> SE : onOnline / periodic sync
SE -> SC : POST /sync/mutations [batch]
SC -> SPUSH : replay mutations
SPUSH -> SVC : appels REST internes\n(Idempotency-Key)
SVC --> SPUSH : résultats
SPUSH --> SE : mapping clientId → serverId
SE -> IDB : resolve dependencies
SE -> SC : GET /sync/changes?since=cursor
SC --> SE : deltas serveur
SE -> IDB : merge + update cursor

@enduml
```

---

## 9. DiagrammeSequence_InscriptionKYC — **MANQUANT**

**Fichier de sortie :** `figures/DiagrammeSequence_InscriptionKYC.pdf`

```plantuml
@startuml DiagrammeSequence_InscriptionKYC
autonumber

actor "Futur gestionnaire" as M
participant "Frontend signup" as FE
participant "PublicApiController" as PUB
participant "SubscriptionRegistrationService" as SRS
participant "LocalFileStorageService" as LFS
database "PostgreSQL" as DB
actor "Super-Admin" as SA
participant "SuperAdminController" as SAC

M -> FE : formulaire inscription\n+ pièces ID_CARD, CRIMINAL_RECORD
FE -> PUB : POST /api/v1/public/register-manager\n(multipart)
PUB -> SRS : registerManager(cmd)
SRS -> LFS : upload documents (max 10)
LFS --> SRS : fileUrl
SRS -> DB : INSERT subscription (PENDING)\n+ subscription_documents
SRS --> FE : 201 Created

SA -> SAC : GET /subscriptions/pending
SAC --> SA : liste demandes
SA -> SAC : POST /subscriptions/{id}/approve
SAC -> DB : status = ACTIVE
SAC -> SRS : provision Kernel user/actor
SRS --> SA : compte activé

@enduml
```

---

## 10. DiagrammeComposants_Frontend — **MANQUANT**

**Fichier de sortie :** `figures/DiagrammeComposants_Frontend.pdf`

```plantuml
@startuml DiagrammeComposants_Frontend
skinparam packageStyle rectangle

package "Next.js 15 App Router" {
  package "(auth)" {
    [login/page] as LOGIN
    [signup/page] as SIGNUP
  }
  package "(dashboard)" {
    [manager/*] as MGR
    [admin/*] as ADM
    [super-admin/*] as SA
    [driver/*] as DRV
    [sync/page] as SYNC_PG
  }
  [sw.ts Serwist] as SW
  [manifest.ts PWA] as MAN
}

package "Contextes React" {
  [AuthProvider] as AUTH
  [OfflineProvider] as OFF
  [LanguageProvider] as I18N
}

package "lib/api" {
  [client.ts apiFetch] as API
  [manager.ts / admin.ts] as ROLE_API
  [mock-wrapper.ts] as MOCK
}

package "lib/offline" {
  [api-client.ts] as OAPI
  [sync-engine.ts] as SYNC
  [mutation-queue.ts] as MQ
  [entity-store.ts] as ES
}

package "lib/auth" {
  [session.ts] as SESS
  [refresh.ts] as REF
}

LOGIN --> AUTH
MGR --> OFF
MGR --> ROLE_API
ROLE_API --> MOCK
MOCK --> API
MOCK --> OAPI
OAPI --> SYNC
SYNC --> MQ
SYNC --> ES
AUTH --> SESS
AUTH --> REF
OFF --> SYNC
SW --> MAN
@enduml
```

---

## 11. DiagrammeIntegration_Kernel — **MANQUANT**

**Fichier de sortie :** `figures/DiagrammeIntegration_Kernel.pdf`

Vue d'ensemble de l'intégration réalisée (remplace le « plan en 6 phases » comme vision cible).

```plantuml
@startuml DiagrammeIntegration_Kernel
skinparam componentStyle rectangle

package "FleetMan Backend" {
  [KernelAuthAdapter] as AUTH
  [KernelResourceAdapter] as RES
  [KernelFileAdapter] as FILE
  [KernelOrganizationAdapter] as ORG
  [KernelActorAdapter] as ACT
  [KernelEventConsumer] as EVT
  [KernelTokenHolder] as TOK
}

package "Tables locales (liens Kernel)" {
  database "users.kernel_id" as U
  database "fleets.kernel_organization_id" as F
  database "vehicles.kernel_resource_id" as V
  database "drivers.kernel_actor_id" as D
}

cloud "Kernel RT-Comops API" {
  [/api/auth/*] as KAUTH
  [/api/resources/*] as KRES
  [/api/files/*] as KFILE
  [/api/organizations/*] as KORG
  [/api/actors/*] as KACT
}

queue "Kafka iwm.events.business" as KFK

AUTH --> KAUTH
RES --> KRES
FILE --> KFILE
ORG --> KORG
ACT --> KACT
TOK --> KAUTH
EVT <-- KFK
AUTH --> U
ORG --> F
RES --> V
ACT --> D
@enduml
```

---

## Instructions de compilation

```bash
# Exemple avec PlantUML CLI
cd figures/
plantuml -tpdf ../diagrammes/*.puml

# Ou en ligne : copier chaque bloc @startuml … @enduml
# sur https://www.plantuml.com/plantuml
```

Une fois les images générées, placez-les dans `figures/` et signalez-moi pour intégration finale dans le rapport LaTeX.
