package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.infrastructure.config.bootstrap.DemoTestAccounts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Vérifie au démarrage que les secrets nécessaires sont fournis par l'environnement
 * (variables d'environnement ou fichier .env ignoré par git) : aucun secret n'a plus de
 * valeur par défaut dans le dépôt. En mode Kernel, un secret manquant arrête le démarrage
 * avec un message explicite plutôt que de produire des 401 incompréhensibles plus tard.
 */
@Slf4j
@Component
public class SecretsStartupValidator {

    @Value("${application.auth.mode:kernel}")
    private String authMode;

    @Value("${application.kernel.api-key:}")
    private String kernelApiKey;

    @Value("${application.bootstrap.admin.password:}")
    private String ownerPassword;

    @Value("${application.demo.password:}")
    private String demoPassword;

    @PostConstruct
    void validate() {
        DemoTestAccounts.configureDemoPassword(demoPassword);

        if (!"kernel".equalsIgnoreCase(authMode)) {
            return;
        }
        List<String> missing = new ArrayList<>();
        if (kernelApiKey.isBlank()) missing.add("KERNEL_API_KEY");
        if (ownerPassword.isBlank()) missing.add("KERNEL_OWNER_PASSWORD");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Secrets manquants : " + String.join(", ", missing)
                    + ". Copiez .env.example en .env (dossier fleet-management-back) et complétez-le,"
                    + " ou définissez ces variables d'environnement.");
        }
        log.info("🔐 Secrets Kernel chargés depuis l'environnement");
    }
}
