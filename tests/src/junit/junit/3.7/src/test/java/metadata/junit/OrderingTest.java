/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.manipulation.InvalidOrderingException;
import org.junit.runner.manipulation.Orderable;
import org.junit.runner.manipulation.Orderer;
import org.junit.runner.manipulation.Ordering;
import org.junit.runner.manipulation.Sorter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class OrderingTest {
    @Test
    public void definedByFactoryClassCreatesOrderingWithNoArgumentConstructor() throws InvalidOrderingException {
        RecordingReverseOrderingFactory.target = null;
        Description target = Description.createSuiteDescription(OrderedFixture.class);

        Ordering ordering = Ordering.definedBy(RecordingReverseOrderingFactory.class, target);

        assertNotNull(ordering);
        assertSame(target, RecordingReverseOrderingFactory.target);

        Description first = Description.createTestDescription(OrderedFixture.class, "first");
        Description second = Description.createTestDescription(OrderedFixture.class, "second");
        RecordingOrderable orderable = new RecordingOrderable(Arrays.asList(first, second));

        ordering.apply(orderable);

        assertEquals(Arrays.asList("second", "first"), orderable.orderedMethodNames());
    }

    public static final class RecordingReverseOrderingFactory implements Ordering.Factory {
        private static Description target;

        public RecordingReverseOrderingFactory() {
        }

        @Override
        public Ordering create(Ordering.Context context) {
            target = context.getTarget();
            return new ReverseOrdering();
        }
    }

    private static final class ReverseOrdering extends Ordering {
        @Override
        protected List<Description> orderItems(Collection<Description> descriptions) {
            List<Description> orderedDescriptions = new ArrayList<>(descriptions);
            Collections.reverse(orderedDescriptions);
            return orderedDescriptions;
        }
    }

    private static final class RecordingOrderable implements Orderable {
        private final List<Description> descriptions;
        private List<Description> orderedDescriptions = Collections.emptyList();

        private RecordingOrderable(List<Description> descriptions) {
            this.descriptions = descriptions;
        }

        @Override
        public void order(Orderer orderer) throws InvalidOrderingException {
            orderedDescriptions = orderer.order(descriptions);
        }

        @Override
        public void sort(Sorter sorter) {
            throw new AssertionError("Ordering.apply must use order(Orderer)");
        }

        private List<String> orderedMethodNames() {
            List<String> methodNames = new ArrayList<>();
            for (Description description : orderedDescriptions) {
                methodNames.add(description.getMethodName());
            }
            return methodNames;
        }
    }

    private static final class OrderedFixture {
        @org.junit.Test
        public void first() {
        }

        @org.junit.Test
        public void second() {
        }
    }
}
