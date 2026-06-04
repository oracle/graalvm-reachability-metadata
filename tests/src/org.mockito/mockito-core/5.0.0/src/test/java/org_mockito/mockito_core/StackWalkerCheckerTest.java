/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.plugins.MockMaker;

import static org.assertj.core.api.Assertions.assertThat;

public class StackWalkerCheckerTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void constructionMockingAllowsRealSubclassConstructorCalls() throws Exception {
        try {
            exerciseConstructionMockingWithSubclassConstructor();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } catch (Exception exception) {
            Error unsupportedFeatureError = findUnsupportedFeatureError(exception);
            if (unsupportedFeatureError == null) {
                throw exception;
            }
        }
    }

    private static void exerciseConstructionMockingWithSubclassConstructor() {
        MockMaker inlineMockMaker = Mockito.framework().getPlugins().getInlineMockMaker();
        MockMaker.ConstructionMockControl<ConstructedBase> control =
                inlineMockMaker.createConstructionMock(
                        ConstructedBase.class,
                        context -> {
                            throw new AssertionError("subclass construction should stay real");
                        },
                        context -> {
                            throw new AssertionError("subclass construction should stay real");
                        },
                        (mock, context) -> {
                            throw new AssertionError("subclass construction should stay real");
                        });
        control.enable();
        try {
            ConstructedChild subclassConstruction = new ConstructedChild("subclass");
            assertThat(subclassConstruction.name()).isEqualTo("real-subclass");
        } finally {
            control.disable();
        }
    }

    private static Error findUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error
                    && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return (Error) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private static class ConstructedBase {
        private final String name;

        ConstructedBase(String name) {
            this.name = "real-" + name;
        }

        String name() {
            return name;
        }
    }

    private static final class ConstructedChild extends ConstructedBase {
        ConstructedChild(String name) {
            super(name);
        }
    }
}
