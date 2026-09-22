/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twelvemonkeys_imageio.imageio_jpeg;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

public class Imageio_jpegTest {

    private static final String PLUGIN_PACKAGE = "com.twelvemonkeys.imageio.plugins.jpeg.";

    @Test
    void discoversTwelveMonkeysJpegProviders() {
        ImageReader reader = newJpegReader();
        ImageWriter writer = newJpegWriter();

        try {
            assertThat(reader.getOriginatingProvider().getFormatNames()).contains("JPEG", "JPG", "jpeg", "jpg");
            assertThat(reader.getOriginatingProvider().getMIMETypes()).contains("image/jpeg");
            assertThat(writer.getOriginatingProvider().getFormatNames()).contains("JPEG", "JPG", "jpeg", "jpg");
            assertThat(writer.getOriginatingProvider().getMIMETypes()).contains("image/jpeg");
        } finally {
            reader.dispose();
            writer.dispose();
        }
    }

    @Test
    void writesAndReadsRgbImageThroughPluginProviders() throws IOException {
        BufferedImage source = createColorQuadrants(48, 32);
        byte[] encoded = writeJpeg(source, false);

        assertThat(encoded).startsWith((byte) 0xff, (byte) 0xd8).endsWith((byte) 0xff, (byte) 0xd9);

        ImageReader reader = newJpegReader();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(encoded))) {
            reader.setInput(input);

            assertThat(reader.getNumImages(true)).isEqualTo(1);
            assertThat(reader.getWidth(0)).isEqualTo(48);
            assertThat(reader.getHeight(0)).isEqualTo(32);

            BufferedImage decoded = reader.read(0);
            assertThat(decoded.getWidth()).isEqualTo(source.getWidth());
            assertThat(decoded.getHeight()).isEqualTo(source.getHeight());
            assertColorCloseTo(decoded.getRGB(12, 8), Color.RED, 20);
            assertColorCloseTo(decoded.getRGB(36, 8), Color.GREEN, 20);
            assertColorCloseTo(decoded.getRGB(12, 24), Color.BLUE, 20);
            assertColorCloseTo(decoded.getRGB(36, 24), Color.YELLOW, 20);
        } finally {
            reader.dispose();
        }
    }

    @Test
    void readsRegionWithSubsampling() throws IOException {
        BufferedImage source = new BufferedImage(32, 24, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int sample = x * 255 / (source.getWidth() - 1);
                source.getRaster().setSample(x, y, 0, sample);
            }
        }

        byte[] encoded = writeJpeg(source, false);
        ImageReader reader = newJpegReader();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(encoded))) {
            reader.setInput(input);
            ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceRegion(new Rectangle(4, 4, 24, 16));
            param.setSourceSubsampling(3, 2, 0, 0);

            BufferedImage decoded = reader.read(0, param);
            assertThat(decoded.getWidth()).isEqualTo(8);
            assertThat(decoded.getHeight()).isEqualTo(8);
            assertThat(decoded.getRaster().getSample(0, 4, 0))
                    .isLessThan(decoded.getRaster().getSample(7, 4, 0));
        } finally {
            reader.dispose();
        }
    }

    @Test
    void exposesJpegImageMetadata() throws IOException {
        byte[] encoded = writeJpeg(createColorQuadrants(24, 16), true);
        ImageReader reader = newJpegReader();

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(encoded))) {
            reader.setInput(input);
            IIOMetadata metadata = reader.getImageMetadata(0);

            assertThat(metadata.isStandardMetadataFormatSupported()).isTrue();
            assertThat(metadata.getMetadataFormatNames())
                    .contains("javax_imageio_1.0", "javax_imageio_jpeg_image_1.0");
            Node nativeTree = metadata.getAsTree("javax_imageio_jpeg_image_1.0");
            Node standardTree = metadata.getAsTree("javax_imageio_1.0");
            assertThat(nativeTree.getNodeName()).isEqualTo("javax_imageio_jpeg_image_1.0");
            assertThat(standardTree.getNodeName()).isEqualTo("javax_imageio_1.0");
            assertThat(reader.read(0)).isNotNull();
        } finally {
            reader.dispose();
        }
    }

    @Test
    void readsEmbeddedJfifThumbnail() throws IOException {
        BufferedImage thumbnail = createColorQuadrants(4, 4);
        byte[] encoded = withJfifThumbnail(writeJpeg(createColorQuadrants(24, 16), false), thumbnail);
        ImageReader reader = newJpegReader();

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(encoded))) {
            reader.setInput(input);

            assertThat(reader.readerSupportsThumbnails()).isTrue();
            assertThat(reader.getNumThumbnails(0)).isEqualTo(1);
            assertThat(reader.getThumbnailWidth(0, 0)).isEqualTo(4);
            assertThat(reader.getThumbnailHeight(0, 0)).isEqualTo(4);

            BufferedImage decoded = reader.readThumbnail(0, 0);
            assertThat(new Color(decoded.getRGB(1, 1))).isEqualTo(Color.RED);
            assertThat(new Color(decoded.getRGB(3, 1))).isEqualTo(Color.GREEN);
            assertThat(new Color(decoded.getRGB(1, 3))).isEqualTo(Color.BLUE);
            assertThat(new Color(decoded.getRGB(3, 3))).isEqualTo(Color.YELLOW);
        } finally {
            reader.dispose();
        }
    }

    private static byte[] writeJpeg(BufferedImage image, boolean progressive) throws IOException {
        ImageWriter writer = newJpegWriter();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            assertThat(param.canWriteCompressed()).isTrue();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.95f);
            if (progressive) {
                assertThat(param.canWriteProgressive()).isTrue();
                param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            }
            writer.write(null, new IIOImage(image, null, null), param);
            output.flush();
        } finally {
            writer.dispose();
        }

        return bytes.toByteArray();
    }

    private static BufferedImage createColorQuadrants(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, width / 2, height / 2);
            graphics.setColor(Color.GREEN);
            graphics.fillRect(width / 2, 0, width - width / 2, height / 2);
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, height / 2, width / 2, height - height / 2);
            graphics.setColor(Color.YELLOW);
            graphics.fillRect(width / 2, height / 2, width - width / 2, height - height / 2);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static byte[] withJfifThumbnail(byte[] jpeg, BufferedImage thumbnail) {
        int segmentOffset = findJfifSegment(jpeg);
        int oldSegmentLength = (jpeg[segmentOffset + 2] & 0xff) << 8 | jpeg[segmentOffset + 3] & 0xff;
        int thumbnailLength = thumbnail.getWidth() * thumbnail.getHeight() * 3;
        int newSegmentLength = 16 + thumbnailLength;
        int oldSegmentEnd = segmentOffset + 2 + oldSegmentLength;
        byte[] result = new byte[jpeg.length - oldSegmentLength + newSegmentLength];

        System.arraycopy(jpeg, 0, result, 0, segmentOffset + 16);
        result[segmentOffset + 2] = (byte) (newSegmentLength >>> 8);
        result[segmentOffset + 3] = (byte) newSegmentLength;
        result[segmentOffset + 16] = (byte) thumbnail.getWidth();
        result[segmentOffset + 17] = (byte) thumbnail.getHeight();

        int pixelOffset = segmentOffset + 18;
        for (int y = 0; y < thumbnail.getHeight(); y++) {
            for (int x = 0; x < thumbnail.getWidth(); x++) {
                int rgb = thumbnail.getRGB(x, y);
                result[pixelOffset++] = (byte) (rgb >>> 16);
                result[pixelOffset++] = (byte) (rgb >>> 8);
                result[pixelOffset++] = (byte) rgb;
            }
        }

        System.arraycopy(jpeg, oldSegmentEnd, result, pixelOffset, jpeg.length - oldSegmentEnd);
        return result;
    }

    private static int findJfifSegment(byte[] jpeg) {
        for (int offset = 2; offset + 9 < jpeg.length; offset++) {
            if ((jpeg[offset] & 0xff) == 0xff
                    && (jpeg[offset + 1] & 0xff) == 0xe0
                    && jpeg[offset + 4] == 'J'
                    && jpeg[offset + 5] == 'F'
                    && jpeg[offset + 6] == 'I'
                    && jpeg[offset + 7] == 'F'
                    && jpeg[offset + 8] == 0) {
                return offset;
            }
        }
        throw new AssertionError("JPEG writer did not produce a JFIF segment");
    }

    private static ImageReader newJpegReader() {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("JPEG");
        while (readers.hasNext()) {
            ImageReader reader = readers.next();
            if (reader.getClass().getName().startsWith(PLUGIN_PACKAGE)) {
                return reader;
            }
            reader.dispose();
        }
        throw new AssertionError("TwelveMonkeys JPEG reader was not registered");
    }

    private static ImageWriter newJpegWriter() {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("JPEG");
        while (writers.hasNext()) {
            ImageWriter writer = writers.next();
            if (writer.getClass().getName().startsWith(PLUGIN_PACKAGE)) {
                return writer;
            }
            writer.dispose();
        }
        throw new AssertionError("TwelveMonkeys JPEG writer was not registered");
    }

    private static void assertColorCloseTo(int actualRgb, Color expected, int tolerance) {
        Color actual = new Color(actualRgb);
        assertThat(Math.abs(actual.getRed() - expected.getRed())).isLessThanOrEqualTo(tolerance);
        assertThat(Math.abs(actual.getGreen() - expected.getGreen())).isLessThanOrEqualTo(tolerance);
        assertThat(Math.abs(actual.getBlue() - expected.getBlue())).isLessThanOrEqualTo(tolerance);
    }
}
