/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.GroovyClassLoader;
import org.apache.groovy.plugin.GroovyRunner;
import org.apache.groovy.plugin.GroovyRunnerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultRunnersInnerJunit4TestRunnerTest {
    @Test
    void recognizesBothJUnitAnnotationStylesAndRejectsAnOrdinaryClass() throws Exception {
        try (GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader())) {
            GroovyRunner runner = GroovyRunnerRegistry.getInstance().values().stream()
                    .filter(candidate -> candidate.canRun(AnnotatedMethods.class, loader)).findFirst().orElseThrow();
            assertTrue(runner.canRun(AnnotatedMethods.class, loader));
            assertTrue(runner.canRun(ExplicitRunner.class, loader));
            assertFalse(runner.canRun(OrdinaryClass.class, loader));
        }
    }

    public static class AnnotatedMethods {
        @org.junit.Test
        public void checksAValue() {
            org.junit.Assert.assertEquals(4, 2 + 2);
        }
    }

    @RunWith(JUnit4.class)
    public static class ExplicitRunner extends AnnotatedMethods {
    }

    public static class OrdinaryClass {
    }
}
