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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilTest {
    @Test
    void discoversDeclaredFieldsThroughClassUtilApi() {
        Field declaredField = ClassUtil.getDeclaredField(FieldFixture.class, "name");
        Field missingField = ClassUtil.getDeclaredField(FieldFixture.class, "missing");
        Field[] declaredFields = ClassUtil.getDeclaredFields(FieldFixture.class);

        assertThat(declaredField).isNotNull();
        assertThat(declaredField.getName()).isEqualTo("name");
        assertThat(missingField).isNull();
        assertThat(fieldNames(declaredFields)).contains("name", "count");
    }

    @Test
    void enumeratesClassPathResourcesThroughClassUtilApi() {
        Set<String> classPaths = ClassUtil.getClassPaths("cn.hutool.core.util", true);

        assertThat(classPaths).isNotNull();
    }

    @Test
    void invokesInstanceMethodByClassAndMethodName() {
        String greeting = ClassUtil.invoke(
                InvocationFixture.class.getName(), "greeting", false, "Hutool");

        assertThat(greeting).isEqualTo("hello Hutool");
    }

    private static List<String> fieldNames(Field[] fields) {
        List<String> names = new ArrayList<>();
        for (Field field : fields) {
            names.add(field.getName());
        }
        return names;
    }

    public static class FieldFixture {
        private String name;
        private int count;
    }

    public static class InvocationFixture {
        public InvocationFixture() {
        }

        public String greeting(String name) {
            return "hello " + name;
        }
    }
}
