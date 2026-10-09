/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import com.openai.models.chat.completions.ChatCompletionAudioParam;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.images.ImageGenerateParams;
import org.junit.jupiter.api.Test;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.util.JacksonUtils;

public class Spring_ai_openaiTest {

    @Test
    void embeddingOptionsCreateSdkParametersForConfiguredRequest() {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model("text-embedding-3-large")
                .deploymentName("embedding-deployment")
                .user("embedding-user")
                .encodingFormat(OpenAiEmbeddingOptions.EncodingFormat.FLOAT)
                .dimensions(1536)
                .build();

        EmbeddingCreateParams parameters = options.toOpenAiCreateParams(List.of("first input", "second input"));

        assertThat(parameters.model().asString()).isEqualTo("embedding-deployment");
        assertThat(parameters.input().isArrayOfStrings()).isTrue();
        assertThat(parameters.input().asArrayOfStrings()).containsExactly("first input", "second input");
        assertThat(parameters.user()).contains("embedding-user");
        assertThat(parameters.encodingFormat()).hasValueSatisfying(format ->
                assertThat(format.asString()).isEqualTo("float"));
        assertThat(parameters.dimensions()).contains(1536L);
    }

    @Test
    void imageOptionsCreateSdkParametersForConfiguredRequest() {
        OpenAiImageOptions options = OpenAiImageOptions.builder()
                .model("gpt-image-1")
                .n(2)
                .width(1024)
                .height(768)
                .quality("HIGH")
                .responseFormat("B64_JSON")
                .style("VIVID")
                .user("image-user")
                .customHeaders(Map.of("x-tenant", "tenant-a"))
                .build();

        ImageGenerateParams parameters = options.toOpenAiImageGenerateParams(
                new ImagePrompt("A watercolor painting of a mountain lake", options));

        assertThat(parameters.prompt()).isEqualTo("A watercolor painting of a mountain lake");
        assertThat(parameters.model()).hasValueSatisfying(
                model -> assertThat(model.asString()).isEqualTo("gpt-image-1"));
        assertThat(parameters.n()).contains(2L);
        assertThat(parameters.quality()).hasValueSatisfying(
                quality -> assertThat(quality.asString()).isEqualTo("high"));
        assertThat(parameters.responseFormat()).hasValueSatisfying(format ->
                assertThat(format.asString()).isEqualTo("b64_json"));
        assertThat(parameters.size()).hasValueSatisfying(size -> assertThat(size.asString()).isEqualTo("1024x768"));
        assertThat(parameters.style()).hasValueSatisfying(style -> assertThat(style.asString()).isEqualTo("vivid"));
        assertThat(parameters.user()).contains("image-user");
        assertThat(parameters._additionalHeaders().values("x-tenant")).containsExactly("tenant-a");
    }

    @Test
    void imageOptionsRejectAnEmptyPrompt() {
        OpenAiImageOptions options = OpenAiImageOptions.builder().model("gpt-image-1").build();

        assertThatThrownBy(() -> options.toOpenAiImageGenerateParams(new ImagePrompt(List.of(), options)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Image prompt instructions cannot be empty");
    }

    @Test
    void chatAudioParametersTranslateToSdkValues() {
        OpenAiChatOptions.AudioParameters audio = new OpenAiChatOptions.AudioParameters(
                OpenAiChatOptions.AudioParameters.Voice.NOVA,
                OpenAiChatOptions.AudioParameters.AudioResponseFormat.WAV);

        ChatCompletionAudioParam parameters = audio.toChatCompletionAudioParam();

        assertThat(parameters.voice().asString()).isEqualTo("nova");
        assertThat(parameters.format().asString()).isEqualTo("wav");
    }

    @Test
    void chatToolChoiceParserBuildsNamedFunctionChoice() throws Exception {
        var node = JacksonUtils.getDefaultJsonMapper().readTree("""
                {
                    "type": "function",
                    "function": {
                        "name": "lookupWeather"
                    }
                }
                """);

        var choice = OpenAiChatModel.parseToolChoice(node);

        assertThat(choice.isNamedToolChoice()).isTrue();
        assertThat(choice.asNamedToolChoice().function().name()).isEqualTo("lookupWeather");
    }

    @Test
    void chatToolChoiceParserBuildsBuiltInChoices() throws Exception {
        for (String mode : List.of("auto", "required", "none")) {
            var node = JacksonUtils.getDefaultJsonMapper().readTree("{\"type\":\"" + mode + "\"}");

            var choice = OpenAiChatModel.parseToolChoice(node);

            assertThat(choice.isAuto()).isTrue();
            assertThat(choice.auto()).hasValueSatisfying(auto -> assertThat(auto.asString()).isEqualTo(mode));
        }
    }

}
