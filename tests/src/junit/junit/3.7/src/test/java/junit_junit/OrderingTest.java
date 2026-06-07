/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.manipulation.InvalidOrderingException;
import org.junit.runner.manipulation.Orderable;
import org.junit.runner.manipulation.Orderer;
import org.junit.runner.manipulation.Ordering;
import org.junit.runner.manipulation.Sorter;

public class OrderingTest {
    private static final AtomicInteger FACTORY_CONSTRUCTOR_CALLS = new AtomicInteger();
    private static final AtomicInteger FACTORY_CREATE_CALLS = new AtomicInteger();
    private static final AtomicReference<Description> FACTORY_TARGET = new AtomicReference<>();

    @Test
    void definedByInstantiatesFactoryClassAndUsesReturnedOrdering() throws InvalidOrderingException {
        FACTORY_CONSTRUCTOR_CALLS.set(0);
        FACTORY_CREATE_CALLS.set(0);
        FACTORY_TARGET.set(null);
        final Description targetDescription = Description.createSuiteDescription(OrderedCase.class);

        final Ordering ordering = Ordering.definedBy(RecordingOrderingFactory.class, targetDescription);
        final RecordingOrderable orderable = new RecordingOrderable(
                Description.createTestDescription(OrderedCase.class, "second"),
                Description.createTestDescription(OrderedCase.class, "first"));
        ordering.apply(orderable);

        assertThat(FACTORY_CONSTRUCTOR_CALLS).hasValue(1);
        assertThat(FACTORY_CREATE_CALLS).hasValue(1);
        assertThat(FACTORY_TARGET).hasValue(targetDescription);
        assertThat(orderable.orderedMethodNames()).containsExactly("first", "second");
    }

    public static final class RecordingOrderingFactory implements Ordering.Factory {
        public RecordingOrderingFactory() {
            FACTORY_CONSTRUCTOR_CALLS.incrementAndGet();
        }

        @Override
        public Ordering create(final Ordering.Context context) {
            FACTORY_CREATE_CALLS.incrementAndGet();
            FACTORY_TARGET.set(context.getTarget());
            return new Ordering() {
                @Override
                protected List<Description> orderItems(final Collection<Description> descriptions) {
                    final List<Description> orderedDescriptions = new ArrayList<>(descriptions);
                    orderedDescriptions.sort(Comparator.comparing(Description::getMethodName));
                    return orderedDescriptions;
                }
            };
        }
    }

    private static final class RecordingOrderable implements Orderable {
        private final List<Description> originalDescriptions;
        private List<Description> orderedDescriptions;

        RecordingOrderable(final Description... descriptions) {
            this.originalDescriptions = List.of(descriptions);
        }

        @Override
        public void order(final Orderer orderer) throws InvalidOrderingException {
            orderedDescriptions = orderer.order(originalDescriptions);
        }

        @Override
        public void sort(final Sorter sorter) {
            throw new AssertionError("Ordering.apply should not sort through the Sortable API");
        }

        List<String> orderedMethodNames() {
            return orderedDescriptions.stream()
                    .map(Description::getMethodName)
                    .toList();
        }
    }

    public static final class OrderedCase {
        @org.junit.Test
        public void first() {
        }

        @org.junit.Test
        public void second() {
        }
    }
}
