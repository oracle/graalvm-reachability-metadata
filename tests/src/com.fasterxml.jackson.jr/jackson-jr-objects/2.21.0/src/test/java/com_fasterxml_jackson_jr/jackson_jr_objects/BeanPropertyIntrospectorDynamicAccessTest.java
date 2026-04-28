/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.JSON;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPropertyIntrospectorDynamicAccessTest {
    private static final JSON JSON_WITH_FIELD_MATCHING_GETTERS = JSON.builder()
            .enable(JSON.Feature.USE_FIELD_MATCHING_GETTERS)
            .build();

    @Test
    void deserializesBeansUsingDeclaredConstructors() throws Exception {
        ConstructorIntrospectedBean fromObject = JSON.std.beanFrom(ConstructorIntrospectedBean.class, "{\"count\":4}");
        ConstructorIntrospectedBean fromString = JSON.std.beanFrom(ConstructorIntrospectedBean.class, "\"Ada\"");
        ConstructorIntrospectedBean fromNumber = JSON.std.beanFrom(ConstructorIntrospectedBean.class, "7");

        assertThat(fromObject.getCreationPath()).isEqualTo("default");
        assertThat(fromObject.count).isEqualTo(4);
        assertThat(fromString.getCreationPath()).isEqualTo("string");
        assertThat(fromString.getName()).isEqualTo("Ada");
        assertThat(fromNumber.getCreationPath()).isEqualTo("long");
        assertThat(fromNumber.count).isEqualTo(7);
    }

    @Test
    void serializesAndDeserializesBeansUsingDeclaredFieldsAndMethods() throws Exception {
        FieldAndMethodIntrospectedBean bean = JSON_WITH_FIELD_MATCHING_GETTERS.beanFrom(
                FieldAndMethodIntrospectedBean.class,
                "{\"visible\":5,\"nickname\":\"Bob\",\"active\":false}");

        assertThat(bean.visible).isEqualTo(5);
        assertThat(bean.nickname()).isEqualTo("Bob");
        assertThat(bean.isActive()).isFalse();

        String json = JSON_WITH_FIELD_MATCHING_GETTERS.asString(bean);

        assertThat(json).isEqualTo("{\"active\":false,\"nickname\":\"Bob\",\"visible\":5}");
        assertThat(json).doesNotContain("ignoredStatic", "IGNORED_CONSTANT");
    }

    public static final class ConstructorIntrospectedBean {
        public int count;

        private final String creationPath;
        private String name;

        public ConstructorIntrospectedBean() {
            creationPath = "default";
        }

        public ConstructorIntrospectedBean(String name) {
            creationPath = "string";
            this.name = name;
        }

        public ConstructorIntrospectedBean(long count) {
            creationPath = "long";
            this.count = (int) count;
        }

        public String getCreationPath() {
            return creationPath;
        }

        public String getName() {
            return name;
        }
    }

    public static class FieldBaseBean {
        public int visible;
    }

    public static final class FieldAndMethodIntrospectedBean extends FieldBaseBean {
        public static int ignoredStatic = 12;
        public static final int IGNORED_CONSTANT = 13;

        private String nickname;
        private boolean active;

        public FieldAndMethodIntrospectedBean() {
        }

        public String nickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
