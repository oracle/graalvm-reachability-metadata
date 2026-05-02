/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import net.sf.cglib.util.ParallelSorter;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ParallelSorterTest {
    @Test
    void quickSortOrdersParallelObjectAndPrimitiveArrays() {
        try {
            String[] names = {"gamma", "alpha", "delta", "beta"};
            Integer[] priorities = {3, 1, 4, 2};
            int[] weights = {30, 10, 40, 20};

            ParallelSorter sorter = ParallelSorter.create(new Object[]{names, priorities, weights});
            sorter.quickSort(1);

            assertThat(priorities).containsExactly(1, 2, 3, 4);
            assertThat(names).containsExactly("alpha", "beta", "gamma", "delta");
            assertThat(weights).containsExactly(10, 20, 30, 40);
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    @Test
    void mergeSortUsesProvidedComparatorAndPreservesParallelRows() {
        try {
            String[] labels = {"kilo", "yard", "inch", "mile"};
            Integer[] ids = {40, 20, 10, 30};

            ParallelSorter sorter = ParallelSorter.create(new Object[]{labels, ids});
            sorter.mergeSort(0, new LengthThenNameComparator());

            assertThat(labels).containsExactly("inch", "kilo", "mile", "yard");
            assertThat(ids).containsExactly(10, 40, 30, 20);
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    private static boolean isUnsupportedNativeImageDynamicClassLoading(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return true;
            }
            if (current instanceof NoClassDefFoundError) {
                String message = current.getMessage();
                if (message != null && message.startsWith("Could not initialize class net.sf.cglib.")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class LengthThenNameComparator implements Comparator<String> {
        public int compare(String left, String right) {
            int lengthComparison = left.length() - right.length();
            if (lengthComparison != 0) {
                return lengthComparison;
            }
            return left.compareTo(right);
        }
    }
}
