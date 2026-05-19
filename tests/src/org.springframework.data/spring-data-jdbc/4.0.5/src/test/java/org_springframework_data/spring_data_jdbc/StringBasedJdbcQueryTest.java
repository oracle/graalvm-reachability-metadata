/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.convert.MappingJdbcConverter;
import org.springframework.data.jdbc.repository.query.JdbcQueryMethod;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.jdbc.repository.query.RowMapperFactory;
import org.springframework.data.jdbc.repository.query.StringBasedJdbcQuery;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.core.support.PropertiesBasedNamedQueries;
import org.springframework.data.repository.query.ValueExpressionDelegate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public class StringBasedJdbcQueryTest {

    @Test
    void stringQueryUsesConfiguredRowMapperClass() throws Exception {
        Account account = new Account(1L, "Ada");
        RecordingNamedParameterJdbcTemplate operations = new RecordingNamedParameterJdbcTemplate(account);
        RelationalMappingContext mappingContext = new RelationalMappingContext();
        MappingJdbcConverter converter = new MappingJdbcConverter(mappingContext, (identifier, path) -> List.of());
        RowMapperFactory rowMapperFactory = resultType -> new AccountRowMapper();

        StringBasedJdbcQuery query = new StringBasedJdbcQuery(queryMethod(mappingContext), operations, rowMapperFactory,
                converter, ValueExpressionDelegate.create());

        Object result = query.execute(new Object[0]);

        assertThat(result).isSameAs(account);
        assertThat(operations.rowMapperType()).isEqualTo(AccountRowMapper.class);
    }

    private static JdbcQueryMethod queryMethod(RelationalMappingContext mappingContext) throws NoSuchMethodException {
        Method method = AccountRepository.class.getMethod("findMappedAccount");
        return new JdbcQueryMethod(method, new DefaultRepositoryMetadata(AccountRepository.class),
                new SpelAwareProxyProjectionFactory(), PropertiesBasedNamedQueries.EMPTY, mappingContext);
    }

    interface AccountRepository extends Repository<Account, Long> {

        @Query(value = "select id, name from account", rowMapperClass = AccountRowMapper.class)
        Account findMappedAccount();
    }

    public static final class AccountRowMapper implements RowMapper<Object> {

        @Override
        public Object mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            return new Account(resultSet.getLong("id"), resultSet.getString("name"));
        }
    }

    static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private final Account account;
        private Class<?> rowMapperType;

        RecordingNamedParameterJdbcTemplate(Account account) {
            super(new DriverManagerDataSource());
            this.account = account;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            this.rowMapperType = rowMapper.getClass();
            return (T) account;
        }

        Class<?> rowMapperType() {
            return rowMapperType;
        }
    }

    static final class Account {

        @Id
        private final Long id;
        private final String name;

        Account(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        Long getId() {
            return id;
        }

        String getName() {
            return name;
        }
    }
}
