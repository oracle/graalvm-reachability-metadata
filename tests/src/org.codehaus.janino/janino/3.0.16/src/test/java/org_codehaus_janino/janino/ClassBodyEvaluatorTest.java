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
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ClassBodyEvaluatorTest {
    @Test
    void createsInstanceFromClassBodyReader() throws Exception {
        try {
            final ClassBodyEvaluator evaluator = new ClassBodyEvaluator();
            evaluator.setImplementedInterfaces(new Class<?>[] { ValueProvider.class });

            final ValueProvider provider = (ValueProvider) evaluator.createInstance(new StringReader("""
                    public int value() {
                        return 41;
                    }
                    """));

            assertThat(provider.value()).isEqualTo(41);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    void createsFastClassBodyEvaluatorInstanceFromScanner() throws Exception {
        try {
            final Scanner scanner = new Scanner(null, new StringReader("""
                    public int value() {
                        return 42;
                    }
                    """));

            final ValueProvider provider = (ValueProvider) ClassBodyEvaluator.createFastClassBodyEvaluator(
                    scanner,
                    "GeneratedFastClassBody",
                    null,
                    new Class<?>[] { ValueProvider.class },
                    ClassBodyEvaluatorTest.class.getClassLoader()
            );

            assertThat(provider.value()).isEqualTo(42);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    public interface ValueProvider {
        int value();
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
