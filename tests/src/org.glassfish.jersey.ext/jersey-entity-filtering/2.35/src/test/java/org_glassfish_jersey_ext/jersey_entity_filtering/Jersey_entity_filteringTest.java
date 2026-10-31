/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_ext.jersey_entity_filtering;

import org.glassfish.jersey.message.filtering.internal.LocalizationMessages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Jersey_entity_filteringTest {
    @Test
    void localizesFilteringScopeDiagnostic() {
        assertEquals("The entity-filtering scope property does not contain an instance of Annotation or Annotation[] (scope-name).",
                LocalizationMessages.ENTITY_FILTERING_SCOPE_NOT_ANNOTATIONS("scope-name"));
    }
}
