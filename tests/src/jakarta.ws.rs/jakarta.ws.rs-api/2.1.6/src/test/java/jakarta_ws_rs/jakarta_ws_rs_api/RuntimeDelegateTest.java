/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_ws_rs.jakarta_ws_rs_api;

import javax.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RuntimeDelegateTest {
    private static final String RUNTIME_DELEGATE_PROPERTY = RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY;
    private static final String INVALID_RUNTIME_DELEGATE_CLASS = InvalidRuntimeDelegateProvider.class.getName();

    @Test
    public void getInstanceReportsProviderLoadedAsWrongType() {
        String previousProvider = System.getProperty(RUNTIME_DELEGATE_PROPERTY);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            RuntimeDelegate.setInstance(null);
            System.setProperty(RUNTIME_DELEGATE_PROPERTY, INVALID_RUNTIME_DELEGATE_CLASS);
            Thread.currentThread().setContextClassLoader(RuntimeDelegateTest.class.getClassLoader());

            assertThatThrownBy(RuntimeDelegate::getInstance)
                    .isInstanceOf(LinkageError.class)
                    .hasMessageContaining("ClassCastException: attempting to cast")
                    .hasMessageContaining("javax/ws/rs/ext/RuntimeDelegate.class");
        } finally {
            RuntimeDelegate.setInstance(null);
            restoreProviderProperty(previousProvider);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    private static void restoreProviderProperty(String previousProvider) {
        if (previousProvider == null) {
            System.clearProperty(RUNTIME_DELEGATE_PROPERTY);
        } else {
            System.setProperty(RUNTIME_DELEGATE_PROPERTY, previousProvider);
        }
    }

    public static final class InvalidRuntimeDelegateProvider {
        public InvalidRuntimeDelegateProvider() {
        }
    }
}
