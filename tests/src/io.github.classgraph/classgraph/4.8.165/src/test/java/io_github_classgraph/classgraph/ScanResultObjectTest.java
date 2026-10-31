/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import io.github.classgraph.AnnotationClassRef;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertSame;

public class ScanResultObjectTest {
    @TempDir
    Path directory;

    @Test
    void loadsClassReferencesAndTypedClassInfo() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scan(2)) {
            AnnotationClassRef type = (AnnotationClassRef) ScanSupport.details(scan).getParameterValues().getValue("type");
            assertSame(String.class, type.loadClass());
            assertSame(ScanFixtures.Subject.class, ScanSupport.subject(scan).loadClass(Runnable.class));
        }
    }
}
