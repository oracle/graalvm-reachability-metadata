/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.naming.StringRefAddr;

import org.apache.naming.LookupRef;
import org.apache.naming.factory.BeanFactory;
import org.apache.naming.factory.Constants;
import org.apache.naming.factory.LookupFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LookupFactoryTest {

    @Test
    void usesConfiguredFactoryAndChecksReturnedType() throws Exception {
        LookupRef reference = new LookupRef(String.class.getName(), null);
        reference.add(new StringRefAddr(Constants.FACTORY, BeanFactory.class.getName()));

        Object value = new LookupFactory().getObjectInstance(reference, null, null, null);

        assertThat(value).isNull();
    }
}
