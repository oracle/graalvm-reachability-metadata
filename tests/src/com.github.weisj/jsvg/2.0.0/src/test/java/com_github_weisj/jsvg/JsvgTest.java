/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_weisj.jsvg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.DocumentLimits;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.parser.resources.ResourcePolicy;
import com.github.weisj.jsvg.renderer.NullPlatformSupport;
import com.github.weisj.jsvg.renderer.animation.Animation;
import com.github.weisj.jsvg.renderer.animation.AnimationState;
import com.github.weisj.jsvg.renderer.output.Output;
import com.github.weisj.jsvg.view.FloatSize;
import com.github.weisj.jsvg.view.ViewBox;

public class JsvgTest {
    @Test
    void loadsStyledGeometryDefinitionsAndComputesShape() {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="100" height="80" viewBox="0 0 100 80">
                  <style><![CDATA[
                    .accent { fill: rgb(255, 0, 0); opacity: 0.8; }
                  ]]></style>
                  <defs>
                    <rect id="tile" x="10" y="10" width="30" height="30"/>
                  </defs>
                  <g transform="translate(10,10)">
                    <use href="#tile" fill="#00cc00" stroke="#0000ff" stroke-width="4"/>
                  </g>
                  <circle class="accent" cx="72" cy="42" r="12"/>
                  <path d="M10 65 C20 55, 30 75, 40 65 S60 75, 70 65"
                        stroke="#111111" fill="none" stroke-width="3"/>
                </svg>
                """;

        SVGDocument document = loadSvg(svg);
        FloatSize size = document.size();
        ViewBox viewBox = document.viewBox();
        Shape shape = document.computeShape();
        Rectangle2D bounds = shape.getBounds2D();
        BufferedImage image = render(document, 100, 80);
        Color rectangle = colorAt(image, 35, 35);
        Color circle = colorAt(image, 72, 42);

        assertThat(size.getWidth()).isCloseTo(100.0, offset(0.001));
        assertThat(size.getHeight()).isCloseTo(80.0, offset(0.001));
        assertThat(viewBox.getX()).isCloseTo(0.0, offset(0.001));
        assertThat(viewBox.getY()).isCloseTo(0.0, offset(0.001));
        assertThat(viewBox.getWidth()).isCloseTo(100.0, offset(0.001));
        assertThat(viewBox.getHeight()).isCloseTo(80.0, offset(0.001));
        assertThat(bounds.getWidth()).isGreaterThan(60.0);
        assertThat(bounds.getHeight()).isGreaterThan(45.0);
        assertThat(shape.contains(35, 35)).isTrue();
        assertThat(shape.contains(72, 42)).isTrue();
        assertThat(rectangle.getGreen()).isGreaterThan(rectangle.getRed());
        assertThat(rectangle.getGreen()).isGreaterThan(rectangle.getBlue());
        assertThat(circle.getRed()).isGreaterThan(200);
        assertThat(circle.getAlpha()).isGreaterThan(150);
    }

    @Test
    void rendersGradientClippingAndTransforms() {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="60" height="40" viewBox="0 0 60 40">
                  <defs>
                    <linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="0%">
                      <stop offset="0" stop-color="#0000ff"/>
                      <stop offset="1" stop-color="#ff0000"/>
                    </linearGradient>
                    <clipPath id="clip">
                      <circle cx="20" cy="20" r="15"/>
                    </clipPath>
                  </defs>
                  <rect x="5" y="5" width="30" height="30" fill="url(#grad)" clip-path="url(#clip)"/>
                  <rect x="40" y="5" width="12" height="25" fill="#ffff00" transform="skewY(10)"/>
                </svg>
                """;

        BufferedImage image = render(loadSvg(svg), 60, 40);
        Color blueSide = colorAt(image, 12, 20);
        Color redSide = colorAt(image, 30, 20);
        Color clippedCorner = colorAt(image, 5, 5);
        Color transformedRectangle = colorAt(image, 48, 20);

        assertThat(blueSide.getBlue()).isGreaterThan(blueSide.getRed());
        assertThat(redSide.getRed()).isGreaterThan(redSide.getBlue());
        assertThat(clippedCorner.getAlpha()).isZero();
        assertThat(transformedRectangle.getRed()).isGreaterThan(200);
        assertThat(transformedRectangle.getGreen()).isGreaterThan(200);
    }

    @Test
    void rendersMasksWithPartialTransparency() {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="40" height="20" viewBox="0 0 40 20">
                  <defs>
                    <mask id="fade" maskUnits="userSpaceOnUse" maskContentUnits="userSpaceOnUse"
                          x="0" y="0" width="40" height="20">
                      <rect width="40" height="20" fill="#000000"/>
                      <rect width="20" height="20" fill="#ffffff"/>
                      <rect x="20" width="10" height="20" fill="#808080"/>
                    </mask>
                  </defs>
                  <rect width="40" height="20" fill="#0066ff" mask="url(#fade)"/>
                </svg>
                """;

        BufferedImage image = render(loadSvg(svg), 40, 20);
        Color visible = colorAt(image, 10, 10);
        Color partiallyMasked = colorAt(image, 25, 10);
        Color fullyMasked = colorAt(image, 35, 10);

        assertThat(visible.getBlue()).isGreaterThan(200);
        assertThat(visible.getAlpha()).isEqualTo(255);
        assertThat(partiallyMasked.getBlue()).isGreaterThan(200);
        assertThat(partiallyMasked.getAlpha()).isBetween(120, 140);
        assertThat(fullyMasked.getAlpha()).isZero();
    }

    @Test
    void rendersPatternPaintWithRepeatedTiles() {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="32" height="16" viewBox="0 0 32 16">
                  <defs>
                    <pattern id="checker" patternUnits="userSpaceOnUse" width="8" height="8">
                      <rect width="8" height="8" fill="#ffffff"/>
                      <rect x="0" y="0" width="4" height="4" fill="#ff6600"/>
                      <rect x="4" y="4" width="4" height="4" fill="#0033cc"/>
                    </pattern>
                  </defs>
                  <rect width="32" height="16" fill="url(#checker)"/>
                </svg>
                """;

        BufferedImage image = render(loadSvg(svg), 32, 16);
        Color firstOrangeTile = colorAt(image, 2, 2);
        Color repeatedOrangeTile = colorAt(image, 10, 2);
        Color firstBlueTile = colorAt(image, 6, 6);
        Color repeatedBlueTile = colorAt(image, 14, 6);
        Color tileBackground = colorAt(image, 6, 2);

        assertThat(firstOrangeTile.getRed()).isGreaterThan(200);
        assertThat(firstOrangeTile.getGreen()).isGreaterThan(70);
        assertThat(firstOrangeTile.getBlue()).isLessThan(40);
        assertThat(repeatedOrangeTile).isEqualTo(firstOrangeTile);
        assertThat(firstBlueTile.getBlue()).isGreaterThan(150);
        assertThat(firstBlueTile.getRed()).isLessThan(40);
        assertThat(firstBlueTile.getGreen()).isLessThan(80);
        assertThat(repeatedBlueTile).isEqualTo(firstBlueTile);
        assertThat(tileBackground.getRed()).isGreaterThan(240);
        assertThat(tileBackground.getGreen()).isGreaterThan(240);
        assertThat(tileBackground.getBlue()).isGreaterThan(240);
        assertThat(tileBackground.getAlpha()).isEqualTo(255);
    }

    @Test
    void loadsExternalImageResourcesRelativeToDocumentUrl(@TempDir Path tempDir) throws Exception {
        BufferedImage pixel = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D imageGraphics = pixel.createGraphics();
        try {
            imageGraphics.setColor(new Color(20, 40, 220, 255));
            imageGraphics.fillRect(0, 0, 4, 4);
        } finally {
            imageGraphics.dispose();
        }
        Path png = tempDir.resolve("pixel.png");
        ImageIO.write(pixel, "png", png.toFile());

        Path svgFile = tempDir.resolve("icon.svg");
        Files.writeString(svgFile, """
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 20 20">
                  <rect width="20" height="20" fill="#ffffff"/>
                  <image href="pixel.png" x="5" y="5" width="10" height="10" preserveAspectRatio="none"/>
                </svg>
                """, StandardCharsets.UTF_8);
        LoaderContext context = LoaderContext.builder()
                .externalResourcePolicy(ResourcePolicy.ALLOW_ALL)
                .build();

        SVGDocument document = new SVGLoader().load(svgFile.toUri().toURL(), context);
        assertThat(document).isNotNull();
        BufferedImage rendered = render(document, 20, 20);
        Color loadedImagePixel = colorAt(rendered, 10, 10);
        URI resolved = ResourcePolicy.ALLOW_RELATIVE.resolveResourceURI(svgFile.toUri(), URI.create("pixel.png"));

        assertThat(loadedImagePixel.getBlue()).isGreaterThan(180);
        assertThat(loadedImagePixel.getRed()).isLessThan(80);
        assertThat(ResourcePolicy.DENY_ALL.resolveResourceURI(svgFile.toUri(), png.toUri())).isNull();
        assertThat(resolved).isEqualTo(png.toUri());
    }

    @Test
    void exposesAnimationStateAndHonorsDocumentLimits() {
        String animatedSvg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="40" height="20" viewBox="0 0 40 20">
                  <rect x="0" y="4" width="10" height="10" fill="#00ff00">
                    <animate attributeName="x" values="0;20" dur="2s" fill="freeze"/>
                  </rect>
                </svg>
                """;
        SVGDocument animatedDocument = loadSvg(animatedSvg);
        Animation animation = animatedDocument.animation();
        AnimationState midpoint = new AnimationState(100, 1_100);
        BufferedImage animationImage = new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D animationGraphics = animationImage.createGraphics();
        Output output = Output.createForGraphics(animationGraphics);
        DocumentLimits restrictiveLimits = new DocumentLimits(1, 1, 10);
        LoaderContext restrictiveContext = LoaderContext.builder()
                .documentLimits(restrictiveLimits)
                .build();
        String tooDeep = """
                <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10">
                  <g><g><rect width="10" height="10"/></g></g>
                </svg>
                """;

        try {
            animatedDocument.renderWithPlatform(NullPlatformSupport.INSTANCE, output, new ViewBox(40, 20), midpoint);
        } finally {
            output.dispose();
            animationGraphics.dispose();
        }

        assertThat(animatedDocument.isAnimated()).isTrue();
        assertThat(animation.startTime()).isZero();
        assertThat(animation.duration()).isEqualTo(2_000L);
        assertThat(animation.endTime()).isEqualTo(2_000L);
        assertThat(midpoint.timestamp()).isEqualTo(1_000L);
        assertThat(restrictiveLimits.maxNestingDepth()).isEqualTo(1);
        assertThat(restrictiveLimits.maxUseNestingDepth()).isEqualTo(1);
        assertThat(restrictiveLimits.maxPathCount()).isEqualTo(10);
        assertThat(loadSvgOrNull(tooDeep, restrictiveContext)).isNull();
    }

    private static SVGDocument loadSvg(String svg) {
        SVGDocument document = loadSvgOrNull(svg, LoaderContext.createDefault());
        assertThat(document).isNotNull();
        return document;
    }

    private static SVGDocument loadSvgOrNull(String svg, LoaderContext context) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8));
        return new SVGLoader().load(inputStream, URI.create("memory:/document.svg"), context);
    }

    private static BufferedImage render(SVGDocument document, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            document.render(null, graphics, new ViewBox(width, height));
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static Color colorAt(BufferedImage image, int x, int y) {
        return new Color(image.getRGB(x, y), true);
    }
}
