package com.yowyob.fleet.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssignmentOverlapTest {

    private static Assignment assignment(
            LocalDateTime start,
            LocalDateTime end
    ) {
        return new Assignment(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                start,
                end,
                Assignment.Status.PENDING,
                null,
                null,
                BigDecimal.TEN,
                null,
                null,
                LocalDateTime.now()
        );
    }

    @Test
    void overlapsWhenRangesIntersect() {
        var a = assignment(
                LocalDateTime.of(2026, 6, 10, 8, 0),
                LocalDateTime.of(2026, 6, 10, 12, 0)
        );

        assertTrue(a.overlapsWith(
                LocalDateTime.of(2026, 6, 10, 10, 0),
                LocalDateTime.of(2026, 6, 10, 14, 0)
        ));
    }

    @Test
    void doesNotOverlapWhenRangesAreDisjoint() {
        var a = assignment(
                LocalDateTime.of(2026, 6, 10, 8, 0),
                LocalDateTime.of(2026, 6, 10, 10, 0)
        );

        assertFalse(a.overlapsWith(
                LocalDateTime.of(2026, 6, 10, 10, 0),
                LocalDateTime.of(2026, 6, 10, 12, 0)
        ));
    }
}
