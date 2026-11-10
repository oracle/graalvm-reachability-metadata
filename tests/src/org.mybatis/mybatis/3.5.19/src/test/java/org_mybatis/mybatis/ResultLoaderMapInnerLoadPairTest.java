/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.io.Serializable;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.cache.decorators.SerializedCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.loader.ResultLoader;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.transaction.Transaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ResultLoaderMapInnerLoadPairTest {
    @Test
    void loadsADeserializedPropertyUsingTheConfigurationFactory() throws Exception {
        Configuration configuration = TestSupport.configuration();
        configuration.addMapper(NameMapper.class);
        configuration.setConfigurationFactory(ConfigurationProvider.class);
        ConfigurationProvider.configuration = configuration;
        Transaction transaction = configuration.getEnvironment().getTransactionFactory()
                .newTransaction(configuration.getEnvironment().getDataSource(), null, false);
        Executor executor = configuration.newExecutor(transaction);
        try {
            Bean original = new Bean();
            MappedStatement statement = configuration.getMappedStatement(NameMapper.class.getName() + ".name");
            ResultLoader loader = new ResultLoader(configuration, executor, statement, 1, String.class, null, null);
            ResultLoaderMap loaders = new ResultLoaderMap();
            loaders.addLoader("name", configuration.newMetaObject(original), loader);
            SerializedCache cache = new SerializedCache(new PerpetualCache("lazy-properties"));
            cache.putObject("name", loaders.getProperties().get("NAME"));
            ResultLoaderMap.LoadPair restored = (ResultLoaderMap.LoadPair) cache.getObject("name");
            Bean copy = new Bean();
            restored.load(copy);
            assertEquals("Ada", copy.getName());
            assertNull(original.getName());
        } finally {
            executor.close(true);
            ConfigurationProvider.configuration = null;
        }
    }

    public static class ConfigurationProvider {
        private static Configuration configuration;

        public static Configuration getConfiguration() {
            return configuration;
        }
    }

    public interface NameMapper {
        @Select("SELECT 'Ada' WHERE #{value} = 1")
        String name(int id);
    }

    public static class Bean implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
