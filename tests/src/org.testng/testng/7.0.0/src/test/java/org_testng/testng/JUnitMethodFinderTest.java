/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testng.junit.JUnitMethodFinder;

public class JUnitMethodFinderTest {
    private static final String TEST_NAME = "junit-method-finder-test";

    @Test
    void returnsEmptyMethodArraysForUnsupportedJUnitConfigurationScopes() {
        JUnitMethodFinder finder = new JUnitMethodFinder(TEST_NAME, null);

        assertThat(finder.getBeforeClassMethods(JUnitStyleTestCase.class)).isEmpty();
        assertThat(finder.getAfterClassMethods(JUnitStyleTestCase.class)).isEmpty();
        assertThat(finder.getBeforeSuiteMethods(JUnitStyleTestCase.class)).isEmpty();
        assertThat(finder.getAfterSuiteMethods(JUnitStyleTestCase.class)).isEmpty();
        assertThat(finder.getBeforeTestConfigurationMethods(JUnitStyleTestCase.class)).isEmpty();
        assertThat(finder.getAfterTestConfigurationMethods(JUnitStyleTestCase.class)).isEmpty();
        assertThat(finder.getBeforeGroupsConfigurationMethods(JUnitStyleTestCase.class)).isEmpty();
        assertThat(finder.getAfterGroupsConfigurationMethods(JUnitStyleTestCase.class)).isEmpty();
    }

    public static final class JUnitStyleTestCase {
        public void setUp() {
        }

        public void testAlpha() {
        }

        public void tearDown() {
        }
    }
}
