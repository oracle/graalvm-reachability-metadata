/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidDataSourceStatLoggerImpl;
import com.alibaba.druid.pool.JDBC4ValidConnectionChecker;
import com.alibaba.druid.pool.vendor.MySqlExceptionSorter;
import com.alibaba.druid.util.DruidPasswordCallback;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class DruidAbstractDataSourceTest {
    @Test
    void configuresExtensionTypesByClassName() throws Exception {
        DruidDataSource dataSource = new DruidDataSource();
        try {
            dataSource.setStatLoggerClassName(DruidDataSourceStatLoggerImpl.class.getName());
            dataSource.setValidConnectionCheckerClassName(JDBC4ValidConnectionChecker.class.getName());
            dataSource.setPasswordCallbackClassName(DruidPasswordCallback.class.getName());
            dataSource.setExceptionSorter(MySqlExceptionSorter.class.getName());

            assertInstanceOf(DruidDataSourceStatLoggerImpl.class, dataSource.getStatLogger());
            assertInstanceOf(JDBC4ValidConnectionChecker.class, dataSource.getValidConnectionChecker());
            assertInstanceOf(DruidPasswordCallback.class, dataSource.getPasswordCallback());
            assertInstanceOf(MySqlExceptionSorter.class, dataSource.getExceptionSorter());
        } finally {
            dataSource.close();
        }
    }
}
