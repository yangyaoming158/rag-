package com.ragdocs.web.dto;

public record StatsOverviewDto(
        long kbCount,
        long docCount,
        long chunkCount,
        long tokenSum,
        long avgLatencyMs
) {
}
