/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import org.junit.Assert;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlockJUnit4ClassRunnerWithParametersTest {
    @Test
    public void createsParameterizedTestsUsingConstructorInjection() {
        ConstructorInjectionFixture.events.clear();

        Result result = new JUnitCore().run(ConstructorInjectionFixture.class);

        assertTrue(result.wasSuccessful(), result.getFailures().toString());
        assertEquals(2, result.getRunCount());
        assertEquals(Arrays.asList("alpha:1", "beta:2"), ConstructorInjectionFixture.events);
    }

    @Test
    public void createsParameterizedTestsUsingFieldInjection() {
        FieldInjectionFixture.events.clear();

        Result result = new JUnitCore().run(FieldInjectionFixture.class);

        assertTrue(result.wasSuccessful(), result.getFailures().toString());
        assertEquals(2, result.getRunCount());
        assertEquals(Arrays.asList("red:3", "blue:5"), FieldInjectionFixture.events);
    }

    @RunWith(Parameterized.class)
    public static class ConstructorInjectionFixture {
        private static final List<String> events = new ArrayList<>();

        private final String name;
        private final int value;

        public ConstructorInjectionFixture(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> parameters() {
            return Arrays.asList(new Object[][] {{"alpha", 1}, {"beta", 2}});
        }

        @org.junit.Test
        public void receivesConstructorParameters() {
            Assert.assertNotNull(name);
            Assert.assertTrue(value > 0);
            events.add(name + ":" + value);
        }
    }

    @RunWith(Parameterized.class)
    public static class FieldInjectionFixture {
        private static final List<String> events = new ArrayList<>();

        @Parameterized.Parameter(0)
        public String name;

        @Parameterized.Parameter(1)
        public int value;

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> parameters() {
            return Arrays.asList(new Object[][] {{"red", 3}, {"blue", 5}});
        }

        @org.junit.Test
        public void receivesInjectedFields() {
            Assert.assertNotNull(name);
            Assert.assertTrue(value > 0);
            events.add(name + ":" + value);
        }
    }
}
