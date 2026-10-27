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

    @Test
    void createsAndInitializesConfiguredServerAuthModule() throws Exception {
        TestServerAuthModule.INITIALIZED.set(false);
        String key = "org.apache.catalina.authenticator.jaspic.ServerAuthModule.1";
        SimpleServerAuthConfig config = new SimpleServerAuthConfig("HttpServlet", "localhost /app",
                callbacks -> { }, Map.of(key, TestServerAuthModule.class.getName()));

        ServerAuthContext context = config.getAuthContext("request", new Subject(), Map.of("setting", "value"));

        assertThat(context).isNotNull();
        assertThat(TestServerAuthModule.INITIALIZED).isTrue();
        assertThat(config.getMessageLayer()).isEqualTo("HttpServlet");
        assertThat(config.getAppContext()).isEqualTo("localhost /app");
    }

    public static class TestServerAuthModule implements ServerAuthModule {
        static final AtomicBoolean INITIALIZED = new AtomicBoolean();

        @Override
        public void initialize(MessagePolicy requestPolicy, MessagePolicy responsePolicy, CallbackHandler handler,
                Map<String, Object> options) {
            INITIALIZED.set("value".equals(options.get("setting")));
        }

        @Override
        public Class<?>[] getSupportedMessageTypes() {
            return new Class<?>[] {Object.class};
        }

        @Override
        public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject, Subject serviceSubject)
                throws AuthException {
            return AuthStatus.SUCCESS;
        }
    }
}
