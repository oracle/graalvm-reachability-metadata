/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_baomidou.dynamic_datasource_creator;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.DatasourceInitProperties;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class DatasourceInitPropertiesTest {
    @Test
    void bindsNestedInitializationPropertiesThroughJavaBeansAccessors() throws Exception {
        DataSourceProperty dataSource = new DataSourceProperty();
        DatasourceInitProperties init = new DatasourceInitProperties();

        PropertyDescriptor initProperty = descriptor(DataSourceProperty.class, "init");
        initProperty.getWriteMethod().invoke(dataSource, init);
        DatasourceInitProperties bound =
                (DatasourceInitProperties) initProperty.getReadMethod().invoke(dataSource);

        write(bound, "schema", "schema.sql");
        write(bound, "data", "data.sql");
        write(bound, "continueOnError", false);
        write(bound, "separator", "@@");

        assertThat(read(bound, "schema")).isEqualTo("schema.sql");
        assertThat(read(bound, "data")).isEqualTo("data.sql");
        assertThat(read(bound, "continueOnError")).isEqualTo(false);
        assertThat(read(bound, "separator")).isEqualTo("@@");
    }

    private static void write(DatasourceInitProperties target, String name, Object value)
            throws Exception {
        descriptor(DatasourceInitProperties.class, name).getWriteMethod().invoke(target, value);
    }

    private static Object read(DatasourceInitProperties target, String name) throws Exception {
        return descriptor(DatasourceInitProperties.class, name).getReadMethod().invoke(target);
    }

    private static PropertyDescriptor descriptor(Class<?> beanType, String name) throws Exception {
        return Arrays.stream(Introspector.getBeanInfo(beanType).getPropertyDescriptors())
                .filter(property -> property.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing JavaBeans property: " + name));
    }
}
