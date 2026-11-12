/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.naming.Reference;

import org.apache.naming.NamingContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NamingContextTest {
    @Test
    void resolvesBoundObjectFactoryReference() throws Exception {
        NamingContext context = new NamingContext(null, "application");
        try {
            context.bind("greeting", new Reference(String.class.getName(),
                    NamingSupport.GreetingFactory.class.getName(), null));
            assertThat(context.lookup("greeting")).isEqualTo("resolved-greeting");
        } finally {
            context.close();
        }
    }
}
