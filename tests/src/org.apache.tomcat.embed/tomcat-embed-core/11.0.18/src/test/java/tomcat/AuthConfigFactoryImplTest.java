/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.Map;

import org.apache.catalina.authenticator.jaspic.AuthConfigFactoryImpl;
import org.apache.catalina.authenticator.jaspic.SimpleAuthConfigProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthConfigFactoryImplTest {

    @Test
    void registersProviderByClassName() {
        AuthConfigFactoryImpl factory = new AuthConfigFactoryImpl();

        String registrationId = factory.registerConfigProvider(
                SimpleAuthConfigProvider.class.getName(), Map.of(), "HttpServlet", "app", "description");

        assertThat(registrationId).isNotBlank();
        assertThat(factory.getConfigProvider("HttpServlet", "app", null)).isInstanceOf(SimpleAuthConfigProvider.class);
    }
}
