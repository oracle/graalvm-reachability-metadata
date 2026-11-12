/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.naming.StringRefAddr;

import org.apache.naming.ResourceRef;
import org.apache.naming.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryBaseTest {
    @Test
    void delegatesResourceCreationToConfiguredFactory() throws Exception {
        ResourceRef reference = new ResourceRef(String.class.getName(), null, null, null, true);
        reference.add(new StringRefAddr("factory", NamingSupport.GreetingFactory.class.getName()));
        assertThat(new ResourceFactory().getObjectInstance(reference, null, null, null))
                .isEqualTo("resolved-greeting");
    }
}
