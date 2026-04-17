/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.apache.seata.common.util.ReflectionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilTest {
    @Test
    void getClassByNameResolvesClassesFromTheContextClassLoader() throws ClassNotFoundException {
        assertThat(ReflectionUtil.getClassByName(SampleChild.class.getName())).isSameAs(SampleChild.class);
    }

    @Test
    void getAllFieldsReturnsOnlyInstanceFieldsAcrossTheHierarchy() {
        Field[] fields = ReflectionUtil.getAllFields(SyntheticFieldChild.class);
        List<String> fieldNames = Arrays.stream(fields).map(Field::getName).toList();

        assertThat(fieldNames)
                .containsExactlyInAnyOrder("childValue", "parentValue", "secret")
                .doesNotContain("IGNORED_CHILD_STATIC", "IGNORED_PARENT_STATIC", "this$0");
    }

    @Test
    void getFieldAndSetFieldValueTraverseSuperclasses() throws NoSuchFieldException {
        SampleChild sample = new SampleChild();
        Field field = ReflectionUtil.getField(SampleChild.class, "secret");

        String initialValue = ReflectionUtil.getFieldValue(sample, field);

        assertThat(field.getDeclaringClass()).isEqualTo(SampleParent.class);
        assertThat(initialValue).isEqualTo("initial");

        ReflectionUtil.setFieldValue(sample, field, "updated");

        String updatedValue = ReflectionUtil.getFieldValue(sample, "secret");
        assertThat(updatedValue).isEqualTo("updated");
    }

    @Test
    void getMethodAndInvokeMethodTraverseSuperclasses() throws Exception {
        SampleChild sample = new SampleChild();
        Method method = ReflectionUtil.getMethod(SampleChild.class, "describe", String.class);

        assertThat(method.getDeclaringClass()).isEqualTo(SampleParent.class);
        assertThat(ReflectionUtil.invokeMethod(sample, method, "prefix")).isEqualTo("prefix-initial");
    }

    @Test
    void modifyStaticFinalFieldUpdatesStaticFieldsByName() throws Exception {
        assertThat(StaticFieldHolder.getValue()).isEqualTo("before");

        try {
            ReflectionUtil.modifyStaticFinalField(StaticFieldHolder.class, "VALUE", "after");
            assertThat(StaticFieldHolder.getValue()).isEqualTo("after");
        } finally {
            ReflectionUtil.modifyStaticFinalField(StaticFieldHolder.class, "VALUE", "before");
        }
    }

    private static class SampleParent {
        private static final String IGNORED_PARENT_STATIC = "ignored";

        private String parentValue = "parent";
        private String secret = "initial";

        private String describe(String prefix) {
            return prefix + "-" + secret;
        }
    }

    private static final class SampleChild extends SampleParent {
    }

    private final class SyntheticFieldChild extends SampleParent {
        private static final String IGNORED_CHILD_STATIC = "ignored";

        private String childValue = "child";
    }

    private static final class StaticFieldHolder {
        private static String VALUE = "before";

        private StaticFieldHolder() {
        }

        private static String getValue() {
            return VALUE;
        }
    }
}
