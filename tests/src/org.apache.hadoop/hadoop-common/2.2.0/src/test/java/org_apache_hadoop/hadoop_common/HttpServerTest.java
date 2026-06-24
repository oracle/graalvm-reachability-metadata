/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileNotFoundException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.http.HttpServer;
import org.junit.jupiter.api.Test;

public class HttpServerTest {
    private static final String MISSING_WEBAPP_NAME = "missing-hadoop-common-httpserver-test-webapp";

    @Test
    void constructorReportsMissingWebApplicationResource() {
        Configuration conf = new Configuration(false);

        assertThatThrownBy(() -> new HttpServer(MISSING_WEBAPP_NAME, "127.0.0.1", 0, false, conf))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("webapps/" + MISSING_WEBAPP_NAME);
    }
}
