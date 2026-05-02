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
import org.junit.runner.Runner;
import org.junit.runners.Parameterized;
import org.junit.runners.model.InitializationError;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParameterizedInnerRunnersFactoryTest {
    @Test
    public void createsFactoryDeclaredByUseParametersRunnerFactory() {
        ParameterizedFixture.events.clear();
        CustomParametersRunnerFactory.instancesCreated = 0;
        CustomParametersRunnerFactory.parametersSeen.clear();

        Result result = new JUnitCore().run(ParameterizedFixture.class);

        assertTrue(result.wasSuccessful(), result.getFailures().toString());
        assertEquals(2, result.getRunCount());
        assertEquals(1, CustomParametersRunnerFactory.instancesCreated);
        assertEquals(Arrays.asList("first", "second"), CustomParametersRunnerFactory.parametersSeen);
        assertEquals(Arrays.asList("first:2", "second:4"), ParameterizedFixture.events);
    }

    @RunWith(Parameterized.class)
    @Parameterized.UseParametersRunnerFactory(CustomParametersRunnerFactory.class)
    public static class ParameterizedFixture {
        private static final List<String> events = new ArrayList<>();

        private final String name;
        private final int value;

        public ParameterizedFixture(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> parameters() {
            return Arrays.asList(new Object[][] {{"first", 2}, {"second", 4}});
        }

        @org.junit.Test
        public void receivesParametersFromCustomFactoryRunner() {
            Assert.assertNotNull(name);
            Assert.assertTrue(value % 2 == 0);
            events.add(name + ":" + value);
        }
    }

    public static class CustomParametersRunnerFactory implements ParametersRunnerFactory {
        private static final List<String> parametersSeen = new ArrayList<>();
        private static int instancesCreated;

        public CustomParametersRunnerFactory() {
            instancesCreated++;
        }

        @Override
        public Runner createRunnerForTestWithParameters(TestWithParameters test) throws InitializationError {
            parametersSeen.add((String) test.getParameters().get(0));
            return new BlockJUnit4ClassRunnerWithParameters(test);
        }
    }
}
