package org.example.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceValidatorTest {
    @Test
    void acceptsOnlyDirectEvidence() {
        String source = "We jointly estimate phase and illumination parameters.";
        assertTrue(EvidenceValidator.isDirectSubstring("jointly estimate phase", source));
        assertFalse(EvidenceValidator.isDirectSubstring("jointly optimize pupil", source));
    }
}
