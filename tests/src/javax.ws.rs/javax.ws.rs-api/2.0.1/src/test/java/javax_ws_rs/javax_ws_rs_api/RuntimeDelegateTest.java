/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_ws_rs.javax_ws_rs_api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;

import javax.ws.rs.ext.RuntimeDelegate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RuntimeDelegateTest {
    private static final String RUNTIME_DELEGATE_PROPERTY = RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY;
    private static final String RUNTIME_DELEGATE_SERVICE = "META-INF/services/" + RUNTIME_DELEGATE_PROPERTY;
    private static final String INVALID_RUNTIME_DELEGATE_CLASS = InvalidRuntimeDelegateProvider.class.getName();

    private ClassLoader originalContextClassLoader;
    private String originalRuntimeDelegateProperty;

    @BeforeEach
    void resetRuntimeDelegate() {
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        originalRuntimeDelegateProperty = System.getProperty(RUNTIME_DELEGATE_PROPERTY);
        RuntimeDelegate.setInstance(null);
        System.clearProperty(RUNTIME_DELEGATE_PROPERTY);
    }

    @AfterEach
    void restoreRuntimeDelegateState() {
        RuntimeDelegate.setInstance(null);
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        if (originalRuntimeDelegateProperty == null) {
            System.clearProperty(RUNTIME_DELEGATE_PROPERTY);
        } else {
            System.setProperty(RUNTIME_DELEGATE_PROPERTY, originalRuntimeDelegateProperty);
        }
    }

    @Test
    void rejectsProviderThatDoesNotImplementRuntimeDelegate() {
        System.setProperty(RUNTIME_DELEGATE_PROPERTY, INVALID_RUNTIME_DELEGATE_CLASS);
        Thread.currentThread().setContextClassLoader(new NoRuntimeDelegateServiceClassLoader());

        assertThatThrownBy(RuntimeDelegate::getInstance)
                .isInstanceOf(LinkageError.class)
                .hasMessageContaining("ClassCastException: attempting to cast");
    }

    public static final class InvalidRuntimeDelegateProvider {
        public InvalidRuntimeDelegateProvider() {
        }
    }

    private static final class NoRuntimeDelegateServiceClassLoader extends ClassLoader {
        private NoRuntimeDelegateServiceClassLoader() {
            super(RuntimeDelegateTest.class.getClassLoader());
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (RUNTIME_DELEGATE_SERVICE.equals(name)) {
                return null;
            }
            return super.getResourceAsStream(name);
        }
    }
}
