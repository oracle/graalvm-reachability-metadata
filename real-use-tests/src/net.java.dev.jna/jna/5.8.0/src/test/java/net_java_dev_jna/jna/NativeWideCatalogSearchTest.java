/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises JNA's wide-character string marshalling to search a multilingual product catalog with C wcsstr.
 */
public class NativeWideCatalogSearchTest {

    @Test
    @Timeout(60)
    public void findsProductsByUnicodeTitleWithoutUsingTheHostLocale() {
        WideText text = Native.load(Platform.C_LIBRARY_NAME, WideText.class);
        List<Product> catalog = List.of(
                new Product("COFFEE-01", "caf\u00e9 moulu"),
                new Product("TEA-02", "\u65e5\u672c\u8336"),
                new Product("COFFEE-03", "caf\u00e9 en grains"),
                new Product("TEA-04", "\u7d05\u8336"),
                new Product("COFFEE-05", "cafe decaf"));

        assertEquals(List.of("COFFEE-01", "COFFEE-03"), findProducts(text, catalog, "caf\u00e9"),
                "Accented product names must match without also matching an unaccented spelling");
        assertEquals(List.of("TEA-02", "TEA-04"), findProducts(text, catalog, "\u8336"),
                "A wide-character query must find titles containing the requested ideograph");
        assertEquals(List.of("TEA-02"), findProducts(text, catalog, "\u65e5\u672c"));
        assertEquals(List.of(), findProducts(text, catalog, "CAF\u00c9"),
                "Catalog substring matching is case-sensitive and independent of locale");
        assertEquals(List.of(), findProducts(text, catalog, "\u62b9\u8336"),
                "A missing multi-character query must not match only its final character");
    }

    private static List<String> findProducts(WideText text, List<Product> catalog, String query) {
        WString nativeQuery = new WString(query);
        return catalog.stream()
                .filter(product -> contains(text, product.title(), nativeQuery))
                .map(Product::sku)
                .toList();
    }

    private static boolean contains(WideText text, String title, WString query) {
        // Reserve wchar_t units, including the terminator, rather than UTF-8 bytes.
        long allocation = Native.malloc((title.length() + 1L) * Native.WCHAR_SIZE);
        if (allocation == 0) {
            throw new OutOfMemoryError("Cannot allocate native catalog title");
        }
        try {
            Pointer nativeTitle = new Pointer(allocation);
            nativeTitle.setWideString(0, title);
            // wcsstr borrows title storage; consume the result before releasing that storage.
            return text.wcsstr(nativeTitle, query) != null;
        } finally {
            Native.free(allocation);
        }
    }

    private record Product(String sku, String title) {
    }

    public interface WideText extends Library {
        Pointer wcsstr(Pointer title, WString query);
    }
}
