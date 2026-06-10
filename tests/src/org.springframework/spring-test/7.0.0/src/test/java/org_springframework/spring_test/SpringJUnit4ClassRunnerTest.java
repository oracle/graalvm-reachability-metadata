/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runners.model.InitializationError;

import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringJUnit4ClassRunnerTest {
    @Test
    void createsRunnerForJUnit4TestClass() throws InitializationError {
        SpringJUnit4ClassRunner runner = new SpringJUnit4ClassRunner(PlainJUnit4TestCase.class);

        Description description = runner.getDescription();

        assertThat(description.getTestClass()).isEqualTo(PlainJUnit4TestCase.class);
        assertThat(description.getChildren()).hasSize(1);
        assertThat(description.getChildren().get(0).getMethodName()).isEqualTo("sampleTest");
    }

    public static class PlainJUnit4TestCase {
        @org.junit.Test
        public void sampleTest() {
        }
    }
}
