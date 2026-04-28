/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xpp3.xpp3;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.xmlpull.v1.builder.XmlElement;
import org.xmlpull.v1.builder.XmlInfosetBuilder;
import org.xmlpull.v1.builder.adapter.XmlElementAdapter;

public class XmlElementAdapterTest {
    @Test
    void castOrWrapCreatesAdapterForPlainElement() throws Exception {
        XmlElement element = newFragment("plain");

        XmlElementAdapter adapter = XmlElementAdapter.castOrWrap(element, PlainElementAdapter.class);

        assertThat(adapter).isInstanceOf(PlainElementAdapter.class);
        assertThat(adapter.getTarget()).isSameAs(element);
        assertThat(adapter.getTopAdapter()).isSameAs(adapter);
        assertThat(adapter.getName()).isEqualTo("plain");
    }

    @Test
    void castOrWrapAddsRequestedAdapterOnExistingAdapterChain() throws Exception {
        XmlElement element = newFragment("chained");
        FirstElementAdapter firstAdapter = (FirstElementAdapter) XmlElementAdapter.castOrWrap(
                element, FirstElementAdapter.class);

        XmlElementAdapter secondAdapter = XmlElementAdapter.castOrWrap(firstAdapter, SecondElementAdapter.class);

        assertThat(secondAdapter).isInstanceOf(SecondElementAdapter.class);
        assertThat(secondAdapter.getTarget()).isSameAs(firstAdapter);
        assertThat(firstAdapter.getTopAdapter()).isSameAs(secondAdapter);
        assertThat(secondAdapter.getTopAdapter()).isSameAs(secondAdapter);
        assertThat(secondAdapter.getName()).isEqualTo("chained");
    }

    private static XmlElement newFragment(String name) throws Exception {
        XmlInfosetBuilder builder = XmlInfosetBuilder.newInstance();
        return builder.newFragment(name);
    }

    public static class PlainElementAdapter extends XmlElementAdapter {
        public PlainElementAdapter(XmlElement target) {
            super(target);
        }
    }

    public static class FirstElementAdapter extends XmlElementAdapter {
        public FirstElementAdapter(XmlElement target) {
            super(target);
        }
    }

    public static class SecondElementAdapter extends XmlElementAdapter {
        public SecondElementAdapter(XmlElement target) {
            super(target);
        }
    }
}
