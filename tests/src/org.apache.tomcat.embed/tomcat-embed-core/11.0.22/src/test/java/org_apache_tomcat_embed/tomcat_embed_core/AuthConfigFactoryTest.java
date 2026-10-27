/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.security.Security;
import java.util.Map;

import jakarta.security.auth.message.config.AuthConfigFactory;
import jakarta.security.auth.message.config.AuthConfigProvider;
import jakarta.security.auth.message.config.RegistrationListener;
import jakarta.security.auth.message.module.ServerAuthModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthConfigFactoryTest {

    private static final String DEFAULT_FACTORY =
            "org.apache.catalina.authenticator.jaspic.AuthConfigFactoryImpl";

    @Test
    void createsFactorySelectedBySecurityProperty() {
        try {
            AuthConfigFactory.setFactory(null);
            Security.setProperty(AuthConfigFactory.DEFAULT_FACTORY_SECURITY_PROPERTY,
                    TestAuthConfigFactory.class.getName());

            assertThat(AuthConfigFactory.getFactory()).isInstanceOf(TestAuthConfigFactory.class);
        } finally {
            Security.setProperty(AuthConfigFactory.DEFAULT_FACTORY_SECURITY_PROPERTY, DEFAULT_FACTORY);
            AuthConfigFactory.setFactory(null);
        }
    }

    public static final class TestAuthConfigFactory extends AuthConfigFactory {
        public TestAuthConfigFactory() {
        }

        @Override
        public AuthConfigProvider getConfigProvider(String layer, String appContext,
                RegistrationListener listener) {
            return null;
        }

        @Override
        public String registerConfigProvider(String className, Map<String, String> properties, String layer,
                String appContext, String description) {
            return null;
        }

        @Override
        public String registerConfigProvider(AuthConfigProvider provider, String layer, String appContext,
                String description) {
            return null;
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

        @Override
        public String registerServerAuthModule(ServerAuthModule serverAuthModule, Object context) {
            return null;
        }

        @Override
        public void removeServerAuthModule(Object context) {
        }
    }
}
