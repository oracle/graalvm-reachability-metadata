/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import net.sf.cglib.transform.NullClassVisitor;
import net.sf.cglib.util.ParallelSorter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class ParallelSorterTest {
    @Test
    void requiresAtLeastOneArrayColumn() {
        ParallelSorter.Generator generator = new ParallelSorter.Generator();
        generator.setArrays(new Object[0]);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> generator.generateClass(NullClassVisitor.INSTANCE))
                .withMessage("No arrays specified to sort");
    }

    @Test
    void requiresEveryColumnToBeAnArray() {
        ParallelSorter.Generator generator = new ParallelSorter.Generator();
        generator.setArrays(new Object[]{new String[]{"alpha", "beta"}, "not an array"});

        assertThatIllegalArgumentException()
                .isThrownBy(() -> generator.generateClass(NullClassVisitor.INSTANCE))
                .withMessageContaining("is not an array");
    }
}
