/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_ws_rs.jakarta_ws_rs_api;

import javax.ws.rs.client.ClientBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClientBuilderTest {
    private static final String CLIENT_BUILDER_PROPERTY = ClientBuilder.JAXRS_DEFAULT_CLIENT_BUILDER_PROPERTY;
    private static final String INVALID_CLIENT_BUILDER_CLASS = InvalidClientBuilderProvider.class.getName();

    @Test
    public void newBuilderReportsProviderLoadedAsWrongType() {
        String previousProvider = System.getProperty(CLIENT_BUILDER_PROPERTY);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            System.setProperty(CLIENT_BUILDER_PROPERTY, INVALID_CLIENT_BUILDER_CLASS);
            Thread.currentThread().setContextClassLoader(ClientBuilderTest.class.getClassLoader());

            assertThatThrownBy(ClientBuilder::newBuilder)
                    .isInstanceOf(LinkageError.class)
                    .hasMessageContaining("ClassCastException: attempting to cast")
                    .hasMessageContaining("javax/ws/rs/client/ClientBuilder.class");
        } finally {
            restoreProviderProperty(previousProvider);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    private static void restoreProviderProperty(String previousProvider) {
        if (previousProvider == null) {
            System.clearProperty(CLIENT_BUILDER_PROPERTY);
        } else {
            System.setProperty(CLIENT_BUILDER_PROPERTY, previousProvider);
        }
    }

    public static final class InvalidClientBuilderProvider {
        public InvalidClientBuilderProvider() {
        }
    }
}
