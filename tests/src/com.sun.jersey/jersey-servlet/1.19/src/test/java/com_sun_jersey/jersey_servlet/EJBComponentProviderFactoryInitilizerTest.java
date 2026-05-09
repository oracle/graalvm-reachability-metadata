/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_servlet;

import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.server.impl.ejb.EJBComponentProviderFactoryInitilizer;
import com.sun.jersey.server.impl.ejb.EJBExceptionMapper;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicReference;
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

public class EJBComponentProviderFactoryInitilizerTest {
    private static final String INTERCEPTOR_BINDER_NAME = "java:org.glassfish.ejb.container.interceptor_binding_spi";
    private static final AtomicReference<InterceptorBinder> INTERCEPTOR_BINDER = new AtomicReference<InterceptorBinder>();

    private InterceptorBinder interceptorBinder;

    @BeforeAll
    static void installInitialContextFactory() throws NamingException {
        NamingManager.setInitialContextFactoryBuilder(new TestInitialContextFactoryBuilder());
    }

    @BeforeEach
    void setUp() {
        interceptorBinder = new InterceptorBinder();
        INTERCEPTOR_BINDER.set(interceptorBinder);
    }

    @Test
    void initializeRegistersEjbInterceptorAndResourceConfigProviders() {
        ResourceConfig resourceConfig = new DefaultResourceConfig();

        EJBComponentProviderFactoryInitilizer.initialize(resourceConfig);

        assertThat(interceptorBinder.getInterceptor())
                .isNotNull()
                .extracting(interceptor -> interceptor.getClass().getName())
                .isEqualTo("com.sun.jersey.server.impl.ejb.EJBInjectionInterceptor");
        assertThat(resourceConfig.getSingletons())
                .singleElement()
                .extracting(singleton -> singleton.getClass().getName())
                .isEqualTo("com.sun.jersey.server.impl.ejb.EJBComponentProviderFactory");
        assertThat(resourceConfig.getClasses()).contains(EJBExceptionMapper.class);
    }

    public static final class InterceptorBinder {
        private Object interceptor;

        public void registerInterceptor(Object interceptor) {
            this.interceptor = interceptor;
        }

        private Object getInterceptor() {
            return interceptor;
        }
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
                return INTERCEPTOR_BINDER.get();
            }
            throw new NameNotFoundException(name);
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
            return new Hashtable<Object, Object>();
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
