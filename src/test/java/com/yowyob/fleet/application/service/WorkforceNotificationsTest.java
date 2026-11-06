package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.Assignment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Notifications du domaine effectifs : postes (SHIFT_*) envoyées au chauffeur via AppNotifier. */
@ExtendWith(MockitoExtension.class)
class WorkforceNotificationsTest {

    @Mock DatabaseClient db;
    @Mock AppNotifier notifier;

    @InjectMocks WorkRulesService workRules;

    private final UUID driver = UUID.randomUUID();
    private final UUID schedule = UUID.randomUUID();
    private final UUID shift = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedRefs(String event) {
        ArgumentCaptor<Map<String, Object>> refs = ArgumentCaptor.forClass(Map.class);
        verify(notifier).toUser(eq(driver), isNull(), anyString(), anyString(), any(), eq(event), refs.capture());
        return refs.getValue();
    }

    @Test
    void modifiedShiftKeepsShiftIdSoTheDriverCanOpenIt() {
        when(notifier.toUser(any(), any(), anyString(), anyString(), any(), anyString(), any())).thenReturn(Mono.empty());
        StepVerifier.create(workRules.notifyShift(driver, "Poste modifié", "msg",
                WorkRulesService.ShiftChangeKind.MODIFIED, schedule, shift)).verifyComplete();
        Map<String, Object> refs = capturedRefs("SHIFT_CHANGED");
        assertThat(refs).containsEntry("assignmentId", shift.toString())
                .containsEntry("scheduleId", schedule.toString())
                .containsEntry("change", "MODIFIED");
    }

    @Test
    void deletedOrRemovedShiftIsNotLinkedAnymore() {
        when(notifier.toUser(any(), any(), anyString(), anyString(), any(), anyString(), any())).thenReturn(Mono.empty());
        StepVerifier.create(workRules.notifyShift(driver, "Poste supprimé", "msg",
                WorkRulesService.ShiftChangeKind.DELETED, schedule, shift)).verifyComplete();
        Map<String, Object> refs = capturedRefs("SHIFT_CHANGED");
        assertThat(refs).doesNotContainKey("assignmentId").containsEntry("change", "DELETED");
    }

    @Test
    void newShiftIsInformativeOthersAreWarnings() {
        when(notifier.toUser(any(), any(), anyString(), anyString(), any(), anyString(), any())).thenReturn(Mono.empty());
        StepVerifier.create(workRules.notifyShift(driver, "Nouveau poste", "msg",
                WorkRulesService.ShiftChangeKind.NEW, null, shift)).verifyComplete();
        verify(notifier).toUser(eq(driver), isNull(), anyString(), anyString(), eq(AppNotifier.Level.INFO),
                eq("SHIFT_CHANGED"), any());
    }

    @Test
    void noShowNotifiesTheDriverWithTheReason() {
        when(notifier.toUser(any(), any(), anyString(), anyString(), any(), anyString(), any())).thenReturn(Mono.empty());
        Assignment a = new Assignment(shift, schedule, UUID.randomUUID(), UUID.randomUUID(), driver,
                LocalDateTime.of(2026, 11, 6, 8, 0), LocalDateTime.of(2026, 11, 6, 17, 0),
                Assignment.Status.NO_SHOW, null, null, null);
        StepVerifier.create(workRules.notifyNoShow(a, "Injoignable")).verifyComplete();
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notifier).toUser(eq(driver), isNull(), eq("Absence au poste"), message.capture(),
                eq(AppNotifier.Level.WARNING), eq("SHIFT_NO_SHOW"), any());
        assertThat(message.getValue()).contains("06/11 à 08:00").contains("Injoignable").doesNotContain("—");
        assertThat(capturedRefs("SHIFT_NO_SHOW")).containsEntry("assignmentId", shift.toString());
    }

    @Test
    void reasonSuffixFallsBackToAFullStop() {
        assertThat(AssignmentService.suffix(null)).isEqualTo(".");
        assertThat(AssignmentService.suffix("  ")).isEqualTo(".");
        assertThat(AssignmentService.suffix(" Panne ")).isEqualTo(" : Panne");
    }
}
