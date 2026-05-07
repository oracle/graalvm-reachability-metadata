/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_base;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Converter;
import org.apache.camel.LoggingLevel;
import org.apache.camel.TypeConverter;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.impl.converter.AnnotationTypeConverterLoader;
import org.apache.camel.impl.converter.TypeConvertersPackageScanClassResolver;
import org.apache.camel.spi.BulkTypeConverters;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.TypeConvertible;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationTypeConverterLoaderTest {
    @Test
    void loadDiscoversServicePackageAndRegistersAnnotatedConverterMethod() throws Exception {
        TypeConvertersPackageScanClassResolver resolver
                = new TypeConvertersPackageScanClassResolver(TestStringConverters.class);
        RecordingTypeConverterRegistry registry = new RecordingTypeConverterRegistry();
        AnnotationTypeConverterLoader loader = new AnnotationTypeConverterLoader(resolver);

        loader.load(registry);

        TypeConverter converter = registry.lookup(Integer.class, String.class);
        assertThat(converter).isNotNull();
        assertThat(converter.convertTo(Integer.class, "41")).isEqualTo(42);
    }

    @Converter
    public static final class TestStringConverters {
        private TestStringConverters() {
        }

        @Converter
        public static Integer toInteger(String value) {
            return Integer.parseInt(value) + 1;
        }
    }

    private static final class RecordingTypeConverterRegistry implements TypeConverterRegistry {
        private final Map<TypeConvertible<?, ?>, TypeConverter> converters = new HashMap<>();
        private final Statistics statistics = new EmptyStatistics();
        private Injector injector;
        private CamelContext camelContext;
        private LoggingLevel loggingLevel = LoggingLevel.WARN;
        private TypeConverterExists typeConverterExists = TypeConverterExists.Override;

        @Override
        public void addBulkTypeConverters(BulkTypeConverters bulkTypeConverters) {
        }

        @Override
        public void addTypeConverter(Class<?> toType, Class<?> fromType, TypeConverter typeConverter) {
            converters.put(new TypeConvertible<>(fromType, toType), typeConverter);
        }

        @Override
        public boolean removeTypeConverter(Class<?> toType, Class<?> fromType) {
            return converters.remove(new TypeConvertible<>(fromType, toType)) != null;
        }

        @Override
        public void addTypeConverters(Object typeConverters) {
        }

        @Override
        public void addFallbackTypeConverter(TypeConverter typeConverter, boolean canPromote) {
        }

        @Override
        public TypeConverter lookup(Class<?> toType, Class<?> fromType) {
            return converters.get(new TypeConvertible<>(fromType, toType));
        }

        @Override
        public Map<Class<?>, TypeConverter> lookup(Class<?> toType) {
            Map<Class<?>, TypeConverter> matchingConverters = new HashMap<>();
            for (Map.Entry<TypeConvertible<?, ?>, TypeConverter> entry : converters.entrySet()) {
                if (entry.getKey().getTo().equals(toType)) {
                    matchingConverters.put(entry.getKey().getFrom(), entry.getValue());
                }
            }
            return matchingConverters;
        }

        @Override
        public void setInjector(Injector injector) {
            this.injector = injector;
        }

        @Override
        public Injector getInjector() {
            return injector;
        }

        @Override
        public Statistics getStatistics() {
            return statistics;
        }

        @Override
        public int size() {
            return converters.size();
        }

        @Override
        public LoggingLevel getTypeConverterExistsLoggingLevel() {
            return loggingLevel;
        }

        @Override
        public void setTypeConverterExistsLoggingLevel(LoggingLevel loggingLevel) {
            this.loggingLevel = loggingLevel;
        }

        @Override
        public TypeConverterExists getTypeConverterExists() {
            return typeConverterExists;
        }

        @Override
        public void setTypeConverterExists(TypeConverterExists typeConverterExists) {
            this.typeConverterExists = typeConverterExists;
        }

        @Override
        public void addConverter(TypeConvertible<?, ?> typeConvertible, TypeConverter typeConverter) {
            converters.put(typeConvertible, typeConverter);
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    }

    private static final class EmptyStatistics implements TypeConverterRegistry.Statistics {
        @Override
        public long getNoopCounter() {
            return 0;
        }

        @Override
        public long getAttemptCounter() {
            return 0;
        }

        @Override
        public long getHitCounter() {
            return 0;
        }

        @Override
        public long getMissCounter() {
            return 0;
        }

        @Override
        public long getFailedCounter() {
            return 0;
        }

        @Override
        public void reset() {
        }
    }
}
