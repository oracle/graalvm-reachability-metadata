/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_py4j.py4j;

import java.util.List;

import org.junit.jupiter.api.Test;

import py4j.model.Py4JClass;
import py4j.model.Py4JMember;

import static org.assertj.core.api.Assertions.assertThat;

public class Py4JClassTest {
    @Test
    void buildsModelFromDeclaredClassesMethodsAndFields() {
        Py4JClass model = Py4JClass.buildClass(DescribedFixture.class);

        assertThat(model.getName()).isEqualTo(DescribedFixture.class.getCanonicalName());
        assertThat(model.getExtendType()).isEqualTo(BaseFixture.class.getCanonicalName());
        assertThat(model.getImplementTypes()).containsExactly(NamedFixture.class.getCanonicalName());
        assertThat(model.getSignature(false)).isEqualTo(DescribedFixture.class.getCanonicalName()
                + " extends " + BaseFixture.class.getCanonicalName()
                + " implements " + NamedFixture.class.getCanonicalName());

        assertThat(memberNames(model.getClasses()))
                .containsExactly(DescribedFixture.PublicNestedFixture.class.getCanonicalName());
        assertThat(memberNames(model.getMethods()))
                .containsExactly("describe", "name");
        assertThat(memberNames(model.getFields()))
                .containsExactly("count", "text");
    }

    private static List<String> memberNames(List<? extends Py4JMember> members) {
        return members.stream()
                .map(Py4JMember::getName)
                .toList();
    }

    public interface NamedFixture {
        String name();
    }

    public static class BaseFixture {
        public String inheritedValue() {
            return "base";
        }
    }

    public static class DescribedFixture extends BaseFixture implements NamedFixture {
        public String text = "text";
        public int count = 3;
        private String hidden = "hidden";

        @Override
        public String name() {
            return "fixture";
        }

        public String describe(Integer value) {
            return text + value;
        }

        private String hiddenMethod() {
            return hidden;
        }

        public static class PublicNestedFixture {
            public String nestedValue() {
                return "nested";
            }
        }

        private static class HiddenNestedFixture {
            String hiddenValue() {
                return "hidden";
            }
        }
    }
}
