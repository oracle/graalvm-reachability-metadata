/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration.interpol;

import static org.assertj.core.api.Assertions.assertThat;

import commons_configuration.commons_configuration.DefaultBeanFactoryTest;

import org.apache.commons.configuration.interpol.ExprLookup;
import org.junit.jupiter.api.Test;

public class ExprLookupInnerVariableTest {
    @Test
    public void setValueInstantiatesClassNamedByString() {
        ExprLookup.Variable variable = new ExprLookup.Variable("testBean", DefaultBeanFactoryTest.class.getName());

        Object value = variable.getValue();

        assertThat(value).isInstanceOf(DefaultBeanFactoryTest.class);
    }
}
