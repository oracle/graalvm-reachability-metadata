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
import java.lang.reflect.Field;

import org.dom4j.QName;
import org.dom4j.bean.BeanDocumentFactory;
import org.dom4j.bean.BeanElement;
import org.dom4j.tree.NamespaceStack;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;

public class BeanElementTest {
    @Test
    void compilerGeneratedClassLookupResolvesBeanElementType() throws Throwable {
        MethodHandle classLookup = MethodHandles.privateLookupIn(BeanElement.class, MethodHandles.lookup())
                .findStatic(BeanElement.class, "class$", MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(BeanElement.class.getName());

        assertThat(resolvedClass).isEqualTo(BeanElement.class);
    }

    @Test
    void saxClassAttributeInitializesBackingBean() throws Exception {
        clearCompilerGeneratedClassCache();
        BeanElement element = new BeanElement(QName.get("configuredBean"));
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", BeanDocumentFactory.class.getName());

        element.setAttributes(attributes, new NamespaceStack(), false);

        assertThat(element.getData()).isInstanceOf(BeanDocumentFactory.class);
    }

    private static void clearCompilerGeneratedClassCache() throws Exception {
        Field field = BeanElement.class.getDeclaredField("class$org$dom4j$bean$BeanElement");
        field.setAccessible(true);
        field.set(null, null);
    }
}
