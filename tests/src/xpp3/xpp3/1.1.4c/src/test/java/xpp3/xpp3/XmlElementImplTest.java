/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xpp3.xpp3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;

import org.junit.jupiter.api.Test;
import org.xmlpull.v1.builder.XmlElement;
import org.xmlpull.v1.builder.XmlInfosetBuilder;

public class XmlElementImplTest {
    @Test
    void cloneCopiesCloneableChildAddedThroughPublicApi() throws Exception {
        XmlElement element = XmlInfosetBuilder.newInstance().newFragment("root");
        CloneableElementChild child = new CloneableElementChild("content");
        element.addChild(child);

        XmlElement cloned = (XmlElement) element.clone();
        Iterator<?> clonedChildren = cloned.children();

        Object clonedChild = clonedChildren.next();
        assertThat(clonedChild).isInstanceOf(CloneableElementChild.class);
        assertThat(clonedChild).isNotSameAs(child);
        assertThat(((CloneableElementChild) clonedChild).value()).isEqualTo("content");
        assertThat(clonedChildren.hasNext()).isFalse();
    }

    public static class CloneableElementChild implements Cloneable {
        private final String value;

        CloneableElementChild(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }

        @Override
        public CloneableElementChild clone() {
            return new CloneableElementChild(value);
        }
    }
}
