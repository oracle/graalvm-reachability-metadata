/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_autoconfigure_model_chat_observation;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.model.chat.observation.autoconfigure.ChatObservationAutoConfiguration;
import org.springframework.ai.model.chat.observation.autoconfigure.ChatObservationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_ai_autoconfigure_model_chat_observationTest {

    @Test
    void autoConfigurationIsRegisteredForSpringBootDiscovery() {
        ChatObservationProperties properties = new ChatObservationProperties();
        ClassLoader classLoader = Spring_ai_autoconfigure_model_chat_observationTest.class
                .getClassLoader();
        List<String> candidates = ImportCandidates.load(AutoConfiguration.class, classLoader).getCandidates();

        assertThat(properties.isLogPrompt()).isFalse();
        assertThat(candidates).contains(ChatObservationAutoConfiguration.class.getName());
    }

    @Test
    void observationPropertiesRetainConfiguredValues() {
        ChatObservationProperties properties = new ChatObservationProperties();

        assertThat(properties.isLogPrompt()).isFalse();
        assertThat(properties.isLogCompletion()).isFalse();
        assertThat(properties.isIncludeErrorLogging()).isFalse();

        properties.setLogPrompt(true);
        properties.setLogCompletion(true);
        properties.setIncludeErrorLogging(true);

        assertThat(properties.isLogPrompt()).isTrue();
        assertThat(properties.isLogCompletion()).isTrue();
        assertThat(properties.isIncludeErrorLogging()).isTrue();
    }

    @Test
    void autoConfigurationCanBeCreated() {
        ChatObservationProperties properties = new ChatObservationProperties();
        ChatObservationAutoConfiguration autoConfiguration = new ChatObservationAutoConfiguration();

        assertThat(properties).isNotNull();
        assertThat(autoConfiguration).isNotNull();
    }

}
