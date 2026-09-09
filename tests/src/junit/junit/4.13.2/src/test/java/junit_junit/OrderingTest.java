/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.OrderWith;
import org.junit.runner.Result;
import org.junit.runner.manipulation.Ordering;

public class OrderingTest {

    @Test
    void instantiatesAnOrderingFactoryDeclaredOnATestClass() {
        Result result = JUnitCore.runClasses(OrderedFixture.class);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(result.getRunCount()).isEqualTo(2);
    }

    @OrderWith(SeededOrderingFactory.class)
    public static class OrderedFixture {
        public OrderedFixture() {
        }

        @org.junit.Test
        public void first() {
        }

        @org.junit.Test
        public void second() {
        }
    }

    public static class SeededOrderingFactory implements Ordering.Factory {
        public SeededOrderingFactory() {
        }

        @Override
        public Ordering create(Ordering.Context context) {
            return Ordering.shuffledBy(new Random(1));
        }
    }
}
