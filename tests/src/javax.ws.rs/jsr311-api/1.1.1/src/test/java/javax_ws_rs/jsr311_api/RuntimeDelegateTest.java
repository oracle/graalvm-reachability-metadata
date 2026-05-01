/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_ws_rs.jsr311_api;

import javax.ws.rs.ext.RuntimeDelegate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RuntimeDelegateTest {
    private static final String RUNTIME_DELEGATE_PROPERTY = RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY;

    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    private final String originalRuntimeDelegateProperty = System.getProperty(RUNTIME_DELEGATE_PROPERTY);

    @BeforeEach
    void resetRuntimeDelegate() {
        RuntimeDelegate.setInstance(null);
    }

    @AfterEach
    void restoreThreadState() {
        RuntimeDelegate.setInstance(null);
        currentThread.setContextClassLoader(originalContextClassLoader);
        if (originalRuntimeDelegateProperty == null) {
            System.clearProperty(RUNTIME_DELEGATE_PROPERTY);
        } else {
            System.setProperty(RUNTIME_DELEGATE_PROPERTY, originalRuntimeDelegateProperty);
        }
    }

    @Test
    void reportsProviderClassResourceLocationsWhenProviderHasWrongType() {
        currentThread.setContextClassLoader(new NoServiceResourceClassLoader());
        System.setProperty(RUNTIME_DELEGATE_PROPERTY, NotRuntimeDelegate.class.getName());

        assertThatThrownBy(RuntimeDelegate::getInstance)
                .isInstanceOf(LinkageError.class)
                .hasMessageContaining("ClassCastException")
                .hasMessageContaining("RuntimeDelegate.class");
    }

    public static final class NotRuntimeDelegate {
    }

    private static final class NoServiceResourceClassLoader extends ClassLoader {
        private NoServiceResourceClassLoader() {
            super(null);
        }
    }
}
