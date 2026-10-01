/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.io.IOException;

import org.apache.curator.shaded.com.google.common.reflect.ClassPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathTest {
    @Test
    void discoversAndLoadsClassesFromTheConsumerClassPath() throws IOException {
        ClassPath classPath = ClassPath.from(ClassPathTest.class.getClassLoader());
        ClassPath.ClassInfo classInfo = classPath.getTopLevelClasses(
                "org_apache_curator.curator_client").stream()
                .filter(info -> info.getSimpleName().equals("ClassPathTest"))
                .findFirst()
                .orElseThrow();

        assertThat(classInfo.load()).isEqualTo(ClassPathTest.class);
        assertThat(classPath.getResources().stream()
                .map(ClassPath.ResourceInfo::url)
                .anyMatch(url -> url.toString().contains("ClassPathTest"))).isTrue();
    }
}
