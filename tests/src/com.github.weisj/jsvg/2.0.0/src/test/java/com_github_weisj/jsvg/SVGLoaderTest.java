/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_weisj.jsvg;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SVGLoaderTest {
    private static final String SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" width="80" height="40" viewBox="0 0 80 40">
                <rect x="10" y="5" width="30" height="20"/>
            </svg>
            """;

    @Test
    void loadsDocumentFromUtf8Stream() {
        SVGDocument document = new SVGLoader().load(
                new ByteArrayInputStream(SVG.getBytes(StandardCharsets.UTF_8)),
                null, LoaderContext.createDefault());

        assertNotNull(document);
        assertEquals(80, document.size().width);
        assertEquals(40, document.size().height);
        assertEquals(0, document.viewBox().x);
        assertEquals(0, document.viewBox().y);
        assertEquals(80, document.viewBox().width);
        assertEquals(40, document.viewBox().height);
        assertTrue(document.computeShape().contains(20, 15));
        assertFalse(document.computeShape().contains(60, 30));
    }

    @Test
    void loadsGzipCompressedSvg() throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(SVG.getBytes(StandardCharsets.UTF_8));
        }

        SVGDocument document = new SVGLoader().load(
                new ByteArrayInputStream(compressed.toByteArray()), null, LoaderContext.createDefault());

        assertNotNull(document);
        assertEquals(80, document.size().width);
        assertEquals(40, document.size().height);
        assertTrue(document.computeShape().contains(20, 15));
        assertFalse(document.computeShape().contains(5, 15));
    }

    @Test
    void loadsDocumentFromFileUrl(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("drawing.svg");
        Files.writeString(file, SVG, StandardCharsets.UTF_8);

        SVGDocument document = new SVGLoader().load(file.toUri().toURL());

        assertNotNull(document);
        assertEquals(30, document.computeShape().getBounds2D().getWidth());
        assertEquals(20, document.computeShape().getBounds2D().getHeight());
        assertTrue(document.computeShape().contains(20, 15));
    }
}
