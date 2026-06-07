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
    void resolvesTestClassFromStringBasedDescription() {
        Description description = Description.createTestDescription(
                DescribedJUnitCase.class.getName(),
                "passes");

        assertThat(description.getDisplayName()).isEqualTo("passes(" + DescribedJUnitCase.class.getName() + ")");
        assertThat(description.getMethodName()).isEqualTo("passes");
        assertThat(description.getClassName()).isEqualTo(DescribedJUnitCase.class.getName());
        assertThat(description.getTestClass()).isEqualTo(DescribedJUnitCase.class);
    }

    public static final class DescribedJUnitCase {
        @org.junit.Test
        public void passes() {
        }
    }
}
