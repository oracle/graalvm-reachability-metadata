/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.utils.PojoUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class PojoUtilsTest {
    @Test
    void generalizesAndRealizesBeansCollectionsArraysMapsAndInterfaces() {
        Person person = new Person("Ada");
        person.age = 37;

        Object generalized = PojoUtils.generalize(person);
        Person realized = (Person) PojoUtils.realize(generalized, Person.class);
        assertThat(realized.getName()).isEqualTo("Ada");
        assertThat(realized.age).isEqualTo(37);

        assertThat((String[]) PojoUtils.realize(Arrays.asList("a", "b"), String[].class))
                .containsExactly("a", "b");
        assertThat(PojoUtils.realize(new String[] {"a", "b"}, ArrayList.class))
                .isEqualTo(Arrays.asList("a", "b"));

        ConstructorMap source = new ConstructorMap(new HashMap<Object, Object>());
        source.put("answer", 42);
        assertThat(PojoUtils.generalize(source)).isEqualTo(source);

        Map<String, Object> view = new HashMap<String, Object>();
        view.put("name", "Grace");
        Named named = (Named) PojoUtils.realize(view, Named.class);
        assertThat(named.getName()).isEqualTo("Grace");
        assertThat(named.toString()).contains("name=Grace");
    }

    public interface Named {
        String getName();
    }

    public static class Person {
        public int age;
        private String name;

        public Person(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(CharSequence name) {
            this.name = name.toString();
        }
    }

    public static class ConstructorMap extends HashMap<Object, Object> {
        public ConstructorMap(Map<Object, Object> values) {
            super(values);
        }
    }
}
