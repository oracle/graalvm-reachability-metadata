/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_remote_driver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.io.FileHandler;

public class FileHandlerTest {
    @TempDir
    File outputDir;

    @Test
    void locatesResourceRelativeToFileHandlerClass() throws Exception {
        FileHandler.copyResource(outputDir, FileHandlerTest.class, "file-handler-resource.txt");

        assertTrue(new File(outputDir, "file-handler-resource.txt").exists());
    }

    @Test
    void locatesResourceRelativeToProvidedClass() throws Exception {
        FileHandler.copyResource(outputDir, FileHandlerTest.class, "relative-resource.txt");

        assertTrue(new File(outputDir, "relative-resource.txt").exists());
    }
}
