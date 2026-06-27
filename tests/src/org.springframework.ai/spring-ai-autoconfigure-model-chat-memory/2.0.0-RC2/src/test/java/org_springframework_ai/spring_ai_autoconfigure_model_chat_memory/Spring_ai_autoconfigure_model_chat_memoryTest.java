/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_autoconfigure_model_chat_memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

public class Spring_ai_autoconfigure_model_chat_memoryTest {

    private static final String CONVERSATION_ID = "conversation-1";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChatMemoryAutoConfiguration.class));

    @Test
    void autoConfigurationCreatesInMemoryRepositoryAndMessageWindowChatMemory() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChatMemoryRepository.class);
            assertThat(context).hasSingleBean(ChatMemory.class);

            ChatMemoryRepository repository = context.getBean(ChatMemoryRepository.class);
            ChatMemory chatMemory = context.getBean(ChatMemory.class);
            assertThat(repository).isInstanceOf(InMemoryChatMemoryRepository.class);
            assertThat(chatMemory).isInstanceOf(MessageWindowChatMemory.class);

            assertThat(repository.findConversationIds()).isEmpty();
            chatMemory.add(CONVERSATION_ID, List.of(
                    new SystemMessage("Answer briefly"),
                    new UserMessage("Hello"),
                    new AssistantMessage("Hi there")));

            assertThat(repository.findConversationIds()).containsExactly(CONVERSATION_ID);
            assertThat(chatMemory.get(CONVERSATION_ID))
                    .extracting(Message::getMessageType, Message::getText)
                    .containsExactly(
                            tuple(MessageType.SYSTEM, "Answer briefly"),
                            tuple(MessageType.USER, "Hello"),
                            tuple(MessageType.ASSISTANT, "Hi there"));

            chatMemory.add(CONVERSATION_ID, new UserMessage("What can you do?"));
            assertThat(repository.findByConversationId(CONVERSATION_ID))
                    .extracting(Message::getText)
                    .containsExactly("Answer briefly", "Hello", "Hi there", "What can you do?");

            chatMemory.clear(CONVERSATION_ID);
            assertThat(chatMemory.get(CONVERSATION_ID)).isEmpty();
            assertThat(repository.findConversationIds()).isEmpty();
        });
    }

    @Test
    void autoConfiguredChatMemoryUsesUserProvidedRepository() {
        contextRunner.withUserConfiguration(UserRepositoryConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(ChatMemoryRepository.class);
            assertThat(context).hasSingleBean(ChatMemory.class);

            ChatMemoryRepository repository = context.getBean(ChatMemoryRepository.class);
            ChatMemory chatMemory = context.getBean(ChatMemory.class);
            assertThat(repository).isInstanceOf(RecordingChatMemoryRepository.class);
            assertThat(chatMemory).isInstanceOf(MessageWindowChatMemory.class);

            chatMemory.add(CONVERSATION_ID, new UserMessage("Remember this"));

            RecordingChatMemoryRepository recordingRepository = context.getBean(RecordingChatMemoryRepository.class);
            assertThat(recordingRepository.savedConversationIds()).containsExactly(CONVERSATION_ID);
            assertThat(repository.findByConversationId(CONVERSATION_ID))
                    .extracting(Message::getText)
                    .containsExactly("Remember this");
        });
    }

    @Test
    void userProvidedRepositoryAndChatMemoryBackOffBothAutoConfiguredBeans() {
        contextRunner.withUserConfiguration(UserRepositoryAndMemoryConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(ChatMemoryRepository.class);
            assertThat(context).hasSingleBean(ChatMemory.class);

            ChatMemoryRepository repository = context.getBean(ChatMemoryRepository.class);
            ChatMemory chatMemory = context.getBean(ChatMemory.class);
            assertThat(repository).isInstanceOf(RecordingChatMemoryRepository.class);
            assertThat(chatMemory).isInstanceOf(RecordingChatMemory.class);

            chatMemory.add(CONVERSATION_ID, List.of(new UserMessage("custom question"),
                    new AssistantMessage("custom answer")));

            assertThat(chatMemory.get(CONVERSATION_ID))
                    .extracting(Message::getText)
                    .containsExactly("custom question", "custom answer");
            assertThat(repository.findConversationIds()).isEmpty();
        });
    }

    @Configuration(proxyBeanMethods = false)
    public static class UserRepositoryConfiguration {

        @Bean
        public RecordingChatMemoryRepository chatMemoryRepository() {
            return new RecordingChatMemoryRepository();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class UserRepositoryAndMemoryConfiguration {

        @Bean
        public RecordingChatMemoryRepository chatMemoryRepository() {
            return new RecordingChatMemoryRepository();
        }

        @Bean
        public RecordingChatMemory chatMemory() {
            return new RecordingChatMemory();
        }
    }

    public static final class RecordingChatMemoryRepository implements ChatMemoryRepository {

        private final Map<String, List<Message>> conversations = new LinkedHashMap<>();
        private final List<String> savedConversationIds = new ArrayList<>();

        @Override
        public List<String> findConversationIds() {
            return new ArrayList<>(conversations.keySet());
        }

        @Override
        public List<Message> findByConversationId(String conversationId) {
            return new ArrayList<>(conversations.getOrDefault(conversationId, List.of()));
        }

        @Override
        public void saveAll(String conversationId, List<Message> messages) {
            savedConversationIds.add(conversationId);
            conversations.put(conversationId, new ArrayList<>(messages));
        }

        @Override
        public void deleteByConversationId(String conversationId) {
            conversations.remove(conversationId);
        }

        public List<String> savedConversationIds() {
            return new ArrayList<>(savedConversationIds);
        }
    }

    public static final class RecordingChatMemory implements ChatMemory {

        private final Map<String, List<Message>> conversations = new LinkedHashMap<>();

        @Override
        public void add(String conversationId, List<Message> messages) {
            conversations.computeIfAbsent(conversationId, ignored -> new ArrayList<>()).addAll(messages);
        }

        @Override
        public List<Message> get(String conversationId) {
            return new ArrayList<>(conversations.getOrDefault(conversationId, List.of()));
        }

        @Override
        public void clear(String conversationId) {
            conversations.remove(conversationId);
        }
    }
}
