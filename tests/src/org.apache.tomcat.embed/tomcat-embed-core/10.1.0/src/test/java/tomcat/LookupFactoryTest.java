/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.naming.StringRefAddr;

import org.apache.naming.LookupRef;
import org.apache.naming.factory.LookupFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LookupFactoryTest {
    @Test
    void resolvesAndTypeChecksFactoryResult() throws Exception {
        LookupRef reference = new LookupRef(String.class.getName(), null, null, null);
        reference.add(new StringRefAddr("factory", NamingSupport.GreetingFactory.class.getName()));
        assertThat(new LookupFactory().getObjectInstance(reference, null, null, null))
                .isEqualTo("resolved-greeting");
    }
}
