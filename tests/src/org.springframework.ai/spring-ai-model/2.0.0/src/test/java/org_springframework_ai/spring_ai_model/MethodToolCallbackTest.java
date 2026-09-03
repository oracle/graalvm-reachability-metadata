/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_model;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.ai.tool.method.MethodToolCallback;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodToolCallbackTest {

    @Test
    void invokesConfiguredNoArgumentMethodAsTool() throws NoSuchMethodException {
        UserMessage message = new UserMessage("Summarize the latest search result");
        Method getTextMethod = UserMessage.class.getMethod("getText");

        MethodToolCallback callback = MethodToolCallback.builder()
                .toolDefinition(ToolDefinition.builder()
                        .name("message_text")
                        .description("Reads the text from a message")
                        .inputSchema("""
                                {
                                  "type": "object",
                                  "properties": {}
                                }
                                """)
                        .build())
                .toolMethod(getTextMethod)
                .toolObject(message)
                .toolCallResultConverter(new PlainTextToolCallResultConverter())
                .build();

        String result = callback.call("{}");

        assertThat(result).isEqualTo("Summarize the latest search result");
    }

    private static final class PlainTextToolCallResultConverter implements ToolCallResultConverter {

        @Override
        public String convert(Object result, Type returnType) {
            return Objects.toString(result);
        }

    }

}
