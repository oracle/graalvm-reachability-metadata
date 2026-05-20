/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.testng.internal.annotations.JDK15AnnotationFinder;
import org.testng.junit.JUnitMethodFinder;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

public class JUnitMethodFinderTest {
    @Test
    void reachesJUnitStyleMethodDiscoveryPath() {
        JUnitMethodFinder finder = new JUnitMethodFinder("junit-method-finder", new JDK15AnnotationFinder(null));
        XmlTest xmlTest = new XmlTest(new XmlSuite());

        assertThatThrownBy(() -> finder.getTestMethods(ChildJUnitTestCase.class, xmlTest))
                .isInstanceOf(NullPointerException.class);
        assertThat(finder.getAfterClassMethods(ChildJUnitTestCase.class)).isEmpty();
        assertThat(finder.getBeforeClassMethods(ChildJUnitTestCase.class)).isEmpty();
    }

    public static class ParentJUnitTestCase {
        public void setUp() {
        }

        public void tearDown() {
        }

        public void testInherited() {
        }

        public void testOverridden() {
        }

        public void testWithParameter(String ignored) {
        }
    }

    public static final class ChildJUnitTestCase extends ParentJUnitTestCase {
        public void testChild() {
        }

        @Override
        public void testOverridden() {
        }
    }
}
