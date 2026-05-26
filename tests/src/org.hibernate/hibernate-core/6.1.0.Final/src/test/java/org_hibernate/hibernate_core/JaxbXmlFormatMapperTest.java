/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.engine.jdbc.LobCreator;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.JaxbXmlFormatMapper;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;
import org.hibernate.type.descriptor.java.ArrayJavaType;
import org.hibernate.type.descriptor.java.IntegerPrimitiveArrayJavaType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.spi.UnknownBasicJavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;
import org.hibernate.type.spi.TypeConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Types;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

public class JaxbXmlFormatMapperTest {

    private static final WrapperOptions WRAPPER_OPTIONS = new TypeConfigurationWrapperOptions();

    @Test
    public void fromStringDeserializesObjectArrayWithUnknownElementJavaType() {
        JaxbXmlFormatMapper mapper = new JaxbXmlFormatMapper();
        JavaType<JaxbXmlFormatMapper.MapWrapper[]> javaType = new ArrayJavaType<>(
                new UnknownBasicJavaType<>(JaxbXmlFormatMapper.MapWrapper.class)
        );
        JaxbXmlFormatMapper.MapWrapper[] source = new JaxbXmlFormatMapper.MapWrapper[] {
                new JaxbXmlFormatMapper.MapWrapper(),
                new JaxbXmlFormatMapper.MapWrapper()
        };

        String xml = mapper.toString(source, javaType, WRAPPER_OPTIONS);
        JaxbXmlFormatMapper.MapWrapper[] result = mapper.fromString(xml, javaType, WRAPPER_OPTIONS);

        assertThat(result).hasSize(2).doesNotContainNull();
    }

    @Test
    public void fromStringDeserializesPrimitiveArray() {
        JaxbXmlFormatMapper mapper = new JaxbXmlFormatMapper();
        String xml = """
                <Collection>
                    <value>7</value>
                    <value>11</value>
                </Collection>
                """;

        int[] result = mapper.fromString(
                xml,
                IntegerPrimitiveArrayJavaType.INSTANCE,
                WRAPPER_OPTIONS
        );

        assertThat(result).containsExactly(7, 11);
    }

    @Test
    public void toStringSerializesNullObjectArrayWithNonPluralJavaType() {
        JaxbXmlFormatMapper mapper = new JaxbXmlFormatMapper();
        JavaType<JaxbXmlFormatMapper.MapWrapper[]> javaType = new MapWrapperArrayJavaType();
        JaxbXmlFormatMapper.MapWrapper[] source = new JaxbXmlFormatMapper.MapWrapper[] {null};

        String xml = mapper.toString(source, javaType, WRAPPER_OPTIONS);

        assertThat(xml).contains("Collection").contains("value");
    }

    private static final class MapWrapperArrayJavaType
            extends AbstractClassJavaType<JaxbXmlFormatMapper.MapWrapper[]> {
        private MapWrapperArrayJavaType() {
            super(JaxbXmlFormatMapper.MapWrapper[].class);
        }

        @Override
        public JdbcType getRecommendedJdbcType(JdbcTypeIndicators context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JaxbXmlFormatMapper.MapWrapper[] fromString(CharSequence string) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <X> X unwrap(JaxbXmlFormatMapper.MapWrapper[] value, Class<X> type, WrapperOptions options) {
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            throw unknownUnwrap(type);
        }

        @Override
        public <X> JaxbXmlFormatMapper.MapWrapper[] wrap(X value, WrapperOptions options) {
            if (value == null) {
                return null;
            }
            if (getJavaTypeClass().isInstance(value)) {
                return getJavaTypeClass().cast(value);
            }
            throw unknownWrap(value.getClass());
        }
    }

    private static final class TypeConfigurationWrapperOptions implements WrapperOptions {
        private final SessionFactoryImplementor sessionFactory;

        private TypeConfigurationWrapperOptions() {
            TypeConfiguration typeConfiguration = new TypeConfiguration();
            sessionFactory = proxySessionFactory(typeConfiguration);
        }

        @Override
        public SharedSessionContractImplementor getSession() {
            return null;
        }

        @Override
        public SessionFactoryImplementor getSessionFactory() {
            return sessionFactory;
        }

        @Override
        public boolean useStreamForLobBinding() {
            return false;
        }

        @Override
        public int getPreferredSqlTypeCodeForBoolean() {
            return Types.BOOLEAN;
        }

        @Override
        public LobCreator getLobCreator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TimeZone getJdbcTimeZone() {
            return TimeZone.getTimeZone("UTC");
        }

        private static SessionFactoryImplementor proxySessionFactory(TypeConfiguration typeConfiguration) {
            InvocationHandler handler = new TypeConfigurationInvocationHandler(typeConfiguration);
            Object proxy = Proxy.newProxyInstance(
                    SessionFactoryImplementor.class.getClassLoader(),
                    new Class<?>[] {SessionFactoryImplementor.class},
                    handler
            );
            return SessionFactoryImplementor.class.cast(proxy);
        }
    }

    private static final class TypeConfigurationInvocationHandler implements InvocationHandler {
        private final TypeConfiguration typeConfiguration;

        private TypeConfigurationInvocationHandler(TypeConfiguration typeConfiguration) {
            this.typeConfiguration = typeConfiguration;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            if ("getTypeConfiguration".equals(method.getName()) && method.getParameterCount() == 0) {
                return typeConfiguration;
            }
            throw new UnsupportedOperationException(method.toGenericString());
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
