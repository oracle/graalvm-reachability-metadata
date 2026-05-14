/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.api.migration.baseline.BaselineMigrationConfigurationExtension;
import org.flywaydb.core.extensibility.Plugin;
import org.flywaydb.core.extensibility.ResourceTypeProvider;
import org.flywaydb.core.internal.proprietaryStubs.PrepareCommandExtensionStub;
import org.flywaydb.core.internal.publishing.PublishingConfigurationExtension;
import org.flywaydb.core.internal.resource.CoreResourceTypeProvider;
import org.flywaydb.core.internal.util.ClassUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {

    private final ClassLoader classLoader = getClass().getClassLoader();

    @Test
    void detectsClassesAndServiceImplementationsUsingConfiguredClassLoader() {
        assertThat(ClassUtils.isPresent("org.flywaydb.core.Flyway", classLoader)).isTrue();
        assertThat(ClassUtils.isPresent("org.flywaydb.core.DoesNotExist", classLoader)).isFalse();
        assertThat(ClassUtils.isImplementationPresent(Plugin.class.getName(), classLoader)).isTrue();
    }

    @Test
    void loadsConcretePluginClassAndVerifiesInterfaceCompatibility() throws Exception {
        Class<? extends ResourceTypeProvider> resourceTypeProviderClass = ClassUtils.loadClass(
                ResourceTypeProvider.class,
                CoreResourceTypeProvider.class.getName(),
                classLoader);
        Class<? extends ResourceTypeProvider> incompatibleClass = ClassUtils.loadClass(
                ResourceTypeProvider.class,
                PrepareCommandExtensionStub.class.getName(),
                classLoader);

        assertThat(resourceTypeProviderClass).isEqualTo(CoreResourceTypeProvider.class);
        assertThat(incompatibleClass).isNull();
    }

    @Test
    void readsPublicStaticFieldValueByClassName() {
        String command = ClassUtils.getStaticFieldValue(
                PrepareCommandExtensionStub.class.getName(),
                "COMMAND",
                classLoader);

        assertThat(command).isEqualTo("prepare");
    }

    @Test
    void readsAndWritesPrivateInstanceFields() {
        PublishingConfigurationExtension extension = new PublishingConfigurationExtension();

        assertThat(ClassUtils.getFieldValue(extension, "publishResult")).isEqualTo(false);

        ClassUtils.setFieldValue(extension, "publishResult", true);

        assertThat(extension.isPublishResult()).isTrue();
        assertThat(ClassUtils.getFieldValue(extension, "publishResult")).isEqualTo(true);
    }

    @Test
    void discoversGettableFieldNamesAcrossPublicConfigurationExtension() {
        BaselineMigrationConfigurationExtension extension = new BaselineMigrationConfigurationExtension();

        List<String> fields = ClassUtils.getGettableField(extension, "flyway.");

        assertThat(fields).contains("flyway.baselineMigrationPrefix", "flyway.namespace");
    }

    @Test
    void invokesGettableFieldAccessorsAcrossPublicConfigurationExtension() {
        BaselineMigrationConfigurationExtension extension = new BaselineMigrationConfigurationExtension();
        extension.setBaselineMigrationPrefix("BL");

        Map<String, String> fieldValues = ClassUtils.getGettableFieldValues(extension, "flyway.");

        assertThat(fieldValues).containsEntry("flyway.baselineMigrationPrefix", "BL");
        assertThat(fieldValues).containsEntry("flyway.namespace", "");
    }
}
