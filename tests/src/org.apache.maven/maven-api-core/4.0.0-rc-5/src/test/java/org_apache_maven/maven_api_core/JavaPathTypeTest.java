/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_core;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import javax.tools.StandardLocation;

import org.apache.maven.api.JavaPathType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class JavaPathTypeTest {
    @Test
    void formatsDependencyPathsForJavaTools() {
        Path first = Path.of("dependencies", "application.jar");
        Path second = Path.of("dependencies", "utilities.jar");
        List<Path> paths = List.of(first, second);
        String joined = first + File.pathSeparator + second;

        assertArrayEquals(new String[]{"--class-path", joined}, JavaPathType.CLASSES.option(paths));
        assertArrayEquals(new String[]{"--module-path", joined}, JavaPathType.MODULES.option(paths));
        assertArrayEquals(new String[0], JavaPathType.CLASSES.option(List.of()));
        assertEquals("--processor-path", JavaPathType.PROCESSOR_CLASSES.option().orElseThrow());
    }

    @Test
    void mapsCompilerLocationsToDependencyPathTypes() {
        assertEquals(StandardLocation.CLASS_PATH, JavaPathType.CLASSES.location().orElseThrow());
        assertEquals(JavaPathType.MODULES, JavaPathType.valueOf(StandardLocation.MODULE_PATH).orElseThrow());
        assertEquals(JavaPathType.PROCESSOR_CLASSES,
                JavaPathType.valueOf(StandardLocation.ANNOTATION_PROCESSOR_PATH).orElseThrow());
        assertFalse(JavaPathType.valueOf(StandardLocation.CLASS_OUTPUT).isPresent());
        assertFalse(JavaPathType.AGENT.location().isPresent());
    }

    @Test
    void formatsModuleSpecificPatchPaths() {
        JavaPathType.Modular patch = JavaPathType.patchModule("example.application");
        Path patches = Path.of("target", "test-classes");

        assertArrayEquals(new String[]{"--patch-module", "example.application=" + patches},
                patch.option(List.of(patches)));
        assertArrayEquals(new String[0], patch.option(List.of()));
        assertEquals(JavaPathType.PATCH_MODULE, patch.rawType());
        assertEquals("example.application", patch.moduleName());
        assertEquals("PATCH_MODULE:example.application", patch.id());
        assertEquals(JavaPathType.patchModule("example.application"), patch);
        assertEquals(JavaPathType.patchModule("example.application").hashCode(), patch.hashCode());
        assertNotEquals(JavaPathType.patchModule("example.other"), patch);
    }
}
