/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.internal.util.ClassUtils;
import org.flywaydb.core.internal.util.ClassUtils.DoNotMapForLogging;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {
    private static final ClassLoader CLASS_LOADER = ClassUtilsTest.class.getClassLoader();

    @Test
    void classPresenceChecksUseConfiguredClassLoader() {
        assertThat(ClassUtils.isPresent(String.class.getName(), CLASS_LOADER)).isTrue();
        assertThat(ClassUtils.isImplementationPresent(Runnable.class.getName(), CLASS_LOADER)).isFalse();
    }

    @Test
    void loadClassReturnsConcreteImplementationAfterConstructingIt() throws Exception {
        Class<? extends Runnable> loadedClass = ClassUtils.loadClass(Runnable.class,
                LoadableRunnable.class.getName(),
                CLASS_LOADER);

        assertThat(loadedClass).isSameAs(LoadableRunnable.class);
        assertThat(ClassUtils.loadClass(Runnable.class, NonRunnable.class.getName(), CLASS_LOADER)).isNull();
    }

    @Test
    void staticFieldValueIsReadFromNamedClass() {
        String value = ClassUtils.getStaticFieldValue(StaticFieldTarget.class.getName(), "VALUE", CLASS_LOADER);

        assertThat(value).isEqualTo("static field value");
    }

    @Test
    void fieldValueCanBeReadAndUpdated() {
        MutableFieldTarget target = new MutableFieldTarget();

        assertThat(ClassUtils.getFieldValue(target, "name")).isEqualTo("before");

        ClassUtils.setFieldValue(target, "name", "after");

        assertThat(ClassUtils.getFieldValue(target, "name")).isEqualTo("after");
        assertThat(target.getName()).isEqualTo("after");
    }

    @Test
    void gettableFieldsAreMappedFromPublicGetters() {
        GettableTarget target = new GettableTarget();

        List<String> gettableFields = ClassUtils.getGettableField(target, "flyway.");
        Map<String, String> gettableFieldValues = ClassUtils.getGettableFieldValues(target, "flyway.");

        assertThat(gettableFields).contains("flyway.name", "flyway.count", "flyway.inherited");
        assertThat(gettableFields).doesNotContain("flyway.secret");
        assertThat(gettableFieldValues)
                .containsEntry("flyway.name", "configured")
                .containsEntry("flyway.count", "7")
                .containsEntry("flyway.inherited", "parent");
        assertThat(gettableFieldValues).doesNotContainKey("flyway.secret");
    }

    public static class LoadableRunnable implements Runnable {
        public LoadableRunnable() {
        }

        @Override
        public void run() {
        }
    }

    public static class NonRunnable {
    }

    public static class StaticFieldTarget {
        public static String VALUE = "static field value";
    }

    private static class MutableFieldTarget {
        private String name = "before";

        String getName() {
            return name;
        }
    }

    private static class GettableParent {
        public String getInherited() {
            return "parent";
        }
    }

    private static class GettableTarget extends GettableParent {
        public String getName() {
            return "configured";
        }

        public Integer getCount() {
            return 7;
        }

        @DoNotMapForLogging
        public String getSecret() {
            return "redacted";
        }
    }
}
