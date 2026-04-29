/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.bean.BeanDocumentFactory;
import org.dom4j.bean.BeanElement;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;

public class BeanDocumentFactoryTest {
    @Test
    void createsBeanBackedElementFromClassAttribute() throws Exception {
        BeanDocumentFactory factory = new BeanDocumentFactory();
        clearCompilerGeneratedClassCache();
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", factory.getClass().getName());

        Element element = factory.createElement(QName.get("configuredBean"), attributes);
        Object data = ((BeanElement) element).getData();

        assertThat(element).isInstanceOf(BeanElement.class);
        assertThat(element.getName()).isEqualTo("configuredBean");
        assertThat(data).isNotSameAs(factory);
        assertThat(data.getClass()).isEqualTo(factory.getClass());
    }

    private static void clearCompilerGeneratedClassCache() throws Exception {
        Field field = BeanDocumentFactory.class.getDeclaredField("class$org$dom4j$bean$BeanDocumentFactory");
        field.setAccessible(true);
        field.set(null, null);
    }
}
