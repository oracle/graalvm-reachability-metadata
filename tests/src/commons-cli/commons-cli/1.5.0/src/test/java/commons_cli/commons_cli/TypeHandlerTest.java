/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_cli.commons_cli;

import java.util.ArrayList;

import org.apache.commons.cli.TypeHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TypeHandlerTest {
    @Test
    void createClassLoadsTheRequestedClass() throws Exception {
        assertThat(TypeHandler.createClass("java.lang.String")).isSameAs(String.class);
    }

    @Test
    void createObjectInstantiatesClassesWithPublicNoArgConstructors() throws Exception {
        Object object = TypeHandler.createObject("java.util.ArrayList");

        assertThat(object).isInstanceOf(ArrayList.class);
        assertThat((ArrayList<?>) object).isEmpty();
    }
}
