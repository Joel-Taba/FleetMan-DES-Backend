package com.yowyob.fleet.domain.model;

import com.yowyob.fleet.domain.exception.CompanyException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyRulesTest {

    private static CompanyRules.Profile full() {
        return new CompanyRules.Profile("Fret Express", "Fret Express SARL", "SARL", "RC/YAO/2020/B/123", "M012345678901A",
                "FREIGHT", "Rue 1.234", "Yaoundé", "CM", "+237 699 00 00 00", "contact@fret.cm", "www.fret.cm");
    }

    private static CompanyRules.Profile withName(String name) {
        return new CompanyRules.Profile(name, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void normalizeTrimsAndNullsBlankFields() {
        CompanyRules.Profile p = CompanyRules.normalize(new CompanyRules.Profile(
                "  Fret  ", "   ", null, " RC1 ", "", null, null, null, "cm", null, null, null));
        assertEquals("Fret", p.name());
        assertNull(p.legalName());
        assertEquals("RC1", p.registrationNumber());
        assertNull(p.taxId());
        assertEquals("CM", p.country());
    }

    @Test
    void countryDefaultsToCameroon() {
        assertEquals("CM", CompanyRules.normalize(withName("Fret")).country());
    }

    @Test
    void nameIsRequired() {
        assertThrows(CompanyException.class, () -> CompanyRules.normalize(withName(" ")));
        assertThrows(CompanyException.class, () -> CompanyRules.normalize(withName("x")));
    }

    @Test
    void contactFieldsAreValidated() {
        CompanyRules.Profile base = full();
        assertThrows(CompanyException.class, () -> CompanyRules.normalize(new CompanyRules.Profile(base.name(), null, null, null, null, null, null, null, "CM", null, "pas-un-mail", null)));
        assertThrows(CompanyException.class, () -> CompanyRules.normalize(new CompanyRules.Profile(base.name(), null, null, null, null, null, null, null, "CM", "abc", null, null)));
        assertThrows(CompanyException.class, () -> CompanyRules.normalize(new CompanyRules.Profile(base.name(), null, null, null, null, null, null, null, "CM", null, null, "pas un site")));
        assertEquals("www.fret.cm", CompanyRules.normalize(base).website());
    }

    @Test
    void legalFormAndSectorComeFromTheCatalog() {
        assertThrows(CompanyException.class, () -> CompanyRules.normalize(new CompanyRules.Profile("Fret", null, "XXX", null, null, null, null, null, "CM", null, null, null)));
        assertThrows(CompanyException.class, () -> CompanyRules.normalize(new CompanyRules.Profile("Fret", null, null, null, null, "XXX", null, null, "CM", null, null, null)));
    }

    @Test
    void siteNeedsNameKnownTypeAndCity() {
        CompanyRules.validateSite("Dépôt Nord", "DEPOT", "Douala", null);
        assertThrows(CompanyException.class, () -> CompanyRules.validateSite(" ", "DEPOT", "Douala", null));
        assertThrows(CompanyException.class, () -> CompanyRules.validateSite("Dépôt", "GARAGE", "Douala", null));
        assertThrows(CompanyException.class, () -> CompanyRules.validateSite("Dépôt", "DEPOT", " ", null));
        assertThrows(CompanyException.class, () -> CompanyRules.validateSite("Dépôt", "DEPOT", "Douala", "abc"));
    }

    @Test
    void completeProfileHasNothingMissing() {
        List<String> missing = CompanyRules.missingFields(full(), true, true);
        assertTrue(missing.isEmpty());
        assertEquals(100, CompanyRules.completeness(missing));
    }

    @Test
    void newCompanyOnlyCountsItsName() {
        List<String> missing = CompanyRules.missingFields(CompanyRules.normalize(withName("Fret")), false, false);
        assertEquals(11, missing.size());
        assertTrue(missing.contains("LOGO"));
        assertTrue(missing.contains("HEAD_OFFICE"));
        assertEquals(8, CompanyRules.completeness(missing));
    }

    @Test
    void websiteIsNotRequiredForCompleteness() {
        CompanyRules.Profile f = full();
        CompanyRules.Profile noSite = new CompanyRules.Profile(f.name(), f.legalName(), f.legalForm(), f.registrationNumber(),
                f.taxId(), f.sector(), f.address(), f.city(), f.country(), f.phone(), f.email(), null);
        assertTrue(CompanyRules.missingFields(noSite, true, true).isEmpty());
    }
}
