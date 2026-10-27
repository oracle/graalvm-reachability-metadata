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
import com.baomidou.dynamic.datasource.creator.hikaricp.HikariCpConfig;
import com.baomidou.dynamic.datasource.toolkit.ConfigMergeCreator;
import com.zaxxer.hikari.HikariConfig;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class ConfigMergeCreatorTest {
    @Test
    void mergesGlobalAndDatasourceSpecificHikariConfiguration() {
        HikariCpConfig global = new HikariCpConfig();
        global.setCatalog("global-catalog");
        global.setConnectionTimeout(30_000L);
        global.setMaxPoolSize(17);
        Properties globalProperties = new Properties();
        globalProperties.setProperty("global-only", "global-value");
        globalProperties.setProperty("overridden", "global-value");
        global.setDataSourceProperties(globalProperties);

        HikariCpConfig datasource = new HikariCpConfig();
        datasource.setCatalog("datasource-catalog");
        Properties datasourceProperties = new Properties();
        datasourceProperties.setProperty("datasource-only", "datasource-value");
        datasourceProperties.setProperty("overridden", "datasource-value");
        datasource.setDataSourceProperties(datasourceProperties);

        ConfigMergeCreator<HikariCpConfig, HikariConfig> creator =
                new ConfigMergeCreator<>("HikariCp", HikariCpConfig.class, HikariConfig.class);
        HikariConfig merged = creator.create(global, datasource);

        assertThat(merged.getCatalog()).isEqualTo("datasource-catalog");
        assertThat(merged.getConnectionTimeout()).isEqualTo(30_000L);
        assertThat(merged.getMaximumPoolSize()).isEqualTo(17);
        assertThat(merged.getDataSourceProperties())
                .containsEntry("global-only", "global-value")
                .containsEntry("datasource-only", "datasource-value")
                .containsEntry("overridden", "datasource-value");
    }

    @Test
    void mergesDatasourceInitializationPropertiesFromDataSourceProperty() {
        DataSourceProperty global = new DataSourceProperty();
        global.getInit().setSchema("global-schema.sql");
        global.getInit().setData("global-data.sql");
        global.getInit().setContinueOnError(false);
        global.getInit().setSeparator("@@");

        DataSourceProperty datasource = new DataSourceProperty();
        datasource.getInit().setSchema("datasource-schema.sql");
        datasource.getInit().setData(null);
        datasource.getInit().setContinueOnError(true);
        datasource.getInit().setSeparator(null);

        ConfigMergeCreator<DatasourceInitProperties, DatasourceInitProperties> creator =
                new ConfigMergeCreator<>(
                        "DatasourceInitProperties",
                        DatasourceInitProperties.class,
                        DatasourceInitProperties.class);
        DatasourceInitProperties merged = creator.create(global.getInit(), datasource.getInit());

        assertThat(merged.getSchema()).isEqualTo("datasource-schema.sql");
        assertThat(merged.getData()).isEqualTo("global-data.sql");
        assertThat(merged.isContinueOnError()).isTrue();
        assertThat(merged.getSeparator()).isEqualTo("@@");
    }
}
