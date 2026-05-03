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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {

    @Test
    void detectsClassesWithProvidedClassLoader() {
        ClassLoader classLoader = ClassUtilsTest.class.getClassLoader();

        assertThat(ClassUtils.isPresent(ClassUtilsTest.class.getName(), classLoader)).isTrue();
        assertThat(ClassUtils.isPresent("flyway.DoesNotExist", classLoader)).isFalse();
    }

    @Test
    void detectsMissingServiceImplementationsWithProvidedClassLoader() {
        ClassLoader classLoader = ClassUtilsTest.class.getClassLoader();

        assertThat(ClassUtils.isImplementationPresent(Runnable.class.getName(), classLoader)).isFalse();
    }

    @Test
    void loadsConcreteImplementationClass() throws Exception {
        ClassLoader classLoader = ClassUtilsTest.class.getClassLoader();

        Class<? extends LoadableService> loadedClass = ClassUtils.loadClass(
                LoadableService.class,
                LoadableImplementation.class.getName(),
                classLoader);

        assertThat(loadedClass).isEqualTo(LoadableImplementation.class);
    }

    @Test
    void obtainsPublicStaticFieldValue() {
        ClassLoader classLoader = ClassUtilsTest.class.getClassLoader();

        String value = ClassUtils.getStaticFieldValue(StaticFields.class.getName(), "publicValue", classLoader);

        assertThat(value).isEqualTo("static-value");
    }

    @Test
    void readsAndUpdatesDeclaredFieldValue() {
        FieldHolder holder = new FieldHolder("initial");

        assertThat(ClassUtils.getFieldValue(holder, "value")).isEqualTo("initial");

        ClassUtils.setFieldValue(holder, "value", "updated");

        assertThat(ClassUtils.getFieldValue(holder, "value")).isEqualTo("updated");
    }

    @Test
    void listsGettableFieldsFromClassHierarchy() {
        GetterChild target = new GetterChild("child", "parent");

        List<String> fields = ClassUtils.getGettableField(target, "config.");

        assertThat(fields).containsExactlyInAnyOrder("config.childValue", "config.parentValue");
    }

    @Test
    void obtainsGettableFieldValuesFromClassHierarchy() {
        GetterChild target = new GetterChild("child", "parent");

        Map<String, String> values = ClassUtils.getGettableFieldValues(target, "config.");

        assertThat(values)
                .containsEntry("config.childValue", "child")
                .containsEntry("config.parentValue", "parent");
    }

    public interface LoadableService {
    }

    public static class LoadableImplementation implements LoadableService {
        public LoadableImplementation() {
        }
    }

    public static class StaticFields {
        public static String publicValue = "static-value";
    }

    private static class FieldHolder {
        private String value;

        FieldHolder(String value) {
            this.value = value;
        }
    }

    private static class GetterParent {
        private final String parentValue;

        GetterParent(String parentValue) {
            this.parentValue = parentValue;
        }

        private String getParentValue() {
            return parentValue;
        }
    }

    private static class GetterChild extends GetterParent {
        private final String childValue;

        GetterChild(String childValue, String parentValue) {
            super(parentValue);
            this.childValue = childValue;
        }

        private String getChildValue() {
            return childValue;
        }
    }
}
