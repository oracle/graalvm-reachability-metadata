/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashMap;
import java.util.Map;

import net.sf.cglib.beans.BeanGenerator;
import net.sf.cglib.beans.BeanMap;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class BeanGeneratorTest {
    @Test
    void createsBeanWithConfiguredProperties() {
        try {
            BeanGenerator generator = new BeanGenerator();
            generator.addProperty("name", String.class);
            generator.addProperty("active", Boolean.TYPE);

            Object bean = generator.create();
            BeanMap beanMap = BeanMap.create(bean);
            beanMap.put("name", "generated");
            beanMap.put("active", Boolean.TRUE);

            assertThat(beanMap.keySet()).containsExactlyInAnyOrder("name", "active");
            assertThat(beanMap.get("name")).isEqualTo("generated");
            assertThat(beanMap.get("active")).isEqualTo(Boolean.TRUE);
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    @Test
    void copiesPropertiesFromMapAndRejectsDuplicates() {
        try {
            BeanGenerator generator = new BeanGenerator();
            Map<String, Class<?>> properties = new HashMap<String, Class<?>>();
            properties.put("count", Integer.TYPE);
            properties.put("description", String.class);

            BeanGenerator.addProperties(generator, properties);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> generator.addProperty("count", Long.TYPE))
                    .withMessageContaining("Duplicate property name");
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    private static boolean isUnsupportedNativeImageDynamicClassLoading(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
