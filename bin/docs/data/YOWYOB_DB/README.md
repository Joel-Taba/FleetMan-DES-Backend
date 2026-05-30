# Yowyob Database - Instructions

Ce dossier contient les scripts SQL nécessaires pour initialiser, peupler et tester la base de données PostgreSQL du projet Yowyob. Le projet a été principalement testé en local.

## [Contenu des fichiers]

1. **`yowyob_db_init.sql`** : 
   - Crée le schéma (tables, types ENUM, triggers).
   - **Note :** Ce script commence par un `DROP SCHEMA public CASCADE`. Il effacera toutes les données existantes.

2. **`yowyob_db_seed.sql`** : 
   - Remplit la base avec des **données factices (Fake Data)** pour le développement.

3. **`yowyob_db_test.sql`** : 
   - Contient des requêtes de lecture pour vérifier la cohérence des relations et des données sur **certaines relations** de la base de données. *Chaque jeu de test doit renvoyer **au moins une ligne de données.***

## [Guide d'installation]

### Pré-requis
- PostgreSQL 16 ou supérieur.
- Un utilisateur PostgreSQL ayant les droits de création de tables.

### Étape 1 : Création de la base (si elle n'existe pas)
```bash
createdb -U <user> yowyob_db
```

### Étape 2 : Initialisation de la structure
```bash
psql -U <user> -d yowyob_db -f yowyob_db_init.sql
```

### Étape 3 : Peuplement des données (DEV / STAGING UNIQUEMENT)
```bash
psql -U <user> -d yowyob_db -f yowyob_db_seed.sql
```

### Étape 4 : Vérification
```bash
psql -U <user> -d yowyob_db -f yowyob_db_test.sql
```