/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.api.logging.LogCreator;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.internal.util.ClassUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {
    private static final String SLF4J_LOG_CREATOR = "org.flywaydb.core.internal.logging.slf4j.Slf4jLogCreator";

    @Test
    void instantiatesDefaultConstructableClassesByName() {
        ClassLoader classLoader = ClassUtilsTest.class.getClassLoader();

        Object logCreator = ClassUtils.instantiate(SLF4J_LOG_CREATOR, classLoader);

        assertThat(logCreator).isInstanceOf(LogCreator.class);
    }

    @Test
    void detectsPresentClassesWithTheProvidedClassLoader() {
        ClassLoader classLoader = ClassUtilsTest.class.getClassLoader();

        assertThat(ClassUtils.isPresent(LogCreator.class.getName(), classLoader)).isTrue();
        assertThat(ClassUtils.isPresent("org.flywaydb.core.DoesNotExist", classLoader)).isFalse();
    }

    @Test
    void detectsWhetherServiceImplementationIsPresent() {
        ClassLoader classLoader = ClassUtilsTest.class.getClassLoader();

        assertThat(ClassUtils.isImplementationPresent(LogCreator.class.getName(), classLoader)).isFalse();
    }

    @Test
    void loadsConcreteImplementationsAndVerifiesTheyCanBeConstructed() throws Exception {
        ClassLoader classLoader = ClassUtilsTest.class.getClassLoader();

        Class<? extends LogCreator> loadedClass = ClassUtils.loadClass(
                LogCreator.class,
                SLF4J_LOG_CREATOR,
                classLoader);

        assertThat(loadedClass).isNotNull();
        assertThat(loadedClass.getName()).isEqualTo(SLF4J_LOG_CREATOR);
    }

    @Test
    void readsPublicStaticStringFieldsByName() {
        Assumptions.assumeFalse(isNativeImageRuntime());
        ClassLoader classLoader = ClassUtilsTest.class.getClassLoader();

        String value = ClassUtils.getStaticFieldValue(MigrateResult.class.getName(), "COMMAND", classLoader);

        assertThat(value).isEqualTo("migrate");
    }

    @Test
    void readsAndWritesDeclaredFields() {
        Assumptions.assumeFalse(isNativeImageRuntime());
        MigrateResult migrateResult = new MigrateResult();
        migrateResult.database = "initial";

        assertThat(ClassUtils.getFieldValue(migrateResult, "database")).isEqualTo("initial");

        ClassUtils.setFieldValue(migrateResult, "database", "updated");

        assertThat(migrateResult.database).isEqualTo("updated");
        assertThat(ClassUtils.getFieldValue(migrateResult, "database")).isEqualTo("updated");
    }

    @Test
    void listsGettableFieldsFromClassHierarchy() {
        Assumptions.assumeFalse(isNativeImageRuntime());
        MigrateResult migrateResult = new MigrateResult();

        List<String> fields = ClassUtils.getGettableField(migrateResult, "flyway.");

        assertThat(fields).contains(
                "flyway.operation",
                "flyway.timestamp",
                "flyway.pendingMigrations",
                "flyway.successfulMigrations",
                "flyway.failedMigrations",
                "flyway.totalMigrationTime");
        assertThat(fields).doesNotContain("flyway.class");
    }

    @Test
    void readsGettableFieldValuesFromClassHierarchy() {
        Assumptions.assumeFalse(isNativeImageRuntime());
        MigrateResult migrateResult = new MigrateResult();

        Map<String, String> fieldValues = ClassUtils.getGettableFieldValues(migrateResult, "flyway.");

        assertThat(fieldValues).containsEntry("flyway.operation", "migrate")
                .containsEntry("flyway.pendingMigrations", "[]")
                .containsEntry("flyway.successfulMigrations", "[]")
                .containsEntry("flyway.failedMigrations", "[]")
                .containsEntry("flyway.totalMigrationTime", "0");
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
