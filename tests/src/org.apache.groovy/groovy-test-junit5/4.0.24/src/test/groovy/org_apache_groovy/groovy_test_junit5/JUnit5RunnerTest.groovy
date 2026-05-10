/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_test_junit5

import groovy.junit5.plugin.JUnit5Runner
import groovy.lang.GroovyClassLoader
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertTrue

public class JUnit5RunnerTest {
    @Test
    void canRunFindsJUnit5MethodAnnotation() {
        JUnit5Runner runner = new JUnit5Runner()
        GroovyClassLoader loader = new GroovyClassLoader(MethodAnnotatedSample.class.classLoader)
        try {
            assertTrue(runner.canRun(MethodAnnotatedSample.class, loader))
        } finally {
            loader.close()
        }
    }

    public static class MethodAnnotatedSample {
        @Test
        public void sampleTestMethod() {
        }
    }
}
