/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class XMLConfigBuilderTest {
    @Test
    void parsesFactoriesPluginsAndDatabaseEnvironment() throws Exception {
        String xml = """
                <!DOCTYPE configuration PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
                    "https://mybatis.org/dtd/mybatis-3-config.dtd">
                <configuration>
                  <objectFactory type="org.apache.ibatis.reflection.factory.DefaultObjectFactory"/>
                  <objectWrapperFactory type="org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory"/>
                  <reflectorFactory type="org.apache.ibatis.reflection.DefaultReflectorFactory"/>
                  <plugins>
                    <plugin interceptor="org_mybatis.mybatis.XMLConfigBuilderTest$PassThrough"/>
                  </plugins>
                  <environments default="test">
                    <environment id="test">
                      <transactionManager type="JDBC"/>
                      <dataSource type="UNPOOLED">
                        <property name="driver" value="org.h2.Driver"/>
                        <property name="url" value="jdbc:h2:mem:xml-config"/>
                        <property name="username" value="sa"/>
                        <property name="password" value=""/>
                      </dataSource>
                    </environment>
                  </environments>
                  <databaseIdProvider type="DB_VENDOR">
                    <property name="H2" value="h2"/>
                  </databaseIdProvider>
                </configuration>
                """;
        Configuration configuration = new XMLConfigBuilder(new StringReader(xml)).parse();
        assertEquals("h2", configuration.getDatabaseId());
        assertEquals(1, configuration.getInterceptors().size());
        assertNotNull(configuration.getObjectFactory().create(Configuration.class));
        try (Connection connection = configuration.getEnvironment().getDataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT 23")) {
            rows.next();
            assertEquals(23, rows.getInt(1));
        }
    }

    @Intercepts(@Signature(type = ParameterHandler.class, method = "getParameterObject", args = {}))
    public static class PassThrough implements Interceptor {
        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            return invocation.proceed();
        }
    }
}
