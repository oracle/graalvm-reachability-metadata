/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.manipulation.InvalidOrderingException;
import org.junit.runner.manipulation.Orderable;
import org.junit.runner.manipulation.Orderer;
import org.junit.runner.manipulation.Ordering;
import org.junit.runner.manipulation.Sorter;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgJunitRunnerManipulationOrderingTest {
    @Test
    void createsOrderingFromPublicFactoryClass() throws InvalidOrderingException {
        Description target = Description.createSuiteDescription("ordered-suite");
        ReversingOrderingFactory.lastTarget = null;

        Ordering ordering = Ordering.definedBy(ReversingOrderingFactory.class, target);
        RecordingOrderable orderable = new RecordingOrderable(
                Description.createTestDescription(FirstTestCase.class, "first"),
                Description.createTestDescription(SecondTestCase.class, "second"));

        ordering.apply(orderable);

        assertThat(ReversingOrderingFactory.lastTarget).isSameAs(target);
        assertThat(orderable.orderedDescriptions)
                .extracting(Description::getMethodName)
                .containsExactly("second", "first");
    }

    public static class ReversingOrderingFactory implements Ordering.Factory {
        private static Description lastTarget;

        public ReversingOrderingFactory() {
        }

        @Override
        public Ordering create(Ordering.Context context) {
            lastTarget = context.getTarget();
            return new Ordering() {
                @Override
                protected List<Description> orderItems(Collection<Description> descriptions) {
                    List<Description> ordered = new ArrayList<>(descriptions);
                    Collections.reverse(ordered);
                    return ordered;
                }
            };
        }
    }

    private static class RecordingOrderable implements Orderable {
        private final List<Description> descriptions;
        private List<Description> orderedDescriptions;

        RecordingOrderable(Description first, Description second) {
            descriptions = new ArrayList<>();
            descriptions.add(first);
            descriptions.add(second);
        }

        @Override
        public void order(Orderer orderer) throws InvalidOrderingException {
            orderedDescriptions = orderer.order(descriptions);
        }

        @Override
        public void sort(Sorter sorter) {
            descriptions.sort((left, right) -> sorter.compare(left, right));
        }
    }

    public static class FirstTestCase {
    }

    public static class SecondTestCase {
    }
}
