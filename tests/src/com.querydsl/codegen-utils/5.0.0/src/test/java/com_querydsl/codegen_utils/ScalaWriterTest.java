/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.codegen_utils;

import com.querydsl.codegen.utils.ScalaWriter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

public class ScalaWriterTest {

    @Test
    void writesMultipleSuppressionValues() throws IOException {
        StringWriter output = new StringWriter();
        ScalaWriter writer = new ScalaWriter(output);

        writer.suppressWarnings("unchecked", "rawtypes");

        assertThat(output.toString()).isEqualTo("""
                @java.lang.SuppressWarnings(Array("unchecked", "rawtypes"))
                """);
    }

    @Test
    void copiesAnnotationMembers() throws IOException {
        StringWriter output = new StringWriter();
        ScalaWriter writer = new ScalaWriter(output);

        writer.annotation(LegacyModel.class.getAnnotation(Deprecated.class));

        assertThat(output.toString()).isIn(
                "@java.lang.Deprecated(since=\"replacement\", forRemoval=true)\n",
                "@java.lang.Deprecated(forRemoval=true, since=\"replacement\")\n");
    }

    @Test
    void omitsDefaultAnnotationMembers() throws IOException {
        StringWriter output = new StringWriter();
        ScalaWriter writer = new ScalaWriter(output);

        writer.annotation(OldModel.class.getAnnotation(Deprecated.class));

        assertThat(output.toString()).isEqualTo("@java.lang.Deprecated\n");
    }

    @Deprecated(since = "replacement", forRemoval = true)
    public static class LegacyModel {
    }

    @Deprecated
    public static class OldModel {
    }
}
