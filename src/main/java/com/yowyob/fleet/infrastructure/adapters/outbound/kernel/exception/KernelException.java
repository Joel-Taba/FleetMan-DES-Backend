package com.yowyob.fleet.infrastructure.adapters.outbound.kernel.exception;

import lombok.Getter;

/**
 * Exception typée pour les refus fonctionnels du Kernel RT-Comops.
 */
@Getter
public class KernelException extends RuntimeException {

    private final String kernelCode;

    public KernelException(String kernelCode, String message) {
        super(message);
        this.kernelCode = kernelCode;
    }

    public static KernelException of(String kernelCode, String message) {
        return new KernelException(kernelCode, message);
    }

    public static KernelException fromRemote(String body, int status) {
        String code = extractCode(body);
        return new KernelException(code, "Erreur Kernel [" + status + "]: " + body);
    }

    private static String extractCode(String body) {
        if (body == null) return "KERNEL_ERROR";
        if (body.contains("TENANT_REQUEST_QUOTA_EXCEEDED")) return "TENANT_REQUEST_QUOTA_EXCEEDED";
        if (body.contains("ORGANIZATION_SERVICE_NOT_SUBSCRIBED")) return "ORGANIZATION_SERVICE_NOT_SUBSCRIBED";
        if (body.contains("ORGANIZATION_SERVICE_QUOTA_EXCEEDED")) return "ORGANIZATION_SERVICE_QUOTA_EXCEEDED";
        if (body.contains("CLIENT_APPLICATION_SERVICE_NOT_ALLOWED")) return "CLIENT_APPLICATION_SERVICE_NOT_ALLOWED";
        if (body.contains("ORGANIZATION_CONTEXT_REQUIRED")) return "ORGANIZATION_CONTEXT_REQUIRED";
        return "KERNEL_ERROR";
    }
}
