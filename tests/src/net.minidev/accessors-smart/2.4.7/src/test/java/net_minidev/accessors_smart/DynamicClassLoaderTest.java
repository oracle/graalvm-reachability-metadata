/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.accessors_smart;

import java.util.Base64;

import net.minidev.asm.BeansAccess;
import net.minidev.asm.DynamicClassLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamicClassLoaderTest {
    private static final String RUNTIME_DEFINED_CLASS_NAME = "net_minidev.accessors_smart.generated.DynamicLoaderGenerated";
    private static final String RUNTIME_DEFINED_CLASS_BYTES =
            "yv66vgAAADQAEQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCAAIAQAP"
                    + "cnVudGltZS1kZWZpbmVkBwAKAQA8bmV0X21pbmlkZXYvYWNjZXNzb3JzX3NtYXJ0L2dlbmVyYXRlZC9EeW5h"
                    + "bWljTG9hZGVyR2VuZXJhdGVkAQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEACHRvU3RyaW5nAQAUKClMamF2"
                    + "YS9sYW5nL1N0cmluZzsBAApTb3VyY2VGaWxlAQAbRHluYW1pY0xvYWRlckdlbmVyYXRlZC5qYXZhACEACQAC"
                    + "AAAAAAACAAEABQAGAAEACwAAAB0AAQABAAAABSq3AAGxAAAAAQAMAAAABgABAAAAAwABAA0ADgABAAsAAAAb"
                    + "AAEAAQAAAAMSB7AAAAABAAwAAAAGAAEAAAAGAAEADwAAAAIAEA==";

    @Test
    void generatesAccessorClassesWhenNoPrebuiltAccessorExists() {
        BeansAccess<RuntimeGeneratedBean> access = BeansAccess.get(RuntimeGeneratedBean.class);
        RuntimeGeneratedBean bean = access.newInstance();

        access.set(bean, "count", "7");
        access.set(bean, "label", "generated");

        assertThat(access.getClass().getName()).isEqualTo(RuntimeGeneratedBean.class.getName() + "AccAccess");
        assertThat(bean.getCount()).isEqualTo(7);
        assertThat(bean.getLabel()).isEqualTo("generated");
        assertThat(access.get(bean, "count")).isEqualTo(7);
        assertThat(access.get(bean, "label")).isEqualTo("generated");
    }

    @Test
    void directlyDefinesAndInstantiatesGeneratedClasses() throws InstantiationException, IllegalAccessException {
        Object instance = directInstance();

        assertThat(instance.getClass().getName()).isEqualTo(RUNTIME_DEFINED_CLASS_NAME);
        assertThat(instance.toString()).isEqualTo("runtime-defined");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object directInstance() throws InstantiationException, IllegalAccessException {
        return DynamicClassLoader.directInstance(
                (Class) DynamicClassLoaderTest.class,
                RUNTIME_DEFINED_CLASS_NAME,
                Base64.getDecoder().decode(RUNTIME_DEFINED_CLASS_BYTES));
    }

    public static final class RuntimeGeneratedBean {
        private int count;
        private String label;

        public int getCount() {
            return this.count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getLabel() {
            return this.label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }
}
