/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.graalvm.internal.tck.NativeImageSupport;
import org.jdom.Namespace;
import org.jdom.xpath.XPath;
import org.junit.jupiter.api.Test;

public class XPathTest {
    private static final String XPATH_CLASS_PROPERTY = "org.jdom.xpath.class";

    @Test
    void newInstanceUsesConfiguredImplementationClass() throws Exception {
        String previousXPathClass = System.getProperty(XPATH_CLASS_PROPERTY);
        System.setProperty(XPATH_CLASS_PROPERTY, RecordingXPath.class.getName());

        try {
            XPath xpath = XPath.newInstance("/library/book[@available='true']");

            assertThat(xpath).isInstanceOf(RecordingXPath.class);
            assertThat(xpath.getXPath()).isEqualTo("/library/book[@available='true']");
            assertThat(xpath.selectSingleNode("selected-node")).isEqualTo("selected-node");
            assertThat(xpath.selectNodes("selected-node")).containsExactly("selected-node");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            if (previousXPathClass == null) {
                System.clearProperty(XPATH_CLASS_PROPERTY);
            } else {
                System.setProperty(XPATH_CLASS_PROPERTY, previousXPathClass);
            }
        }
    }

    public static class RecordingXPath extends XPath {
        private final String path;

        public RecordingXPath(String path) {
            this.path = path;
        }

        @Override
        public List selectNodes(Object context) {
            return Collections.singletonList(context);
        }

        @Override
        public Object selectSingleNode(Object context) {
            return context;
        }

        @Override
        public String valueOf(Object context) {
            return String.valueOf(context);
        }

        @Override
        public Number numberValueOf(Object context) {
            return Integer.valueOf(valueOf(context).length());
        }

        @Override
        public void setVariable(String name, Object value) {
        }

        @Override
        public void addNamespace(Namespace namespace) {
        }

        @Override
        public String getXPath() {
            return path;
        }
    }
}
