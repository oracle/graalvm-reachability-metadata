/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_authentication.jakarta_authentication_api;

import java.security.Security;
import java.util.Map;

import jakarta.security.auth.message.config.AuthConfigFactory;
import jakarta.security.auth.message.config.AuthConfigProvider;
import jakarta.security.auth.message.config.RegistrationListener;
import jakarta.security.auth.message.module.ServerAuthModule;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthConfigFactoryAnonymous1Test {
    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    private String originalFactoryClassName;

    @BeforeEach
    void resetFactory() {
        originalFactoryClassName = Security.getProperty(AuthConfigFactory.DEFAULT_FACTORY_SECURITY_PROPERTY);
        AuthConfigFactory.setFactory(null);
        currentThread.setContextClassLoader(getClass().getClassLoader());
    }

    @AfterEach
    void restoreFactory() {
        AuthConfigFactory.setFactory(null);
        currentThread.setContextClassLoader(originalContextClassLoader);
        Security.setProperty(
                AuthConfigFactory.DEFAULT_FACTORY_SECURITY_PROPERTY,
                originalFactoryClassName == null ? "" : originalFactoryClassName);
    }

    @Test
    void getFactoryInstantiatesDefaultFactoryFromSecurityProperty() {
        Security.setProperty(
                AuthConfigFactory.DEFAULT_FACTORY_SECURITY_PROPERTY,
                TestAuthConfigFactory.class.getName());

        AuthConfigFactory factory = AuthConfigFactory.getFactory();

        assertThat(factory).isInstanceOf(TestAuthConfigFactory.class);
        assertThat(AuthConfigFactory.getFactory()).isSameAs(factory);
    }

    public static final class TestAuthConfigFactory extends AuthConfigFactory {
        public TestAuthConfigFactory() {
        }

        @Override
        public AuthConfigProvider getConfigProvider(String layer, String appContext, RegistrationListener listener) {
            return null;
        }

        @Override
        public String registerConfigProvider(
                String className,
                Map<String, String> properties,
                String layer,
                String appContext,
                String description) {
            return "class-registration";
        }

        @Override
        public String registerConfigProvider(
                AuthConfigProvider provider,
                String layer,
                String appContext,
                String description) {
            return "instance-registration";
        }

        @Override
        public String registerServerAuthModule(ServerAuthModule serverAuthModule, Object context) {
            return "server-auth-module-registration";
        }

        @Override
        public void removeServerAuthModule(Object context) {
        }

        @Override
        public boolean removeRegistration(String registrationID) {
            return false;
        }

        @Override
        public String[] detachListener(RegistrationListener listener, String layer, String appContext) {
            return new String[0];
        }

        @Override
        public String[] getRegistrationIDs(AuthConfigProvider provider) {
            return new String[0];
        }

        @Override
        public RegistrationContext getRegistrationContext(String registrationID) {
            return null;
        }

        @Override
        public void refresh() {
        }
    }
}
