/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.util.ClassUtil;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilTest {
    @Test
    public void discoversDeclaredFieldsThroughClassUtil() {
        Field secretField = ClassUtil.getDeclaredField(ClassUtilSubject.class, "secret");
        assertThat(secretField).isNotNull();
        assertThat(secretField.getName()).isEqualTo("secret");

        Field[] fields = ClassUtil.getDeclaredFields(ClassUtilSubject.class);
        assertThat(fields).isNotNull();
        assertThat(fields).extracting(Field::getName).contains("secret", "count");
    }

    @Test
    public void invokesInstanceMethodsUsingDefaultConstructor() {
        String result = ClassUtil.invoke(ClassUtilSubject.class.getName(), "describe", false, "suffix");

        assertThat(result).isEqualTo("subject:suffix:7");
    }

    @Test
    public void resolvesClassPathsForPackages() {
        Set<String> classPaths = ClassUtil.getClassPaths("cn.hutool.core.util");

        assertThat(classPaths).isNotNull();
    }

    public static class ClassUtilSubject {
        private final String secret = "subject";
        private final int count = 7;

        public ClassUtilSubject() {
        }

        public String describe(String suffix) {
            return secret + ":" + suffix + ":" + count;
        }
    }
}
