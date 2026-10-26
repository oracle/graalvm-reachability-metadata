/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Spring annotation-based dependency injection to configure invoice pricing for retail and wholesale deployments.
public class ProfiledInvoiceConfigurationTest {

    @Test
    @Timeout(60)
    public void appliesTheDeploymentPricingPolicyAndExternalTaxRate() {
        try (AnnotationConfigApplicationContext retail = deployment("retail", "0.20");
                AnnotationConfigApplicationContext wholesale = deployment("wholesale", "0.10")) {
            InvoiceService retailInvoices = retail.getBean(InvoiceService.class);
            InvoiceService wholesaleInvoices = wholesale.getBean(InvoiceService.class);
            BigDecimal unitPrice = new BigDecimal("20.00");

            assertEquals(new BigDecimal("120.00"), retailInvoices.total(unitPrice, 5));
            assertEquals(new BigDecimal("240.00"), retailInvoices.total(unitPrice, 10));
            assertEquals(new BigDecimal("110.00"), wholesaleInvoices.total(unitPrice, 5));
            assertEquals(new BigDecimal("187.00"), wholesaleInvoices.total(unitPrice, 10));

            // Configuring the wholesale deployment must not change the retail application's policy.
            assertEquals(new BigDecimal("240.00"), retailInvoices.total(unitPrice, 10));
        }
    }

    private static AnnotationConfigApplicationContext deployment(String profile, String taxRate) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            context.getEnvironment().setActiveProfiles(profile);
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("invoice-deployment", Map.of("invoice.tax-rate", taxRate)));
            context.register(InvoiceConfiguration.class);
            context.refresh();
            return context;
        } catch (RuntimeException | Error failure) {
            context.close();
            throw failure;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static final class InvoiceConfiguration {
        @Bean
        public static PropertySourcesPlaceholderConfigurer invoiceProperties() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        @Profile("retail")
        public PricingPolicy retailPricing() {
            return new PricingPolicy(1, BigDecimal.ONE);
        }

        @Bean
        @Profile("wholesale")
        public PricingPolicy wholesalePricing() {
            return new PricingPolicy(10, new BigDecimal("0.85"));
        }

        @Bean
        public InvoiceService invoiceService(PricingPolicy policy, @Value("${invoice.tax-rate}") BigDecimal taxRate) {
            return new InvoiceService(policy, taxRate);
        }
    }

    public record PricingPolicy(int minimumQuantity, BigDecimal priceFactor) {
        public BigDecimal netTotal(BigDecimal unitPrice, int quantity) {
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            return quantity >= minimumQuantity ? subtotal.multiply(priceFactor) : subtotal;
        }
    }

    public static final class InvoiceService {
        private final PricingPolicy policy;
        private final BigDecimal taxRate;

        public InvoiceService(PricingPolicy policy, BigDecimal taxRate) {
            this.policy = policy;
            this.taxRate = taxRate;
        }

        public BigDecimal total(BigDecimal unitPrice, int quantity) {
            return policy.netTotal(unitPrice, quantity)
                    .multiply(BigDecimal.ONE.add(taxRate)).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
