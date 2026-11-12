/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.HashMap;
import java.util.Map;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;

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
    void loadsConfiguredAuthenticationModuleAndExecutesItsLifecycle() throws Exception {
        SimpleServerAuthConfig configuration = new SimpleServerAuthConfig("HttpServlet", "catalog", null,
                Map.of("org.apache.catalina.authenticator.jaspic.ServerAuthModule.1", TokenModule.class.getName(),
                        "token", "valid-token"));
        ServerAuthContext context = configuration.getAuthContext("catalog", null, Map.of());
        Message message = new Message();
        message.setRequestMessage("valid-token");
        Subject subject = new Subject();
        assertThat(context.validateRequest(message, subject, null)).isEqualTo(AuthStatus.SUCCESS);
        assertThat(message.getMap()).containsEntry("authenticated", Boolean.TRUE);
        assertThat(context.secureResponse(message, subject)).isEqualTo(AuthStatus.SEND_SUCCESS);
        assertThat(message.getResponseMessage()).isEqualTo("secured");
        context.cleanSubject(message, subject);
        assertThat(message.getMap()).doesNotContainKey("authenticated");
    }

    public static class TokenModule implements ServerAuthModule {
        private Object token;

        @Override
        public void initialize(MessagePolicy requestPolicy, MessagePolicy responsePolicy, CallbackHandler handler,
                Map<String, Object> options) {
            token = options.get("token");
        }

        @Override
        public Class<?>[] getSupportedMessageTypes() {
            return new Class<?>[] {String.class};
        }

        @Override
        public AuthStatus validateRequest(MessageInfo info, Subject client, Subject service) {
            if (!token.equals(info.getRequestMessage())) {
                return AuthStatus.SEND_FAILURE;
            }
            info.getMap().put("authenticated", Boolean.TRUE);
            return AuthStatus.SUCCESS;
        }

        @Override
        public AuthStatus secureResponse(MessageInfo info, Subject service) {
            info.setResponseMessage("secured");
            return AuthStatus.SEND_SUCCESS;
        }

        @Override
        public void cleanSubject(MessageInfo info, Subject subject) {
            info.getMap().remove("authenticated");
        }
    }

    public static class Message implements MessageInfo {
        private final Map<String, Object> attributes = new HashMap<>();
        private Object request;
        private Object response;

        @Override
        public Object getRequestMessage() {
            return request;
        }

        @Override
        public Object getResponseMessage() {
            return response;
        }

        @Override
        public void setRequestMessage(Object request) {
            this.request = request;
        }

        @Override
        public void setResponseMessage(Object response) {
            this.response = response;
        }

        @Override
        public Map<String, Object> getMap() {
            return attributes;
        }
    }
}
