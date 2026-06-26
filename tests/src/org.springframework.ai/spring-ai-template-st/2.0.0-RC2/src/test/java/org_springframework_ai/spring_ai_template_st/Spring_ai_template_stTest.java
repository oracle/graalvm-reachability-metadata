/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_template_st;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.ValidationMode;
import org.springframework.ai.template.st.StTemplateRenderer;

public class Spring_ai_template_stTest {

    @Test
    void rendersScalarVariablesRepeatedVariablesAndNumbers() {
        StTemplateRenderer renderer = StTemplateRenderer.builder().build();

        String rendered = renderer.apply("Hello {name}. Again: {name}. Count: {count}.",
                Map.of("name", "Spring AI", "count", 3));

        assertThat(rendered).isEqualTo("Hello Spring AI. Again: Spring AI. Count: 3.");
    }

    @Test
    void rendersListSubtemplateWithSeparator() {
        StTemplateRenderer renderer = StTemplateRenderer.builder()
                .validationMode(ValidationMode.NONE)
                .build();

        String rendered = renderer.apply("Items: {items:{item | [{item}]}; separator=\" \"}",
                Map.of("items", List.of("alpha", "beta", "gamma")));

        assertThat(rendered).isEqualTo("Items: [alpha] [beta] [gamma]");
    }

    @Test
    void canBeUsedThroughTemplateRendererInterfaceWithoutSharingStateAcrossCalls() {
        TemplateRenderer renderer = StTemplateRenderer.builder().build();

        String first = renderer.apply("{greeting}, {name}!", Map.of("greeting", "Hello", "name", "Ada"));
        String second = renderer.apply("{greeting}, {name}!", Map.of("greeting", "Hi", "name", "Grace"));

        assertThat(first).isEqualTo("Hello, Ada!");
        assertThat(second).isEqualTo("Hi, Grace!");
    }

    @Test
    void builderSupportsCustomDelimiterCharacters() {
        StTemplateRenderer renderer = StTemplateRenderer.builder()
                .startDelimiterToken('<')
                .endDelimiterToken('>')
                .build();

        String rendered = renderer.apply("Use <language>; leave {language} untouched.", Map.of("language", "Java"));

        assertThat(rendered).isEqualTo("Use Java; leave {language} untouched.");
    }

    @Test
    void defaultValidationThrowsWhenTemplateVariablesAreMissing() {
        StTemplateRenderer renderer = StTemplateRenderer.builder().build();

        assertThatIllegalStateException()
                .isThrownBy(() -> renderer.apply("Hello {name}, welcome to {place}.", Map.of("name", "Ada")))
                .withMessageContaining("Not all variables were replaced")
                .withMessageContaining("place");
    }

    @Test
    void warnValidationRendersTemplateWhileAllowingMissingVariables() {
        StTemplateRenderer renderer = StTemplateRenderer.builder()
                .validationMode(ValidationMode.WARN)
                .build();

        String rendered = renderer.apply("Hello {name}; missing value is '{missing}'.", Map.of("name", "Ada"));

        assertThat(rendered).isEqualTo("Hello Ada; missing value is ''.");
    }

    @Test
    void noneValidationRendersTemplateWhileAllowingMissingVariables() {
        StTemplateRenderer renderer = new StTemplateRenderer('{', '}', ValidationMode.NONE, false);

        String rendered = renderer.apply("Known={known}; Unknown={unknown}", Map.of("known", "present"));

        assertThat(rendered).isEqualTo("Known=present; Unknown=");
    }

    @Test
    void validationUnderstandsStringTemplateBuiltInFunctions() {
        StTemplateRenderer renderer = StTemplateRenderer.builder().build();

        String rendered = renderer.apply("{if(strlen(memory))}Memory: {memory}{endif}",
                Map.of("memory", "you are a helpful assistant"));

        assertThat(rendered).isEqualTo("Memory: you are a helpful assistant");
    }

    @Test
    void validateStFunctionsIncludesFunctionNamedVariablesInValidation() {
        StTemplateRenderer renderer = StTemplateRenderer.builder()
                .validateStFunctions()
                .build();

        assertThatIllegalStateException()
                .isThrownBy(() -> renderer.apply("First choice: {first}", Map.of()))
                .withMessageContaining("first");

        String rendered = renderer.apply("First choice: {first}", Map.of("first", "tea"));

        assertThat(rendered).isEqualTo("First choice: tea");
    }

    @Test
    void propertyAccessRequiresOnlyTheTopLevelVariable() {
        StTemplateRenderer renderer = StTemplateRenderer.builder().build();

        String rendered = renderer.apply("Hello {user.profile.name}!",
                Map.of("user", Map.of("profile", Map.of("name", "Grace"))));

        assertThat(rendered).isEqualTo("Hello Grace!");
        assertThatIllegalStateException()
                .isThrownBy(() -> renderer.apply("Hello {user.profile.name}!",
                        Map.of("profile", Map.of("name", "Grace"))))
                .withMessageContaining("Missing variable names are: [user]");
    }

    @Test
    void presentNullVariableSatisfiesValidationAndRendersAsEmptyText() {
        StTemplateRenderer renderer = StTemplateRenderer.builder().build();
        Map<String, Object> variables = new HashMap<>();
        variables.put("value", null);

        String rendered = renderer.apply("Value is '{value}'.", variables);

        assertThat(rendered).isEqualTo("Value is ''.");
    }

    @Test
    void rejectsInvalidInputsBeforeRendering() {
        StTemplateRenderer renderer = StTemplateRenderer.builder().build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> renderer.apply(" ", Map.of()))
                .withMessageContaining("template cannot be null or empty");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> renderer.apply("Hello {name}", null))
                .withMessageContaining("variables cannot be null");

        Map<String, Object> variablesWithNullKey = new HashMap<>();
        variablesWithNullKey.put(null, "value");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> renderer.apply("Hello {name}", variablesWithNullKey))
                .withMessageContaining("variables keys cannot be null");
    }

    @Test
    void rejectsInvalidTemplateSyntax() {
        StTemplateRenderer renderer = StTemplateRenderer.builder().build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> renderer.apply("Hello {name!", Map.of("name", "Spring AI")))
                .withMessageContaining("The template string is not valid.");
    }

    @Test
    void constructorRejectsNullValidationMode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StTemplateRenderer('{', '}', null, false))
                .withMessageContaining("validationMode cannot be null");
    }

}
