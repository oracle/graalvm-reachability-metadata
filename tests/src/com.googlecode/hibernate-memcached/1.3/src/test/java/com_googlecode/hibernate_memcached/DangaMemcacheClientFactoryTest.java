/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_googlecode.hibernate_memcached;

import com.googlecode.hibernate.memcached.PropertiesHelper;
import com.googlecode.hibernate.memcached.dangamemcached.DangaMemcacheClientFactory;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DangaMemcacheClientFactoryTest {

    @Test
    void readsDangaBackendConnectionPoolConfiguration() {
        Properties properties = new Properties();
        properties.setProperty(DangaMemcacheClientFactory.PROP_SERVERS, "cache-a:11211 cache-b:11212");
        properties.setProperty(DangaMemcacheClientFactory.PROP_WEIGHTS, "2 5");
        properties.setProperty(DangaMemcacheClientFactory.PROP_COMPRESS_ENABLE, "false");
        properties.setProperty(DangaMemcacheClientFactory.PROP_DEFAULT_ENCODING, "UTF-16");
        properties.setProperty(DangaMemcacheClientFactory.PROP_POOL_NAME, "hibernate-pool");
        properties.setProperty(DangaMemcacheClientFactory.PROP_INIT_CONN, "2");
        properties.setProperty(DangaMemcacheClientFactory.PROP_MIN_CONN, "3");
        properties.setProperty(DangaMemcacheClientFactory.PROP_MAX_CONN, "8");
        properties.setProperty(DangaMemcacheClientFactory.PROP_MAX_IDLE, "45000");
        properties.setProperty(DangaMemcacheClientFactory.PROP_MAINT_SLEEP, "30000");
        properties.setProperty(DangaMemcacheClientFactory.PROP_SOCKET_TIMEOUT, "12000");
        properties.setProperty(DangaMemcacheClientFactory.PROP_SOCKET_CONNECT_TIMEOUT, "10000");
        DangaMemcacheClientFactory factory =
                new DangaMemcacheClientFactory(new PropertiesHelper(properties));

        assertThat(factory.getServers()).containsExactly("cache-a:11211", "cache-b:11212");
        assertThat(factory.getWeights()).containsExactly(2, 5);
        assertThat(factory.isCompressEnable()).isFalse();
        assertThat(factory.getDefaultEncoding()).isEqualTo("UTF-16");
        assertThat(factory.getPoolName()).isEqualTo("hibernate-pool");
        assertThat(factory.getInitConn()).isEqualTo(2);
        assertThat(factory.getMinConn()).isEqualTo(3);
        assertThat(factory.getMaxConn()).isEqualTo(8);
        assertThat(factory.getMaxIdle()).isEqualTo(45000);
        assertThat(factory.getMaintSleep()).isEqualTo(30000);
        assertThat(factory.getSocketTimeout()).isEqualTo(12000);
        assertThat(factory.getSocketConnectTimeout()).isEqualTo(10000);
    }
}
