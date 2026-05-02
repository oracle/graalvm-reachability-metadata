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

public class MixinEverythingEmitterTest {

    @Test
    void createsEverythingMixinFromConcreteDelegateClasses() {
        try {
            Mixin.Generator generator = new Mixin.Generator();
            generator.setStyle(Mixin.STYLE_EVERYTHING);
            generator.setClasses(new Class<?>[] {GreetingDelegate.class, FormattingDelegate.class});
            generator.setDelegates(new Object[] {new GreetingDelegate("hello"), new FormattingDelegate("[", "]")});

            Mixin mixin = generator.create();

            assertThat(mixin).isInstanceOf(GreetingOperations.class);
            assertThat(mixin).isInstanceOf(FormattingOperations.class);
            assertThat(((GreetingOperations) mixin).greet("Spring")).isEqualTo("hello Spring");
            assertThat(((FormattingOperations) mixin).format("core")).isEqualTo("[core]");
            assertThat(mixin.toString()).contains("hello");

            Mixin replacement = mixin.newInstance(new Object[] {
                    new GreetingDelegate("welcome"),
                    new FormattingDelegate("<", ">")
            });

            assertThat(((GreetingOperations) replacement).greet("Spring")).isEqualTo("welcome Spring");
            assertThat(((FormattingOperations) replacement).format("core")).isEqualTo("<core>");
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

        private final String greeting;

        public GreetingDelegate(String greeting) {
            this.greeting = greeting;
        }

        @Override
        public String greet(String name) {
            return this.greeting + " " + name;
        }

        public String describeGreeting() {
            return "GreetingDelegate[" + this.greeting + "]";
        }

        @Override
        public String toString() {
            return describeGreeting();
        }
    }

    public static class FormattingDelegate implements FormattingOperations {

        private final String prefix;

        private final String suffix;

        public FormattingDelegate(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }

        @Override
        public String format(String value) {
            return this.prefix + value + this.suffix;
        }

        public String describeFormatting() {
            return "FormattingDelegate[" + this.prefix + this.suffix + "]";
        }
    }
}
