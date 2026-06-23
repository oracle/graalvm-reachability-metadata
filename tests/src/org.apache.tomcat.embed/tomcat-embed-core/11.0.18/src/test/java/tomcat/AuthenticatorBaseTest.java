/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;

import jakarta.security.auth.message.AuthException;
import jakarta.security.auth.message.AuthStatus;
import jakarta.security.auth.message.MessageInfo;
import jakarta.security.auth.message.config.AuthConfigFactory;
import jakarta.security.auth.message.config.AuthConfigProvider;
import jakarta.security.auth.message.config.ClientAuthConfig;
import jakarta.security.auth.message.config.RegistrationListener;
import jakarta.security.auth.message.config.ServerAuthConfig;
import jakarta.security.auth.message.config.ServerAuthContext;
import jakarta.security.auth.message.module.ServerAuthModule;
import org.apache.catalina.authenticator.BasicAuthenticator;
import org.apache.catalina.authenticator.Constants;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticatorBaseTest {

    private static final String CALLBACK_HANDLER_CLASS =
            "org.apache.catalina.authenticator.jaspic.CallbackHandlerImpl";

    @Test
    void logoutCreatesJaspicCallbackHandlerThroughContextClassLoader() {
        assertLogoutCreatesCallbackHandler(AuthenticatorBaseTest.class.getClassLoader());
    }

    @Test
    void logoutCreatesJaspicCallbackHandlerThroughConfiguredClassName() {
        assertLogoutCreatesCallbackHandler(null);
    }

    private static void assertLogoutCreatesCallbackHandler(ClassLoader contextClassLoader) {
        synchronized (AuthConfigFactory.class) {
            AuthConfigFactory previousFactory = AuthConfigFactory.getFactory();
            AtomicBoolean cleanSubjectCalled = new AtomicBoolean();
            AuthConfigFactory.setFactory(new TestAuthConfigFactory(cleanSubjectCalled));

            ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            try {
                BasicAuthenticator authenticator = new BasicAuthenticator();
                authenticator.setJaspicCallbackHandlerClass(CALLBACK_HANDLER_CLASS);
                Request request = createRequest();
                request.setNote(Constants.REQ_JASPIC_SUBJECT_NOTE, new Subject());

                authenticator.logout(request);

                assertThat(cleanSubjectCalled).isTrue();
                assertThat(request.getUserPrincipal()).isNull();
                assertThat(request.getAuthType()).isNull();
            } finally {
                Thread.currentThread().setContextClassLoader(previousContextClassLoader);
                AuthConfigFactory.setFactory(previousFactory);
            }
        }
    }

    private static Request createRequest() {
        Connector connector = new Connector();
        org.apache.coyote.Request coyoteRequest = new org.apache.coyote.Request();
        org.apache.coyote.Response coyoteResponse = new org.apache.coyote.Response();
        Request request = new Request(connector, coyoteRequest);
        Response response = new Response(coyoteResponse);
        request.setResponse(response);
        response.setRequest(request);
        return request;
    }

    private static final class TestAuthConfigFactory extends AuthConfigFactory {

        private final ServerAuthConfig serverAuthConfig;

        private TestAuthConfigFactory(AtomicBoolean cleanSubjectCalled) {
            this.serverAuthConfig = new TestServerAuthConfig(cleanSubjectCalled);
        }

        @Override
        public AuthConfigProvider getConfigProvider(String layer, String appContext, RegistrationListener listener) {
            return new TestAuthConfigProvider(serverAuthConfig);
        }

        @Override
        public String registerConfigProvider(String className, Map<String, String> properties, String layer,
                String appContext, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String registerConfigProvider(AuthConfigProvider provider, String layer, String appContext,
                String description) {
            throw new UnsupportedOperationException();
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
            // No cached registration state.
        }

        @Override
        public String registerServerAuthModule(ServerAuthModule serverAuthModule, Object context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeServerAuthModule(Object context) {
            // No registered modules.
        }
    }

    private static final class TestAuthConfigProvider implements AuthConfigProvider {

        private final ServerAuthConfig serverAuthConfig;

        private TestAuthConfigProvider(ServerAuthConfig serverAuthConfig) {
            this.serverAuthConfig = serverAuthConfig;
        }

        @Override
        public ClientAuthConfig getClientAuthConfig(String layer, String appContext, CallbackHandler handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServerAuthConfig getServerAuthConfig(String layer, String appContext, CallbackHandler handler) {
            assertThat(handler).isNotNull();
            assertThat(handler.getClass().getName()).isEqualTo(CALLBACK_HANDLER_CLASS);
            return serverAuthConfig;
        }

        @Override
        public void refresh() {
            // No cached configuration state.
        }
    }

    private static final class TestServerAuthConfig implements ServerAuthConfig {

        private final ServerAuthContext serverAuthContext;

        private TestServerAuthConfig(AtomicBoolean cleanSubjectCalled) {
            this.serverAuthContext = new TestServerAuthContext(cleanSubjectCalled);
        }

        @Override
        public ServerAuthContext getAuthContext(String authContextID, Subject serviceSubject,
                Map<String, Object> properties) {
            return serverAuthContext;
        }

        @Override
        public String getMessageLayer() {
            return "HttpServlet";
        }

        @Override
        public String getAppContext() {
            return null;
        }

        @Override
        public String getAuthContextID(MessageInfo messageInfo) {
            return "test";
        }

        @Override
        public void refresh() {
            // No cached context state.
        }

        @Override
        public boolean isProtected() {
            return true;
        }
    }

    private static final class TestServerAuthContext implements ServerAuthContext {

        private final AtomicBoolean cleanSubjectCalled;

        private TestServerAuthContext(AtomicBoolean cleanSubjectCalled) {
            this.cleanSubjectCalled = cleanSubjectCalled;
        }

        @Override
        public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject, Subject serviceSubject)
                throws AuthException {
            return AuthStatus.SUCCESS;
        }

        @Override
        public void cleanSubject(MessageInfo messageInfo, Subject subject) {
            assertThat(messageInfo).isNotNull();
            assertThat(subject).isNotNull();
            cleanSubjectCalled.set(true);
        }
    }
}
