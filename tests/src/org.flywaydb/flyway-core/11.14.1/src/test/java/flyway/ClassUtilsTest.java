/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.ProgressModel;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.extensibility.Plugin;
import org.flywaydb.core.internal.resource.CoreResourceTypeProvider;
import org.flywaydb.core.internal.util.ClassUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {

    @Test
    void checksPresenceAndLoadsImplementations() throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        assertThat(ClassUtils.isPresent(Location.class.getName(), classLoader)).isTrue();
        assertThat(ClassUtils.isImplementationPresent(Plugin.class.getName(), classLoader)).isTrue();
        assertThat(ClassUtils.loadClass(Plugin.class, CoreResourceTypeProvider.class.getName(), classLoader))
                .isEqualTo(CoreResourceTypeProvider.class);
    }

    @Test
    void readsStaticFieldsByName() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        assertThat(ClassUtils.getStaticFieldValue(Location.class.getName(), "FILESYSTEM_PREFIX", classLoader))
                .isEqualTo("filesystem:");
    }

    @Test
    void getsAndSetsDeclaredFields() {
        ProgressModel progress = new ProgressModel();

        ClassUtils.setFieldValue(progress, "operation", "migrate");

        assertThat(ClassUtils.getFieldValue(progress, "operation")).isEqualTo("migrate");
    }

    @Test
    void readsGettableFieldsAndValues() {
        ProgressModel progress = new ProgressModel();
        progress.setOperation("migrate");
        progress.setMessage("running");
        progress.setTotalSteps(2);

        List<String> fields = ClassUtils.getGettableField(progress, "progress.");
        Map<String, String> values = ClassUtils.getGettableFieldValues(progress, "progress.");

        assertThat(fields)
                .contains(
                        "progress.operation",
                        "progress.message",
                        "progress.totalSteps",
                        "progress.tag");
        assertThat(values)
                .containsEntry("progress.operation", "migrate")
                .containsEntry("progress.message", "running")
                .containsEntry("progress.totalSteps", "2")
                .containsEntry("progress.tag", "progress");
    }
}
