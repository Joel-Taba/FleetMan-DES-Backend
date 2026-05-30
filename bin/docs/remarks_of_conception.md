# Remarques cahier d'analyse et conception

# Objectif
L'objectif de ce document est de fournir des remarques pour le cahier d'analyse et conception du projet Fleet management et geofence s'integrant dans le projet synthese de 5Gi: Trans Ens

# Liste des remarques

Chaque remarque sera constitue d'un page du titre de la remarque et de la description

---
## R1: raphael au feminin 

**page**: 2

**descripion**:  
Bihai rapahel
rapahel est ecrit au feminin,est-ce normal?

---
## R2: utilisation de FareCalculator

**page** : 14

**description** : Notre systeme utilisera t'il reellement farecalculator ? si oui dans quel cas d'utilisation ?

---

## R3: Absence de Navigoo dans le diagarmme de contexte

**page** : 14

**description** : Notre systeme utilisera certainement l'api des cartes pour montrer au client,chauffeur,fleet amanager mais notre diagaramme de contexte de le met pas en evidence

---

## R4: fautre d'orthographe

**page** : 14

**description** : les acteurs (qui utilise le système),le verbe utiliser doit etre a la 3e personne du pluriel(`avec ent`)

---

## R5: Probleme de coherence entre les diagrammes de cas d'utilisation et de contexte

**page** : 14 et 15

**description** : sur le diagramme de contexte,on voit bien que farecalculator fait partie des acteurs externes et le customer aussi mais dans le diagramme de cas d'utilisation,on ne les retrpiuve pas. De plus,on retrouve navigoo qui n'etait pas dans le diagramme de contexte. 

---

## R6: diagramme de use case,coherence dans le regroupement des use case

**page** : 15

**description** : 
Je remarque la factorisation qui est regroupage elegant des cas d'utilisation dans ce diagramme par l'heritage,ce qui est une bnne chose mais pour l'acteur fleet manager,je trouve que ce n'est pas suffisemment regroupe pour le management des vehicule(je parle de track vehicule,view vehicule statictics qui selpon moi sont des sous-cas de manage vehicule)

---
## R7: diagramme de sequence systeme
**page** : 29

**description** : 
- la methode valider identifiant que le systeme envoir a l'api d'auth doit etre une fonction ayant pour parametre les identifiants(email,mot de passe)

- Il y'a egalement un probleme de coherence linguistique,depuis le debut tous les diagrammes sont en anglais,mais celui ci est en francais.il nous faut une seule langue.

- est ce que le token est un message ou une variable? je crois aussi qu'ici c'est une fontion que l'api d'auth utilise pour envoyer le token et le role...aussi le role 
---
