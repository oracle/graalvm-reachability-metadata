/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.util.FileUtil;
import org.junit.jupiter.api.Test;

public class FileUtilTest {

    private static final String RESOURCE_NAME = "ch_qos_logback/logback_core/file-util-resource.txt";

    @Test
    void readsResourceFromProvidedClassLoader() {
        FileUtil fileUtil = new FileUtil(new ContextBase());

        String resourceContents = fileUtil.resourceAsString(FileUtilTest.class.getClassLoader(), RESOURCE_NAME);

        assertThat(resourceContents.trim()).isEqualTo("file util resource");
    }
}
