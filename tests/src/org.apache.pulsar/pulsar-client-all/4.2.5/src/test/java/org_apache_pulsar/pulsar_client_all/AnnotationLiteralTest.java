/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.apache.pulsar.shade.org.glassfish.jersey.internal.inject.AnnotationLiteral;
import org.junit.jupiter.api.Test;

public class AnnotationLiteralTest {
    @Test
    void comparesAnnotationMembersWithRuntimeAnnotation() {
        LabelLiteral literal = new LabelLiteral("persistent");
        Label declared = Labelled.class.getAnnotationsByType(Label.class)[0];

        assertThat(literal.annotationType()).isEqualTo(Label.class);
        assertThat(literal).isEqualTo(declared);
        assertThat(literal.hashCode()).isEqualTo(declared.hashCode());
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Label {
        String value();
    }

    @Label("persistent")
    public static class Labelled {
    }

    public static class LabelLiteral extends AnnotationLiteral<Label> implements Label {
        private final String value;

        public LabelLiteral(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }
}
