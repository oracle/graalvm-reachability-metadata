/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Spring message localization with external order-notification catalogs and explicit refresh.
public class OrderMessageLocalizationTest {

    @Test
    @Timeout(60)
    public void localizesOrderReceiptsAndPublishesUpdatedTranslations(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("messages.properties"), """
                support.contact=Contact support at {0}
                """, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("messages_en.properties"), """
                order.receipt=Order {0}: {1} items, total {2,number,0.00}
                """, StandardCharsets.UTF_8);
        Path frenchCatalog = directory.resolve("messages_fr.properties");
        Files.writeString(frenchCatalog, """
                order.receipt=Commande {0} : {1} articles, total {2,number,0.00}
                """, StandardCharsets.UTF_8);

        ReloadableResourceBundleMessageSource messages = new ReloadableResourceBundleMessageSource();
        messages.setBasename(directory.resolve("messages").toUri().toString());
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        // The application explicitly refreshes the catalog after an administrator publishes it.
        messages.setCacheMillis(-1);

        Object[] receipt = {"ORD-42", 3, new BigDecimal("12.50")};
        assertEquals("Order ORD-42: 3 items, total 12.50",
                messages.getMessage("order.receipt", receipt, Locale.ENGLISH));
        assertEquals("Commande ORD-42 : 3 articles, total 12,50",
                messages.getMessage("order.receipt", receipt, Locale.FRENCH));
        assertEquals("Contact support at help@example.test",
                messages.getMessage("support.contact", new Object[]{"help@example.test"}, Locale.FRENCH));
        assertEquals("Delivery: Tuesday",
                messages.getMessage("delivery.window", new Object[]{"Tuesday"}, "Delivery: {0}", Locale.ENGLISH));

        Files.writeString(frenchCatalog, """
                order.receipt=Merci ! Commande {0} : {1} articles, total {2,number,0.00}
                """, StandardCharsets.UTF_8);
        messages.clearCache();

        assertEquals("Merci ! Commande ORD-42 : 3 articles, total 12,50",
                messages.getMessage("order.receipt", receipt, Locale.FRENCH));
        assertEquals("Order ORD-42: 3 items, total 12.50",
                messages.getMessage("order.receipt", receipt, Locale.ENGLISH));
        assertEquals("Contact support at help@example.test",
                messages.getMessage("support.contact", new Object[]{"help@example.test"}, Locale.GERMAN));
    }
}
