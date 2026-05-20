/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testng.xml.XmlMethodSelectors;
import org.testng.xml.XmlSuite;
import org.testng.xml.dom.TestNGTagFactory;

public class TestNGTagFactoryTest {
    @Test
    void resolvesXmlTagNamesToTheirTestNgModelClasses() {
        TestNGTagFactory factory = new TestNGTagFactory();

        assertThat(factory.getClassForTag("suite")).isEqualTo(XmlSuite.class);
        assertThat(factory.getClassForTag("method-selectors")).isEqualTo(XmlMethodSelectors.class);
    }
}
