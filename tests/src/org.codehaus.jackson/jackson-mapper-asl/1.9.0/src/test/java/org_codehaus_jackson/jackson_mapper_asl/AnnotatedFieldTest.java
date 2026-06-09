/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.introspect.AnnotatedClass;
import org.codehaus.jackson.map.introspect.AnnotatedField;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.junit.jupiter.api.Test;

public class AnnotatedFieldTest {
    private static final AnnotationIntrospector INTROSPECTOR = new JacksonAnnotationIntrospector();

    @Test
    public void setsBeanPropertyThroughAnnotatedField() {
        AnnotatedClass annotatedClass = AnnotatedClass.construct(
                FieldTarget.class, INTROSPECTOR, null);
        annotatedClass.resolveFields(true);
        FieldTarget target = new FieldTarget("initial");

        AnnotatedField field = findField(annotatedClass, "name");
        field.setValue(target, "updated");

        assertThat(target.getName()).isEqualTo("updated");
    }

    private static AnnotatedField findField(AnnotatedClass annotatedClass, String name) {
        for (AnnotatedField field : annotatedClass.fields()) {
            if (name.equals(field.getName())) {
                return field;
            }
        }
        throw new AssertionError("No field named " + name);
    }

    public static final class FieldTarget {
        public String name;

        public FieldTarget(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
