/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import org.junit.jupiter.api.Test;
import org.junit.runner.Description;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DescriptionTest {
    @Test
    public void resolvesTestClassFromStringBasedDescription() {
        Description description = Description.createTestDescription(
                NamedDescriptionFixture.class.getName(), "sampleTest");

        assertEquals(NamedDescriptionFixture.class.getName(), description.getClassName());
        assertEquals("sampleTest", description.getMethodName());
        assertSame(NamedDescriptionFixture.class, description.getTestClass());
    }

    public static final class NamedDescriptionFixture {
        @org.junit.Test
        public void sampleTest() {
        }
    }
}
