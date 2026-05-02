/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.impl.BeanConstructors;
import com.fasterxml.jackson.jr.ob.impl.BeanPropertyIntrospector;
import com.fasterxml.jackson.jr.ob.impl.RecordsHelpers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanConstructorsDynamicAccessTest {
    private static final JSON JSON_WITH_FORCE_ACCESS = JSON.std.with(JSON.Feature.FORCE_REFLECTION_ACCESS);

    @BeforeEach
    void resetConstructorCounters() {
        PublicDefaultCtorBean.CONSTRUCTOR_CALLS.set(0);
        PrivateDefaultCtorBean.CONSTRUCTOR_CALLS.set(0);
    }

    @Test
    void createsBeansDirectlyThroughDefaultConstructorsDiscoveredByLibraryIntrospection() throws Exception {
        AccessibleBeanConstructors constructors = AccessibleBeanConstructors.forNonRecord(PublicDefaultCtorBean.class);

        PublicDefaultCtorBean bean = (PublicDefaultCtorBean) constructors.createBean();

        assertThat(bean).isNotNull();
        assertThat(PublicDefaultCtorBean.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void createsBeansDirectlyThroughNonPublicDefaultConstructorsWhenAccessIsForced() throws Exception {
        AccessibleBeanConstructors constructors = AccessibleBeanConstructors.forNonRecord(PrivateDefaultCtorBean.class);
        constructors.forceAccess();

        PrivateDefaultCtorBean bean = (PrivateDefaultCtorBean) constructors.createBean();

        assertThat(bean).isNotNull();
        assertThat(PrivateDefaultCtorBean.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void createsRecordsDirectlyThroughCanonicalConstructorsDiscoveredByLibraryIntrospection() throws Exception {
        AccessibleBeanConstructors constructors = AccessibleBeanConstructors.forRecord(ArticleRecord.class);

        ArticleRecord article = (ArticleRecord) constructors.createRecordBean("GraalVM", 42, true);

        assertThat(article.title()).isEqualTo("GraalVM");
        assertThat(article.pageCount()).isEqualTo(42);
        assertThat(article.published()).isTrue();
    }

    @Test
    void createsBeansThroughPublicDefaultConstructorsWhenReadingObjects() throws Exception {
        PublicDefaultCtorBean bean = JSON.std.beanFrom(PublicDefaultCtorBean.class, "{\"name\":\"Ada\"}");

        assertThat(bean.name).isEqualTo("Ada");
        assertThat(PublicDefaultCtorBean.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void createsBeansThroughNonPublicDefaultConstructorsWhenAccessIsForced() throws Exception {
        PrivateDefaultCtorBean bean = JSON_WITH_FORCE_ACCESS.beanFrom(PrivateDefaultCtorBean.class,
                "{\"name\":\"Ada\"}");

        assertThat(bean.name).isEqualTo("Ada");
        assertThat(PrivateDefaultCtorBean.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void createsRecordsThroughCanonicalConstructorsWhenReadingObjects() throws Exception {
        ArticleRecord article = JSON.std.beanFrom(ArticleRecord.class,
                """
                {"published":true,"pageCount":42,"title":"GraalVM"}
                """);

        assertThat(article.title()).isEqualTo("GraalVM");
        assertThat(article.pageCount()).isEqualTo(42);
        assertThat(article.published()).isTrue();
    }

    static final class AccessibleBeanConstructors extends BeanConstructors {
        private AccessibleBeanConstructors(Class<?> valueType) {
            super(valueType);
        }

        static AccessibleBeanConstructors forNonRecord(Class<?> valueType) {
            AccessibleBeanConstructors constructors = new AccessibleBeanConstructors(valueType);
            BeanPropertyIntrospector.addNonRecordConstructors(valueType, constructors);
            return constructors;
        }

        static AccessibleBeanConstructors forRecord(Class<?> valueType) {
            AccessibleBeanConstructors constructors = new AccessibleBeanConstructors(valueType);
            constructors.addRecordConstructor(RecordsHelpers.findCanonicalConstructor(valueType));
            return constructors;
        }

        Object createBean() throws Exception {
            return create();
        }

        Object createRecordBean(Object... components) throws Exception {
            return createRecord(components);
        }
    }

    public static final class PublicDefaultCtorBean {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public String name;

        public PublicDefaultCtorBean() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }
    }

    public static final class PrivateDefaultCtorBean {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public String name;

        private PrivateDefaultCtorBean() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }
    }

    public record ArticleRecord(String title, int pageCount, boolean published) {
    }
}
