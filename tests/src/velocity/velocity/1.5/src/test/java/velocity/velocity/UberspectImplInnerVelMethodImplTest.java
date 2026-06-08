/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.util.introspection.Info;
import org.apache.velocity.util.introspection.UberspectImpl;
import org.apache.velocity.util.introspection.VelMethod;
import org.junit.jupiter.api.Test;

public class UberspectImplInnerVelMethodImplTest {
    @Test
    void invokesDiscoveredMethodThroughVelocityIntrospection() throws Exception {
        final UberspectImpl uberspect = newUberspect();
        final GreetingService service = new GreetingService();
        final Info info = new Info("method.vm", 1, 1);

        final VelMethod method = uberspect.getMethod(
                service,
                "greet",
                new Object[] {"Velocity", Integer.valueOf(2)},
                info);

        assertThat(method).isNotNull();
        assertThat(method.getMethodName()).isEqualTo("greet");
        assertThat(method.getReturnType()).isEqualTo(String.class);
        assertThat(method.invoke(service, new Object[] {"Velocity", Integer.valueOf(2)}))
                .isEqualTo("Hello Velocity! Hello Velocity!");
    }

    private static UberspectImpl newUberspect() {
        final UberspectImpl uberspect = new UberspectImpl();
        uberspect.setLog(new Log(new NullLogChute()));
        uberspect.init();
        return uberspect;
    }

    public static final class GreetingService {
        public String greet(final String name, final Integer repetitions) {
            final StringBuilder builder = new StringBuilder();
            for (int i = 0; i < repetitions.intValue(); i++) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append("Hello ").append(name).append('!');
            }
            return builder.toString();
        }
    }
}
