/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testng.TestNG;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlInclude;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

public class XmlMethodSelectorTest {
    @TempDir
    Path outputDirectory;

    @Test
    void matchesConfiguredIncludedMethodsAgainstPublicTestMethods() {
        IncludedMethodTestCase.reset();

        TestNG testNg = new TestNG(true);
        testNg.setUseDefaultListeners(false);
        testNg.setVerbose(0);
        testNg.setOutputDirectory(outputDirectory.toString());
        testNg.setXmlSuites(Collections.singletonList(suiteWithIncludedMethod("selected.*")));

        testNg.run();

        assertThat(testNg.getStatus()).isZero();
        assertThat(IncludedMethodTestCase.selectedMethodInvocations).isEqualTo(1);
        assertThat(IncludedMethodTestCase.otherMethodInvocations).isZero();
    }

    private static XmlSuite suiteWithIncludedMethod(String methodNamePattern) {
        XmlSuite suite = new XmlSuite();
        suite.setName("xml-method-selector-suite");
        XmlTest test = new XmlTest(suite);
        test.setName("xml-method-selector-test");

        XmlClass xmlClass = new XmlClass(IncludedMethodTestCase.class);
        xmlClass.setIncludedMethods(Collections.singletonList(new XmlInclude(methodNamePattern)));
        test.setXmlClasses(Collections.singletonList(xmlClass));
        return suite;
    }

    public static final class IncludedMethodTestCase {
        private static int selectedMethodInvocations;
        private static int otherMethodInvocations;

        public IncludedMethodTestCase() {
        }

        private static void reset() {
            selectedMethodInvocations = 0;
            otherMethodInvocations = 0;
        }

        @org.testng.annotations.Test
        public void selectedMethod() {
            selectedMethodInvocations++;
        }

        @org.testng.annotations.Test
        public void otherMethod() {
            otherMethodInvocations++;
        }
    }
}
