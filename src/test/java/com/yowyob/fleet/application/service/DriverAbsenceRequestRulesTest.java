package com.yowyob.fleet.application.service;

import com.yowyob.fleet.application.service.DriverAbsenceRequestService.CreateCommand;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Recevabilité d'une demande d'absence du chauffeur. */
class DriverAbsenceRequestRulesTest {

    private final LocalDate today = LocalDate.of(2026, 11, 5);

    @Test
    void acceptsAFutureLeave() {
        assertThat(DriverAbsenceRequestService.validate(
                new CreateCommand("leave", today.plusDays(3), today.plusDays(7), "Mariage"), today)).isNull();
    }

    @Test
    void suspensionCannotBeRequested() {
        assertThat(DriverAbsenceRequestService.validate(
                new CreateCommand("SUSPENSION", today, today, null), today)).contains("congé");
    }

    @Test
    void leaveCannotStartInThePastButSickLeaveCanBeBackdated() {
        assertThat(DriverAbsenceRequestService.validate(
                new CreateCommand("LEAVE", today.minusDays(1), today, null), today)).isNotNull();
        assertThat(DriverAbsenceRequestService.validate(
                new CreateCommand("SICK_LEAVE", today.minusDays(3), today, null), today)).isNull();
        assertThat(DriverAbsenceRequestService.validate(
                new CreateCommand("SICK_LEAVE", today.minusDays(8), today, null), today)).isNotNull();
    }

    @Test
    void datesMustBeOrderedAndBounded() {
        assertThat(DriverAbsenceRequestService.validate(
                new CreateCommand("LEAVE", today.plusDays(5), today.plusDays(2), null), today)).contains("précède");
        assertThat(DriverAbsenceRequestService.validate(
                new CreateCommand("LEAVE", today, null, null), today)).isNotNull();
        assertThat(DriverAbsenceRequestService.validate(
                new CreateCommand("LEAVE", today, today.plusDays(DriverAbsenceRequestService.MAX_DAYS), null), today)).isNotNull();
    }

    @Test
    void absenceLabelsAreFrench() {
        assertThat(DriverAbsenceService.typeLabel("SICK_LEAVE")).isEqualTo("arrêt maladie");
        assertThat(DriverAbsenceService.period(today, today)).isEqualTo("le 05/11/2026");
        assertThat(DriverAbsenceService.period(today, null)).contains("sans date de fin");
    }
}
