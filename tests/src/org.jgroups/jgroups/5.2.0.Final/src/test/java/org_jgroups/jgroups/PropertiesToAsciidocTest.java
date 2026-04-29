/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.stack.Protocol;
import org.jgroups.util.PropertiesToAsciidoc;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertiesToAsciidocTest extends PropertiesToAsciidoc {
    private static final String STATS_DESCRIPTION = """
        Determines whether to collect statistics (and expose them via JMX). Default is true""";
    private static final String ERGONOMICS_DESCRIPTION = """
        Enables ergonomics: dynamically find the best values for properties at runtime""";

    @Test
    void collectsProtocolPropertyDescriptionsFromFieldsAndMethods() throws Exception {
        Map<String, String> descriptions = new TreeMap<>();

        collectDescriptions(Protocol.class, descriptions);

        assertThat(descriptions)
            .containsEntry("stats", STATS_DESCRIPTION)
            .containsEntry("ergonomics", ERGONOMICS_DESCRIPTION)
            .containsEntry("level", "logger level (see javadocs)");
    }

    private static void collectDescriptions(
        Class<?> propertyHolder,
        Map<String, String> descriptions) throws Exception {
        getDescriptions(propertyHolder, descriptions, null, false);
    }
}
