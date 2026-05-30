# Fleet Management & Geofencing API 🚚🛰️

Service backend réactif pour la gestion de flottes de véhicules et le géorepérage en temps réel.

## 🤝 Workflow de Collaboration IA

Ce projet est développé en mode **Pair Programming** avec une IA (Gemini/ChatGPT). Pour maintenir la cohérence :

1. **Roadmap** : Consultez le fichier `todo.md` pour voir la tâche en cours.
2. **Contextualisation** : L'IA a besoin du code source complet. Utilisez le script de synchronisation :
   ```bash
   chmod +x import_context.sh
   ./import_context.sh
   ```
   Cela génère/met à jour le fichier `project_context.txt`.
3. **Initialisation de l'IA** : Pour commencer une session, copiez-collez le contenu de `docs/prompts/master_pair_programmer.md` suivi du contenu de `project_context.txt`.

## 🛠️ Installation & Tests

### Prérequis
- Java 21
- PostgreSQL (avec accès aux serveurs distants configurés dans le `.yml`)

### Lancer l'application
```bash
./mvnw clean install
./mvnw spring-boot:run
```

### Valider les changements
- **Swagger UI** : [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Santé de la DB** : Utilisez les endpoints définis dans chaque jalon (voir `todo.md`).
```

