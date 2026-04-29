/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.bean.BeanDocumentFactory;
import org.dom4j.bean.BeanElement;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;

public class BeanDocumentFactoryTest {
    @Test
    void compilerGeneratedClassLookupResolvesBeanDocumentFactoryType() throws Throwable {
        MethodHandle classLookup = MethodHandles.privateLookupIn(BeanDocumentFactory.class, MethodHandles.lookup())
                .findStatic(BeanDocumentFactory.class, "class$", MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(BeanDocumentFactory.class.getName());

        assertThat(resolvedClass).isEqualTo(BeanDocumentFactory.class);
    }

    @Test
    void createsBeanBackedElementFromClassAttribute() {
        BeanDocumentFactory factory = new BeanDocumentFactory();
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", BeanDocumentFactory.class.getName());

        Element element = factory.createElement(QName.get("configuredBean"), attributes);
        Object data = ((BeanElement) element).getData();

        assertThat(element).isInstanceOf(BeanElement.class);
        assertThat(element.getName()).isEqualTo("configuredBean");
        assertThat(data).isInstanceOf(BeanDocumentFactory.class);
        assertThat(data).isNotSameAs(factory);
    }
}
