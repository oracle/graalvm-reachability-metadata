/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.web.bindery.requestfactory.server.ServiceLayer;
import com.google.web.bindery.requestfactory.server.ServiceLayerDecorator;
import com.google.web.bindery.requestfactory.shared.Locator;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

public class ReflectiveServiceLayerTest {
    @Test
    void createsDomainObjectAndInvokesBeanAccessors() {
        ServiceLayer serviceLayer = newServiceLayer();

        DomainRecord record = serviceLayer.createDomainObject(DomainRecord.class);
        serviceLayer.setProperty(record, "name", String.class, "created through setter");
        serviceLayer.setProperty(record, "id", Long.class, Long.valueOf(7L));
        serviceLayer.setProperty(record, "version", Integer.class, Integer.valueOf(3));

        assertThat(serviceLayer.getProperty(record, "name")).isEqualTo("created through setter");
        assertThat(serviceLayer.getId(record)).isEqualTo(Long.valueOf(7L));
        assertThat(serviceLayer.getVersion(record)).isEqualTo(Integer.valueOf(3));
        assertThat(serviceLayer.getGetter(DomainRecord.class, "name")).isNotNull();
        assertThat(serviceLayer.getSetter(DomainRecord.class, "name")).isNotNull();
    }

    @Test
    void findsDomainObjectsAndInvokesStaticAndInstanceDomainMethods() throws Exception {
        ServiceLayer serviceLayer = newServiceLayer();
        DomainRecord stored = new DomainRecord(Long.valueOf(11L), "stored", Integer.valueOf(5));
        DomainRecord.remember(stored);

        assertThat(serviceLayer.getIdType(DomainRecord.class)).isEqualTo(Long.class);
        assertThat(serviceLayer.loadDomainObject(DomainRecord.class, Long.valueOf(11L)))
                .isSameAs(stored);
        assertThat(serviceLayer.isLive(stored)).isTrue();

        Method staticMethod = DomainRecord.class.getMethod("staticGreeting", String.class);
        Method instanceMethod = DomainRecord.class.getMethod("rename", String.class);

        assertThat(serviceLayer.invoke(staticMethod, "hello")).isEqualTo("static:hello");
        assertThat(serviceLayer.invoke(instanceMethod, stored, "renamed"))
                .isEqualTo("stored->renamed");
        assertThat(stored.getName()).isEqualTo("renamed");
    }

    private static ServiceLayer newServiceLayer() {
        return ServiceLayer.create(new NoLocatorDecorator());
    }

    private static final class NoLocatorDecorator extends ServiceLayerDecorator {
        @Override
        public Class<? extends Locator<?, ?>> resolveLocator(Class<?> domainType) {
            return null;
        }
    }

    public static final class DomainRecord {
        private static DomainRecord remembered;

        private Long id;
        private String name;
        private Integer version;

        public DomainRecord() {
        }

        DomainRecord(Long id, String name, Integer version) {
            this.id = id;
            this.name = name;
            this.version = version;
        }

        public static DomainRecord findDomainRecord(Long id) {
            if (remembered != null && remembered.getId().equals(id)) {
                return remembered;
            }
            return null;
        }

        public static String staticGreeting(String value) {
            return "static:" + value;
        }

        static void remember(DomainRecord record) {
            remembered = record;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getVersion() {
            return version;
        }

        public void setVersion(Integer version) {
            this.version = version;
        }

        public String rename(String newName) {
            String previousName = name;
            name = newName;
            return previousName + "->" + newName;
        }
    }
}
