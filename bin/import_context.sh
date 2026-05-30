#!/bin/bash

# Script pour générer un fichier de contexte complet pour le projet Spring Boot WebFlux.
OUTPUT_FILE="project_context.txt"
PROJECT_ROOT="."

echo "Génération du contexte pour le projet Fleet Management..."

echo "# Contexte du Projet : Fleet & Geofence API" > "$OUTPUT_FILE"
echo "Généré le : $(date)" >> "$OUTPUT_FILE"
echo "Framework : Spring Boot WebFlux (Reactive)" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# 2. Arborescence
echo "## 1. Arborescence du Projet" >> "$OUTPUT_FILE"
echo "\`\`\`" >> "$OUTPUT_FILE"
tree -a -I 'target|build|.git|.idea|.vscode|.mvn|gradle|bin' "$PROJECT_ROOT" >> "$OUTPUT_FILE"
echo "\`\`\`" >> "$OUTPUT_FILE"

# 3. Contenu des fichiers
echo "## 2. Contenu des Fichiers" >> "$OUTPUT_FILE"

find "$PROJECT_ROOT" -type f \( \
    -name "pom.xml" -o \
    -name "build.gradle" -o \
    -name "*.java" -o \
    -name "*.yml" -o \
    -name "*.properties" -o \
    -name "*.sql" -o \
    -name "*.md" \
\) | grep -v -e 'target/' -e 'build/' -e '.git/' | while read -r file; do
    
    echo "" >> "$OUTPUT_FILE"
    echo "---" >> "$OUTPUT_FILE"
    echo "### Fichier : \`$file\`" >> "$OUTPUT_FILE"
    echo "\`\`\`$(echo ${file##*.} | sed 's/yml/yaml/')" >> "$OUTPUT_FILE"
    cat "$file" >> "$OUTPUT_FILE"
    echo "\`\`\`" >> "$OUTPUT_FILE"
done

echo "✅ Contexte généré : $OUTPUT_FILE"