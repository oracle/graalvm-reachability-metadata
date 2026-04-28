/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_py4j.py4j;

import org.junit.jupiter.api.Test;
import py4j.model.Py4JClass;
import py4j.model.Py4JMember;

import static org.assertj.core.api.Assertions.assertThat;

public class Py4JClassTest {
    @Test
    void buildsModelFromDeclaredPublicMembers() {
        Py4JClass model = Py4JClass.buildClass(DocumentedFixture.class);

        assertThat(model.getName()).isEqualTo(DocumentedFixture.class.getCanonicalName());
        assertThat(model.getExtendType()).isEqualTo(BaseFixture.class.getCanonicalName());
        assertThat(model.getImplementTypes()).containsExactly(Cloneable.class.getCanonicalName());
        assertThat(model.getSignature(false))
                .isEqualTo(DocumentedFixture.class.getCanonicalName()
                        + " extends "
                        + BaseFixture.class.getCanonicalName()
                        + " implements "
                        + Cloneable.class.getCanonicalName());

        assertThat(model.getClasses()).extracting(Py4JMember::getName)
                .containsExactly(DocumentedFixture.PublicChild.class.getCanonicalName());
        assertThat(model.getFields()).extracting(Py4JMember::getName).containsExactly("publicName");
        assertThat(model.getMethods()).extracting(Py4JMember::getName).containsExactly("count", "describe");
    }

    public static class BaseFixture {
    }

    public static class DocumentedFixture extends BaseFixture implements Cloneable {
        public String publicName = "py4j";

        private String privateName = "hidden";

        public int count() {
            return publicName.length();
        }

        public String describe(String prefix, Integer suffix) {
            return prefix + publicName + suffix;
        }

        private String secret() {
            return privateName;
        }

        public static class PublicChild {
            public String childName() {
                return "child";
            }
        }

        private static class PrivateChild {
        }
    }
}
