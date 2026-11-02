package com.yowyob.fleet.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private static final List<String> REQUIRED = List.of("INSURANCE");

    private static DocumentRules.Piece piece(String review, boolean current, LocalDate expiry) {
        return new DocumentRules.Piece("INSURANCE", review, current, expiry);
    }

    @Test
    void pendingOrRejectedPiece_doesNotCount() {
        assertTrue(DocumentRules.gaps(List.of(piece("PENDING", false, TODAY.plusYears(1))), REQUIRED, TODAY).get(0).missing());
        assertTrue(DocumentRules.gaps(List.of(piece("REJECTED", false, TODAY.plusYears(1))), REQUIRED, TODAY).get(0).missing());
    }

    @Test
    void renewalPending_keepsPreviousVersionInForce() {
        var pieces = List.of(piece("APPROVED", true, TODAY.plusDays(5)), piece("PENDING", false, TODAY.plusYears(1)));
        assertTrue(DocumentRules.gaps(pieces, REQUIRED, TODAY).isEmpty());
        assertEquals("EXPIRING", DocumentRules.checklistState(pieces, TODAY));
    }

    @Test
    void expiredBeforeSlotEnd_isAGap() {
        var gaps = DocumentRules.gaps(List.of(piece("APPROVED", true, TODAY.plusDays(1))), REQUIRED, TODAY.plusDays(2));
        assertFalse(gaps.get(0).missing());
        assertEquals(TODAY.plusDays(1), gaps.get(0).expiredOn());
    }

    @Test
    void pieceWithoutExpiry_staysValid() {
        assertTrue(DocumentRules.gaps(List.of(piece("APPROVED", true, null)), REQUIRED, TODAY.plusYears(50)).isEmpty());
    }

    @Test
    void checklistState_followsReviewThenExpiry() {
        assertEquals("MISSING", DocumentRules.checklistState(List.of(), TODAY));
        assertEquals("PENDING", DocumentRules.checklistState(List.of(piece("PENDING", false, null)), TODAY));
        assertEquals("REJECTED", DocumentRules.checklistState(List.of(piece("REJECTED", false, null)), TODAY));
        assertEquals("EXPIRED", DocumentRules.checklistState(List.of(piece("APPROVED", true, TODAY.minusDays(1))), TODAY));
        assertEquals("OK", DocumentRules.checklistState(List.of(piece("APPROVED", true, TODAY.plusDays(31))), TODAY));
    }

    @Test
    void effectiveStatus_archiveAndReplacementWin() {
        assertEquals("ARCHIVED", DocumentRules.effectiveStatus("APPROVED", true, false, null, TODAY));
        assertEquals("REPLACED", DocumentRules.effectiveStatus("APPROVED", false, true, null, TODAY));
        assertEquals("EXPIRING_SOON", DocumentRules.effectiveStatus("APPROVED", false, false, TODAY.plusDays(30), TODAY));
    }

    @Test
    void legalBase_isFixed() {
        assertEquals(List.of("INSURANCE", "TECHNICAL_CONTROL", "REGISTRATION"), DocumentCatalog.legalTypes(DocumentCatalog.TargetType.VEHICLE));
        assertEquals(List.of("DRIVING_LICENSE"), DocumentCatalog.legalTypes(DocumentCatalog.TargetType.DRIVER));
        assertTrue(DocumentCatalog.legalTypes(DocumentCatalog.TargetType.MANAGER).isEmpty());
    }
}
