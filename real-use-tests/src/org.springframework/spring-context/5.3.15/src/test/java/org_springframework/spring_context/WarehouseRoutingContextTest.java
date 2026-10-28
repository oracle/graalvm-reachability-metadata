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
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Spring XML application-context loading to configure shipping routes from imports and external properties.
public class WarehouseRoutingContextTest {

    @Test
    @Timeout(60)
    public void loadsRegionalCarriersAndDeploymentSettingsFromExternalConfiguration(@TempDir Path directory)
            throws IOException {
        Files.writeString(directory.resolve("carriers.xml"), """
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xmlns:util="http://www.springframework.org/schema/util"
                       xsi:schemaLocation="
                           http://www.springframework.org/schema/beans
                           https://www.springframework.org/schema/beans/spring-beans.xsd
                           http://www.springframework.org/schema/util
                           https://www.springframework.org/schema/util/spring-util.xsd">
                    <util:map id="regionalCarriers" key-type="java.lang.String" value-type="java.lang.String">
                        <entry key="DE" value="parcel-eu"/>
                        <entry key="US" value="parcel-us"/>
                    </util:map>
                </beans>
                """, StandardCharsets.UTF_8);
        Path settings = directory.resolve("shipping.properties");
        Files.writeString(settings, """
                shipping.fallback=international-post
                shipping.reference-prefix=WAREHOUSE-A
                """, StandardCharsets.UTF_8);
        Path configuration = directory.resolve("shipping.xml");
        Files.writeString(configuration, """
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xmlns:context="http://www.springframework.org/schema/context"
                       xsi:schemaLocation="
                           http://www.springframework.org/schema/beans
                           https://www.springframework.org/schema/beans/spring-beans.xsd
                           http://www.springframework.org/schema/context
                           https://www.springframework.org/schema/context/spring-context.xsd">
                    <import resource="carriers.xml"/>
                    <context:property-placeholder location="%s" system-properties-mode="NEVER"/>
                    <bean id="shippingRouter"
                          class="org_springframework.spring_context.WarehouseRoutingContextTest$ShippingRouter">
                        <constructor-arg index="0" ref="regionalCarriers"/>
                        <constructor-arg index="1" value="${shipping.fallback}"/>
                        <constructor-arg index="2" value="${shipping.reference-prefix}"/>
                    </bean>
                </beans>
                """.formatted(settings.toUri()), StandardCharsets.UTF_8);

        try (FileSystemXmlApplicationContext context =
                     new FileSystemXmlApplicationContext(configuration.toUri().toString())) {
            ShippingRouter router = context.getBean("shippingRouter", ShippingRouter.class);
            assertEquals("WAREHOUSE-A/order-42 via parcel-eu", router.dispatch("order-42", "DE"));
            assertEquals("WAREHOUSE-A/order-43 via parcel-us", router.dispatch("order-43", "US"));
            assertEquals("WAREHOUSE-A/order-44 via international-post", router.dispatch("order-44", "AU"));
        }

        // The same application can be deployed with another warehouse's external settings.
        Files.writeString(settings, """
                shipping.fallback=global-express
                shipping.reference-prefix=WAREHOUSE-B
                """, StandardCharsets.UTF_8);
        try (FileSystemXmlApplicationContext context =
                     new FileSystemXmlApplicationContext(configuration.toUri().toString())) {
            ShippingRouter router = context.getBean("shippingRouter", ShippingRouter.class);
            assertEquals("WAREHOUSE-B/order-45 via global-express", router.dispatch("order-45", "AU"));
            assertEquals("WAREHOUSE-B/order-46 via parcel-eu", router.dispatch("order-46", "DE"));
        }
    }

    public static final class ShippingRouter {
        private final Map<String, String> regionalCarriers;
        private final String fallbackCarrier;
        private final String referencePrefix;

        public ShippingRouter(Map<String, String> regionalCarriers, String fallbackCarrier, String referencePrefix) {
            this.regionalCarriers = Map.copyOf(regionalCarriers);
            this.fallbackCarrier = fallbackCarrier;
            this.referencePrefix = referencePrefix;
        }

        public String dispatch(String orderId, String destinationCountry) {
            String carrier = regionalCarriers.getOrDefault(destinationCountry, fallbackCarrier);
            return referencePrefix + "/" + orderId + " via " + carrier;
        }
    }
}
