/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_base_engine;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.engine.DefaultBeanIntrospection;
import org.apache.camel.impl.engine.DefaultStreamCachingStrategy;
import org.apache.camel.spi.BeanIntrospection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IntrospectionSupportTest {
    @Test
    void cacheClassAndGetPropertiesIntrospectPublicAccessors() {
        BeanIntrospection introspection = new DefaultBeanIntrospection();
        DefaultStreamCachingStrategy strategy = new DefaultStreamCachingStrategy();
        strategy.setEnabled(true);
        strategy.setSpoolThreshold(4096L);

        BeanIntrospection.ClassInfo classInfo = introspection.cacheClass(DefaultStreamCachingStrategy.class);
        Map<String, Object> properties = new LinkedHashMap<>();
        boolean foundProperties = introspection.getProperties(strategy, properties, "camel.", false);

        assertThat(classInfo.clazz).isEqualTo(DefaultStreamCachingStrategy.class);
        assertThat(classInfo.methods).isNotEmpty();
        assertThat(foundProperties).isTrue();
        assertThat(properties)
                .containsEntry("camel.enabled", true)
                .containsEntry("camel.spoolThreshold", 4096L);
    }

    @Test
    void propertyGetterAndSetterLookupSupportsBooleanNormalAndIgnoreCaseNames() throws Exception {
        BeanIntrospection introspection = new DefaultBeanIntrospection();

        Method ignoreCaseGetter = introspection.getPropertyGetter(DefaultStreamCachingStrategy.class, "ENABLED", true);
        Method booleanGetter = introspection.getPropertyGetter(DefaultStreamCachingStrategy.class, "enabled", false);
        Method normalGetter = introspection.getPropertyGetter(
                DefaultStreamCachingStrategy.class, "spoolThreshold", false);
        Method setter = introspection.getPropertySetter(DefaultStreamCachingStrategy.class, "enabled");

        assertThat(ignoreCaseGetter.getName()).isEqualTo("isEnabled");
        assertThat(booleanGetter.getName()).isEqualTo("isEnabled");
        assertThat(normalGetter.getName()).isEqualTo("getSpoolThreshold");
        assertThat(setter.getName()).isEqualTo("setEnabled");
    }

    @Test
    void getOrElsePropertyInvokesCaseSensitiveAndIgnoreCaseGetters() {
        BeanIntrospection introspection = new DefaultBeanIntrospection();
        DefaultStreamCachingStrategy strategy = new DefaultStreamCachingStrategy();
        strategy.setEnabled(true);
        strategy.setSpoolThreshold(8192L);

        Object enabled = introspection.getOrElseProperty(strategy, "enabled", false, false);
        Object spoolThreshold = introspection.getOrElseProperty(strategy, "SPOOLTHRESHOLD", -1L, true);

        assertThat(enabled).isEqualTo(true);
        assertThat(spoolThreshold).isEqualTo(8192L);
    }

    @Test
    void setPropertyFindsSettersAndInvokesDirectAssignmentSetter() throws Exception {
        BeanIntrospection introspection = new DefaultBeanIntrospection();
        DefaultStreamCachingStrategy strategy = new DefaultStreamCachingStrategy();

        Set<Method> publicSetters = introspection.findSetterMethods(
                DefaultStreamCachingStrategy.class, "spoolEnabled", true, false, false);
        Set<Method> declaredSetters = introspection.findSetterMethods(
                DefaultStreamCachingStrategy.class, "spoolCipher", true, true, true);
        boolean updated = introspection.setProperty(
                null, null, strategy, "spoolEnabled", true, null, true, false, false);

        assertThat(publicSetters).extracting(Method::getName).contains("setSpoolEnabled");
        assertThat(declaredSetters).extracting(Method::getName).contains("setSpoolCipher");
        assertThat(updated).isTrue();
        assertThat(strategy.isSpoolEnabled()).isTrue();
    }

    @Test
    void setPropertyInvokesSetterWithConvertedValueWhenValueTypeDoesNotMatch() throws Exception {
        BeanIntrospection introspection = new DefaultBeanIntrospection();
        DefaultStreamCachingStrategy strategy = new DefaultStreamCachingStrategy();

        boolean updated = introspection.setProperty(
                null, new StringToNumberTypeConverter(), strategy, "spoolUsedHeapMemoryThreshold", "75", null, true,
                false, false);

        assertThat(updated).isTrue();
        assertThat(strategy.getSpoolUsedHeapMemoryThreshold()).isEqualTo(75);
    }

    @Test
    void indexedArrayPropertyCreatesEmptyArrayBeforeReportingMissingSetter() {
        BeanIntrospection introspection = new DefaultBeanIntrospection();

        assertThatThrownBy(() -> introspection.setProperty(
                null, null, String.class, "signers[0]", "signer", null, true, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no setter method");
    }

    private static final class StringToNumberTypeConverter implements TypeConverter {
        @Override
        public boolean allowNull() {
            return false;
        }

        @Override
        public <T> T convertTo(Class<T> type, Object value) throws TypeConversionException {
            try {
                return mandatoryConvertTo(type, value);
            } catch (NoTypeConversionAvailableException e) {
                throw new TypeConversionException(value, type, e);
            }
        }

        @Override
        public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
            return convertTo(type, value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T mandatoryConvertTo(Class<T> type, Object value)
                throws TypeConversionException, NoTypeConversionAvailableException {
            if ((type == int.class || type == Integer.class) && value instanceof String text) {
                return (T) Integer.valueOf(text);
            }
            throw new NoTypeConversionAvailableException(value, type);
        }

        @Override
        public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value)
                throws TypeConversionException, NoTypeConversionAvailableException {
            return mandatoryConvertTo(type, value);
        }

        @Override
        public <T> T tryConvertTo(Class<T> type, Object value) {
            try {
                return mandatoryConvertTo(type, value);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public <T> T tryConvertTo(Class<T> type, Exchange exchange, Object value) {
            return tryConvertTo(type, value);
        }
    }
}
