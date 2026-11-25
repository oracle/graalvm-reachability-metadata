/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.math.BigInteger;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.core.NamingContextListener;
import org.apache.catalina.core.StandardServer;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NamingContextListenerTest {
    @Test
    void constructsTypedEnvironmentEntryFromStringValue() throws Exception {
        StandardServer server = new StandardServer();
        ContextEnvironment environment = new ContextEnvironment();
        environment.setName("maximum");
        environment.setType(BigInteger.class.getName());
        environment.setValue("12345678901234567890");
        server.getGlobalNamingResources().addEnvironment(environment);
        NamingContextListener listener = new NamingContextListener();
        listener.setName("typed-environment");
        listener.lifecycleEvent(new LifecycleEvent(server, Lifecycle.CONFIGURE_START_EVENT, null));
        try {
            assertThat(server.getGlobalNamingContext().lookup("maximum"))
                    .isEqualTo(new BigInteger("12345678901234567890"));
        } finally {
            listener.lifecycleEvent(new LifecycleEvent(server, Lifecycle.CONFIGURE_STOP_EVENT, null));
        }
    }
}
