/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_base_engine;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.impl.engine.DefaultBeanIntrospection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IntrospectionSupportTest {
    @Test
    void introspectsReadableWritableProperties() {
        DefaultBeanIntrospection introspection = new DefaultBeanIntrospection();
        SampleBean bean = new SampleBean();
        bean.setName("Camel");
        bean.setEnabled(true);

        Map<String, Object> properties = new LinkedHashMap<>();
        boolean found = introspection.getProperties(bean, properties, "bean.", false);

        assertThat(found).isTrue();
        assertThat(properties)
                .containsEntry("bean.name", "Camel")
                .containsEntry("bean.enabled", true);
        assertThat(introspection.getCachedClassesCounter()).isPositive();
    }

    @Test
    void resolvesPropertyGettersUsingCaseSensitiveAndInsensitiveNames() throws Exception {
        DefaultBeanIntrospection introspection = new DefaultBeanIntrospection();
        SampleBean bean = new SampleBean();
        bean.setName("Camel");
        bean.setEnabled(true);

        Method booleanGetter = introspection.getPropertyGetter(SampleBean.class, "enabled", false);
        Method stringGetter = introspection.getPropertyGetter(SampleBean.class, "name", false);
        Method insensitiveGetter = introspection.getPropertyGetter(SampleBean.class, "nAmE", true);

        assertThat(booleanGetter.getName()).isEqualTo("isEnabled");
        assertThat(stringGetter.getName()).isEqualTo("getName");
        assertThat(insensitiveGetter.getName()).isEqualTo("getName");
        assertThat(introspection.getOrElseProperty(bean, "name", "fallback", false)).isEqualTo("Camel");
        assertThat(introspection.getOrElseProperty(bean, "EnAbLeD", false, true)).isEqualTo(true);
        assertThat(introspection.getOrElseProperty(bean, "missing", "fallback", true)).isEqualTo("fallback");
    }

    @Test
    void resolvesPropertySetterAndAssignsValueDirectly() throws Exception {
        DefaultBeanIntrospection introspection = new DefaultBeanIntrospection();
        SampleBean bean = new SampleBean();

        Method setter = introspection.getPropertySetter(SampleBean.class, "name");
        boolean configured = introspection.setProperty(null, null, bean, "name", "Updated", null, true, false, false);

        assertThat(setter.getName()).isEqualTo("setName");
        assertThat(configured).isTrue();
        assertThat(bean.getName()).isEqualTo("Updated");
    }

    @Test
    void findsPublicBuilderAndPrivateSetterMethods() {
        DefaultBeanIntrospection introspection = new DefaultBeanIntrospection();

        Set<Method> publicBuilders = introspection.findSetterMethods(BuilderBean.class, "title", true, false, false);
        Set<Method> privateSetters = introspection.findSetterMethods(
                PrivateSetterBean.class, "secret", false, true, false);
        Set<Method> caseInsensitiveSetters = introspection.findSetterMethods(
                SampleBean.class, "NAME", true, false, true);

        assertThat(publicBuilders).extracting(Method::getName).contains("withTitle");
        assertThat(privateSetters).extracting(Method::getName).contains("setSecret");
        assertThat(caseInsensitiveSetters).extracting(Method::getName).contains("setName");
    }

    @Test
    void createsAndPopulatesArrayPropertyByIndex() throws Exception {
        DefaultBeanIntrospection introspection = new DefaultBeanIntrospection();
        IndexedBean bean = new IndexedBean();

        boolean configured = introspection.setProperty(
                null, null, bean, "aliases[0]", "primary", null, true, false, false);

        assertThat(configured).isTrue();
        assertThat(bean.getAliases()).containsExactly("primary");
    }

    @Test
    void attemptsSetterInvocationAfterUnsuccessfulConversion() {
        DefaultBeanIntrospection introspection = new DefaultBeanIntrospection();
        NumericBean bean = new NumericBean();

        assertThrows(IllegalArgumentException.class,
                () -> introspection.setProperty(null, null, bean, "count", "not-a-number", null, true, false, false));
        assertThat(bean.getCount()).isZero();
    }

    public static class SampleBean {
        private String name;
        private boolean enabled;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class BuilderBean {
        private String title;

        public String getTitle() {
            return title;
        }

        public BuilderBean withTitle(String title) {
            this.title = title;
            return this;
        }
    }

    public static class PrivateSetterBean {
        private String secret;

        public String getSecret() {
            return secret;
        }

        @SuppressWarnings("unused")
        private void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class IndexedBean {
        private String[] aliases;

        public String[] getAliases() {
            return aliases;
        }

        public void setAliases(String[] aliases) {
            this.aliases = aliases;
        }

        public List<String> getNames() {
            return List.of();
        }
    }

    public static class NumericBean {
        private int count;

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
