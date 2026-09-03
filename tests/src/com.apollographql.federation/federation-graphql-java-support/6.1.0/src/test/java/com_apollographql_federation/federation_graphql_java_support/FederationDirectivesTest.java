/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_apollographql_federation.federation_graphql_java_support;

import static org.assertj.core.api.Assertions.assertThat;

import com.apollographql.federation.graphqljava.Federation;
import com.apollographql.federation.graphqljava.FederationDirectives;
import graphql.language.DirectiveDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.SDLNamedDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class FederationDirectivesTest {
    private static final Map<String, String> FEDERATION_SPECIFICATION_RESOURCES = Map.ofEntries(
            Map.entry(Federation.FEDERATION_SPEC_V2_0, "definitions_fed2_0.graphqls"),
            Map.entry(Federation.FEDERATION_SPEC_V2_1, "definitions_fed2_1.graphqls"),
            Map.entry(Federation.FEDERATION_SPEC_V2_2, "definitions_fed2_2.graphqls"),
            Map.entry(Federation.FEDERATION_SPEC_V2_3, "definitions_fed2_3.graphqls"),
            Map.entry(Federation.FEDERATION_SPEC_V2_5, "definitions_fed2_5.graphqls"),
            Map.entry(Federation.FEDERATION_SPEC_V2_6, "definitions_fed2_6.graphqls"),
            Map.entry(Federation.FEDERATION_SPEC_V2_7, "definitions_fed2_7.graphqls"),
            Map.entry(Federation.FEDERATION_SPEC_V2_8, "definitions_fed2_8.graphqls"),
            Map.entry(Federation.FEDERATION_SPEC_V2_9, "definitions_fed2_9.graphqls"),
            Map.entry(Federation.FEDERATION_SPEC_V2_12, "definitions_fed2_12.graphqls"));

    @Test
    void loadsDefinitionsFromEveryPackagedFederationResource() {
        // Each public specification version selects and parses its packaged resource.
        FEDERATION_SPECIFICATION_RESOURCES.forEach((specification, resource) -> {
            List<SDLNamedDefinition> definitions = FederationDirectives.loadFederationSpecDefinitions(specification);

            assertThat(definitions)
                    .as("definitions loaded from %s", resource)
                    .extracting(SDLNamedDefinition::getName)
                    .contains("FieldSet", "Import", "key", "link");
            assertThat(definitions.stream()
                    .filter(DirectiveDefinition.class::isInstance)
                    .map(DirectiveDefinition.class::cast))
                    .anySatisfy(directive -> {
                        assertThat(directive.getName()).isEqualTo("key");
                        assertThat(directive.getInputValueDefinitions())
                                .extracting(InputValueDefinition::getName)
                                .contains("fields", "resolvable");
                    });
        });
    }
}
