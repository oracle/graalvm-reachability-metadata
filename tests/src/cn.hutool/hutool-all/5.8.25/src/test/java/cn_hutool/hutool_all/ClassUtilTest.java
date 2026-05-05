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
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilTest {

    @Test
    void readsDeclaredFieldMetadata() {
        Field secretField = ClassUtil.getDeclaredField(DeclaredFieldsBean.class, "secret");
        assertThat(secretField).isNotNull();
        assertThat(secretField.getName()).isEqualTo("secret");
        assertThat(secretField.getType()).isEqualTo(String.class);

        Field[] declaredFields = ClassUtil.getDeclaredFields(DeclaredFieldsBean.class);
        assertThat(Arrays.stream(declaredFields).map(Field::getName))
                .contains("secret", "count");
    }

    @Test
    void invokesInstanceMethodByCreatingNewInstance() {
        String greeting = ClassUtil.invoke(InvokableGreeting.class.getName(), "greet", false);
        assertThat(greeting).isEqualTo("hello from ClassUtil");
    }

    @Test
    void discoversClassPathResourcesThroughConfiguredClassLoader() {
        Set<String> classPaths = ClassUtil.getClassPaths("cn.hutool.core.util", true);
        assertThat(classPaths).isNotNull();
    }

    public static class DeclaredFieldsBean {
        private String secret = "value";
        private int count = 42;
    }

    public static class InvokableGreeting {
        public String greet() {
            return "hello from ClassUtil";
        }
    }
}
