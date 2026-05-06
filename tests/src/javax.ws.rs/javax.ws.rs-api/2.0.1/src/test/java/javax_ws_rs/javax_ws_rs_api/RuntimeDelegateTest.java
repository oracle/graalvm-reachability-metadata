/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_ws_rs.javax_ws_rs_api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.ws.rs.ext.RuntimeDelegate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RuntimeDelegateTest {
    private final String originalRuntimeDelegateProperty = System.getProperty(
            RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY);

    @BeforeEach
    void resetRuntimeDelegate() {
        RuntimeDelegate.setInstance(null);
        System.clearProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY);
    }

    @AfterEach
    void restoreRuntimeDelegateState() {
        RuntimeDelegate.setInstance(null);
        if (originalRuntimeDelegateProperty == null) {
            System.clearProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY);
        } else {
            System.setProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY, originalRuntimeDelegateProperty);
        }
    }

    @Test
    void getInstanceReportsProviderClassThatIsNotRuntimeDelegate() {
        withRuntimeDelegateProperty(NonRuntimeDelegateProvider.class.getName(), () ->
                assertThatThrownBy(RuntimeDelegate::getInstance)
                        .isInstanceOf(LinkageError.class)
                        .hasMessageContaining("ClassCastException: attempting to cast"));
    }

    private static synchronized void withRuntimeDelegateProperty(String providerClassName, Runnable action) {
        String previousProvider = System.getProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY);
        RuntimeDelegate.setInstance(null);
        System.setProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY, providerClassName);
        try {
            action.run();
        } finally {
            RuntimeDelegate.setInstance(null);
            if (previousProvider == null) {
                System.clearProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY);
            } else {
                System.setProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY, previousProvider);
            }
        }
    }

    public static final class NonRuntimeDelegateProvider {
    }
}
