package com.yowyob.fleet.infrastructure.shared.util;

import reactor.core.publisher.Flux;

import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;

/**
 * Utilitaire de tri réactif pour les Flux.
 *
 * Permet d'appliquer un tri dynamique sur un Flux à partir
 * d'un nom de champ et d'une direction (asc/desc).
 *
 * Utilisé par les controllers pour supporter le paramètre ?sort=field,direction
 */
public final class SortUtils {

    private SortUtils() {}

    /**
     * Applique un tri sur un Flux selon un champ et une direction.
     *
     * @param flux       Flux source
     * @param sortField  Nom du champ de tri (ex: "name", "createdAt")
     * @param sortDir    Direction : "asc" ou "desc"
     * @param extractors Map des extracteurs de champs comparables
     * @param <T>        Type des éléments
     * @return Flux trié
     */
    public static <T> Flux<T> sort(
            Flux<T> flux,
            String sortField,
            String sortDir,
            Map<String, Function<T, Comparable>> extractors
    ) {
        if (sortField == null || sortField.isBlank()) {
            return flux;
        }

        @SuppressWarnings("unchecked")
        Function<T, Comparable> extractor = extractors.get(sortField.toLowerCase());
        if (extractor == null) {
            return flux; // Champ inconnu → pas de tri
        }

        @SuppressWarnings("unchecked")
        Comparator<T> comparator = Comparator.comparing(
                t -> (Comparable) extractor.apply(t),
                Comparator.nullsLast(Comparator.naturalOrder())
        );

        if ("desc".equalsIgnoreCase(sortDir)) {
            comparator = comparator.reversed();
        }

        return flux.sort(comparator);
    }

    /**
     * Détermine si la direction de tri est descendante.
     */
    public static boolean isDesc(String sortDir) {
        return "desc".equalsIgnoreCase(sortDir);
    }
}
