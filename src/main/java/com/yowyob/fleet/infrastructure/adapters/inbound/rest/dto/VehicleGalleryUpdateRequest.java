package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.util.List;

public record VehicleGalleryUpdateRequest(
        String photoUrl,
        List<String> galleryUrls
) {}
