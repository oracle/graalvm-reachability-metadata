/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;

import jakarta.security.auth.message.AuthException;
import jakarta.security.auth.message.AuthStatus;
import jakarta.security.auth.message.MessageInfo;
import jakarta.security.auth.message.MessagePolicy;
import jakarta.security.auth.message.config.ServerAuthContext;
import jakarta.security.auth.message.module.ServerAuthModule;
import org.apache.catalina.authenticator.jaspic.SimpleServerAuthConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleServerAuthConfigTest {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    @Test
    void createsAndInitializesConfiguredServerAuthModule() throws Exception {
        INITIALIZED.set(false);
        Map<String, Object> properties = Map.of(
                "org.apache.catalina.authenticator.jaspic.ServerAuthModule.1", TestServerAuthModule.class.getName());
        SimpleServerAuthConfig config = new SimpleServerAuthConfig("HttpServlet", "localhost /app", null, properties);

        ServerAuthContext context = config.getAuthContext("request", null, Map.of("test", "value"));

        assertThat(context).isNotNull();
        assertThat(INITIALIZED).isTrue();
        assertThat(config.getMessageLayer()).isEqualTo("HttpServlet");
        assertThat(config.getAppContext()).isEqualTo("localhost /app");
    }

    public static final class TestServerAuthModule implements ServerAuthModule {

        public TestServerAuthModule() {
        }

        @Override
        public void initialize(MessagePolicy requestPolicy, MessagePolicy responsePolicy, CallbackHandler handler,
                Map<String, Object> options) {
            INITIALIZED.set("value".equals(options.get("test")));
        }

        @Override
        public Class<?>[] getSupportedMessageTypes() {
            return new Class<?>[] {Object.class };
        }

        @Override
        public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject, Subject serviceSubject) {
            return AuthStatus.SUCCESS;
        }

        @Override
        public AuthStatus secureResponse(MessageInfo messageInfo, Subject serviceSubject) {
            return AuthStatus.SUCCESS;
        }

        @Override
        public void cleanSubject(MessageInfo messageInfo, Subject subject) throws AuthException {
            subject.getPrincipals().clear();
        }
    }
}
