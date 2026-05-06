/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_ws_rs.jakarta_ws_rs_api;

import javax.ws.rs.sse.SseEventSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SseEventSourceInnerBuilderTest {
    private static final String SSE_BUILDER_PROPERTY =
            SseEventSource.Builder.JAXRS_DEFAULT_SSE_BUILDER_PROPERTY;
    private static final String INVALID_SSE_BUILDER_CLASS = InvalidSseEventSourceBuilderProvider.class.getName();

    @Test
    public void targetReportsProviderLoadedAsWrongType() {
        String previousProvider = System.getProperty(SSE_BUILDER_PROPERTY);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            System.setProperty(SSE_BUILDER_PROPERTY, INVALID_SSE_BUILDER_CLASS);
            Thread.currentThread().setContextClassLoader(SseEventSourceInnerBuilderTest.class.getClassLoader());

            assertThatThrownBy(() -> SseEventSource.target(null))
                    .isInstanceOf(LinkageError.class)
                    .hasMessageContaining("ClassCastException: attempting to cast")
                    .hasMessageContaining("javax/ws/rs/sse/SseEventSource$Builder.class");
        } finally {
            restoreProviderProperty(previousProvider);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    private static void restoreProviderProperty(String previousProvider) {
        if (previousProvider == null) {
            System.clearProperty(SSE_BUILDER_PROPERTY);
        } else {
            System.setProperty(SSE_BUILDER_PROPERTY, previousProvider);
        }
    }

    public static final class InvalidSseEventSourceBuilderProvider {
        public InvalidSseEventSourceBuilderProvider() {
        }
    }
}
