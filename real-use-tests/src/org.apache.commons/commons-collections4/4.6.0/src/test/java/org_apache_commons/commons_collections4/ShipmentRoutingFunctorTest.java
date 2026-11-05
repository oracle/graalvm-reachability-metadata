/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.Closure;
import org.apache.commons.collections4.ClosureUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.collections4.PredicateUtils;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.collections4.TransformerUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the functor subsystem for validating shipments and assigning fulfillment services.
 */
public class ShipmentRoutingFunctorTest {
    @Test
    @Timeout(60)
    public void validatesShipmentsAndRoutesReadyOrders() {
        Predicate<Shipment> paid = Shipment::paid;
        Predicate<Shipment> hasItems = shipment -> shipment.itemCount() > 0;
        Predicate<Shipment> hasDestination = shipment -> !shipment.destination().isBlank();
        Predicate<Shipment> ready = PredicateUtils.allPredicate(paid, hasItems, hasDestination);

        Transformer<Shipment, String> service = TransformerUtils.ifTransformer(
                Shipment::express,
                TransformerUtils.<Shipment, String>constantTransformer("express"),
                TransformerUtils.<Shipment, String>constantTransformer("standard"));

        List<String> dispatchLog = new ArrayList<>();
        Closure<Shipment> dispatch = ClosureUtils.ifClosure(
                ready,
                shipment -> dispatchLog.add(shipment.id() + " via " + service.transform(shipment)),
                shipment -> dispatchLog.add(shipment.id() + " rejected"));

        Shipment expressShipment = new Shipment("S-100", true, 2, "Boston", true);
        Shipment standardShipment = new Shipment("S-101", true, 1, "Denver", false);
        Shipment unpaidShipment = new Shipment("S-102", false, 3, "Austin", true);
        Shipment addressMissingShipment = new Shipment("S-103", true, 1, "", false);

        List.of(expressShipment, standardShipment, unpaidShipment, addressMissingShipment)
                .forEach(dispatch::execute);

        assertEquals(List.of(
                "S-100 via express",
                "S-101 via standard",
                "S-102 rejected",
                "S-103 rejected"), dispatchLog);
    }

    private static final class Shipment {
        private final String id;
        private final boolean paid;
        private final int itemCount;
        private final String destination;
        private final boolean express;

        private Shipment(String id, boolean paid, int itemCount, String destination, boolean express) {
            this.id = id;
            this.paid = paid;
            this.itemCount = itemCount;
            this.destination = destination;
            this.express = express;
        }

        private String id() {
            return id;
        }

        private boolean paid() {
            return paid;
        }

        private int itemCount() {
            return itemCount;
        }

        private String destination() {
            return destination;
        }

        private boolean express() {
            return express;
        }
    }
}
