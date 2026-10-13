/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class ConstantsTest {
    @Test
    void rejectsExperimentalAsmForANonPreviewCaller() {
        assertThatThrownBy(ExperimentalClassVisitorFixture::new)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("can only be used by classes compiled with --enable-preview");
    }
}
