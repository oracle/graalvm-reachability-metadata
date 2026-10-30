/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import oracle.jdbc.logging.annotations.Feature;
import oracle.jdbc.logging.runtime.Features;
import org.junit.jupiter.api.Test;

public class FeaturesTest {
    @Test
    void resolvesTracingFeatureNamesFromTheSupportsAnnotation() {
        Features features = new Features("oracle.jdbc.logging.annotations.Supports");

        assertThat(features.bitVector("CONNECT")).isEqualTo(Feature.CONNECT.bitVector());
        assertThat(features.bitVector("SQL_EXECUTION")).isEqualTo(Feature.SQL_EXECUTION.bitVector());
        assertThat(features.bitVector("ALL")).isEqualTo(Feature.ALL.bitVector());
        assertThatThrownBy(() -> features.bitVector("NO_SUCH_FEATURE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NO_SUCH_FEATURE");
    }
}
