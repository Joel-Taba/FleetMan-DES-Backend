# 📜 Scénarios Fonctionnels & Contrats d'API

**Projet :** Fleet Management System
**Version :** 2.1 (Validée)
**Objectif :** Définir toutes les interactions possibles entre le Frontend et le Backend.

---

## 1. Module : Authentification & Profils
*Gestion de l'identité et du compte personnel. L'application agit souvent comme un proxy vers le service Auth distant.*

| Acteur | Scénario | Méthode | Endpoint | Description |
| :--- | :--- | :---: | :--- | :--- |
| **Tous** | **S'inscrire** | `POST` | `/api/v1/auth/register` | Création de compte (User + Profil métier). Upload photo optionnel. |
| **Tous** | **Se connecter** | `POST` | `/api/v1/auth/login` | Récupération du Token JWT. |
| **Tous** | **Rafraîchir Token** | `POST` | `/api/v1/auth/refresh` | Renouvellement de session sans login. |
| **Tous** | **Voir mon profil** | `GET` | `/api/v1/auth/me` | Récupère les infos agrégées (Auth + Rôle local). |
| **Tous** | **Mettre à jour profil** | `PUT` | `/api/v1/auth/me` | Modification nom, téléphone, photo, etc. |
| **Tous** | **Supprimer mon compte** | `DELETE` | `/api/v1/auth/me` | Désactivation complète (Soft delete). |

---

## 2. Module : Administration (Super-User)
*Gestion des entreprises (Fleet Managers).*

| Acteur | Scénario | Méthode | Endpoint | Description |
| :--- | :--- | :---: | :--- | :--- |
| **Admin** | **Créer un Manager** | `POST` | `/api/v1/admin/managers` | Enrôlement manuel d'une entreprise. |
| **Admin** | **Lister les Managers** | `GET` | `/api/v1/admin/managers` | Liste paginée avec filtres (nom, statut). |
| **Admin** | **Détail d'un Manager** | `GET` | `/api/v1/admin/managers/{id}` | Infos complètes + Stats rapides. |
| **Admin** | **Modifier un Manager** | `PUT` | `/api/v1/admin/managers/{id}` | Mise à jour données administratives. |
| **Admin** | **Supprimer un Manager** | `DELETE` | `/api/v1/admin/managers/{id}` | Suppression définitive ou archivage. |
| **Admin** | **Suspendre/Activer** | `PATCH` | `/api/v1/admin/managers/{id}/status` | Bloque l'accès à la plateforme. |
| **Admin** | **Statistiques Globales**| `GET` | `/api/v1/admin/stats` | Vue d'ensemble (Nb Flottes, Véhicules, Users). |

---

## 3. Module : Gestion des Flottes
*Un Fleet Manager organise ses ressources en "Flottes".*

| Acteur | Scénario | Méthode | Endpoint | Description |
| :--- | :--- | :---: | :--- | :--- |
| **Manager** | **Créer une flotte** | `POST` | `/api/v1/fleets` | Création d'une entité organisationnelle. |
| **Manager** | **Lister mes flottes** | `GET` | `/api/v1/fleets` | Liste des flottes gérées par le user connecté. |
| **Manager** | **Détail d'une flotte** | `GET` | `/api/v1/fleets/{id}` | Infos + Synthèse (nb véhicules, nb drivers). |
| **Manager** | **Modifier une flotte** | `PUT` | `/api/v1/fleets/{id}` | Renommage, contact. |
| **Manager** | **Supprimer une flotte** | `DELETE` | `/api/v1/fleets/{id}` | Possible seulement si vide (ou cascade). |

---

## 4. Module : Gestion des Véhicules
*Cœur de l'inventaire. Le véhicule est une entité complexe composée.*

### 4.1. CRUD Véhicule (Base)
| Acteur | Scénario | Méthode | Endpoint | Description |
| :--- | :--- | :---: | :--- | :--- |
| **Manager** | **Ajouter un véhicule** | `POST` | `/api/v1/fleets/{id}/vehicles` | Création pivot (Plaque, Marque, Modèle). |
| **Manager** | **Lister les véhicules** | `GET` | `/api/v1/fleets/{id}/vehicles` | Filtres: Statut, Driver, Marque. |
| **Manager** | **Détail complet** | `GET` | `/api/v1/vehicles/{id}` | Agrège TOUT (Infos, Finance, Maint, Position). |
| **Manager** | **Modifier infos base** | `PUT` | `/api/v1/vehicles/{id}` | Marque, Modèle, Couleur, Année. |
| **Manager** | **Supprimer véhicule** | `DELETE` | `/api/v1/vehicles/{id}` | Retire le véhicule de la gestion. |

### 4.2. Gestion des Détails (Sous-ressources)
| Acteur | Scénario | Méthode | Endpoint | Description |
| :--- | :--- | :---: | :--- | :--- |
| **Manager** | **M.à.j Financier** | `PUT` | `/api/v1/vehicles/{id}/financial` | Assurance, Coût/km, Achat, Amortissement. |
| **Manager** | **M.à.j Maintenance** | `PUT` | `/api/v1/vehicles/{id}/maintenance` | Dates révision, État moteur, Santé batterie. |
| **Manager** | **Voir Opérationnel** | `GET` | `/api/v1/vehicles/{id}/operational` | Vue spécifique Télémétrie (Vitesse, Fuel, Km). |
| **Manager** | **Ajouter Photo** | `POST` | `/api/v1/vehicles/{id}/photos` | Upload image (Extérieur/Intérieur). |
| **Manager** | **Supprimer Photo** | `DELETE` | `/api/v1/vehicles/{id}/photos/{photoId}` | Suppression d'une image. |

---

## 5. Module : Gestion des Chauffeurs
*Le chauffeur est un Utilisateur avec des droits restreints et un permis.*

| Acteur | Scénario | Méthode | Endpoint | Description |
| :--- | :--- | :---: | :--- | :--- |
| **Manager** | **Enrôler un chauffeur** | `POST` | `/api/v1/drivers` | Crée le compte User + le Profil Driver. |
| **Manager** | **Lister mes chauffeurs**| `GET` | `/api/v1/drivers` | Liste filtrable (Actifs, En course, Libres). |
| **Manager** | **Détail Chauffeur** | `GET` | `/api/v1/drivers/{id}` | Profil, Permis, Véhicule actuel, Stats. |
| **Manager** | **Modifier Chauffeur** | `PUT` | `/api/v1/drivers/{id}` | Mise à jour infos, permis, photo. |
| **Manager** | **Désactiver Chauffeur** | `DELETE` | `/api/v1/drivers/{id}` | Désactive le compte (ne supprime pas l'historique). |
| **Manager** | **Assigner Véhicule** | `POST` | `/api/v1/drivers/{id}/assign` | Lie un véhicule libre à ce chauffeur. |
| **Manager** | **Libérer Chauffeur** | `POST` | `/api/v1/drivers/{id}/unassign` | Retire le véhicule (fin de quart). |

---

## 6. Module : Opérations & Trajets (Mobile)
*Utilisé par l'application Chauffeur.*

| Acteur | Scénario | Méthode | Endpoint | Description |
| :--- | :--- | :---: | :--- | :--- |
| **Driver** | **Mon Véhicule** | `GET` | `/api/v1/driver/vehicle` | Récupère le véhicule assigné au user connecté. |
| **Driver** | **Démarrer Course** | `POST` | `/api/v1/trips` | Initie un trajet. Start GPS. |
| **Driver** | **Envoyer Position** | `POST` | `/api/v1/trips/{id}/telemetry` | Envoi périodique (Lat, Lng, Vitesse). |
| **Driver** | **Terminer Course** | `POST` | `/api/v1/trips/{id}/end` | Clôture le trajet (Calcul distance/temps). |
| **Driver** | **Mes Trajets** | `GET` | `/api/v1/driver/trips` | Historique personnel. |
| **Manager**| **Tous les Trajets** | `GET` | `/api/v1/trips` | Historique global filtrable. |
| **Manager**| **Détail Trajet** | `GET` | `/api/v1/trips/{id}` | Tracé sur carte, timeline événements. |

---

## 7. Module : Geofencing
*Définition des zones de surveillance.*

| Acteur | Scénario | Méthode | Endpoint | Description |
| :--- | :--- | :---: | :--- | :--- |
| **Manager** | **Créer une Zone** | `POST` | `/api/v1/geofence/zones` | Envoi GeoJSON (Polygone/Cercle). |
| **Manager** | **Lister les Zones** | `GET` | `/api/v1/geofence/zones` | Récupère toutes les zones définies. |
| **Manager** | **Détail Zone** | `GET` | `/api/v1/geofence/zones/{id}` | Géométrie précise et règles. |
| **Manager** | **Modifier Zone** | `PUT` | `/api/v1/geofence/zones/{id}` | Ajustement des points ou du nom. |
| **Manager** | **Supprimer Zone** | `DELETE` | `/api/v1/geofence/zones/{id}` | Suppression logique. |
| **Manager** | **Historique Alertes** | `GET` | `/api/v1/geofence/events` | Liste des entrées/sorties détectées. |