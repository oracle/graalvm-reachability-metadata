/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.mail.handlers.image_gif;
import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class ImageGifTest {
    @Test
    void advertisesGifImagesAndRejectsNonImageOutput() {
        image_gif handler = new image_gif();
        DataFlavor[] flavors = handler.getTransferDataFlavors();
        assertEquals(1, flavors.length);
        assertEquals(Image.class, flavors[0].getRepresentationClass());
        assertEquals("image", flavors[0].getPrimaryType());
        assertEquals("gif", flavors[0].getSubType());
        IOException failure = assertThrows(IOException.class,
                () -> handler.writeTo("not an image", "image/gif", new ByteArrayOutputStream()));
        assertTrue(failure.getMessage().contains("requires Image object"));
    }
}
