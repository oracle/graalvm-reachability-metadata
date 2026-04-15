/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_findbugs.findbugs_annotations;

import edu.umd.cs.findbugs.annotations.Confidence;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Findbugs_annotationsTest {
    @Test
    void test() throws Exception {
        System.out.println("This is just a placeholder, implement your test");
    }

    @Test
    void confidenceValuesReturnsDefensiveCopy() {
        Confidence[] values = Confidence.values();
        values[0] = Confidence.IGNORE;
        values[1] = Confidence.IGNORE;

        assertThat(Confidence.values())
                .containsExactly(Confidence.HIGH, Confidence.MEDIUM, Confidence.LOW, Confidence.IGNORE);
    }

    @Test
    void confidenceValueOfRejectsInvalidInput() {
        assertThatThrownBy(() -> Confidence.valueOf("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");

        assertThatThrownBy(() -> Confidence.valueOf(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void confidenceThresholdLookupHandlesExtremeAndBoundaryValues() {
        assertThat(Confidence.getConfidence(Integer.MIN_VALUE)).isSameAs(Confidence.HIGH);
        assertThat(Confidence.getConfidence(0)).isSameAs(Confidence.HIGH);
        assertThat(Confidence.getConfidence(1)).isSameAs(Confidence.HIGH);
        assertThat(Confidence.getConfidence(2)).isSameAs(Confidence.MEDIUM);
        assertThat(Confidence.getConfidence(3)).isSameAs(Confidence.LOW);
        assertThat(Confidence.getConfidence(4)).isSameAs(Confidence.IGNORE);
        assertThat(Confidence.getConfidence(5)).isSameAs(Confidence.IGNORE);
        assertThat(Confidence.getConfidence(Integer.MAX_VALUE)).isSameAs(Confidence.IGNORE);
    }
}
