/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.codegen_utils;

import com.querydsl.codegen.utils.JavaWriter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaWriterTest {

    @Test
    void writesMultipleSuppressionValues() throws IOException {
        StringWriter output = new StringWriter();
        JavaWriter writer = new JavaWriter(output);

        writer.suppressWarnings("unchecked", "rawtypes");

        assertThat(output.toString()).isEqualTo("""
                @SuppressWarnings({"unchecked", "rawtypes"})
                """);
    }

    @Test
    @SuppressWarnings("checkstyle:annotationAccess")
    void copiesAnnotationMembers() throws IOException {
        StringWriter output = new StringWriter();
        JavaWriter writer = new JavaWriter(output);

        writer.annotation(LegacyModel.class.getAnnotation(Deprecated.class));

        assertThat(output.toString()).isIn(
                "@Deprecated(since=\"replacement\", forRemoval=true)\n",
                "@Deprecated(forRemoval=true, since=\"replacement\")\n");
    }

    @Test
    @SuppressWarnings("checkstyle:annotationAccess")
    void omitsDefaultAnnotationMembers() throws IOException {
        StringWriter output = new StringWriter();
        JavaWriter writer = new JavaWriter(output);

        writer.annotation(OldModel.class.getAnnotation(Deprecated.class));

        assertThat(output.toString()).isEqualTo("@Deprecated\n");
    }

    @Deprecated(since = "replacement", forRemoval = true)
    public static class LegacyModel {
    }

    @Deprecated
    public static class OldModel {
    }
}
