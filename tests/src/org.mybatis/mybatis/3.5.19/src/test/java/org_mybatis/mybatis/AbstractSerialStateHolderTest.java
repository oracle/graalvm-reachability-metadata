/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.One;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.cache.decorators.SerializedCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.executor.loader.AbstractSerialStateHolder;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.mapping.FetchType;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AbstractSerialStateHolderTest {
    @Test
    void preservesUnloadedPropertiesForAStaticDeserializationProxy() {
        Configuration configuration = TestSupport.configuration();
        configuration.setLazyLoadingEnabled(true);
        configuration.setProxyFactory(new StaticProxyFactory());
        configuration.setConfigurationFactory(ConfigurationProvider.class);
        configuration.addMapper(NameMapper.class);
        ConfigurationProvider.configuration = configuration;
        try {
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
            NameBean original;
            try (SqlSession session = factory.openSession()) {
                original = session.getMapper(NameMapper.class).find(1);
                assertEquals(1, original.getId());
                assertNull(original.name);
            }

            SerializedCache cache = new SerializedCache(new PerpetualCache("static-lazy-beans"));
            cache.putObject("person", original);
            NameBean restored = (NameBean) cache.getObject("person");
            assertNotSame(original, restored);
            assertEquals(1, restored.getId());
            assertNull(restored.name);
            assertEquals("Ada", restored.getName());
            assertEquals("Ada", restored.getName());
            assertNull(original.name);
        } finally {
            ConfigurationProvider.configuration = null;
        }
    }

    public interface NameMapper {
        @Select("SELECT #{value} AS id")
        @Results({
                @Result(property = "id", column = "id", id = true),
                @Result(property = "name", column = "id",
                        one = @One(select = "findName", fetchType = FetchType.LAZY))
        })
        NameBean find(int id);

        @Select("SELECT 'Ada' WHERE #{value} = 1")
        String findName(int id);
    }

    public static class ConfigurationProvider {
        private static Configuration configuration;

        public static Configuration getConfiguration() {
            return configuration;
        }
    }

    public static class NameBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private int id;
        private String name;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class StaticProxyFactory implements ProxyFactory {
        @Override
        public Object createProxy(Object target, ResultLoaderMap lazyLoader, Configuration configuration,
                ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
            return new NameProxy((NameBean) target, lazyLoader, null, objectFactory, constructorArgTypes, constructorArgs);
        }
    }

    public static class NameProxy extends NameBean {
        private static final long serialVersionUID = 1L;
        private final ResultLoaderMap liveLoaders;
        private final Map<String, ResultLoaderMap.LoadPair> restoredLoaders;
        private final ObjectFactory objectFactory;
        private final List<Class<?>> constructorArgTypes;
        private final List<Object> constructorArgs;

        public NameProxy(NameBean target, ResultLoaderMap liveLoaders,
                Map<String, ResultLoaderMap.LoadPair> restoredLoaders, ObjectFactory objectFactory,
                List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
            setId(target.getId());
            setName(target.name);
            this.liveLoaders = liveLoaders;
            this.restoredLoaders = restoredLoaders;
            this.objectFactory = objectFactory;
            this.constructorArgTypes = constructorArgTypes;
            this.constructorArgs = constructorArgs;
        }

        @Override
        public String getName() {
            try {
                if (liveLoaders != null) {
                    liveLoaders.load("name");
                } else {
                    ResultLoaderMap.LoadPair pair = restoredLoaders.remove("NAME");
                    if (pair != null) {
                        pair.load(this);
                    }
                }
                return super.getName();
            } catch (SQLException exception) {
                throw new IllegalStateException("Cannot load the deferred name", exception);
            }
        }

        public Object writeReplace() {
            NameBean target = objectFactory.create(NameBean.class, constructorArgTypes, constructorArgs);
            target.setId(getId());
            target.setName(super.getName());
            Map<String, ResultLoaderMap.LoadPair> properties = liveLoaders == null
                    ? restoredLoaders : liveLoaders.getProperties();
            return new NameState(target, properties, objectFactory, constructorArgTypes, constructorArgs);
        }
    }

    public static class NameState extends AbstractSerialStateHolder {
        private static final long serialVersionUID = 1L;

        public NameState() {
        }

        public NameState(NameBean target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties,
                ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
            super(target, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
        }

        @Override
        protected Object createDeserializationProxy(Object target,
                Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
                List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
            return new NameProxy((NameBean) target, null, unloadedProperties, objectFactory,
                    constructorArgTypes, constructorArgs);
        }
    }
}
