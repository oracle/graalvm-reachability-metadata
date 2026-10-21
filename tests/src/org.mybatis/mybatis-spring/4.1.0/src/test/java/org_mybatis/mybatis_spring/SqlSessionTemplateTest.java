/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis_spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;

public class SqlSessionTemplateTest {

    @Test
    void readsSqlSessionFactoryThroughMyBatisMetaObject() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:meta_object;DB_CLOSE_DELAY=-1");
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        SqlSessionTemplate template = new SqlSessionTemplate(sqlSessionFactory);

        Object value = SystemMetaObject.forObject(template).getValue("sqlSessionFactory");

        assertThat(value).isSameAs(sqlSessionFactory);
    }
}
