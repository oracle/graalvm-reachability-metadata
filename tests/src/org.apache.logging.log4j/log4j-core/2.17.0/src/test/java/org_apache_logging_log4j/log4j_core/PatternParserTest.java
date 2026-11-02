/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PatternParserTest {
    @Test
    void resolvesPatternConvertersAndTheirOptions() {
        PatternLayout layout = PatternLayout.newBuilder().withPattern("[%level] %logger{1}: %replace{%message}{secret}{***}")
                .build();
        assertEquals("[INFO] worker: a *** value", layout.toSerializable(TestSupport.event("a secret value")));
    }
}
