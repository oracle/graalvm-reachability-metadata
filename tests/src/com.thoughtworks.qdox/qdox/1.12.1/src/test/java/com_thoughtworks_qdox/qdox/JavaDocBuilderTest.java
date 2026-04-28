/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_qdox.qdox;

import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaField;
import com.thoughtworks.qdox.model.JavaMethod;
import com.thoughtworks.qdox.model.JavaSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaDocBuilderTest {
    @TempDir
    File tempDir;

    @Test
    void createsModelForBinaryClass() {
        JavaDocBuilder builder = new JavaDocBuilder();

        JavaClass javaClass = builder.getClassByName(BinaryFixture.class.getName());

        assertThat(javaClass.getFullyQualifiedName()).isEqualTo(BinaryFixture.class.getName());
        assertThat(javaClass.getFields()).extracting(JavaField::getName).contains("count", "name");
        assertThat(javaClass.getMethods())
                .extracting(JavaMethod::getName)
                .contains("visibleOperation", "hiddenOperation");
    }

    @Test
    void savesAndLoadsParsedSources() throws Exception {
        JavaDocBuilder builder = new JavaDocBuilder();
        JavaSource source = builder.addSource(new StringReader("""
                package samples;
                /** Parsed source used to exercise JavaDocBuilder serialization. */
                public class ParsedWidget {
                    private final String name;
                    public ParsedWidget(String name) { this.name = name; }
                    public String getName() { return name; }
                }
                """));
        File savedBuilder = new File(tempDir, "qdox-builder.bin");

        builder.save(savedBuilder);
        JavaDocBuilder loadedBuilder = JavaDocBuilder.load(savedBuilder);

        assertThat(source.getClasses()[0].getFullyQualifiedName()).isEqualTo("samples.ParsedWidget");
        assertThat(loadedBuilder.getClassByName("samples.ParsedWidget").getMethods())
                .extracting(JavaMethod::getName)
                .contains("getName");
    }

    public static class BinaryFixture {
        public int count;
        private String name;

        public BinaryFixture() {
        }

        private BinaryFixture(String name) {
            this.name = name;
        }

        public String visibleOperation(int value) {
            return name + value;
        }

        private void hiddenOperation() {
        }
    }
}
