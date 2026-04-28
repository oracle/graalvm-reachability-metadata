/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_ws_rs.jsr311_api;

import javax.ws.rs.ext.RuntimeDelegate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class RuntimeDelegateTest {
    private static final String RUNTIME_DELEGATE_PROPERTY = RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY;
    private static final String INVALID_PROVIDER_CLASS_NAME = RuntimeDelegateTest.class.getName();

    private final String originalRuntimeDelegateProperty = System.getProperty(RUNTIME_DELEGATE_PROPERTY);

    @AfterEach
    void restoreRuntimeDelegateLookupState() {
        RuntimeDelegate.setInstance(null);
        if (originalRuntimeDelegateProperty == null) {
            System.clearProperty(RUNTIME_DELEGATE_PROPERTY);
        } else {
            System.setProperty(RUNTIME_DELEGATE_PROPERTY, originalRuntimeDelegateProperty);
        }
    }

    @Test
    void reportsClassResourceLocationsWhenProviderIsNotRuntimeDelegate() {
        RuntimeDelegate.setInstance(null);
        System.setProperty(RUNTIME_DELEGATE_PROPERTY, INVALID_PROVIDER_CLASS_NAME);

        Throwable thrown = catchThrowable(RuntimeDelegate::getInstance);

        assertThat(thrown).isInstanceOfAny(LinkageError.class, RuntimeException.class);
        if (thrown instanceof LinkageError) {
            assertThat(thrown).hasMessageContaining("ClassCastException: attempting to cast");
        } else {
            assertThat(thrown).hasCauseInstanceOf(NullPointerException.class);
        }
    }
}
