package com.yowyob.fleet.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Renouvelle proactivement le token Kernel owner avant expiration (~15 min JWT).
 * Évite les pannes KYC / fichiers lorsque le token service n'est plus valide.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KernelTokenRefreshJob {

    private static final long REFRESH_BEFORE_SECONDS = 180L;

    private final KernelTokenHolder tokenHolder;

    @Scheduled(fixedRate = 12 * 60 * 1000L)
    public void refreshKernelTokenIfNeeded() {
        Instant expiry = tokenHolder.getCurrentTokenExpiry();
        boolean needsRefresh = expiry == null
                || !Instant.now().isBefore(expiry.minusSeconds(REFRESH_BEFORE_SECONDS));

        if (!needsRefresh) {
            log.debug("🔑 [KERNEL TOKEN JOB] Token encore valide jusqu'à {}", expiry);
            return;
        }

        tokenHolder.forceRefresh()
                .doOnSuccess(t -> log.info("✅ [KERNEL TOKEN JOB] Token service renouvelé"))
                .doOnError(e -> log.warn("⚠️ [KERNEL TOKEN JOB] Échec renouvellement : {}", e.getMessage()))
                .subscribe();
    }
}
