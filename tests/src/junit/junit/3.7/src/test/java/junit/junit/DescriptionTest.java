/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.runner.Description;

public class DescriptionTest {
    @Test
    void resolvesTestClassFromStringBackedDescription() {
        Description description = Description.createTestDescription(ClassNameLookupTarget.class.getName(), "runs");

        assertThat(description.getClassName()).isEqualTo(ClassNameLookupTarget.class.getName());
        assertThat(description.getMethodName()).isEqualTo("runs");
        assertThat(description.getTestClass()).isSameAs(ClassNameLookupTarget.class);
    }

    public static final class ClassNameLookupTarget {
        private ClassNameLookupTarget() {
        }
    }
}
