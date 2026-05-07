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
    void setPropertyAttemptsConvertedSetterInvocationWhenValueTypeDoesNotMatch() {
        BeanIntrospection introspection = new DefaultBeanIntrospection();
        DefaultStreamCachingStrategy strategy = new DefaultStreamCachingStrategy();

        assertThatThrownBy(() -> introspection.setProperty(
                null, null, strategy, "spoolUsedHeapMemoryThreshold", "75", null, true, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void indexedArrayPropertyCreatesEmptyArrayBeforeReportingMissingSetter() {
        BeanIntrospection introspection = new DefaultBeanIntrospection();

        assertThatThrownBy(() -> introspection.setProperty(
                null, null, String.class, "signers[0]", "signer", null, true, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no setter method");
    }
}
