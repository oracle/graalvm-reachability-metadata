/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis_spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.context.support.GenericApplicationContext;

public class ClassPathMapperScannerTest {

    @Test
    void scansMapperAndWiresFactoryUsingClassConstructorArgument() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:scanned_mapper");
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        SqlSessionFactory factory = factoryBean.getObject();

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            ClassPathMapperScanner scanner = new ClassPathMapperScanner(context, context.getEnvironment());
            scanner.setAnnotationClass(Mapper.class);
            scanner.setSqlSessionFactory(factory);
            scanner.setBeanNameGenerator((definition, registry) -> "scannedMapper");
            scanner.registerFilters();

            Set<BeanDefinitionHolder> scanned = scanner.doScan("org_mybatis.mybatis_spring");

            assertThat(scanned).hasSize(1);
            BeanDefinition definition = context.getBeanDefinition("scannedMapper");
            assertThat(definition.getBeanClassName()).isEqualTo(MapperFactoryBean.class.getName());
            assertThat(definition.getConstructorArgumentValues().getGenericArgumentValues()).hasSize(1);
            assertThat(definition.getConstructorArgumentValues().getGenericArgumentValues().get(0).getValue())
                    .isEqualTo(AnswerMapper.class);

            context.refresh();

            AnswerMapper mapper = context.getBean("scannedMapper", AnswerMapper.class);
            assertThat(mapper.answer()).isEqualTo(42);
            assertThat(context.getBean(AnswerMapper.class)).isSameAs(mapper);
            assertThat(factory.getConfiguration().hasMapper(AnswerMapper.class)).isTrue();
        }
    }

    @Mapper
    public interface AnswerMapper {
        @Select("SELECT 6 * 7")
        int answer();
    }
}
