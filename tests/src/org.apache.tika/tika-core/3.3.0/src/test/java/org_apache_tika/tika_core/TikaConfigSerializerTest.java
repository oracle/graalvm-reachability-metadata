/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.tika.config.Field;
import org.apache.tika.config.TikaConfigSerializer;
import org.apache.tika.utils.XMLReaderUtils;

public class TikaConfigSerializerTest {

    @Test
    public void serializeParamsWritesPrimitiveCollectionMapAndObjectParameters() throws Exception {
        Document document = XMLReaderUtils.getDocumentBuilder().newDocument();
        Element root = document.createElement("component");
        document.appendChild(root);

        TikaConfigSerializer.serializeParams(document, root, new SerializableComponent());

        assertThat(paramText(root, "name")).isEqualTo("component-name");
        assertThat(paramText(root, "enabled")).isEqualTo("true");
        assertThat(paramText(root, "count")).isEqualTo("7");
        assertThat(paramText(root, "nestedValue")).isEqualTo("nested-name");

        Element namesParam = param(root, "names");
        assertThat(namesParam.getAttribute("type")).isEqualTo("list");
        assertThat(strings(namesParam)).containsExactly("first", "second");

        Element aliasesParam = param(root, "aliases");
        assertThat(aliasesParam.getAttribute("type")).isEqualTo("map");
        assertThat(mapValue(aliasesParam, "alpha")).isEqualTo("one");
        assertThat(mapValue(aliasesParam, "beta")).isEqualTo("two");

        Element nested = (Element) root.getElementsByTagName("nested").item(0);
        assertThat(nested.getAttribute("class")).isEqualTo(NestedValue.class.getCanonicalName());
    }

    @Test
    public void findGetterMatchesSetterNameAndType() throws Exception {
        Class<?> methodTupleClass = Arrays.stream(TikaConfigSerializer.class.getDeclaredClasses())
                .filter(clazz -> "MethodTuple".equals(clazz.getSimpleName()))
                .findFirst()
                .orElseThrow();
        Method setter = SerializableComponent.class.getMethod("setName", String.class);
        Constructor<?> constructor = methodTupleClass.getDeclaredConstructor(String.class,
                Method.class, Class.class);
        constructor.setAccessible(true);
        Object setterTuple = constructor.newInstance("Name", setter, String.class);

        Method findGetter = TikaConfigSerializer.class.getDeclaredMethod("findGetter",
                methodTupleClass, Object.class);
        findGetter.setAccessible(true);

        Method getter = (Method) findGetter.invoke(null, setterTuple, new SerializableComponent());

        assertThat(getter).isEqualTo(SerializableComponent.class.getMethod("getName"));
    }

    private static Element param(Element root, String name) {
        NodeList params = root.getElementsByTagName("param");
        for (int i = 0; i < params.getLength(); i++) {
            Element param = (Element) params.item(i);
            if (name.equals(param.getAttribute("name"))) {
                return param;
            }
        }
        throw new AssertionError("Missing param: " + name);
    }

    private static String paramText(Element root, String name) {
        return param(root, name).getTextContent();
    }

    private static List<String> strings(Element param) {
        NodeList strings = param.getElementsByTagName("string");
        return IntStream.range(0, strings.getLength())
                .mapToObj(index -> strings.item(index).getTextContent())
                .toList();
    }

    private static String mapValue(Element param, String key) {
        NodeList strings = param.getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) {
            Node node = strings.item(i);
            if (node instanceof Element element && key.equals(element.getAttribute("key"))) {
                return element.getAttribute("value");
            }
        }
        throw new AssertionError("Missing map key: " + key);
    }

    public static class SerializableComponent {
        private String name = "component-name";
        private boolean enabled = true;
        private int count = 7;
        private List<String> names = List.of("first", "second");
        private Map<String, String> aliases = Map.of("alpha", "one", "beta", "two");
        private NestedValue nested = new NestedValue("nested-name");

        @Field
        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Field
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        @Field
        public void setCount(int count) {
            this.count = count;
        }

        public int getCount() {
            return count;
        }

        @Field
        public void setNames(List<String> names) {
            this.names = names;
        }

        public List<String> getNames() {
            return names;
        }

        @Field
        public void setAliases(Map<String, String> aliases) {
            this.aliases = aliases;
        }

        public Map<String, String> getAliases() {
            return aliases;
        }

        @Field
        public void setNested(NestedValue nested) {
            this.nested = nested;
        }

        public NestedValue getNested() {
            return nested;
        }
    }

    public static class NestedValue {
        private String nestedValue;

        public NestedValue(String nestedValue) {
            this.nestedValue = nestedValue;
        }

        @Field
        public void setNestedValue(String nestedValue) {
            this.nestedValue = nestedValue;
        }

        public String getNestedValue() {
            return nestedValue;
        }
    }
}
