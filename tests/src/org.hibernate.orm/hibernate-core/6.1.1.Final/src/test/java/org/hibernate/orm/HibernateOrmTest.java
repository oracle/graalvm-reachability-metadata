/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.hibernate.orm;

import static org.junit.Assert.assertNotNull;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Date;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.AttributeAccessor;
import org.hibernate.annotations.AttributeBinderType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.CurrentTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GeneratedColumn;
import org.hibernate.annotations.GenerationTime;
import org.hibernate.annotations.GeneratorType;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.ValueGenerationType;
import org.hibernate.boot.registry.classloading.internal.ClassLoaderServiceImpl;
import org.hibernate.boot.registry.selector.internal.StrategySelectorImpl;
import org.hibernate.boot.registry.selector.spi.StrategySelector;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.annotations.PropertyBinder;
import org.hibernate.engine.jdbc.dialect.internal.DialectFactoryImpl;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.mapping.Property;
import org.hibernate.property.access.internal.PropertyAccessStrategyBasicImpl;
import org.hibernate.property.access.spi.PropertyAccessStrategy;
import org.hibernate.service.Service;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.spi.ServiceBinding;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.tuple.AnnotationValueGeneration;
import org.hibernate.tuple.AttributeBinder;
import org.hibernate.tuple.GenerationTiming;
import org.hibernate.tuple.TenantIdGeneration;
import org.hibernate.tuple.ValueGenerator;
import org.junit.jupiter.api.Test;

public class HibernateOrmTest {

    @Test
    void jpaBootstrap() {

        EntityManagerFactory emFactory = Persistence.createEntityManagerFactory("org.hibernate.orm");

        EntityManager entityManager = emFactory.createEntityManager();
        entityManager.getTransaction().begin();
        entityManager.persist(new Event("Our very first event!", new Date()));
        entityManager.persist(new Event("A follow up event", new Date()));
        entityManager.getTransaction().commit();
        entityManager.close();
    }

    @Test
    void hibernateBootstrap() {

        Configuration config = h2Config(Event.class);

        SessionFactory sessionFactory = config.buildSessionFactory();
        Session session = sessionFactory.openSession();

        session.persist(new Event("Our very first event!", new Date()));
        session.persist(new Event("A follow up event", new Date()));

        session.close();
        sessionFactory.close();
    }

    @Test
    void relations() {

        Configuration config = h2Config(Cart.class, Item.class);

        SessionFactory sessionFactory = config.buildSessionFactory();
        Session session = sessionFactory.openSession();

        Cart cart = new Cart();
        Item item = new Item();
        item.setCart(cart);
        cart.setItems(Collections.singleton(item));

        session.persist(cart);
        session.persist(item);

        Cart load = session.byId(Cart.class).load(cart.getId());

        session.close();
        sessionFactory.close();
    }

    @Test
    void idGenerator() {

        Configuration config = h2Config(User.class);

        SessionFactory sessionFactory = config.buildSessionFactory();
        Session session = sessionFactory.openSession();

        User user = new User();
        user.setUsername("u1");

        session.persist(user);

        User load = session.byId(User.class).load(user.getId());

        session.close();
        sessionFactory.close();
    }

    @Test
    void hsqlDialect() {

        DialectFactoryImpl dialectFactory = new DialectFactoryImpl();
        dialectFactory.injectServices(new StubServiceRegistryImplementor());

       assertNotNull(dialectFactory.buildDialect(Collections.singletonMap(AvailableSettings.DIALECT, "org.hibernate.dialect.HSQLDialect"), () -> StubDialectResolutionInfo.INSTANCE));
    }

    @Test
    void h2Dialect() {

        DialectFactoryImpl dialectFactory = new DialectFactoryImpl();
        dialectFactory.injectServices(new StubServiceRegistryImplementor());

        assertNotNull(dialectFactory.buildDialect(Collections.singletonMap(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect"), () -> StubDialectResolutionInfo.INSTANCE));
    }

    @Test
    void mariaDbDialect() {

        DialectFactoryImpl dialectFactory = new DialectFactoryImpl();
        dialectFactory.injectServices(new StubServiceRegistryImplementor());

        assertNotNull(dialectFactory.buildDialect(Collections.singletonMap(AvailableSettings.DIALECT, "org.hibernate.dialect.MariaDBDialect"), () -> StubDialectResolutionInfo.INSTANCE));
    }

    @Test
    void mysqlDialect() {

        DialectFactoryImpl dialectFactory = new DialectFactoryImpl();
        dialectFactory.injectServices(new StubServiceRegistryImplementor());

        assertNotNull(dialectFactory.buildDialect(Collections.singletonMap(AvailableSettings.DIALECT, "org.hibernate.dialect.MySQLDialect"), () -> StubDialectResolutionInfo.INSTANCE));
    }

    @Test
    void oracleDialect() {

        DialectFactoryImpl dialectFactory = new DialectFactoryImpl();
        dialectFactory.injectServices(new StubServiceRegistryImplementor());

        assertNotNull(dialectFactory.buildDialect(Collections.singletonMap(AvailableSettings.DIALECT, "org.hibernate.dialect.OracleDialect"), () -> StubDialectResolutionInfo.INSTANCE));
    }

    @Test
    void postgreSqlDialect() {

        DialectFactoryImpl dialectFactory = new DialectFactoryImpl();
        dialectFactory.injectServices(new StubServiceRegistryImplementor());

        assertNotNull(dialectFactory.buildDialect(Collections.singletonMap(AvailableSettings.DIALECT, "org.hibernate.dialect.PostgreSQLDialect"), () -> StubDialectResolutionInfo.INSTANCE));
    }

    @Test
    void testGeneratedAnnotation() throws InstantiationException, IllegalAccessException {

        Generated annotation = new Generated() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return Generated.class;
            }

            @Override
            public GenerationTime value() {
                return GenerationTime.ALWAYS;
            }
        };

        instantiateGenerator(annotation);
    }

    @Test
    void testGeneratedColumnAnnotation() throws InstantiationException, IllegalAccessException {

        GeneratedColumn annotation = new GeneratedColumn() {

            @Override
            public String value() {
                return null;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return GeneratedColumn.class;
            }
        };

        instantiateGenerator(annotation);
    }

    @Test
    void testUpdateTimestampAnnotation() throws InstantiationException, IllegalAccessException {

        UpdateTimestamp annotation = new UpdateTimestamp() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return UpdateTimestamp.class;
            }
        };

        instantiateGenerator(Date.class, annotation);
    }

    @Test
    void testCreationTimestampAnnotation() throws InstantiationException, IllegalAccessException {

        CreationTimestamp annotation = new CreationTimestamp() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return CreationTimestamp.class;
            }
        };

        instantiateGenerator(Date.class, annotation);
    }

    @Test
    void testCurrentTimestampAnnotation() throws InstantiationException, IllegalAccessException {

        CurrentTimestamp annotation = new CurrentTimestamp() {

            @Override
            public GenerationTiming timing() {
                return GenerationTiming.ALWAYS;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return CurrentTimestamp.class;
            }
        };

        instantiateGenerator(annotation);
    }

    @Test
    void testTenantIdAnnotation() throws InstantiationException, IllegalAccessException {

        TenantId annotation = new TenantId() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return TenantId.class;
            }
        };

        instantiateGenerator(annotation);
    }

    @Test
    void testGeneratorTypeAnnotation() throws InstantiationException, IllegalAccessException {

        GeneratorType annotation = new GeneratorType() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return GeneratorType.class;
            }

            @Override
            public Class<? extends ValueGenerator<?>> type() {
                return TenantIdGeneration.class;
            }

            @Override
            public GenerationTime when() {
                return GenerationTime.ALWAYS;
            }
        };

        instantiateGenerator(annotation);
    }

    @Test
    void testTenantAttributeBinder() throws InstantiationException, IllegalAccessException {

        TenantId annotation = new TenantId() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return TenantId.class;
            }
        };

        instantiateBinder(annotation);
    }

    @Test
    void testAttributeAccessorBinder() throws InstantiationException, IllegalAccessException {

        AttributeAccessor annotation = new AttributeAccessor() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return AttributeAccessor.class;
            }

            @Override
            public String value() {
                return null;
            }

            @Override
            public Class<? extends PropertyAccessStrategy> strategy() {
                return PropertyAccessStrategyBasicImpl.class;
            }
        };

        instantiateBinder(annotation);
    }

    /**
     * @see PropertyBinder#makeProperty()
     * @see PropertyBinder#instantiateAndInitializeValueGeneration(Annotation, Class, XProperty)
     */
    private <A extends Annotation> AnnotationValueGeneration<A> instantiateGenerator(A annotation) throws InstantiationException, IllegalAccessException {
        return instantiateGenerator(Object.class, annotation);
    }

    /**
     * @see PropertyBinder#makeProperty()
     * @see PropertyBinder#instantiateAndInitializeValueGeneration(Annotation, Class, XProperty) 
     */
    private <A extends Annotation> AnnotationValueGeneration<A> instantiateGenerator(Class<?> propertyType, A annotation) throws InstantiationException, IllegalAccessException {

        ValueGenerationType generatorAnnotation = annotation.annotationType().getAnnotation(ValueGenerationType.class);

        Class<? extends AnnotationValueGeneration<?>> valueGenerator = generatorAnnotation.generatedBy();
        AnnotationValueGeneration<A> valueGeneration = (AnnotationValueGeneration<A>) valueGenerator.newInstance();
        valueGeneration.initialize(annotation, propertyType, "entityName", "propertyName");
        return valueGeneration;
    }

    /**
     * @see PropertyBinder#callAttributeBinders(Property)
     */
    private <A extends Annotation> AttributeBinder<A> instantiateBinder(Annotation annotation) throws InstantiationException, IllegalAccessException {

        AttributeBinderType attributeBinder = annotation.annotationType().getAnnotation(AttributeBinderType.class);
        return (AttributeBinder<A>) attributeBinder.binder().newInstance();
    }

    private static Configuration h2Config(Class<?>... entities) {

        Configuration config = new Configuration();
        config.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        config.setProperty("hibernate.connection.url", "jdbc:h2:mem:test");
        config.setProperty("hibernate.connection.username", "");
        config.setProperty("hibernate.connection.password", "");
        config.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        config.setProperty("hibernate.hbm2ddl.auto", "create-drop");

        for (Class<?> type : entities) {
            config.addAnnotatedClass(type);
        }

        return config;
    }

    private static class StubDialectResolutionInfo implements DialectResolutionInfo {

        static final DialectResolutionInfo INSTANCE = new StubDialectResolutionInfo();

        @Override
        public String getDatabaseName() {
            return null;
        }

        @Override
        public String getDatabaseVersion() {
            return null;
        }

        @Override
        public String getDriverName() {
            return null;
        }

        @Override
        public int getDriverMajorVersion() {
            return 0;
        }

        @Override
        public int getDriverMinorVersion() {
            return 0;
        }

        @Override
        public String getSQLKeywords() {
            return null;
        }

        @Override
        public int getDatabaseMajorVersion() {
            return 0;
        }

        @Override
        public int getDatabaseMinorVersion() {
            return 0;
        }
    }

    private static class StubServiceRegistryImplementor implements ServiceRegistryImplementor {

        @Override
        public <R extends Service> ServiceBinding<R> locateServiceBinding(Class<R> serviceRole) {
            return null;
        }

        @Override
        public void destroy() {

        }

        @Override
        public void registerChild(ServiceRegistryImplementor child) {

        }

        @Override
        public void deRegisterChild(ServiceRegistryImplementor child) {

        }

        @Override
        public <T extends Service> T fromRegistryOrChildren(Class<T> serviceRole) {
            return null;
        }

        @Override
        public ServiceRegistry getParentServiceRegistry() {
            return null;
        }

        @Override
        public <R extends Service> R getService(Class<R> serviceRole) {
            if (serviceRole == StrategySelector.class) {
                return (R) new StrategySelectorImpl(new ClassLoaderServiceImpl());
            }
            return null;
        }
    }
}
