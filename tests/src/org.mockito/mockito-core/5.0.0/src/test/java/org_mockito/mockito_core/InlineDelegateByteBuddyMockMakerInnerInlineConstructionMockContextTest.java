/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.invocation.MockHandler;
import org.mockito.mock.MockCreationSettings;
import org.mockito.plugins.MockMaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.withSettings;

public class InlineDelegateByteBuddyMockMakerInnerInlineConstructionMockContextTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void constructionMockContextResolvesConstructorWithReferenceAndPrimitiveParameters()
            throws Exception {
        try {
            exerciseConstructionMockContext();
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

    private static void exerciseConstructionMockContext() {
        ConstructedService prototype =
                Mockito.mock(
                        ConstructedService.class,
                        withSettings().mockMaker(MockMakers.INLINE));
        MockCreationSettings<ConstructedService> settings = mockCreationSettings(prototype);
        MockHandler<ConstructedService> handler = mockHandler(prototype);

        MockMaker inlineMockMaker = Mockito.framework().getPlugins().getInlineMockMaker();
        AtomicReference<Constructor<?>> constructor = new AtomicReference<>();
        AtomicReference<List<?>> arguments = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();

        MockMaker.ConstructionMockControl<ConstructedService> control =
                inlineMockMaker.createConstructionMock(
                        ConstructedService.class,
                        context -> settings,
                        context -> handler,
                        (mock, context) -> captureContext(context, constructor, arguments, count));
        control.enable();
        try {
            Helper helper = new Helper("dependency");
            ConstructedService constructed = new ConstructedService("alpha", helper, 7);

            assertThat(control.getMocks()).containsExactly(constructed);
            assertThat(constructor.get().getDeclaringClass()).isEqualTo(ConstructedService.class);
            assertThat(constructor.get().getParameterTypes())
                    .containsExactly(String.class, Helper.class, int.class);
            assertThat(arguments.get()).hasSize(3);
            assertThat(arguments.get().get(0)).isEqualTo("alpha");
            assertThat(arguments.get().get(1)).isSameAs(helper);
            assertThat(arguments.get().get(2)).isEqualTo(7);
            assertThat(count.get()).isEqualTo(1);
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

    private static void captureContext(
            MockedConstruction.Context context,
            AtomicReference<Constructor<?>> constructor,
            AtomicReference<List<?>> arguments,
            AtomicInteger count) {
        count.set(context.getCount());
        constructor.set(context.constructor());
        arguments.set(context.arguments());
    }

    @SuppressWarnings("unchecked")
    private static MockCreationSettings<ConstructedService> mockCreationSettings(
            ConstructedService prototype) {
        return (MockCreationSettings<ConstructedService>)
                Mockito.mockingDetails(prototype).getMockCreationSettings();
    }

    @SuppressWarnings("unchecked")
    private static MockHandler<ConstructedService> mockHandler(ConstructedService prototype) {
        return (MockHandler<ConstructedService>) Mockito.mockingDetails(prototype).getMockHandler();
    }

    public static class ConstructedService {
        private final String name;
        private final Helper helper;
        private final int attempts;

        public ConstructedService(String name, Helper helper, int attempts) {
            this.name = name;
            this.helper = helper;
            this.attempts = attempts;
        }

        public String description() {
            return name + helper.name() + attempts;
        }
    }

    public static class Helper {
        private final String name;

        public Helper(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }
    }
}
