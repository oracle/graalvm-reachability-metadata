/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis_spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.support.GenericApplicationContext;

public class SqlSessionTemplateTest {

    @Test
    void exposesFactoryForSpringWiringAndExecutesQueryThroughSessionProxy() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:spring_template");
        Configuration configuration = new Configuration();
        ResultMap resultMap = new ResultMap.Builder(configuration, "answerResult", Integer.class, List.of()).build();
        MappedStatement statement = new MappedStatement.Builder(configuration, "answer",
                new StaticSqlSource(configuration, "SELECT 41 + 1"), SqlCommandType.SELECT)
                .resultMaps(List.of(resultMap)).build();
        configuration.addMappedStatement(statement);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        SqlSessionFactory factory = factoryBean.getObject();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("sessionTemplate", SqlSessionTemplate.class, () -> new SqlSessionTemplate(factory));
            context.registerBeanDefinition("templateFactory",
                    BeanDefinitionBuilder.genericBeanDefinition(SqlSessionFactory.class)
                            .setFactoryMethodOnBean("getSqlSessionFactory", "sessionTemplate").getBeanDefinition());
            context.refresh();

            assertThat(context.getBean("templateFactory", SqlSessionFactory.class)).isSameAs(factory);
            SqlSessionTemplate template = context.getBean("sessionTemplate", SqlSessionTemplate.class);
            Integer answer = template.selectOne("answer");
            assertThat(answer).isEqualTo(42);
            assertThat(template.<Integer>selectList("answer")).containsExactly(42);
        }
    }
}
