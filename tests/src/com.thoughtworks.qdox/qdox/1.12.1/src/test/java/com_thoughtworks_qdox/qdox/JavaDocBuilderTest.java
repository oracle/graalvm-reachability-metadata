/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_qdox.qdox;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;

import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaDocBuilderTest {
    @TempDir
    Path tempDir;

    @Test
    void buildsModelForBinaryClassLoadedByName() {
        JavaDocBuilder builder = new JavaDocBuilder();

        JavaClass javaClass = builder.getClassByName(BinaryFixture.class.getName());

        assertThat(javaClass).isNotNull();
        assertThat(javaClass.getFullyQualifiedName()).isEqualTo(BinaryFixture.class.getName());
        assertThat(javaClass.getFieldByName("CONSTANT")).isNotNull();
        assertThat(javaClass.getFieldByName("value")).isNotNull();
        assertThat(hasMethod(javaClass, "compute")).isTrue();
        assertThat(hasMethod(javaClass, "secret")).isTrue();
        assertThat(hasConstructor(javaClass)).isTrue();
    }

    @Test
    void savesAndLoadsParsedSources() throws IOException {
        JavaDocBuilder builder = new JavaDocBuilder();
        builder.addSource(new StringReader("""
                package sample;

                /** Example type. */
                public class Sample {
                    private String name;

                    public String getName() {
                        return name;
                    }
                }
                """));
        File savedBuilder = tempDir.resolve("qdox-builder.bin").toFile();

        builder.save(savedBuilder);
        JavaDocBuilder loadedBuilder = JavaDocBuilder.load(savedBuilder);

        JavaClass loadedClass = loadedBuilder.getClassByName("sample.Sample");
        assertThat(loadedBuilder.getSources()).hasSize(1);
        assertThat(loadedClass).isNotNull();
        assertThat(loadedClass.getFieldByName("name")).isNotNull();
        assertThat(hasMethod(loadedClass, "getName")).isTrue();
    }

    private static boolean hasMethod(JavaClass javaClass, String methodName) {
        JavaMethod[] methods = javaClass.getMethods();
        for (JavaMethod method : methods) {
            if (methodName.equals(method.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasConstructor(JavaClass javaClass) {
        JavaMethod[] methods = javaClass.getMethods();
        for (JavaMethod method : methods) {
            if (method.isConstructor()) {
                return true;
            }
        }
        return false;
    }

    public static final class BinaryFixture {
        public static final String CONSTANT = "constant";
        private int value;

        public BinaryFixture() {
            this(1);
        }

        private BinaryFixture(int value) {
            this.value = value;
        }

        public String compute(String prefix, int count) throws IOException {
            if (count < 0) {
                throw new IOException("count must be non-negative");
            }
            return prefix + (value + count);
        }

        private void secret() {
            value++;
        }
    }
}
