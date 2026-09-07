/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.runner.Description;

public class DescriptionTest {

    @Test
    void resolvesTheTestClassFromAClassNamedDescription() {
        Description description = Description.createTestDescription(Fixture.class.getName(), "execute");

        assertThat(description.getTestClass()).isEqualTo(Fixture.class);
        assertThat(description.getMethodName()).isEqualTo("execute");
    }

    public static class Fixture {
    }
}
