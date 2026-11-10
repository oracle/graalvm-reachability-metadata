/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;

import jakarta.security.auth.message.AuthStatus;
import jakarta.security.auth.message.MessageInfo;
import jakarta.security.auth.message.MessagePolicy;
import jakarta.security.auth.message.config.ServerAuthContext;
import jakarta.security.auth.message.module.ServerAuthModule;
import org.apache.catalina.authenticator.jaspic.MessageInfoImpl;
import org.apache.catalina.authenticator.jaspic.SimpleServerAuthConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SimpleServerAuthConfigTest {
    @Test
    void loadsModuleAndAppliesItsAuthenticationAndCleanup() throws Exception {
        Map<String, Object> properties = Map.of(
                "org.apache.catalina.authenticator.jaspic.ServerAuthModule.1", ApplicationModule.class.getName(),
                "application", "catalog");
        SimpleServerAuthConfig config = new SimpleServerAuthConfig("HttpServlet", "app", null, properties);
        ServerAuthContext context = config.getAuthContext("request", null, Map.of());
        MessageInfo info = new MessageInfoImpl();
        Subject client = new Subject();
        assertEquals(AuthStatus.SUCCESS, context.validateRequest(info, client, null));
        assertEquals("catalog", client.getPublicCredentials().iterator().next());
        assertEquals(AuthStatus.SEND_SUCCESS, context.secureResponse(info, null));
        context.cleanSubject(info, client);
        assertEquals(0, client.getPublicCredentials().size());
        assertSame(context, config.getAuthContext("request", null, Map.of()));
        config.refresh();
        assertNotSame(context, config.getAuthContext("request", null, Map.of()));
    }

    public static class ApplicationModule implements ServerAuthModule {
        private String application;

        @Override
        public void initialize(MessagePolicy request, MessagePolicy response, CallbackHandler handler,
                Map<String, Object> options) {
            application = (String) options.get("application");
        }

        @Override
        public Class<?>[] getSupportedMessageTypes() {
            return new Class<?>[] {Object.class};
        }

        @Override
        public AuthStatus validateRequest(MessageInfo info, Subject client, Subject service) {
            client.getPublicCredentials().add(application);
            return AuthStatus.SUCCESS;
        }

        @Override
        public AuthStatus secureResponse(MessageInfo info, Subject service) {
            return AuthStatus.SEND_SUCCESS;
        }

        @Override
        public void cleanSubject(MessageInfo info, Subject subject) {
            subject.getPublicCredentials().clear();
        }
    }
}
