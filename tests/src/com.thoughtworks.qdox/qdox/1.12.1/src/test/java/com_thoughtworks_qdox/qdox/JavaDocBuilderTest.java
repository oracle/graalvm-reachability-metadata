/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_qdox.qdox;

import static org.assertj.core.api.Assertions.assertThat;

import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaMethod;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class JavaDocBuilderTest {
    @Test
    void resolvesBinaryClassMembersThroughBuilderClassLookup() {
        JavaDocBuilder builder = new JavaDocBuilder();

        JavaClass javaClass = builder.getClassByName(BinarySample.class.getName());

        assertThat(javaClass.getFullyQualifiedName()).isEqualTo(BinarySample.class.getName());
        assertThat(javaClass.getFieldByName("label")).isNotNull();
        assertThat(javaClass.getFieldByName("count")).isNotNull();
        assertThat(hasConstructor(javaClass)).isTrue();
        assertThat(methodNamed(javaClass, "describe")).isNotNull();
        assertThat(methodNamed(javaClass, "reset")).isNotNull();
    }

    @Test
    void savesAndLoadsParsedSources() throws Exception {
        JavaDocBuilder builder = new JavaDocBuilder();
        builder.addSource(new StringReader("""
                package sample;

                /** A parsed source used to verify persistence. */
                public class ParsedSample {
                    private int value;

                    public String describe() {
                        return String.valueOf(value);
                    }
                }
                """));
        File file = Files.createTempFile("qdox-builder", ".ser").toFile();
        try {
            builder.save(file);

            JavaDocBuilder loadedBuilder = JavaDocBuilder.load(file);
            JavaClass loadedClass = loadedBuilder.getClassByName("sample.ParsedSample");

            assertThat(loadedClass.getFieldByName("value")).isNotNull();
            assertThat(methodNamed(loadedClass, "describe")).isNotNull();
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    private static boolean hasConstructor(JavaClass javaClass) {
        return Arrays.stream(javaClass.getMethods()).anyMatch(JavaMethod::isConstructor);
    }

    private static JavaMethod methodNamed(JavaClass javaClass, String name) {
        return Arrays.stream(javaClass.getMethods())
                .filter(method -> !method.isConstructor())
                .filter(method -> name.equals(method.getName()))
                .findFirst()
                .orElse(null);
    }

    public static class BinarySample {
        public String label;
        private int count;

        public BinarySample() {
        }

        public BinarySample(String label, int count) {
            this.label = label;
            this.count = count;
        }

        public String describe(String prefix) {
            return prefix + label + count;
        }

        public void reset() {
            label = null;
            count = 0;
        }
    }
}
