/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.JavaFile;
import org.springframework.javapoet.TypeSpec;

/** Verifies generated source qualifies names that clash with inherited nested types. */
public class TypeSpecInnerBuilderTest {
    @Test
    void avoidsNestedClassImportCollision() {
        TypeSpec type = TypeSpec.classBuilder("GeneratedConfiguration")
                .avoidClashesWithNestedClasses(Parent.class)
                .addField(ClassName.get("java.time", "Duration"), "timeout")
                .build();

        String source = JavaFile.builder("example.generated", type).build().toString();

        assertThat(source).contains("java.time.Duration timeout;")
                .doesNotContain("import java.time.Duration;");
        assertThat(type.alwaysQualifiedNames()).contains("Duration");
    }

    public static class Parent {
        public static class Duration { }
    }
}
