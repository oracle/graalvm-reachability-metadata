/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity;

import java.io.StringWriter;
import java.util.List;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Velocity's loop-break directive subsystem for choosing an available warehouse.
public class FirstAvailableWarehouseTemplateTest {
    @Test
    @Timeout(60)
    public void selectsFirstWarehouseWithInventory() {
        VelocityEngine engine = new VelocityEngine();
        engine.init();

        VelocityContext context = new VelocityContext();
        context.put("warehouses", List.of(
                new Warehouse("East", false),
                new Warehouse("Central", true),
                new Warehouse("West", true)));

        StringWriter result = new StringWriter();
        boolean rendered = engine.evaluate(
                context,
                result,
                "warehouse-selection",
                "#set($selected = 'none')\n"
                        + "#foreach($warehouse in $warehouses)\n"
                        + "#if($warehouse.available)\n"
                        + "#set($selected = $warehouse.name)\n"
                        + "#break\n"
                        + "#end\n"
                        + "#end\n"
                        + "Warehouse: $selected");

        assertTrue(rendered);
        assertEquals("Warehouse: Central", result.toString());
    }

    public static final class Warehouse {
        private final String name;
        private final boolean available;

        public Warehouse(String name, boolean available) {
            this.name = name;
            this.available = available;
        }

        public String getName() {
            return name;
        }

        public boolean isAvailable() {
            return available;
        }
    }
}
