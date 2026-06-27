/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.SimpleTypeConverter;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeConverterDelegateTest {

    private final SimpleTypeConverter converter = new SimpleTypeConverter();

    @Test
    void convertsStringUsingPublicStringConstructor() {
        StringConstructedValue value = converter.convertIfNecessary("spring", StringConstructedValue.class);

        assertThat(value).isNotNull();
        assertThat(value.text).isEqualTo("spring");
    }

    @Test
    void convertsStringToEnumUsingEnumConstantField() {
        SampleEnum value = converter.convertIfNecessary("ALPHA", SampleEnum.class);

        assertThat(value).isEqualTo(SampleEnum.ALPHA);
    }

    @Test
    void convertsFullyQualifiedEnumConstantNameToRawEnumProperty() {
        RawEnumBean bean = new RawEnumBean();
        BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);

        wrapper.setPropertyValue("choice", SampleEnum.class.getName() + ".BETA");

        assertThat(bean.getChoice()).isEqualTo(SampleEnum.BETA);
    }

    @Test
    void convertsCollectionToTypedArray() {
        Integer[] values = converter.convertIfNecessary(List.of("1", "2"), Integer[].class);

        assertThat(values).containsExactly(1, 2);
    }

    @Test
    void convertsArrayToDifferentTypedArray() {
        Integer[] values = converter.convertIfNecessary(new String[] {"3", "4"}, Integer[].class);

        assertThat(values).containsExactly(3, 4);
    }

    @Test
    void convertsSingleValueToTypedArray() {
        Integer[] values = converter.convertIfNecessary("5", Integer[].class);

        assertThat(values).containsExactly(5);
    }

    @Test
    void convertsCollectionToConcreteCollectionCopy() {
        PublicStringCollection values = converter.convertIfNecessary(List.of("spring", "beans"),
                PublicStringCollection.class);

        assertThat(values).isInstanceOf(PublicStringCollection.class);
        assertThat(values).containsExactly("spring", "beans");
    }

    @Test
    void convertsMapToConcreteMapCopy() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("framework", "spring");
        source.put("module", "beans");

        PublicStringMap values = converter.convertIfNecessary(source, PublicStringMap.class);

        assertThat(values).isInstanceOf(PublicStringMap.class);
        assertThat(values).containsEntry("framework", "spring");
        assertThat(values).containsEntry("module", "beans");
    }

    public static class StringConstructedValue {
        private final String text;

        public StringConstructedValue(String text) {
            this.text = text;
        }
    }

    public enum SampleEnum {
        ALPHA,
        BETA
    }

    public static class RawEnumBean {
        private Enum<?> choice;

        public Enum<?> getChoice() {
            return choice;
        }

        public void setChoice(Enum<?> choice) {
            this.choice = choice;
        }
    }

    public static class PublicStringCollection extends ArrayList<String> {
        public PublicStringCollection() {
        }
    }

    public static class PublicStringMap extends LinkedHashMap<String, String> {
        public PublicStringMap() {
        }
    }
}
