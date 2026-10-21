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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

public class DatasourceInitPropertiesTest {
    @Test
    void bindsDatasourceInitializationPropertiesFromExternalConfiguration() {
        Map<String, String> properties =
                Map.of(
                        "datasource.init.schema", "schema.sql",
                        "datasource.init.data", "data.sql",
                        "datasource.init.continue-on-error", "false",
                        "datasource.init.separator", "@@");
        Binder binder = new Binder(new MapConfigurationPropertySource(properties));

        DataSourceProperty dataSource =
                binder.bind("datasource", Bindable.of(DataSourceProperty.class)).get();
        DatasourceInitProperties init = dataSource.getInit();

        assertThat(init.getSchema()).isEqualTo("schema.sql");
        assertThat(init.getData()).isEqualTo("data.sql");
        assertThat(init.isContinueOnError()).isFalse();
        assertThat(init.getSeparator()).isEqualTo("@@");
    }
}
