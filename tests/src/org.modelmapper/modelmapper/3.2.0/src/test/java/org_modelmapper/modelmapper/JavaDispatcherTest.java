/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Permission;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.utility.dispatcher.JavaDispatcher;

public class JavaDispatcherTest {
    @Test
    void createsProxyWithDefaultDispatchersWhenProxiedTypeIsUnavailable() {
        MissingTypeDispatcher dispatcher = new ConfigurableJavaDispatcher<>(
            MissingTypeDispatcher.class,
            false).run();

        assertThat(dispatcher.text()).isNull();
        assertThat(dispatcher.number()).isZero();
    }

    @Test
    void inspectsProxyMethodsBeforeGeneratingDispatcherForUnavailableType() {
        try {
            MissingTypeDispatcher dispatcher = new ConfigurableJavaDispatcher<>(
                MissingTypeDispatcher.class,
                true).run();

            assertThat(dispatcher.text()).isNull();
            assertThat(dispatcher.number()).isZero();
        } catch (RuntimeException exception) {
            assertThat(exception).isNotInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @SuppressWarnings("removal")
    void checksInstalledSecurityManagerBeforeCreatingDispatcher() {
        SecurityManager previous = System.getSecurityManager();
        RecordingSecurityManager securityManager = new RecordingSecurityManager();
        boolean installed = false;
        try {
            try {
                System.setSecurityManager(securityManager);
                installed = true;
            } catch (UnsupportedOperationException ignored) {
                installed = false;
            }

            MissingTypeDispatcher dispatcher = new ConfigurableJavaDispatcher<>(
                MissingTypeDispatcher.class,
                false).run();

            assertThat(dispatcher.number()).isZero();
            if (installed) {
                assertThat(securityManager.checkedJavaDispatcherPermission).isTrue();
            }
        } finally {
            if (installed) {
                System.setSecurityManager(previous);
            }
        }
    }

    @JavaDispatcher.Defaults
    @JavaDispatcher.Proxied("org.modelmapper.dynamicaccess.MissingDispatcherTarget")
    public interface MissingTypeDispatcher {
        String text();

        int number();
    }

    private static final class ConfigurableJavaDispatcher<T> extends JavaDispatcher<T> {
        private ConfigurableJavaDispatcher(Class<T> proxy, boolean generate) {
            super(proxy, JavaDispatcherTest.class.getClassLoader(), generate);
        }
    }

    @SuppressWarnings("removal")
    private static final class RecordingSecurityManager extends SecurityManager {
        private boolean checkedJavaDispatcherPermission;

        @Override
        public void checkPermission(Permission permission) {
            if ("org.modelmapper.internal.bytebuddy.createJavaDispatcher".equals(permission.getName())) {
                checkedJavaDispatcherPermission = true;
            }
        }
    }
}
