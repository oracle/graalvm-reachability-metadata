/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.security.auth.callback.CallbackHandler;

import jakarta.security.auth.message.config.AuthConfigFactory;
import jakarta.security.auth.message.config.AuthConfigProvider;
import jakarta.security.auth.message.config.ServerAuthConfig;
import org.apache.catalina.Globals;
import org.apache.catalina.authenticator.jaspic.AuthConfigFactoryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthConfigFactoryImplTest {

    private static final String SIMPLE_PROVIDER_CLASS_NAME =
            "org.apache.catalina.authenticator.jaspic.SimpleAuthConfigProvider";

    @TempDir
    Path catalinaBase;

    private String previousCatalinaBase;
    private ClassLoader previousContextClassLoader;

    @BeforeEach
    void configureIsolatedCatalinaBase() throws IOException {
        previousCatalinaBase = System.getProperty(Globals.CATALINA_BASE_PROP);
        previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        System.setProperty(Globals.CATALINA_BASE_PROP, catalinaBase.toString());
        Files.createDirectories(catalinaBase.resolve("conf"));
    }

    @AfterEach
    void restoreThreadAndSystemState() {
        Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        if (previousCatalinaBase == null) {
            System.clearProperty(Globals.CATALINA_BASE_PROP);
        } else {
            System.setProperty(Globals.CATALINA_BASE_PROP, previousCatalinaBase);
        }
    }

    @Test
    void registersNamedAuthConfigProviderThroughContextClassLoader() throws Exception {
        Thread.currentThread().setContextClassLoader(AuthConfigFactoryImplTest.class.getClassLoader());
        assertRegistersNamedAuthConfigProvider();
    }

    @Test
    void registersNamedAuthConfigProviderThroughFactoryClassLoadingFallback() throws Exception {
        Thread.currentThread().setContextClassLoader(new RejectingClassLoader(SIMPLE_PROVIDER_CLASS_NAME));
        assertRegistersNamedAuthConfigProvider();
    }

    private static void assertRegistersNamedAuthConfigProvider() throws Exception {
        AuthConfigFactoryImpl factory = new AuthConfigFactoryImpl();

        String registrationId = factory.registerConfigProvider(SIMPLE_PROVIDER_CLASS_NAME,
                Map.of("testProperty", "testValue"), "HttpServlet", "localhost /test", "test provider");

        try {
            AuthConfigProvider provider = factory.getConfigProvider("HttpServlet", "localhost /test", null);
            AuthConfigFactory.RegistrationContext registrationContext = factory.getRegistrationContext(registrationId);

            assertThat(provider.getClass().getName()).isEqualTo(SIMPLE_PROVIDER_CLASS_NAME);
            assertThat(registrationContext.getMessageLayer()).isEqualTo("HttpServlet");
            assertThat(registrationContext.getAppContext()).isEqualTo("localhost /test");
            assertThat(registrationContext.getDescription()).isEqualTo("test provider");
            assertThat(registrationContext.isPersistent()).isTrue();

            CallbackHandler callbackHandler = callbacks -> {
                // The simple configuration stores the handler without invoking it during creation.
            };
            ServerAuthConfig serverAuthConfig = provider.getServerAuthConfig("HttpServlet", "localhost /test",
                    callbackHandler);

            assertThat(serverAuthConfig.getMessageLayer()).isEqualTo("HttpServlet");
            assertThat(serverAuthConfig.getAppContext()).isEqualTo("localhost /test");
            assertThat(serverAuthConfig.isProtected()).isFalse();
        } finally {
            factory.removeRegistration(registrationId);
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {

        private final String rejectedClassName;

        private RejectingClassLoader(String rejectedClassName) {
            super(null);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
