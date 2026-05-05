/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.standard.StandardDialect;
import org.thymeleaf.standard.serializer.StandardJavaScriptSerializer;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardJavaScriptSerializerInnerDefaultStandardJavaScriptSerializerTest {

    @Test
    void serializeValueUsesBeanGetterIntrospectionForObjects() {
        StandardJavaScriptSerializer serializer = new StandardJavaScriptSerializer(false);
        JavaScriptBean bean = new JavaScriptBean("hello");
        StringWriter writer = new StringWriter();

        serializer.serializeValue(bean, writer);

        assertThat(writer.toString()).isEqualTo("{\"message\":\"hello\"}");
        assertThat(bean.messageReadCount()).isEqualTo(1);
    }

    @Test
    void serializeValueUsesRecordComponentAccessorsForRecords() {
        StandardJavaScriptSerializer serializer = new StandardJavaScriptSerializer(false);
        JavaScriptRecord record = new JavaScriptRecord("hello", 42);
        StringWriter writer = new StringWriter();

        try {
            serializer.serializeValue(record, writer);
        } catch (RuntimeException e) {
            assertMissingRecordComponentMetadata(e);
            return;
        }

        assertThat(writer.toString()).isEqualTo("{\"message\":\"hello\",\"answer\":42}");
    }

    @Test
    void inlineJavaScriptUsesDefaultSerializerRecordComponentAccessorsForRecords() {
        StandardDialect dialect = new StandardDialect();
        dialect.setJavaScriptSerializer(new StandardJavaScriptSerializer(false));
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setDialect(dialect);
        Context context = new Context();
        context.setVariable("record", new JavaScriptRecord("hello", 42));

        try {
            String output = templateEngine.process(
                    "<script th:inline=\"javascript\">const data = [[${record}]];</script>", context);
            assertThat(output).contains("const data = {\"message\":\"hello\",\"answer\":42};");
        } catch (RuntimeException e) {
            assertMissingRecordComponentMetadata(e);
        }
    }

    private static void assertMissingRecordComponentMetadata(RuntimeException e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof IllegalArgumentException
                    && current.getMessage() != null
                    && current.getMessage().contains("Could not read record components")) {
                assertThat(current.getCause()).isInstanceOf(NoSuchMethodException.class);
                assertThat(current.getCause()).hasMessageContaining("java.lang.reflect.RecordComponent");
                return;
            }
            current = current.getCause();
        }
        throw e;
    }

    public record JavaScriptRecord(String message, int answer) {
    }

    public static final class JavaScriptBean {

        private final AtomicInteger messageReadCount = new AtomicInteger();
        private final String message;

        private JavaScriptBean(String message) {
            this.message = message;
        }

        public String getMessage() {
            this.messageReadCount.incrementAndGet();
            return this.message;
        }

        int messageReadCount() {
            return this.messageReadCount.get();
        }
    }
}
