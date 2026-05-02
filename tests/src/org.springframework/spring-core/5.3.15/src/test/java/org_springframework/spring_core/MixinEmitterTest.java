/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.cglib.core.CodeGenerationException;
import org.springframework.cglib.proxy.Mixin;

public class MixinEmitterTest {

    @Test
    void createsInterfaceMixinFromExplicitDelegateClasses() {
        try {
            Mixin mixin = Mixin.create(
                    new Class<?>[] {GreetingOperations.class, FormattingOperations.class},
                    new Object[] {new GreetingDelegate(), new FormattingDelegate()}
            );

            assertThat(mixin).isInstanceOf(GreetingOperations.class);
            assertThat(mixin).isInstanceOf(FormattingOperations.class);
            assertThat(((GreetingOperations) mixin).greet("Spring")).isEqualTo("hello Spring");
            assertThat(((FormattingOperations) mixin).format("core")).isEqualTo("[core]");

            Mixin replacement = mixin.newInstance(new Object[] {
                    new FriendlyGreetingDelegate(),
                    new UppercaseFormattingDelegate()
            });

            assertThat(((GreetingOperations) replacement).greet("Spring")).isEqualTo("welcome Spring");
            assertThat(((FormattingOperations) replacement).format("core")).isEqualTo("CORE");
        }
        catch (CodeGenerationException ex) {
            ignoreUnsupportedDynamicClassLoading(ex);
        }
        catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
        }
    }

    private static void ignoreUnsupportedDynamicClassLoading(CodeGenerationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) cause)) {
            return;
        }
        throw ex;
    }

    private static void ignoreUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public interface GreetingOperations {

        String greet(String name);
    }

    public interface FormattingOperations {

        String format(String value);
    }

    public static class GreetingDelegate implements GreetingOperations {

        @Override
        public String greet(String name) {
            return "hello " + name;
        }
    }

    public static class FriendlyGreetingDelegate implements GreetingOperations {

        @Override
        public String greet(String name) {
            return "welcome " + name;
        }
    }

    public static class FormattingDelegate implements FormattingOperations {

        @Override
        public String format(String value) {
            return "[" + value + "]";
        }
    }

    public static class UppercaseFormattingDelegate implements FormattingOperations {

        @Override
        public String format(String value) {
            return value.toUpperCase();
        }
    }
}
