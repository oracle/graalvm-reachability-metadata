/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;
import org.apache.pulsar.shade.javassist.LoaderClassPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class LoaderClassPathTest {
    @Test
    void opensAndLocatesClassFileThroughClassLoader() throws Exception {
        LoaderClassPath classPath = new LoaderClassPath(getClass().getClassLoader());

        URL location = classPath.find(LoaderClassPath.class.getName());
        try (InputStream input = classPath.openClassfile(LoaderClassPath.class.getName())) {
            assertThat(location).isNotNull();
            assertThat(input).isNotNull();
            assertThat(input.read()).isEqualTo(0xca);
        }
    }
}
