/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_autoconfigure_model_chat_memory;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_ai_autoconfigure_model_chat_memoryTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChatMemoryAutoConfiguration.class));

    @Test
    void registersAutoConfigurationForSpringBootDiscovery() {
        ClassLoader classLoader = ChatMemoryAutoConfiguration.class.getClassLoader();

        assertThat(ImportCandidates.load(AutoConfiguration.class, classLoader).getCandidates())
                .contains(ChatMemoryAutoConfiguration.class.getName());
    }

    @Test
    void createsDefaultMemoryAndInMemoryRepository() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChatMemoryRepository.class);
            assertThat(context).hasSingleBean(ChatMemory.class);

            ChatMemoryRepository repository = context.getBean(ChatMemoryRepository.class);
            ChatMemory memory = context.getBean(ChatMemory.class);

            assertThat(repository).isInstanceOf(InMemoryChatMemoryRepository.class);
            assertThat(memory).isInstanceOf(MessageWindowChatMemory.class);

            memory.add("first", new SystemMessage("You are concise"));
            memory.add("first", new UserMessage("Hello"));
            memory.add("second", new UserMessage("Separate conversation"));

            assertThat(memory.get("first")).extracting(Message::getText)
                    .containsExactly("You are concise", "Hello");
            assertThat(memory.get("second")).extracting(Message::getText)
                    .containsExactly("Separate conversation");
            assertThat(repository.findConversationIds()).containsExactlyInAnyOrder("first", "second");

            memory.clear("first");

            assertThat(memory.get("first")).isEmpty();
            assertThat(repository.findConversationIds()).containsExactly("second");
        });
    }

    @Test
    void defaultMessageWindowEvictsOldMessagesAndPreservesSystemMessage() {
        this.contextRunner.run(context -> {
            ChatMemory memory = context.getBean(ChatMemory.class);
            memory.add("window", new SystemMessage("system instructions"));
            for (int index = 0; index < 20; index++) {
                memory.add("window", new UserMessage("message-" + index));
            }

            assertThat(memory.get("window")).extracting(Message::getText)
                    .containsExactly("system instructions", "message-1", "message-2", "message-3", "message-4",
                            "message-5", "message-6", "message-7", "message-8", "message-9", "message-10",
                            "message-11", "message-12", "message-13", "message-14", "message-15", "message-16",
                            "message-17", "message-18", "message-19");
        });
    }

    @Test
    void usesUserProvidedRepositoryForAutoConfiguredMemory() {
        ChatMemoryRepository repository = new InMemoryChatMemoryRepository();

        this.contextRunner.withBean(ChatMemoryRepository.class, () -> repository).run(context -> {
            assertThat(context).hasSingleBean(ChatMemoryRepository.class);
            assertThat(context.getBean(ChatMemoryRepository.class)).isSameAs(repository);

            ChatMemory memory = context.getBean(ChatMemory.class);
            memory.add("custom", new UserMessage("stored by custom repository"));

            assertThat(repository.findConversationIds()).containsExactly("custom");
            assertThat(repository.findByConversationId("custom")).extracting(Message::getText)
                    .containsExactly("stored by custom repository");
        });
    }

    @Test
    void backsOffWhenUserProvidesChatMemory() {
        ChatMemory customMemory = MessageWindowChatMemory.builder().maxMessages(2).build();

        this.contextRunner.withBean(ChatMemory.class, () -> customMemory).run(context -> {
            assertThat(context).hasSingleBean(ChatMemory.class);
            assertThat(context.getBean(ChatMemory.class)).isSameAs(customMemory);
            assertThat(context).hasSingleBean(ChatMemoryRepository.class);

            ChatMemory memory = context.getBean(ChatMemory.class);
            memory.add("provided", new UserMessage("first"));
            memory.add("provided", new UserMessage("second"));
            memory.add("provided", new UserMessage("third"));

            assertThat(memory.get("provided")).extracting(Message::getText).containsExactly("second", "third");
        });
    }
}
