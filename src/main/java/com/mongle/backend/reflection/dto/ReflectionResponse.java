package com.mongle.backend.reflection.dto;

import java.util.List;

public record ReflectionResponse(String reply, boolean riskDetected, List<RiskResource> riskResources) {

    public static ReflectionResponse safe(String reply) {
        return new ReflectionResponse(reply, false, null);
    }

    public static ReflectionResponse risky(String reply, List<RiskResource> resources) {
        return new ReflectionResponse(reply, true, resources);
    }
}
