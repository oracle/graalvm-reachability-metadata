/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Modifier;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MethodInfoTest {
    @TempDir
    Path directory;

    @Test
    void resolvesPublicAndPrivateMethodsAndConstructors() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2)) {
            ClassInfo info = ScanSupport.subject(scan);
            Object subject = info.getConstructorInfo().stream()
                    .filter(method -> method.getParameterInfo().length == 0).findFirst().orElseThrow()
                    .loadClassAndGetConstructor().newInstance();
            assertEquals("hello reader", info.getMethodInfo("greet").get(0)
                    .loadClassAndGetMethod().invoke(subject, "hello "));
            assertTrue(Modifier.isPrivate(info.getMethodInfo("secret").get(0)
                    .loadClassAndGetMethod().getModifiers()));
            MethodInfo privateConstructor = info.getConstructorInfo().stream()
                    .filter(method -> method.getParameterInfo().length == 1).findFirst().orElseThrow();
            assertTrue(Modifier.isPrivate(privateConstructor.loadClassAndGetConstructor().getModifiers()));
        }
    }
}
