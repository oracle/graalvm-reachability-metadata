/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity_engine_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.app.FieldMethodizer;
import org.junit.jupiter.api.Test;

public class FieldMethodizerTest {
    @Test
    void exposesPublicStaticFieldsAddedByClassName() throws Exception {
        FieldMethodizer methodizer = new FieldMethodizer();

        methodizer.addObject(MethodizedFields.class.getName());

        assertThat(methodizer.get("TEXT_VALUE")).isEqualTo("velocity-field-methodizer");
        assertThat(methodizer.get("NUMBER_VALUE")).isEqualTo(23);
    }

    @Test
    void exposesPublicStaticFieldsAddedByObjectInstance() throws Exception {
        FieldMethodizer methodizer = new FieldMethodizer(new MethodizedFields());

        assertThat(methodizer.get("BOOLEAN_VALUE")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void ignoresFieldsThatAreNotPublicAndStatic() throws Exception {
        FieldMethodizer methodizer = new FieldMethodizer();

        methodizer.addObject(MethodizedFields.class.getName());

        assertThat(methodizer.get("PRIVATE_STATIC_VALUE")).isNull();
        assertThat(methodizer.get("publicInstanceValue")).isNull();
        assertThat(methodizer.get("missingField")).isNull();
    }

    public static final class MethodizedFields {
        public static final String TEXT_VALUE = "velocity-field-methodizer";
        public static final Integer NUMBER_VALUE = 23;
        public static final Boolean BOOLEAN_VALUE = Boolean.TRUE;
        public final String publicInstanceValue = "not-static";
        private static final String PRIVATE_STATIC_VALUE = "not-public";
    }
}
