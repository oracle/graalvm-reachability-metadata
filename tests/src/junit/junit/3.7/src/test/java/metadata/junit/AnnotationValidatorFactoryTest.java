/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnotationValidatorFactoryTest {
    @Test
    public void createsValidatorDeclaredOnCategoryAnnotationDuringValidation() {
        Result result = new JUnitCore().run(CategorizedFixture.class);

        assertTrue(result.wasSuccessful(), result.getFailures().toString());
        assertEquals(1, result.getRunCount());
    }

    public interface FastCategory {
    }

    public static class CategorizedFixture {
        @Category(FastCategory.class)
        @org.junit.Test
        public void categorizedTest() {
        }
    }
}
