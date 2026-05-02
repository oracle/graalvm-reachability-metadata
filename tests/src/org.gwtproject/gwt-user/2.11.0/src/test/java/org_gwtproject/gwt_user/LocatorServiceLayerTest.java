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
import com.google.web.bindery.requestfactory.shared.EntityProxy;
import com.google.web.bindery.requestfactory.shared.Locator;
import com.google.web.bindery.requestfactory.shared.ProxyForName;
import com.google.web.bindery.requestfactory.shared.RequestContext;
import com.google.web.bindery.requestfactory.shared.ServiceLocator;
import com.google.web.bindery.requestfactory.shared.ServiceName;

import org.junit.jupiter.api.Test;

public class LocatorServiceLayerTest {
    @Test
    void resolvesProxyForNameLocatorAndCreatesDomainObjectWithIt() {
        ServiceLayer serviceLayer = newServiceLayer();

        Class<? extends Locator<?, ?>> locatorType = serviceLayer.resolveLocator(NamedDomain.class);
        NamedDomain created = serviceLayer.createDomainObject(NamedDomain.class);
        created.setId(Long.valueOf(42L));

        assertThat(locatorType).isEqualTo(NamedLocator.class);
        assertThat(created.getName()).isEqualTo("created by locator");
        assertThat(serviceLayer.getId(created)).isEqualTo(Long.valueOf(42L));
        assertThat(serviceLayer.getIdType(NamedDomain.class)).isEqualTo(Long.class);
        assertThat(serviceLayer.loadDomainObject(NamedDomain.class, Long.valueOf(7L)).getName())
                .isEqualTo("loaded-7");
        assertThat(serviceLayer.getVersion(created)).isEqualTo("locator-version");
        assertThat(serviceLayer.isLive(created)).isTrue();
    }

    @Test
    void resolvesServiceNameLocatorAndCreatesServiceInstanceWithIt() {
        ServiceLayer serviceLayer = newServiceLayer();

        Class<? extends ServiceLocator> locatorType =
                serviceLayer.resolveServiceLocator(NamedRequestContext.class);
        Object service = serviceLayer.createServiceInstance(NamedRequestContext.class);

        assertThat(locatorType).isEqualTo(NamedServiceLocator.class);
        assertThat(service).isInstanceOf(NamedService.class);
        assertThat(((NamedService) service).message()).isEqualTo("service from locator");
    }

    private static ServiceLayer newServiceLayer() {
        return ServiceLayer.create(new TestResolutionDecorator());
    }

    private static final class TestResolutionDecorator extends ServiceLayerDecorator {
        @Override
        public ClassLoader getDomainClassLoader() {
            return LocatorServiceLayerTest.class.getClassLoader();
        }

        @Override
        public <T> Class<? extends T> resolveClientType(
                Class<?> domainClass,
                Class<T> clientType,
                boolean required) {
            if (NamedDomain.class.equals(domainClass)
                    && clientType.isAssignableFrom(NamedDomainProxy.class)) {
                return NamedDomainProxy.class.asSubclass(clientType);
            }
            return super.resolveClientType(domainClass, clientType, required);
        }

        @Override
        public Class<?> resolveServiceClass(Class<? extends RequestContext> requestContextClass) {
            if (NamedRequestContext.class.equals(requestContextClass)) {
                return NamedService.class;
            }
            return super.resolveServiceClass(requestContextClass);
        }
    }

    @ProxyForName(
            value = "org_gwtproject.gwt_user.LocatorServiceLayerTest$NamedDomain",
            locator = "org_gwtproject.gwt_user.LocatorServiceLayerTest$NamedLocator")
    public interface NamedDomainProxy extends EntityProxy {
    }

    public static final class NamedDomain {
        private Long id;
        private String name;

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
    }

    public static final class NamedLocator extends Locator<NamedDomain, Long> {
        @Override
        public NamedDomain create(Class<? extends NamedDomain> clazz) {
            NamedDomain domain = new NamedDomain();
            domain.setName("created by locator");
            return domain;
        }

        @Override
        public NamedDomain find(Class<? extends NamedDomain> clazz, Long id) {
            NamedDomain domain = new NamedDomain();
            domain.setId(id);
            domain.setName("loaded-" + id);
            return domain;
        }

        @Override
        public Class<NamedDomain> getDomainType() {
            return NamedDomain.class;
        }

        @Override
        public Long getId(NamedDomain domainObject) {
            return domainObject.getId();
        }

        @Override
        public Class<Long> getIdType() {
            return Long.class;
        }

        @Override
        public Object getVersion(NamedDomain domainObject) {
            return "locator-version";
        }
    }

    @ServiceName(
            value = "org_gwtproject.gwt_user.LocatorServiceLayerTest$NamedService",
            locator = "org_gwtproject.gwt_user.LocatorServiceLayerTest$NamedServiceLocator")
    public interface NamedRequestContext extends RequestContext {
    }

    public static final class NamedService {
        public String message() {
            return "service from locator";
        }
    }

    public static final class NamedServiceLocator implements ServiceLocator {
        @Override
        public Object getInstance(Class<?> clazz) {
            assertThat(clazz).isEqualTo(NamedService.class);
            return new NamedService();
        }
    }
}
