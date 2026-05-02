/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Parameter;
import org.apache.tools.ant.types.selectors.modifiedselector.ModifiedSelector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ModifiedSelectorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void configuresNamedImplementationsThroughModifiedSelectorDynamicLoading() throws Exception {
        Project project = new Project();
        project.setBaseDir(temporaryDirectory.toFile());
        Path selectedFile = Files.writeString(temporaryDirectory.resolve("input.txt"), "content");

        ModifiedSelector selector = new ResettingModifiedSelector();
        selector.setProject(project);
        selector.setAlgorithm(algorithmName("hashvalue"));
        selector.setCache(cacheName("propertyfile"));
        selector.setComparator(comparatorName("equal"));
        selector.addParam(updateDisabledParameter());

        boolean selected = selector.isSelected(
                temporaryDirectory.toFile(),
                selectedFile.getFileName().toString(),
                selectedFile.toFile());

        assertThat(selected).isTrue();
        assertThat(selector.getAlgorithm()).isInstanceOf(
                org.apache.tools.ant.types.selectors.cacheselector.HashvalueAlgorithm.class);
        assertThat(selector.getCache()).isInstanceOf(
                org.apache.tools.ant.types.selectors.cacheselector.PropertiesfileCache.class);
        assertThat(selector.getComparator()).isInstanceOf(
                org.apache.tools.ant.types.selectors.cacheselector.EqualComparator.class);
    }

    private static ModifiedSelector.AlgorithmName algorithmName(String value) {
        ModifiedSelector.AlgorithmName name = new ModifiedSelector.AlgorithmName();
        name.setValue(value);
        return name;
    }

    private static ModifiedSelector.CacheName cacheName(String value) {
        ModifiedSelector.CacheName name = new ModifiedSelector.CacheName();
        name.setValue(value);
        return name;
    }

    private static ModifiedSelector.ComparatorName comparatorName(String value) {
        ModifiedSelector.ComparatorName name = new ModifiedSelector.ComparatorName();
        name.setValue(value);
        return name;
    }

    private static Parameter updateDisabledParameter() {
        Parameter parameter = new Parameter();
        parameter.setName("update");
        parameter.setValue("false");
        return parameter;
    }

    private static final class ResettingModifiedSelector extends ModifiedSelector {
        @Override
        public void useParameter(Parameter parameter) {
            super.useParameter(parameter);
            resetDefaultImplementations();
        }

        private void resetDefaultImplementations() {
            setField("algorithm", null);
            setField("cache", null);
            setField("comparator", null);
        }

        private void setField(String name, Object value) {
            try {
                Field field = ModifiedSelector.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(this, value);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(
                        "Unable to prepare ModifiedSelector state", exception);
            }
        }
    }
}
