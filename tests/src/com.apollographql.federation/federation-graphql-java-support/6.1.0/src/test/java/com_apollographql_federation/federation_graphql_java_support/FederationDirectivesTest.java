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
import org.junit.jupiter.api.Test;

public class FederationDirectivesTest {
    @Test
    void loadsFederationTwoDefinitionsFromPackagedResource() {
        List<SDLNamedDefinition> definitions = FederationDirectives.loadFederationSpecDefinitions(
                Federation.FEDERATION_SPEC_V2_12);

        assertThat(definitions)
                .extracting(SDLNamedDefinition::getName)
                .contains("FieldSet", "Import", "Purpose", "cacheTag", "key", "link");
        assertThat(definitions.stream()
                .filter(DirectiveDefinition.class::isInstance)
                .map(DirectiveDefinition.class::cast))
                .anySatisfy(directive -> {
                    assertThat(directive.getName()).isEqualTo("key");
                    assertThat(directive.getInputValueDefinitions())
                            .extracting(InputValueDefinition::getName)
                            .contains("fields", "resolvable");
                });
    }
}
