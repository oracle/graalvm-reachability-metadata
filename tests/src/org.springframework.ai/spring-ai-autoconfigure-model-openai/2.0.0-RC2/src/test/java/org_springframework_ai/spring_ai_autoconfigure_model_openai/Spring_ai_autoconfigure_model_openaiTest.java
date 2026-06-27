/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_autoconfigure_model_openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.openai.models.audio.AudioResponseFormat;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams.TimestampGranularity;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.model.openai.autoconfigure.AbstractOpenAiProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAutoConfigurationUtil;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationProperties;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat.Type;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions.AudioParameters;
import org.springframework.ai.openai.OpenAiChatOptions.AudioParameters.Voice;
import org.springframework.ai.openai.OpenAiChatOptions.StreamOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions.EncodingFormat;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.OpenAiModerationOptions;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

public class Spring_ai_autoconfigure_model_openaiTest {

    @Test
    void autoConfigurationImportsExposeEveryOpenAiModelAutoConfiguration() {
        List<String> candidates = ImportCandidates
                .load(AutoConfiguration.class, Spring_ai_autoconfigure_model_openaiTest.class.getClassLoader())
                .getCandidates();

        assertThat(candidates).contains(OpenAiChatAutoConfiguration.class.getName(),
                OpenAiEmbeddingAutoConfiguration.class.getName(), OpenAiImageAutoConfiguration.class.getName(),
                OpenAiAudioSpeechAutoConfiguration.class.getName(),
                OpenAiAudioTranscriptionAutoConfiguration.class.getName(),
                OpenAiModerationAutoConfiguration.class.getName());
    }

    @Test
    void binderPopulatesCurrentCommonAndModelSpecificProperties() {
        Map<String, String> properties = Map.ofEntries(
                Map.entry("spring.ai.openai.base-url", "https://common.example.test/v1"),
                Map.entry("spring.ai.openai.api-key", "common-key"),
                Map.entry("spring.ai.openai.model", "common-model"),
                Map.entry("spring.ai.openai.organization-id", "org-common"),
                Map.entry("spring.ai.openai.timeout", "45s"),
                Map.entry("spring.ai.openai.max-retries", "7"),
                Map.entry("spring.ai.openai.microsoft-foundry", "true"),
                Map.entry("spring.ai.openai.git-hub-models", "false"),
                Map.entry("spring.ai.openai.connection-pool-metrics-enabled", "true"),
                Map.entry("spring.ai.openai.custom-headers.[x-tenant]", "tenant-a"),
                Map.entry("spring.ai.openai.custom-headers.[x-trace]", "trace-a"),
                Map.entry("spring.ai.openai.chat.base-url", "https://chat.example.test/v1"),
                Map.entry("spring.ai.openai.chat.api-key", ""),
                Map.entry("spring.ai.openai.chat.model", "gpt-chat"),
                Map.entry("spring.ai.openai.chat.organization-id", "org-chat"),
                Map.entry("spring.ai.openai.chat.timeout", "15s"),
                Map.entry("spring.ai.openai.chat.max-retries", "2"),
                Map.entry("spring.ai.openai.chat.custom-headers.[x-chat]", "chat-header"),
                Map.entry("spring.ai.openai.chat.frequency-penalty", "0.5"),
                Map.entry("spring.ai.openai.chat.logit-bias.[token-a]", "3"),
                Map.entry("spring.ai.openai.chat.logprobs", "true"),
                Map.entry("spring.ai.openai.chat.top-logprobs", "4"),
                Map.entry("spring.ai.openai.chat.max-tokens", "64"),
                Map.entry("spring.ai.openai.chat.max-completion-tokens", "96"),
                Map.entry("spring.ai.openai.chat.n", "2"),
                Map.entry("spring.ai.openai.chat.output-modalities[0]", "text"),
                Map.entry("spring.ai.openai.chat.output-modalities[1]", "audio"),
                Map.entry("spring.ai.openai.chat.presence-penalty", "0.25"),
                Map.entry("spring.ai.openai.chat.seed", "1234"),
                Map.entry("spring.ai.openai.chat.stop[0]", "END"),
                Map.entry("spring.ai.openai.chat.stop[1]", "STOP"),
                Map.entry("spring.ai.openai.chat.temperature", "0.7"),
                Map.entry("spring.ai.openai.chat.top-p", "0.9"),
                Map.entry("spring.ai.openai.chat.user", "chat-user"),
                Map.entry("spring.ai.openai.chat.parallel-tool-calls", "false"),
                Map.entry("spring.ai.openai.chat.store", "true"),
                Map.entry("spring.ai.openai.chat.metadata.tenant", "acme"),
                Map.entry("spring.ai.openai.chat.reasoning-effort", "medium"),
                Map.entry("spring.ai.openai.chat.verbosity", "low"),
                Map.entry("spring.ai.openai.chat.service-tier", "default"),
                Map.entry("spring.ai.openai.embedding.metadata-mode", "none"),
                Map.entry("spring.ai.openai.embedding.model", "text-embedding-3-large"),
                Map.entry("spring.ai.openai.embedding.user", "embedding-user"),
                Map.entry("spring.ai.openai.embedding.encoding-format", "base64"),
                Map.entry("spring.ai.openai.embedding.dimensions", "1024"));

        OpenAiCommonProperties commonProperties = bind(properties, OpenAiCommonProperties.CONFIG_PREFIX,
                OpenAiCommonProperties.class);
        OpenAiChatProperties chatProperties = bind(properties, OpenAiChatProperties.CONFIG_PREFIX,
                OpenAiChatProperties.class);
        OpenAiEmbeddingProperties embeddingProperties = bind(properties, OpenAiEmbeddingProperties.CONFIG_PREFIX,
                OpenAiEmbeddingProperties.class);

        assertThat(commonProperties.getBaseUrl()).isEqualTo("https://common.example.test/v1");
        assertThat(commonProperties.getApiKey()).isEqualTo("common-key");
        assertThat(commonProperties.getModel()).isEqualTo("common-model");
        assertThat(commonProperties.getOrganizationId()).isEqualTo("org-common");
        assertThat(commonProperties.getTimeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(commonProperties.getMaxRetries()).isEqualTo(7);
        assertThat(commonProperties.isMicrosoftFoundry()).isTrue();
        assertThat(commonProperties.isGitHubModels()).isFalse();
        assertThat(commonProperties.isConnectionPoolMetricsEnabled()).isTrue();
        assertThat(commonProperties.getCustomHeaders()).containsEntry("x-tenant", "tenant-a")
                .containsEntry("x-trace", "trace-a");

        assertThat(chatProperties.getApiKey()).isEmpty();
        assertThat(chatProperties.getModel()).isEqualTo("gpt-chat");
        assertThat(chatProperties.getFrequencyPenalty()).isEqualTo(0.5d);
        assertThat(chatProperties.getLogitBias()).containsEntry("token-a", 3);
        assertThat(chatProperties.getLogprobs()).isTrue();
        assertThat(chatProperties.getTopLogprobs()).isEqualTo(4);
        assertThat(chatProperties.getMaxTokens()).isEqualTo(64);
        assertThat(chatProperties.getMaxCompletionTokens()).isEqualTo(96);
        assertThat(chatProperties.getN()).isEqualTo(2);
        assertThat(chatProperties.getOutputModalities()).containsExactly("text", "audio");
        assertThat(chatProperties.getPresencePenalty()).isEqualTo(0.25d);
        assertThat(chatProperties.getSeed()).isEqualTo(1234);
        assertThat(chatProperties.getStop()).containsExactly("END", "STOP");
        assertThat(chatProperties.getTemperature()).isEqualTo(0.7d);
        assertThat(chatProperties.getTopP()).isEqualTo(0.9d);
        assertThat(chatProperties.getUser()).isEqualTo("chat-user");
        assertThat(chatProperties.getParallelToolCalls()).isFalse();
        assertThat(chatProperties.getStore()).isTrue();
        assertThat(chatProperties.getMetadata()).containsEntry("tenant", "acme");
        assertThat(chatProperties.getReasoningEffort()).isEqualTo("medium");
        assertThat(chatProperties.getVerbosity()).isEqualTo("low");
        assertThat(chatProperties.getServiceTier()).isEqualTo("default");

        assertThat(embeddingProperties.getMetadataMode()).isEqualTo(MetadataMode.NONE);
        assertThat(embeddingProperties.getModel()).isEqualTo("text-embedding-3-large");
        assertThat(embeddingProperties.getUser()).isEqualTo("embedding-user");
        assertThat(embeddingProperties.getEncodingFormat()).isEqualTo(EncodingFormat.BASE64);
        assertThat(embeddingProperties.getDimensions()).isEqualTo(1024);
    }

    @Test
    void resolvesConnectionPropertiesUsingModelOverridesAndCommonFallbacks() {
        OpenAiCommonProperties commonProperties = new OpenAiCommonProperties();
        commonProperties.setBaseUrl("https://common.example.test/v1");
        commonProperties.setApiKey("common-key");
        commonProperties.setModel("common-model");
        commonProperties.setMicrosoftDeploymentName("common-deployment");
        commonProperties.setOrganizationId("org-common");
        commonProperties.setMicrosoftFoundry(true);
        commonProperties.setGitHubModels(false);
        commonProperties.setTimeout(Duration.ofSeconds(30));
        commonProperties.setMaxRetries(6);
        commonProperties.setCustomHeaders(Map.of("x-common", "common"));

        OpenAiChatProperties chatProperties = new OpenAiChatProperties();
        chatProperties.setBaseUrl("https://chat.example.test/v1");
        chatProperties.setApiKey("");
        chatProperties.setModel("chat-model");
        chatProperties.setGitHubModels(true);
        chatProperties.setTimeout(Duration.ofSeconds(12));
        chatProperties.setMaxRetries(1);
        chatProperties.setCustomHeaders(Map.of("x-chat", "chat"));

        OpenAiAutoConfigurationUtil.ResolvedConnectionProperties resolved =
                OpenAiAutoConfigurationUtil.resolveCommonProperties(commonProperties, chatProperties);

        assertThat(resolved.getBaseUrl()).isEqualTo("https://chat.example.test/v1");
        assertThat(resolved.getApiKey()).isEmpty();
        assertThat(resolved.getModel()).isEqualTo("chat-model");
        assertThat(resolved.getMicrosoftDeploymentName()).isEqualTo("common-deployment");
        assertThat(resolved.getOrganizationId()).isEqualTo("org-common");
        assertThat(resolved.isMicrosoftFoundry()).isTrue();
        assertThat(resolved.isGitHubModels()).isTrue();
        assertThat(resolved.getTimeout()).isEqualTo(Duration.ofSeconds(12));
        assertThat(resolved.getMaxRetries()).isEqualTo(1);
        assertThat(resolved.getCustomHeaders()).containsOnly(Map.entry("x-chat", "chat"));
    }

    @Test
    void resolvesConnectionPropertiesFallsBackWhenModelPropertiesUseDefaults() {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example.test", 8080));
        OpenAiCommonProperties commonProperties = new OpenAiCommonProperties();
        commonProperties.setBaseUrl("https://common.example.test/v1");
        commonProperties.setApiKey("common-key");
        commonProperties.setModel("common-model");
        commonProperties.setOrganizationId("org-common");
        commonProperties.setTimeout(Duration.ofSeconds(20));
        commonProperties.setMaxRetries(5);
        commonProperties.setProxy(proxy);
        commonProperties.setCustomHeaders(Map.of("x-common", "common"));

        OpenAiEmbeddingProperties embeddingProperties = new OpenAiEmbeddingProperties();

        OpenAiAutoConfigurationUtil.ResolvedConnectionProperties resolved =
                OpenAiAutoConfigurationUtil.resolveCommonProperties(commonProperties, embeddingProperties);

        assertThat(resolved.getBaseUrl()).isEqualTo("https://common.example.test/v1");
        assertThat(resolved.getApiKey()).isEqualTo("common-key");
        assertThat(resolved.getModel()).isEqualTo("common-model");
        assertThat(resolved.getOrganizationId()).isEqualTo("org-common");
        assertThat(resolved.getTimeout()).isEqualTo(Duration.ofSeconds(20));
        assertThat(resolved.getMaxRetries()).isEqualTo(5);
        assertThat(resolved.getProxy()).isSameAs(proxy);
        assertThat(resolved.getCustomHeaders()).containsOnly(Map.entry("x-common", "common"));
    }

    @Test
    void chatPropertiesCreateEquivalentChatOptions() {
        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(Type.JSON_SCHEMA)
                .jsonSchema("{\"name\":\"answer\"}")
                .build();
        StreamOptions streamOptions = StreamOptions.builder()
                .includeObfuscation(true)
                .includeUsage(true)
                .additionalProperty("trace", "enabled")
                .build();
        AudioParameters audioParameters = new AudioParameters(Voice.ALLOY, AudioParameters.AudioResponseFormat.WAV);
        OpenAiChatProperties properties = new OpenAiChatProperties();
        properties.setModel("gpt-4.1-mini");
        properties.setFrequencyPenalty(0.4d);
        properties.setLogitBias(Map.of("token", 11));
        properties.setLogprobs(true);
        properties.setTopLogprobs(3);
        properties.setMaxCompletionTokens(256);
        properties.setN(2);
        properties.setOutputModalities(List.of("text", "audio"));
        properties.setOutputAudio(audioParameters);
        properties.setPresencePenalty(0.2d);
        properties.setResponseFormat(responseFormat);
        properties.setStreamOptions(streamOptions);
        properties.setSeed(42);
        properties.setStop(List.of("END"));
        properties.setTemperature(0.6d);
        properties.setTopP(0.95d);
        properties.setToolChoice("none");
        properties.setUser("chat-user");
        properties.setParallelToolCalls(false);
        properties.setStore(true);
        properties.setMetadata(Map.of("tenant", "acme"));
        properties.setReasoningEffort("medium");
        properties.setVerbosity("low");
        properties.setServiceTier("default");
        properties.setExtraBody(Map.of("trace", true));

        OpenAiChatOptions options = properties.toOptions();

        assertThat(options.getModel()).isEqualTo("gpt-4.1-mini");
        assertThat(options.getFrequencyPenalty()).isEqualTo(0.4d);
        assertThat(options.getLogitBias()).containsEntry("token", 11);
        assertThat(options.getLogprobs()).isTrue();
        assertThat(options.getTopLogprobs()).isEqualTo(3);
        assertThat(options.getMaxCompletionTokens()).isEqualTo(256);
        assertThat(options.getN()).isEqualTo(2);
        assertThat(options.getOutputModalities()).containsExactly("text", "audio");
        assertThat(options.getOutputAudio()).isEqualTo(audioParameters);
        assertThat(options.getPresencePenalty()).isEqualTo(0.2d);
        assertThat(options.getResponseFormat()).isEqualTo(responseFormat);
        assertThat(options.getStreamOptions()).isEqualTo(streamOptions);
        assertThat(options.getSeed()).isEqualTo(42);
        assertThat(options.getStopSequences()).containsExactly("END");
        assertThat(options.getTemperature()).isEqualTo(0.6d);
        assertThat(options.getTopP()).isEqualTo(0.95d);
        assertThat(options.getToolChoice()).isEqualTo("none");
        assertThat(options.getUser()).isEqualTo("chat-user");
        assertThat(options.getParallelToolCalls()).isFalse();
        assertThat(options.getStore()).isTrue();
        assertThat(options.getMetadata()).containsEntry("tenant", "acme");
        assertThat(options.getReasoningEffort()).isEqualTo("medium");
        assertThat(options.getVerbosity()).isEqualTo("low");
        assertThat(options.getServiceTier()).isEqualTo("default");
        assertThat(options.getExtraBody()).containsEntry("trace", true);
    }

    @Test
    void embeddingImageAudioAndModerationPropertiesCreateEquivalentOptions() {
        OpenAiEmbeddingProperties embeddingProperties = new OpenAiEmbeddingProperties();
        embeddingProperties.setMetadataMode(MetadataMode.INFERENCE);
        embeddingProperties.setModel("text-embedding-3-small");
        embeddingProperties.setUser("embedding-user");
        embeddingProperties.setEncodingFormat(EncodingFormat.FLOAT);
        embeddingProperties.setDimensions(512);

        OpenAiImageProperties imageProperties = new OpenAiImageProperties();
        imageProperties.setModel("gpt-image-1");
        imageProperties.setN(1);
        imageProperties.setWidth(1024);
        imageProperties.setHeight(768);
        imageProperties.setQuality("high");
        imageProperties.setResponseFormat("b64_json");
        imageProperties.setSize("1024x768");
        imageProperties.setStyle("vivid");
        imageProperties.setUser("image-user");

        OpenAiAudioSpeechProperties speechProperties = new OpenAiAudioSpeechProperties();
        speechProperties.setModel("gpt-4o-mini-tts");
        speechProperties.setInput("Spring AI speech");
        speechProperties.setVoice("alloy");
        speechProperties.setResponseFormat("mp3");
        speechProperties.setSpeed(1.25d);

        OpenAiAudioTranscriptionProperties transcriptionProperties = new OpenAiAudioTranscriptionProperties();
        transcriptionProperties.setModel("gpt-4o-transcribe");
        transcriptionProperties.setResponseFormat(AudioResponseFormat.VERBOSE_JSON);
        transcriptionProperties.setPrompt("domain words");
        transcriptionProperties.setLanguage("en");
        transcriptionProperties.setTemperature(0.2f);
        transcriptionProperties.setTimestampGranularities(
                List.of(TimestampGranularity.WORD, TimestampGranularity.SEGMENT));

        OpenAiModerationProperties moderationProperties = new OpenAiModerationProperties();
        moderationProperties.setModel("omni-moderation-latest");

        OpenAiEmbeddingOptions embeddingOptions = embeddingProperties.toOptions();
        OpenAiImageOptions imageOptions = imageProperties.toOptions();
        OpenAiAudioSpeechOptions speechOptions = speechProperties.toOptions();
        OpenAiAudioTranscriptionOptions transcriptionOptions = transcriptionProperties.toOptions();
        OpenAiModerationOptions moderationOptions = moderationProperties.toOptions();

        assertThat(embeddingProperties.getMetadataMode()).isEqualTo(MetadataMode.INFERENCE);
        assertThat(embeddingOptions.getModel()).isEqualTo("text-embedding-3-small");
        assertThat(embeddingOptions.getUser()).isEqualTo("embedding-user");
        assertThat(embeddingOptions.getEncodingFormat()).isEqualTo(EncodingFormat.FLOAT);
        assertThat(embeddingOptions.getDimensions()).isEqualTo(512);

        assertThat(imageOptions.getModel()).isEqualTo("gpt-image-1");
        assertThat(imageOptions.getN()).isEqualTo(1);
        assertThat(imageOptions.getWidth()).isEqualTo(1024);
        assertThat(imageOptions.getHeight()).isEqualTo(768);
        assertThat(imageOptions.getQuality()).isEqualTo("high");
        assertThat(imageOptions.getResponseFormat()).isEqualTo("b64_json");
        assertThat(imageOptions.getSize()).isEqualTo("1024x768");
        assertThat(imageOptions.getStyle()).isEqualTo("vivid");
        assertThat(imageOptions.getUser()).isEqualTo("image-user");

        assertThat(speechOptions.getModel()).isEqualTo("gpt-4o-mini-tts");
        assertThat(speechOptions.getInput()).isEqualTo("Spring AI speech");
        assertThat(speechOptions.getVoice()).isEqualTo("alloy");
        assertThat(speechOptions.getResponseFormat()).isEqualTo("mp3");
        assertThat(speechOptions.getSpeed()).isEqualTo(1.25d);

        assertThat(transcriptionOptions.getModel()).isEqualTo("gpt-4o-transcribe");
        assertThat(transcriptionOptions.getResponseFormat()).isEqualTo(AudioResponseFormat.VERBOSE_JSON);
        assertThat(transcriptionOptions.getPrompt()).isEqualTo("domain words");
        assertThat(transcriptionOptions.getLanguage()).isEqualTo("en");
        assertThat(transcriptionOptions.getTemperature()).isEqualTo(0.2f);
        assertThat(transcriptionOptions.getTimestampGranularities())
                .containsExactly(TimestampGranularity.WORD, TimestampGranularity.SEGMENT);

        assertThat(moderationOptions.getModel()).isEqualTo("omni-moderation-latest");
    }

    @Test
    void defaultCommonPropertiesMatchDocumentedOpenAiClientDefaults() {
        OpenAiCommonProperties properties = new OpenAiCommonProperties();

        assertThat(properties.getTimeout()).isEqualTo(AbstractOpenAiProperties.DEFAULT_TIMEOUT);
        assertThat(properties.getMaxRetries()).isEqualTo(AbstractOpenAiProperties.DEFAULT_MAX_RETRIES);
        assertThat(properties.getCustomHeaders()).isEmpty();
        assertThat(properties.isConnectionPoolMetricsEnabled()).isFalse();
    }

    private static <T> T bind(Map<String, String> properties, String prefix, Class<T> type) {
        Binder binder = new Binder(new MapConfigurationPropertySource(properties));
        return binder.bind(prefix, Bindable.of(type)).get();
    }

}
