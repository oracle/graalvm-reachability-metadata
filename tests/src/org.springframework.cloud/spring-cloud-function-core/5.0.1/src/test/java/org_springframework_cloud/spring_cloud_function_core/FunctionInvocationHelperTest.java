/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_function_core;

import java.util.Locale;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.function.core.FunctionInvocationHelper;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionInvocationHelperTest {
    @Test
    void defaultMethodsKeepInputAndResultUnchanged() {
        FunctionInvocationHelper<InvocationInput> helper = new DefaultFunctionInvocationHelper<>();
        InvocationInput input = new InvocationInput("payload");
        Object conversionContext = new Object();
        InvocationResult result = new InvocationResult("result");

        InvocationInput preProcessedInput = helper.preProcessInput(input, conversionContext);
        Object postProcessedResult = helper.postProcessResult(result, input);

        assertThat(helper.isRetainOutputAsMessage(input)).isTrue();
        assertThat(preProcessedInput).isSameAs(input);
        assertThat(postProcessedResult).isSameAs(result);
    }

    @Test
    void defaultMethodsAcceptNullValues() {
        FunctionInvocationHelper<Object> helper = new DefaultFunctionInvocationHelper<>();

        assertThat(helper.isRetainOutputAsMessage(null)).isTrue();
        assertThat(helper.preProcessInput(null, null)).isNull();
        assertThat(helper.postProcessResult(null, null)).isNull();
    }

    @Test
    void implementationsCanCustomizeAllInvocationHooks() {
        InvocationInput originalInput = new InvocationInput("incoming");
        InvocationInput normalizedInput = new InvocationInput("normalized");
        InvocationResult rawResult = new InvocationResult("raw");
        InvocationResult normalizedResult = new InvocationResult("normalized");
        FunctionInvocationHelper<InvocationInput> helper = new CustomFunctionInvocationHelper(normalizedInput,
                normalizedResult);

        InvocationInput preProcessedInput = helper.preProcessInput(originalInput, "conversion-context");
        Object postProcessedResult = helper.postProcessResult(rawResult, originalInput);

        assertThat(helper.isRetainOutputAsMessage(originalInput)).isFalse();
        assertThat(preProcessedInput).isSameAs(normalizedInput);
        assertThat(postProcessedResult).isSameAs(normalizedResult);
    }

    @Test
    void implementationsCanOverrideOnlySelectedInvocationHooks() {
        InvocationInput originalInput = new InvocationInput(" incoming ");
        InvocationResult result = new InvocationResult("result");
        FunctionInvocationHelper<InvocationInput> helper = new InputTrimmingFunctionInvocationHelper();

        InvocationInput preProcessedInput = helper.preProcessInput(originalInput, "conversion-context");
        Object postProcessedResult = helper.postProcessResult(result, preProcessedInput);

        assertThat(helper.isRetainOutputAsMessage(originalInput)).isTrue();
        assertThat(preProcessedInput.value()).isEqualTo("incoming");
        assertThat(postProcessedResult).isSameAs(result);
    }

    @Test
    void helperCanDecorateFunctionInvocationPipeline() {
        InvocationInput originalInput = new InvocationInput(" spring cloud function ");
        FunctionInvocationHelper<InvocationInput> helper = new InvocationDecoratingFunctionInvocationHelper();
        Function<InvocationInput, InvocationResult> uppercaseFunction = input -> new InvocationResult(
                input.value().toUpperCase(Locale.ROOT));

        InvocationInput preProcessedInput = helper.preProcessInput(originalInput, InvocationInput.class);
        InvocationResult rawResult = uppercaseFunction.apply(preProcessedInput);
        Object postProcessedResult = helper.postProcessResult(rawResult, preProcessedInput);

        assertThat(helper.isRetainOutputAsMessage(preProcessedInput)).isFalse();
        assertThat(preProcessedInput.value()).isEqualTo("spring cloud function");
        assertThat(postProcessedResult).isEqualTo(new InvocationResult("[SPRING CLOUD FUNCTION]"));
    }

    private static final class DefaultFunctionInvocationHelper<I> implements FunctionInvocationHelper<I> {
    }

    private static final class InputTrimmingFunctionInvocationHelper
            implements FunctionInvocationHelper<InvocationInput> {
        @Override
        public InvocationInput preProcessInput(InvocationInput input, Object conversionContext) {
            assertThat(conversionContext).isEqualTo("conversion-context");
            return new InvocationInput(input.value().trim());
        }
    }

    private static final class InvocationDecoratingFunctionInvocationHelper
            implements FunctionInvocationHelper<InvocationInput> {
        @Override
        public boolean isRetainOutputAsMessage(InvocationInput input) {
            return !input.value().contains("cloud");
        }

        @Override
        public InvocationInput preProcessInput(InvocationInput input, Object conversionContext) {
            assertThat(conversionContext).isEqualTo(InvocationInput.class);
            return new InvocationInput(input.value().trim());
        }

        @Override
        public Object postProcessResult(Object result, InvocationInput input) {
            assertThat(input.value()).isEqualTo("spring cloud function");
            assertThat(result).isInstanceOf(InvocationResult.class);
            InvocationResult invocationResult = (InvocationResult) result;
            return new InvocationResult("[" + invocationResult.value() + "]");
        }
    }

    private static final class CustomFunctionInvocationHelper implements FunctionInvocationHelper<InvocationInput> {
        private final InvocationInput preProcessedInput;

        private final InvocationResult postProcessedResult;

        private CustomFunctionInvocationHelper(InvocationInput preProcessedInput,
                InvocationResult postProcessedResult) {
            this.preProcessedInput = preProcessedInput;
            this.postProcessedResult = postProcessedResult;
        }

        @Override
        public boolean isRetainOutputAsMessage(InvocationInput input) {
            return false;
        }

        @Override
        public InvocationInput preProcessInput(InvocationInput input, Object conversionContext) {
            assertThat(input.value()).isEqualTo("incoming");
            assertThat(conversionContext).isEqualTo("conversion-context");
            return this.preProcessedInput;
        }

        @Override
        public Object postProcessResult(Object result, InvocationInput input) {
            assertThat(result).isInstanceOf(InvocationResult.class);
            assertThat(((InvocationResult) result).value()).isEqualTo("raw");
            assertThat(input.value()).isEqualTo("incoming");
            return this.postProcessedResult;
        }
    }

    private record InvocationInput(String value) {
    }

    private record InvocationResult(String value) {
    }
}
