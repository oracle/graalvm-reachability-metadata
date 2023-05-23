/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.net.URL;

import org.hibernate.boot.jaxb.JaxbLogger;
import org.hibernate.cache.spi.SecondLevelCacheLogger;
import org.hibernate.internal.log.UrlMessageBundle;
import org.hibernate.sql.results.graph.embeddable.EmbeddableLoadingLogger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggerTest {

    @Test
    public void urlMessageBundleLogger() throws Exception {
        UrlMessageBundle.URL_MESSAGE_LOGGER.logFileIsNotDirectory(new URL("file:"));
    }

    @Test
    public void jaxbLogger() {
        assertThat(JaxbLogger.JAXB_LOGGER.TRACE_ENABLED).isFalse();
    }

    @Test
    public void secondLevelCacheLogger() {
        assertThat(SecondLevelCacheLogger.L2CACHE_LOGGER.TRACE_ENABLED).isFalse();
    }

    @Test
    public void embeddableLoadingLogger() {
        assertThat(EmbeddableLoadingLogger.EMBEDDED_LOAD_LOGGER.isTraceEnabled()).isFalse();
    }

}
