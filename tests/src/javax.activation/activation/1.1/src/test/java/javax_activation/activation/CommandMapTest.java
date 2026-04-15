/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax.activation;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandMapTest {
    @Test
    void classDollarLoadsCommandMapByName() throws Exception {
        Method classLookupMethod = CommandMap.class.getDeclaredMethod("class$", String.class);

        assertThat(classLookupMethod.invoke(null, "javax.activation.CommandMap")).isSameAs(CommandMap.class);
    }
}
