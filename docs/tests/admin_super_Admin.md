### 🛠️ PARTIE 1 : Lancement de l'environnement

Ouvre deux terminaux à la racine du projet.

**Terminal 1 : Lancer l'application**
```bash
chmod +x run_local.sh
./run_local.sh
```
*Note : Attends de voir le message `✅ Phase d'initialisation terminée`.*

**Terminal 2 : Accès à la DB (CLI)**
Si tu veux vérifier les tables rapidement sans DBeaver :
```bash
docker exec -it fleet-management-db psql -U fleet_admin -d yowyob_db
```

---

### 🧪 PARTIE 2 : Scénarios de Tests (Le rapport de bataille)

Ouvre ton **Swagger UI** : [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

#### ÉTAPE 1 : Connexion du Super Admin (Bootstrap)
On utilise l'utilisateur créé automatiquement dans ton `application.yml`.

1.  Endpoint : `POST /api/v1/auth/login`
2.  Payload :
```json
{
  "identifier": "superadmin123@transens.com",
  "password": "password123"
}
```
3.  **Action :** Copie l' `accessToken`. Clique sur le bouton **Authorize** en haut de Swagger et colle le token.

---

#### ÉTAPE 2 : Super Admin crée un Admin (Success & Logic)
On vérifie que le Super Admin peut déléguer des droits.

1.  Endpoint : `POST /api/v1/admin/super/admins`
2.  Payload :
```json
{
  "username": "admin_douala",
  "password": "Password123!",
  "email": "admin.douala@yowyob.test",
  "phone": "+237611223344",
  "firstName": "Chef",
  "lastName": "Douala"
}
```
3.  **Vérification DB (Souveraineté) :** Dans ton terminal 2, tape :
    `SELECT * FROM fleet.users WHERE username = 'admin_douala';`
    *Tu dois voir la ligne apparaître en local.*

---

#### ÉTAPE 3 : Test des Erreurs (Sécurité)
On vérifie que le système rejette les actions interdites.

1.  **Auto-blocage interdit :** Tente de désactiver ton propre compte Super Admin.
    *   Endpoint : `PATCH /api/v1/admin/super/admins/{ton_id}/toggle`
    *   **Résultat attendu :** `403 Forbidden` avec le code `SADM_002` (Auto-modification interdite).
2.  **Accès refusé :** Déconnecte-toi (ou retire le token) et tente de lister les admins.
    *   **Résultat attendu :** `401 Unauthorized`.

---

#### ÉTAPE 4 : Admin & Auto-réparation (Le "Self-Healing")
C'est le test le plus important pour ta synchro.

1.  **Préparation du "bug" (Terminal 2) :** On simule un manager qui existe en identité mais pas en métier.
    ```sql
    -- On crée un user manager directement en base (ou on en prend un existant)
    -- On supprime sa ligne métier s'il en a une
    DELETE FROM fleet.fleet_managers WHERE user_id IN (SELECT id FROM fleet.users LIMIT 1);
    ```
2.  **Action Admin :** Connecte-toi avec l'Admin créé à l'étape 2, puis liste les managers.
    *   Endpoint : `GET /api/v1/admin/management/managers`
3.  **Vérification :** Retourne dans ton terminal 2.
    ```sql
    SELECT * FROM fleet.fleet_managers;
    ```
    *La ligne supprimée a dû être recréée automatiquement par le service !*

---

#### ÉTAPE 5 : Synchronisation d'Identité
1.  Va sur ton service Auth (ou simule une modif via SQL sur `public.users` si tu es en mode fake).
2.  Change le `firstName` d'un manager.
3.  Appelle `GET /api/v1/admin/management/managers/{id}`.
4.  Vérifie que le `firstName` dans ta table locale `fleet.users` a été mis à jour instantanément.

