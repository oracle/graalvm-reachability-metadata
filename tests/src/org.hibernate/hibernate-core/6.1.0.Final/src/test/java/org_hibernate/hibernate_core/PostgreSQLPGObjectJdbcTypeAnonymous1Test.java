/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.dialect.PostgreSQLInetJdbcType;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.java.StringJavaType;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;

import static org.assertj.core.api.Assertions.assertThat;

public class PostgreSQLPGObjectJdbcTypeAnonymous1Test {

    @Test
    public void bindPreparedStatementValueCreatesTypedPostgreSQLObject() throws Exception {
        CapturingSetObjectHandler handler = new CapturingSetObjectHandler();
        PreparedStatement statement = proxy(PreparedStatement.class, handler);
        ValueBinder<String> binder = PostgreSQLInetJdbcType.INSTANCE.getBinder(StringJavaType.INSTANCE);

        binder.bind(statement, "192.0.2.10", 2, null);

        assertThat(handler.getParameter()).isEqualTo(2);
        assertThat(handler.getObject()).isInstanceOf(PGobject.class);
        PGobject holder = (PGobject) handler.getObject();
        assertThat(holder.getType()).isEqualTo("inet");
        assertThat(holder.getValue()).isEqualTo("192.0.2.10");
    }

    @Test
    public void bindCallableStatementValueCreatesTypedPostgreSQLObject() throws Exception {
        CapturingSetObjectHandler handler = new CapturingSetObjectHandler();
        CallableStatement statement = proxy(CallableStatement.class, handler);
        ValueBinder<String> binder = PostgreSQLInetJdbcType.INSTANCE.getBinder(StringJavaType.INSTANCE);

        binder.bind(statement, "2001:db8::1", "address", null);

        assertThat(handler.getParameter()).isEqualTo("address");
        assertThat(handler.getObject()).isInstanceOf(PGobject.class);
        PGobject holder = (PGobject) handler.getObject();
        assertThat(holder.getType()).isEqualTo("inet");
        assertThat(holder.getValue()).isEqualTo("2001:db8::1");
    }

    private static <T> T proxy(Class<T> interfaceType, InvocationHandler handler) {
        Object proxy = Proxy.newProxyInstance(
                interfaceType.getClassLoader(),
                new Class<?>[] {interfaceType},
                handler
        );
        return interfaceType.cast(proxy);
    }

    private static final class CapturingSetObjectHandler implements InvocationHandler {
        private Object parameter;
        private Object object;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            if ("setObject".equals(method.getName()) && args.length == 2) {
                parameter = args[0];
                object = args[1];
                return null;
            }
            throw new UnsupportedOperationException(method.toGenericString());
        }

        public Object getParameter() {
            return parameter;
        }

        public Object getObject() {
            return object;
        }

        private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "toString":
                    return getClass().getName();
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    throw new UnsupportedOperationException(method.toGenericString());
            }
        }
    }
}
