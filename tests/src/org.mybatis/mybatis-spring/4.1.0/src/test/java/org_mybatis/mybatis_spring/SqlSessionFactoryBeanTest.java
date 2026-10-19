/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis_spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.PropertyDescriptor;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.BeanWrapperImpl;

public class SqlSessionFactoryBeanTest {

    @Test
    void configuresSqlSessionFactoryBeanThroughSpringBeanWrapper() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:bean_wrapper;DB_CLOSE_DELAY=-1");

        BeanWrapperImpl beanWrapper = new BeanWrapperImpl(SqlSessionFactoryBean.class);
        assertThat(beanWrapper.getPropertyDescriptors()).extracting(PropertyDescriptor::getName)
                .contains("dataSource", "configuration");
        beanWrapper.setPropertyValue("dataSource", dataSource);

        SqlSessionFactoryBean factoryBean = (SqlSessionFactoryBean) beanWrapper.getWrappedInstance();
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        assertThat(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource()).isSameAs(dataSource);
    }
}
