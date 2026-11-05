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
import com.github.weisj.jsvg.view.ViewBox;
import org.junit.jupiter.api.Test;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SVGDocumentTest {
    @Test
    void computesTransformedGeometryFromReferencedElements() {
        SVGDocument document = load("""
                <svg xmlns="http://www.w3.org/2000/svg" width="100" height="80">
                    <defs><rect id="tile" width="10" height="8"/></defs>
                    <g transform="translate(20 10) scale(2)">
                        <use href="#tile"/>
                        <use href="#tile" x="20"/>
                    </g>
                </svg>
                """);

        Shape shape = document.computeShape();

        assertTrue(shape.contains(25, 15));
        assertTrue(shape.contains(65, 15));
        assertFalse(shape.contains(50, 15));
        Rectangle2D bounds = shape.getBounds2D();
        assertEquals(20, bounds.getX(), 0.001);
        assertEquals(10, bounds.getY(), 0.001);
        assertEquals(60, bounds.getWidth(), 0.001);
        assertEquals(16, bounds.getHeight(), 0.001);
    }

    @Test
    void appliesStylesheetEvenOddFillRuleToCompoundPath() {
        SVGDocument document = load("""
                <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
                    <style>.frame { fill-rule: evenodd; }</style>
                    <path class="frame" d="M10 10 H90 V90 H10 Z M30 30 H70 V70 H30 Z"/>
                </svg>
                """);

        Shape shape = document.computeShape();

        assertTrue(shape.contains(20, 50));
        assertTrue(shape.contains(80, 50));
        assertFalse(shape.contains(50, 50));
        assertFalse(shape.contains(5, 50));
    }

    @Test
    void scalesGeometryIntoRequestedViewport() {
        SVGDocument document = load("""
                <svg xmlns="http://www.w3.org/2000/svg" width="40" height="20" viewBox="0 0 40 20">
                    <rect x="5" y="5" width="10" height="10"/>
                </svg>
                """);

        Shape shape = document.computeShape(new ViewBox(10, 20, 80, 40));
        Rectangle2D bounds = shape.getBounds2D();

        assertEquals(20, bounds.getX(), 0.001);
        assertEquals(30, bounds.getY(), 0.001);
        assertEquals(20, bounds.getWidth(), 0.001);
        assertEquals(20, bounds.getHeight(), 0.001);
        assertTrue(shape.contains(30, 40));
        assertFalse(shape.contains(15, 40));
        assertFalse(document.isAnimated());
    }

    @Test
    void includesStrokeOutlineInComputedShape() {
        SVGDocument document = load("""
                <svg xmlns="http://www.w3.org/2000/svg" width="100" height="40">
                    <path d="M10 20 H90" fill="none" stroke="black" stroke-width="6"/>
                </svg>
                """);

        Shape shape = document.computeShape();

        assertTrue(shape.contains(50, 20));
        assertTrue(shape.contains(50, 22));
        assertFalse(shape.contains(50, 24));
        assertEquals(80, shape.getBounds2D().getWidth(), 0.001);
        assertEquals(6, shape.getBounds2D().getHeight(), 0.001);
    }

    private static SVGDocument load(String svg) {
        SVGDocument document = new SVGLoader().load(
                new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)),
                null, LoaderContext.createDefault());
        assertNotNull(document);
        return document;
    }
}
