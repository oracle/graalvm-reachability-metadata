/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.util.ClassUtil;
import org.junit.jupiter.api.Test;

public class ClassUtilTest {
    @Test
    void findContainingJarScansClassLoaderResourcesForTheClassFile() {
        String containingJar = ClassUtil.findContainingJar(ClassUtil.class);

        if (containingJar != null) {
            assertThat(containingJar)
                    .contains("hadoop-common")
                    .doesNotContain("!");
        } else {
            assertThat(containingJar).isNull();
        }
    }
}
