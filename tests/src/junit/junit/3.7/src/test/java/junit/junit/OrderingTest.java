/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.manipulation.InvalidOrderingException;
import org.junit.runner.manipulation.Orderable;
import org.junit.runner.manipulation.Orderer;
import org.junit.runner.manipulation.Ordering;
import org.junit.runner.manipulation.Sorter;

public class OrderingTest {
    @Test
    void definedByInstantiatesPublicFactoryClass() throws Exception {
        Description target = Description.createSuiteDescription("reverse method ordering");
        Ordering ordering = Ordering.definedBy(MethodNameOrderingFactory.class, target);
        RecordingOrderable orderable = new RecordingOrderable(List.of(
                testDescription("alpha"),
                testDescription("charlie"),
                testDescription("bravo")));

        ordering.apply(orderable);

        assertThat(orderable.methodNames()).containsExactly("charlie", "bravo", "alpha");
    }

    public static final class MethodNameOrderingFactory implements Ordering.Factory {
        public MethodNameOrderingFactory() {
        }

        @Override
        public Ordering create(Ordering.Context context) {
            boolean reverse = context.getTarget().getDisplayName().contains("reverse");
            return new MethodNameOrdering(reverse);
        }
    }

    private static final class MethodNameOrdering extends Ordering {
        private final boolean reverse;

        private MethodNameOrdering(boolean reverse) {
            this.reverse = reverse;
        }

        @Override
        protected List<Description> orderItems(Collection<Description> descriptions) {
            Comparator<Description> comparator = Comparator.comparing(Description::getMethodName);
            if (reverse) {
                comparator = comparator.reversed();
            }
            List<Description> orderedDescriptions = new ArrayList<>(descriptions);
            orderedDescriptions.sort(comparator);
            return orderedDescriptions;
        }
    }

    private static final class RecordingOrderable implements Orderable {
        private List<Description> descriptions;

        private RecordingOrderable(List<Description> descriptions) {
            this.descriptions = new ArrayList<>(descriptions);
        }

        @Override
        public void order(Orderer orderer) throws InvalidOrderingException {
            descriptions = orderer.order(descriptions);
        }

        @Override
        public void sort(Sorter sorter) {
            descriptions.sort(sorter);
        }

        private List<String> methodNames() {
            return descriptions.stream()
                    .map(Description::getMethodName)
                    .toList();
        }
    }

    private static Description testDescription(String methodName) {
        return Description.createTestDescription(OrderingTarget.class, methodName);
    }

    private static final class OrderingTarget {
    }
}
