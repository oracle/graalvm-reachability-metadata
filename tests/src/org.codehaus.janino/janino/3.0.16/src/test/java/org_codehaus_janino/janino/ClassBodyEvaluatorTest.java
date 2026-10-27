/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.janino;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.codehaus.janino.ClassBodyEvaluator;
import org.codehaus.janino.Scanner;
import org.junit.jupiter.api.Test;

public class ClassBodyEvaluatorTest {
    @Test
    void createInstanceCooksClassBodyAndInstantiatesGeneratedSubclass() throws Exception {
        try {
            final ClassBodyEvaluator evaluator = new ClassBodyEvaluator();
            evaluator.setExtendedClass(GreetingBase.class);

            final Object instance = evaluator.createInstance(new StringReader("""
                    public String greet(String name) {
                        return "Hello, " + name;
                    }
                    """));

            final GreetingBase greeting = (GreetingBase) instance;
            assertThat(greeting.greet("Janino")).isEqualTo("Hello, Janino");
        } catch (Throwable throwable) {
            NativeImageDynamicClassLoadingSupport.rethrowIfNotNativeImageDynamicClassLoadingFailure(throwable);
        }
    }

    @Test
    void createFastClassBodyEvaluatorInstantiatesGeneratedInterfaceImplementation() throws Exception {
        try {
            final Scanner scanner = new Scanner(null, new StringReader("""
                    public int doubleValue(int value) {
                        return value * 2;
                    }
                    """));

            final Object instance = ClassBodyEvaluator.createFastClassBodyEvaluator(
                    scanner,
                    Doubling.class,
                    ClassBodyEvaluatorTest.class.getClassLoader());

            final Doubling doubling = (Doubling) instance;
            assertThat(doubling.doubleValue(21)).isEqualTo(42);
        } catch (Throwable throwable) {
            NativeImageDynamicClassLoadingSupport.rethrowIfNotNativeImageDynamicClassLoadingFailure(throwable);
        }
    }

    public abstract static class GreetingBase {
        public abstract String greet(String name);
    }

    public interface Doubling {
        int doubleValue(int value);
    }
}
