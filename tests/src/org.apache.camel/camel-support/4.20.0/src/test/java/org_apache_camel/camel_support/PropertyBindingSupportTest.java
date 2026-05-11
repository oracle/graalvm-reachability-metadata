/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_support;

import org.apache.camel.CamelContext;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.extension.verifier.OptionsGroup;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.support.DefaultEndpointUtilizationStatistics;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.SimpleTypeConverter;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyBindingSupportTest {
    @Test
    void constructorParametersCanCreateInstanceWithMatchedConstructor() throws Exception {
        CamelContext camelContext = camelContextWithTypeConverter();

        Object created = PropertyBindingSupport.newInstanceConstructorParameters(
                camelContext, OptionsGroup.class, "'credentials'");

        assertThat(created).isInstanceOf(OptionsGroup.class);
        assertThat(((OptionsGroup) created).getName()).isEqualTo("credentials");
    }

    @Test
    void constructorParametersRetryWithUnquotedParametersBeforeCreatingInstance() throws Exception {
        CamelContext camelContext = camelContextWithTypeConverter();

        Object created = PropertyBindingSupport.newInstanceConstructorParameters(
                camelContext, DefaultEndpointUtilizationStatistics.class, "'{{answer}}'");

        assertThat(created).isInstanceOf(DefaultEndpointUtilizationStatistics.class);
        assertThat(((DefaultEndpointUtilizationStatistics) created).maxCapacity()).isEqualTo(41);
    }

    @Test
    void factoryParametersCanInvokeMatchedStaticFactoryMethod() throws Exception {
        CamelContext camelContext = camelContextWithTypeConverter();

        Object created = PropertyBindingSupport.newInstanceFactoryParameters(
                camelContext, ResourceHelper.class, "getScheme", "'classpath:routes.xml'");

        assertThat(created).isEqualTo("classpath:");
    }

    @Test
    void factoryParametersRetryWithUnquotedParametersBeforeInvokingStaticFactoryMethod() throws Exception {
        CamelContext camelContext = camelContextWithTypeConverter();

        Object created = PropertyBindingSupport.newInstanceFactoryParameters(
                camelContext, MemoryIdempotentRepository.class, "memoryIdempotentRepositoryFifo", "'{{answer}}'");

        assertThat(created).isInstanceOf(IdempotentRepository.class);
    }

    @Test
    void propertyBindingCreatesArrayCollectionForIndexedPropertyThroughConfigurer() {
        ArrayCollectionTarget target = new ArrayCollectionTarget();
        PropertyConfigurer configurer = new ArrayCollectionTargetConfigurer();

        ArrayCollectionItem item = new ArrayCollectionItem();

        boolean bound = PropertyBindingSupport.build()
                .withConfigurer(configurer)
                .withPlaceholder(false)
                .withReflection(false)
                .bind(null, target, "items[1]", item);

        assertThat(bound).isTrue();
        assertThat(target.items()).hasSize(2);
        assertThat(target.items()[0]).isNull();
        assertThat(target.items()[1]).isSameAs(item);
    }

    private static CamelContext camelContextWithTypeConverter() {
        return new TypeConvertingCamelContext();
    }

    private static final class TypeConvertingCamelContext extends DefaultCamelContext {
        private static final TypeConverter TYPE_CONVERTER = new SimpleTypeConverter(false, (type, exchange, value) -> {
            if (type == int.class || type == Integer.class) {
                if ("{{answer}}".equals(value)) {
                    return 41;
                }
                return Integer.valueOf(value.toString());
            }
            if (type == String.class) {
                return value.toString();
            }
            return value;
        });

        @Override
        public TypeConverter getTypeConverter() {
            return TYPE_CONVERTER;
        }
    }

    public static final class ArrayCollectionTarget {
        private ArrayCollectionItem[] items;

        ArrayCollectionItem[] items() {
            return items;
        }
    }

    public static final class ArrayCollectionItem {
    }

    private static final class ArrayCollectionTargetConfigurer implements PropertyConfigurer, PropertyConfigurerGetter {
        @Override
        public boolean configure(
                CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            if (target instanceof ArrayCollectionTarget arrayCollectionTarget && "items".equals(name)) {
                arrayCollectionTarget.items = (ArrayCollectionItem[]) value;
                return true;
            }
            return false;
        }

        @Override
        public Class<?> getOptionType(String name, boolean javaType) {
            if ("items".equals(name)) {
                return ArrayCollectionItem[].class;
            }
            return null;
        }

        @Override
        public Object getOptionValue(Object target, String name, boolean ignoreCase) {
            if (target instanceof ArrayCollectionTarget arrayCollectionTarget && "items".equals(name)) {
                return arrayCollectionTarget.items;
            }
            return null;
        }
    }
}
