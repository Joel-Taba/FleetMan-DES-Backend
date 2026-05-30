#!/bin/bash

# Configuration des chemins
SRC_ROOT="src/main/java/com/yowyob/fleet"
DEST_PROJECT="../../../rideandgo/ride_and_go_backend"
DEST_ROOT="$DEST_PROJECT/src/main/java/com/yowyob/rideandgo"

# Liste des fichiers à copier
FILES=(
  "domain/ports/in/AuthUseCase.java"
  "domain/ports/out/AuthPort.java"
  "application/service/AuthService.java"
  "infrastructure/config/AuthConfig.java"
  "infrastructure/adapters/outbound/external/FakeAuthAdapter.java"
  "infrastructure/adapters/outbound/external/RemoteAuthAdapter.java"
  "infrastructure/adapters/outbound/external/client/AuthApiClient.java"
  "infrastructure/adapters/inbound/rest/AuthController.java"
)

echo "🚀 Début de la synchronisation de l'Auth vers Ride & Go..."

for FILE in "${FILES[@]}"; do
    echo "📄 Traitement de : $FILE"
    
    # Créer le répertoire de destination s'il n'existe pas
    mkdir -p "$(dirname "$DEST_ROOT/$FILE")"
    
    # Copier le fichier
    cp "$SRC_ROOT/$FILE" "$DEST_ROOT/$FILE"
    
    # Remplacer le nom du package et les imports (fleet -> rideandgo)
    sed -i 's/com.yowyob.fleet/com.yowyob.rideandgo/g' "$DEST_ROOT/$FILE"
done

echo "✅ Synchronisation terminée !"
echo "⚠️ N'oublie pas de vérifier la cohérence de WebClientConfig dans Ride & Go."
