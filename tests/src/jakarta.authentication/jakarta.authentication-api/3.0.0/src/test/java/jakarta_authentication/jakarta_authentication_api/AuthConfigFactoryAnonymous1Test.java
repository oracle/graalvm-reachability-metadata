/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_authentication.jakarta_authentication_api;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.security.Security;
import java.util.Map;

import jakarta.security.auth.message.config.AuthConfigFactory;
import jakarta.security.auth.message.config.AuthConfigProvider;
import jakarta.security.auth.message.config.RegistrationListener;
import jakarta.security.auth.message.module.ServerAuthModule;
import org.junit.jupiter.api.Test;

public class AuthConfigFactoryAnonymous1Test extends AuthConfigFactory {
    private static final String FACTORY_CLASS_NAME = AuthConfigFactoryAnonymous1Test.class.getName();

    public AuthConfigFactoryAnonymous1Test() {
    }

    @Test
    void getFactoryLoadsDefaultFactoryFromSecurityProperty() {
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        String previousFactoryProperty = Security.getProperty(DEFAULT_FACTORY_SECURITY_PROPERTY);
        AuthConfigFactory factory = null;
        AuthConfigFactory.setFactory(null);
        Security.setProperty(DEFAULT_FACTORY_SECURITY_PROPERTY, FACTORY_CLASS_NAME);
        Thread.currentThread().setContextClassLoader(AuthConfigFactoryAnonymous1Test.class.getClassLoader());

        try {
            factory = AuthConfigFactory.getFactory();

            assertInstanceOf(AuthConfigFactoryAnonymous1Test.class, factory);
            assertSame(factory, AuthConfigFactory.getFactory());
        } finally {
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            if (previousFactoryProperty != null) {
                Security.setProperty(DEFAULT_FACTORY_SECURITY_PROPERTY, previousFactoryProperty);
                AuthConfigFactory.setFactory(null);
            } else if (factory != null) {
                AuthConfigFactory.setFactory(factory);
            }
        }
    }

    @Override
    public AuthConfigProvider getConfigProvider(String layer, String appContext, RegistrationListener listener) {
        throw new UnsupportedOperationException("The reflective factory creation test does not register providers.");
    }

    @Override
    public String registerConfigProvider(String className, Map<String, String> properties, String layer,
            String appContext, String description) {
        throw new UnsupportedOperationException("The reflective factory creation test does not register providers.");
    }

    @Override
    public String registerConfigProvider(AuthConfigProvider provider, String layer, String appContext,
            String description) {
        throw new UnsupportedOperationException("The reflective factory creation test does not register providers.");
    }

    @Override
    public String registerServerAuthModule(ServerAuthModule serverAuthModule, Object context) {
        throw new UnsupportedOperationException("The reflective factory creation test does not register modules.");
    }

    @Override
    public void removeServerAuthModule(Object context) {
        throw new UnsupportedOperationException("The reflective factory creation test does not register modules.");
    }

    @Override
    public boolean removeRegistration(String registrationID) {
        throw new UnsupportedOperationException("The reflective factory creation test does not register providers.");
    }

    @Override
    public String[] detachListener(RegistrationListener listener, String layer, String appContext) {
        throw new UnsupportedOperationException("The reflective factory creation test does not register listeners.");
    }

    @Override
    public String[] getRegistrationIDs(AuthConfigProvider provider) {
        throw new UnsupportedOperationException("The reflective factory creation test does not register providers.");
    }

    @Override
    public RegistrationContext getRegistrationContext(String registrationID) {
        throw new UnsupportedOperationException("The reflective factory creation test does not register providers.");
    }

    @Override
    public void refresh() {
        throw new UnsupportedOperationException("The reflective factory creation test does not persist providers.");
    }
}
