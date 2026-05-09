/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_servlet;

import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.core.spi.component.ioc.IoCComponentProvider;
import com.sun.jersey.core.spi.component.ioc.IoCComponentProviderFactory;
import com.sun.jersey.core.spi.component.ioc.IoCDestroyable;
import com.sun.jersey.core.spi.component.ioc.IoCInstantiatedComponentProvider;
import com.sun.jersey.server.impl.managedbeans.ManagedBeanComponentProviderFactoryInitilizer;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.ManagedBean;
import javax.naming.Binding;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import javax.naming.spi.InitialContextFactoryBuilder;
import javax.naming.spi.NamingManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ManagedBeanComponentProviderFactoryInnerManagedBeanComponentProviderTest {
    private InjectionManager injectionManager;

    @BeforeAll
    static void installInitialContextFactory() throws NamingException {
        InitialContextSupport.install();
    }

    @BeforeEach
    void setUp() {
        injectionManager = new InjectionManager();
        InitialContextSupport.setInjectionManager(injectionManager);
    }

    @Test
    void providerCreatesAndDestroysManagedBeanThroughInjectionManager() {
        ResourceConfig resourceConfig = new DefaultResourceConfig();

        ManagedBeanComponentProviderFactoryInitilizer.initialize(resourceConfig);

        assertThat(resourceConfig.getSingletons()).hasSize(1);
        Object factoryObject = resourceConfig.getSingletons().iterator().next();
        assertThat(factoryObject).isInstanceOf(IoCComponentProviderFactory.class);
        IoCComponentProviderFactory factory = (IoCComponentProviderFactory) factoryObject;
        IoCComponentProvider provider = factory.getComponentProvider(ManagedResource.class);
        assertThat(provider)
                .isInstanceOf(IoCInstantiatedComponentProvider.class)
                .isInstanceOf(IoCDestroyable.class);

        Object instance = ((IoCInstantiatedComponentProvider) provider).getInstance();
        assertThat(instance).isInstanceOf(ManagedResource.class);
        assertThat(injectionManager.getCreatedTypes()).containsExactly(ManagedResource.class);

        ((IoCDestroyable) provider).destroy(instance);
        assertThat(injectionManager.getDestroyedObjects()).containsExactly(instance);
    }

    @ManagedBean
    public static final class ManagedResource {
    }

    public static final class InjectionManager {
        private final List<Class<?>> createdTypes = new ArrayList<Class<?>>();
        private final List<Object> destroyedObjects = new ArrayList<Object>();

        public Object createManagedObject(Class<?> type) {
            createdTypes.add(type);
            if (type == ManagedResource.class) {
                return new ManagedResource();
            }
            throw new IllegalArgumentException(type.getName());
        }

        public void destroyManagedObject(Object object) {
            destroyedObjects.add(object);
        }

        private List<Class<?>> getCreatedTypes() {
            return createdTypes;
        }

        private List<Object> getDestroyedObjects() {
            return destroyedObjects;
        }
    }

    public static final class InitialContextSupport {
        private static final String INTERCEPTOR_BINDER_NAME =
                "java:org.glassfish.ejb.container.interceptor_binding_spi";
        private static final String INJECTION_MANAGER_NAME =
                "com.sun.enterprise.container.common.spi.util.InjectionManager";
        private static final AtomicBoolean INSTALLED = new AtomicBoolean();
        private static final AtomicReference<Object> INTERCEPTOR_BINDER = new AtomicReference<Object>();
        private static final AtomicReference<InjectionManager> INJECTION_MANAGER =
                new AtomicReference<InjectionManager>();

        private InitialContextSupport() {
        }

        public static void install() throws NamingException {
            if (INSTALLED.compareAndSet(false, true)) {
                NamingManager.setInitialContextFactoryBuilder(new TestInitialContextFactoryBuilder());
            }
        }

        public static void setInterceptorBinder(Object interceptorBinder) {
            INTERCEPTOR_BINDER.set(interceptorBinder);
        }

        private static void setInjectionManager(InjectionManager injectionManager) {
            INJECTION_MANAGER.set(injectionManager);
        }

        private static final class TestInitialContextFactoryBuilder implements InitialContextFactoryBuilder {
            @Override
            public InitialContextFactory createInitialContextFactory(Hashtable<?, ?> environment) {
                return new TestInitialContextFactory();
            }
        }

        private static final class TestInitialContextFactory implements InitialContextFactory {
            @Override
            public Context getInitialContext(Hashtable<?, ?> environment) {
                return new TestContext();
            }
        }

        private static final class TestContext implements Context {
            @Override
            public Object lookup(Name name) throws NamingException {
                return lookup(name.toString());
            }

            @Override
            public Object lookup(String name) throws NamingException {
                if (INTERCEPTOR_BINDER_NAME.equals(name)) {
                    return requireBound(INTERCEPTOR_BINDER.get(), name);
                }
                if (INJECTION_MANAGER_NAME.equals(name)) {
                    return requireBound(INJECTION_MANAGER.get(), name);
                }
                throw new NameNotFoundException(name);
            }

            private Object requireBound(Object object, String name) throws NamingException {
                if (object == null) {
                    throw new NameNotFoundException(name);
                }
                return object;
            }

            @Override
            public void bind(Name name, Object obj) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void bind(String name, Object obj) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void rebind(Name name, Object obj) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void rebind(String name, Object obj) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void unbind(Name name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void unbind(String name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void rename(Name oldName, Name newName) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void rename(String oldName, String newName) {
                throw new UnsupportedOperationException();
            }

            @Override
            public NamingEnumeration<NameClassPair> list(Name name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public NamingEnumeration<NameClassPair> list(String name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public NamingEnumeration<Binding> listBindings(Name name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public NamingEnumeration<Binding> listBindings(String name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void destroySubcontext(Name name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void destroySubcontext(String name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Context createSubcontext(Name name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Context createSubcontext(String name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Object lookupLink(Name name) throws NamingException {
                return lookup(name);
            }

            @Override
            public Object lookupLink(String name) throws NamingException {
                return lookup(name);
            }

            @Override
            public NameParser getNameParser(Name name) {
                return CompositeName::new;
            }

            @Override
            public NameParser getNameParser(String name) {
                return CompositeName::new;
            }

            @Override
            public Name composeName(Name name, Name prefix) throws NamingException {
                Name composedName = (Name) prefix.clone();
                composedName.addAll(name);
                return composedName;
            }

            @Override
            public String composeName(String name, String prefix) {
                return prefix + name;
            }

            @Override
            public Object addToEnvironment(String propName, Object propVal) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Object removeFromEnvironment(String propName) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Hashtable<?, ?> getEnvironment() {
                return new java.util.Properties();
            }

            @Override
            public void close() {
                // No resources to release.
            }

            @Override
            public String getNameInNamespace() {
                return "";
            }
        }
    }
}
