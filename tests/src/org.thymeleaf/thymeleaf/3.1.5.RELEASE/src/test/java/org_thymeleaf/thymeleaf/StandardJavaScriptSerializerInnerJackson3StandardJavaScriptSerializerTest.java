/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.standard.serializer.StandardJavaScriptSerializer;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardJavaScriptSerializerInnerJackson3StandardJavaScriptSerializerTest {

    @Test
    void serializeValueUsesJackson3JsonMapperBuilder() {
        StandardJavaScriptSerializer serializer = new StandardJavaScriptSerializer(true);
        Map<String, Object> payload = createPayload();
        StringWriter writer = new StringWriter();

        serializer.serializeValue(payload, writer);

        assertThat(writer.toString())
                .isEqualTo("{\"message\":\"snowman ☃ \\u0026 \\/\",\"active\":true,\"count\":3}");
    }

    @Test
    void javascriptInlinedTemplateUsesJackson3JsonMapperBuilder() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("payload", createPayload());

        String output = templateEngine.process(
                """
                <script th:inline="javascript">
                    const payload = [[${payload}]];
                </script>
                """,
                context);

        assertThat(output)
                .contains("const payload = {\"message\":\"snowman ☃ \\u0026 \\/\",\"active\":true,\"count\":3};");
    }

    private static Map<String, Object> createPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", "snowman ☃ & /");
        payload.put("active", true);
        payload.put("count", 3);
        return payload;
    }
}
