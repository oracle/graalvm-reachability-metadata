/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class Langchain4jTest {

    @Test
    void buildsProxyAndLoadsSystemMessageTemplateFromRootClasspathResource() {
        RecordingChatModel chatModel = new RecordingChatModel();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .build();

        String response = assistant.answer("Summarize the prompt");
        ChatMessage systemMessage = chatModel.messages().get(0);
        ChatMessage userMessage = chatModel.messages().get(1);

        assertThat(response).isEqualTo("stubbed answer");
        assertThat(chatModel.messages()).hasSize(2);
        assertThat(systemMessage).isInstanceOfSatisfying(
                dev.langchain4j.data.message.SystemMessage.class,
                message -> assertThat(message.text().stripTrailing())
                        .isEqualTo("You must answer from the root classpath resource."));
        assertThat(userMessage)
                .isInstanceOfSatisfying(UserMessage.class, message -> assertThat(message.singleText())
                        .isEqualTo("Summarize the prompt"));
    }

    interface Assistant {

        @SystemMessage(fromResource = "langchain4j-system-message.txt")
        String answer(String prompt);
    }

    private static final class RecordingChatModel implements ChatModel {

        private List<ChatMessage> messages;

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            this.messages = chatRequest.messages();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("stubbed answer"))
                    .build();
        }

        List<ChatMessage> messages() {
            return messages;
        }
    }
}
