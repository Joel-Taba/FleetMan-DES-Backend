package com.yowyob.fleet.infrastructure.shared.util;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utilitaire d'export CSV réactif.
 *
 * Génère un fichier CSV en mémoire à partir d'un Flux de données.
 * Utilisé par les endpoints d'export pour les rapports et historiques.
 *
 * Exemple d'utilisation :
 * <pre>
 *   return CsvExportUtil.export(
 *       maintenanceFlux,
 *       List.of("ID", "Sujet", "Coût", "Date"),
 *       m -> List.of(
 *           m.getId().toString(),
 *           m.getSubject(),
 *           m.getCost() != null ? m.getCost().toString() : "",
 *           m.getDateTime().toString()
 *       )
 *   );
 * </pre>
 */
public final class CsvExportUtil {

    private static final char SEPARATOR = ',';
    private static final char QUOTE = '"';
    private static final String LINE_END = "\r\n";
    private static final String BOM = "\uFEFF"; // UTF-8 BOM pour Excel

    private CsvExportUtil() {}

    /**
     * Génère un CSV complet depuis un Flux réactif.
     *
     * @param flux        Flux source des données
     * @param headers     En-têtes des colonnes
     * @param rowMapper   Fonction de mapping d'un élément vers une liste de valeurs
     * @param <T>         Type des éléments
     * @return Mono<String> contenant le CSV complet (avec BOM UTF-8)
     */
    public static <T> Mono<String> export(
            Flux<T> flux,
            List<String> headers,
            Function<T, List<String>> rowMapper
    ) {
        return flux.collectList()
                .map(items -> buildCsv(items, headers, rowMapper));
    }

    /**
     * Génère un CSV depuis une liste déjà collectée.
     */
    public static <T> String buildCsv(
            List<T> items,
            List<String> headers,
            Function<T, List<String>> rowMapper
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(BOM); // BOM pour compatibilité Excel

        // En-têtes
        sb.append(buildRow(headers));

        // Lignes de données
        for (T item : items) {
            sb.append(buildRow(rowMapper.apply(item)));
        }

        return sb.toString();
    }

    /**
     * Construit une ligne CSV à partir d'une liste de valeurs.
     * Gère l'échappement des guillemets et des virgules.
     */
    private static String buildRow(List<String> values) {
        return values.stream()
                .map(CsvExportUtil::escapeValue)
                .collect(Collectors.joining(String.valueOf(SEPARATOR)))
                + LINE_END;
    }

    /**
     * Échappe une valeur pour le format CSV.
     * Entoure de guillemets si la valeur contient une virgule, un guillemet ou un saut de ligne.
     */
    private static String escapeValue(String value) {
        if (value == null) return "";
        if (value.contains(String.valueOf(SEPARATOR))
                || value.contains(String.valueOf(QUOTE))
                || value.contains("\n")
                || value.contains("\r")) {
            return QUOTE + value.replace(String.valueOf(QUOTE), String.valueOf(QUOTE) + QUOTE) + QUOTE;
        }
        return value;
    }
}
