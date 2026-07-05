package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Wrapper de pagination réactif pour les réponses de listing.
 *
 * Utilisé par tous les endpoints de listing pour retourner :
 * - Le contenu de la page courante
 * - Les métadonnées de pagination (page, taille, total)
 *
 * Exemple d'utilisation dans un controller :
 * <pre>
 *   return PageResponse.of(
 *       vehicleUseCase.getVehicles(managerId),
 *       page, size
 *   );
 * </pre>
 *
 * @param <T> Type des éléments de la page
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty
) {

    /**
     * Construit une PageResponse à partir d'un Flux réactif.
     * Collecte tous les éléments, puis applique la pagination en mémoire.
     *
     * @param flux   Flux source (tous les éléments filtrés par manager)
     * @param page   Numéro de page (0-indexé)
     * @param size   Taille de la page
     * @param <T>    Type des éléments
     * @return Mono<PageResponse<T>>
     */
    public static <T> Mono<PageResponse<T>> of(Flux<T> flux, int page, int size) {
        // Validation des paramètres
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 200); // Max 200 éléments par page

        return flux.collectList()
                .map(allItems -> {
                    long total = allItems.size();
                    int totalPagesCount = (int) Math.ceil((double) total / safeSize);

                    int fromIndex = safePage * safeSize;
                    int toIndex = Math.min(fromIndex + safeSize, (int) total);

                    List<T> pageContent = fromIndex >= total
                            ? List.of()
                            : allItems.subList(fromIndex, toIndex);

                    return new PageResponse<>(
                            pageContent,
                            safePage,
                            safeSize,
                            total,
                            totalPagesCount,
                            safePage == 0,
                            safePage >= totalPagesCount - 1,
                            pageContent.isEmpty()
                    );
                });
    }

    /**
     * Construit une PageResponse directement depuis une liste déjà collectée.
     * Utile quand la pagination est déléguée à la base de données.
     *
     * @param content       Contenu de la page
     * @param page          Numéro de page
     * @param size          Taille de la page
     * @param totalElements Nombre total d'éléments
     * @param <T>           Type des éléments
     * @return PageResponse<T>
     */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPagesCount = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PageResponse<>(
                content,
                page,
                size,
                totalElements,
                totalPagesCount,
                page == 0,
                page >= totalPagesCount - 1,
                content.isEmpty()
        );
    }

    /**
     * Retourne une page vide.
     */
    public static <T> PageResponse<T> empty(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0, true, true, true);
    }
}
