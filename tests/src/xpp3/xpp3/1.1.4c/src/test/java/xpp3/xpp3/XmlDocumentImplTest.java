/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xpp3.xpp3;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xmlpull.v1.builder.XmlElement;
import org.xmlpull.v1.builder.impl.XmlDocumentImpl;

public class XmlDocumentImplTest {
    private static final VarHandle CHILDREN = findChildrenHandle();

    @Test
    void cloneCopiesCloneableDocumentChildren() throws CloneNotSupportedException {
        XmlDocumentImpl document = new XmlDocumentImpl("1.0", Boolean.TRUE, "UTF-8");
        CloneableDocumentChild child = new CloneableDocumentChild("before-root");
        XmlElement root = document.addDocumentElement("root");
        setChildren(document, new ArrayList<>(Arrays.asList(child, root)));

        XmlDocumentImpl cloned = (XmlDocumentImpl) document.clone();
        Iterator<?> children = cloned.children().iterator();

        Object clonedChild = children.next();
        assertThat(clonedChild).isInstanceOf(CloneableDocumentChild.class);
        assertThat(clonedChild).isNotSameAs(child);
        assertThat(((CloneableDocumentChild) clonedChild).value()).isEqualTo("before-root");

        Object clonedRoot = children.next();
        assertThat(clonedRoot).isSameAs(cloned.getDocumentElement());
        assertThat(clonedRoot).isNotSameAs(root);
        assertThat(((XmlElement) clonedRoot).getName()).isEqualTo("root");
        assertThat(children.hasNext()).isFalse();
    }

    private static void setChildren(XmlDocumentImpl document, List<Object> children) {
        CHILDREN.set(document, children);
    }

    private static VarHandle findChildrenHandle() {
        try {
            return MethodHandles.privateLookupIn(XmlDocumentImpl.class, MethodHandles.lookup())
                    .findVarHandle(XmlDocumentImpl.class, "children", List.class);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static class CloneableDocumentChild implements Cloneable {
        private final String value;

        CloneableDocumentChild(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }

        @Override
        public CloneableDocumentChild clone() {
            return new CloneableDocumentChild(value);
        }
    }
}
