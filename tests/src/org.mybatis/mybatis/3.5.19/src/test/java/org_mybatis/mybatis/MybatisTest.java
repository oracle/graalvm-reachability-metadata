/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.scripting.defaults.RawLanguageDriver;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MybatisTest {
    @Test
    void retrievesDataFromH2() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:mybatis;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        Configuration configuration = new Configuration(
                new Environment("h2", new JdbcTransactionFactory(), dataSource));
        assertEquals("org.apache.ibatis.executor.loader.javassist.JavassistProxyFactory",
                configuration.getProxyFactory().getClass().getName());
        XMLLanguageDriver languageDriver = assertInstanceOf(
                XMLLanguageDriver.class,
                configuration.getDefaultScriptingLanguageInstance());
        assertInstanceOf(XMLLanguageDriver.class,
                configuration.getLanguageRegistry().getDriver(XMLLanguageDriver.class));
        assertInstanceOf(RawLanguageDriver.class,
                configuration.getLanguageRegistry().getDriver(RawLanguageDriver.class));

        configuration.addMapper(UserMapper.class);
        assertTrue(configuration.getMapperRegistry().hasMapper(UserMapper.class));

        SqlSessionFactory sessionFactory = new SqlSessionFactoryBuilder().build(configuration);

        try (SqlSession session = sessionFactory.openSession()) {
            session.getConnection().createStatement().execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(255))");
            UserMapper userMapper = session.getMapper(UserMapper.class);
            userMapper.insertUser(1, "Ada");
            session.commit();

            assertEquals("Ada", userMapper.findUserName(1));
        }
    }
}
