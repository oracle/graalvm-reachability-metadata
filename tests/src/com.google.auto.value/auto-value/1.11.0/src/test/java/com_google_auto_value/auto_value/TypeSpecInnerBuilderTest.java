/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.squareup.javapoet.TypeSpec;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TypeSpecInnerBuilderTest {
    @Test
    void avoidsNamesOfNestedClassesFromAnInheritedType() {
        TypeSpec generated = TypeSpec.classBuilder("Generated")
                .avoidClashesWithNestedClasses(Map.class)
                .avoidClashesWithNestedClasses(Map.Entry.class)
                .addType(TypeSpec.classBuilder("Nested").build())
                .build();

        assertThat(generated.name).isEqualTo("Generated");
        assertThat(generated.typeSpecs).hasSize(1);
        assertThat(generated.typeSpecs.get(0).name).isEqualTo("Nested");
    }
}
