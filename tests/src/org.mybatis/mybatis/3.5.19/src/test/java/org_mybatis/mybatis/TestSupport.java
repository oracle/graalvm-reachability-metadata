/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

public final class TestSupport {
    private TestSupport() {
    }

    public static UnpooledDataSource dataSource() {
        return new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
    }

    public static Configuration configuration() {
        return new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource()));
    }

    public static SqlSessionFactory factory(Class<?> mapper) {
        Configuration configuration = configuration();
        configuration.addMapper(mapper);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    public static final class RecordingLog implements Log {
        public final List<String> messages = new ArrayList<>();

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public boolean isTraceEnabled() {
            return true;
        }

        @Override
        public void error(String message, Throwable cause) {
            messages.add(message);
        }

        @Override
        public void error(String message) {
            messages.add(message);
        }

        @Override
        public void debug(String message) {
            messages.add(message);
        }

        @Override
        public void trace(String message) {
            messages.add(message);
        }

        @Override
        public void warn(String message) {
            messages.add(message);
        }
    }
}
