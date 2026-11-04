/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack_handlebars;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.context.FieldValueResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Handlebars' public-field value-resolution subsystem for an inventory page.
public class PublicFieldInventoryTest {
    @Test
    @Timeout(60)
    public void rendersInventoryFromPublicFieldModel() throws IOException {
        Inventory inventory = new Inventory(
                "WH-7",
                "Central workshop",
                List.of(
                        new Item("WR-42", "Socket wrench", 18),
                        new Item("SG-10", "Safety glasses", 6)));
        Handlebars handlebars = new Handlebars();
        Template template = handlebars.compileInline("""
                <section data-warehouse="{{warehouseCode}}">
                  <h1>{{name}}</h1>
                  <ul>{{#each items}}<li data-sku="{{sku}}">{{description}} ({{quantity}})</li>{{/each}}</ul>
                </section>
                """);

        Context context = Context.newBuilder(inventory)
                .resolver(FieldValueResolver.INSTANCE)
                .build();
        String rendered = template.apply(context);

        assertEquals(
                "<section data-warehouse=\"WH-7\"> <h1>Central workshop</h1> "
                        + "<ul><li data-sku=\"WR-42\">Socket wrench (18)</li>"
                        + "<li data-sku=\"SG-10\">Safety glasses (6)</li></ul> </section>",
                rendered.replaceAll("\\s+", " ").trim());
    }

    public static final class Inventory {
        public final String warehouseCode;
        public final String name;
        public final List<Item> items;

        public Inventory(String warehouseCode, String name, List<Item> items) {
            this.warehouseCode = warehouseCode;
            this.name = name;
            this.items = items;
        }
    }

    public static final class Item {
        public final String sku;
        public final String description;
        public final int quantity;

        public Item(String sku, String description, int quantity) {
            this.sku = sku;
            this.description = description;
            this.quantity = quantity;
        }
    }
}
