/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Vector;
import org.apache.tools.ant.types.Parameter;
import org.apache.tools.ant.types.selectors.cacheselector.EqualComparator;
import org.apache.tools.ant.types.selectors.cacheselector.HashvalueAlgorithm;
import org.apache.tools.ant.types.selectors.cacheselector.PropertiesfileCache;
import org.apache.tools.ant.types.selectors.modifiedselector.ModifiedSelector;
import org.junit.jupiter.api.Test;

public class ModifiedSelectorTest {
    @Test
    void configureInstantiatesNamedComponentsFromLegacySelectorNames() throws Exception {
        ModifiedSelector selector = new ModifiedSelector();
        NullingParameterVector parameters = new NullingParameterVector(selector);
        parameters.add(parameter("algorithm", "hashvalue"));
        parameters.add(parameter("cache", "propertyfile"));
        parameters.add(parameter("comparator", "equal"));
        setField(selector, "configParameter", parameters);

        selector.configure();

        assertThat(selector.getAlgorithm()).isInstanceOf(HashvalueAlgorithm.class);
        assertThat(selector.getCache()).isInstanceOf(PropertiesfileCache.class);
        assertThat(selector.getComparator()).isInstanceOf(EqualComparator.class);
    }

    private static Parameter parameter(String name, String value) {
        Parameter parameter = new Parameter();
        parameter.setName(name);
        parameter.setValue(value);
        return parameter;
    }

    private static void clearDefaultComponents(ModifiedSelector selector) {
        try {
            setField(selector, "algorithm", null);
            setField(selector, "cache", null);
            setField(selector, "comparator", null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = ModifiedSelector.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class NullingParameterVector extends Vector<Parameter> {
        private final ModifiedSelector selector;
        private boolean defaultsCleared;

        private NullingParameterVector(ModifiedSelector selector) {
            this.selector = selector;
        }

        @Override
        public synchronized Iterator<Parameter> iterator() {
            Iterator<Parameter> delegate = super.iterator();
            return new Iterator<Parameter>() {
                @Override
                public boolean hasNext() {
                    boolean hasNext = delegate.hasNext();
                    if (!hasNext && !defaultsCleared) {
                        defaultsCleared = true;
                        clearDefaultComponents(selector);
                    }
                    return hasNext;
                }

                @Override
                public Parameter next() {
                    return delegate.next();
                }
            };
        }
    }
}
