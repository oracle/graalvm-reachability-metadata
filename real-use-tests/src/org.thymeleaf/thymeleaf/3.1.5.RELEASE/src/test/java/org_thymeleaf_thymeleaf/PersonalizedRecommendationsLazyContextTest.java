/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf_thymeleaf;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.LazyContextVariable;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Thymeleaf's lazy context variable subsystem for an optional product recommendations panel.
public class PersonalizedRecommendationsLazyContextTest {
    @Test
    public void loadsRecommendationsOnlyWhenThePageDisplaysThem() {
        TemplateEngine templateEngine = newTemplateEngine();
        AtomicInteger recommendationLoads = new AtomicInteger();
        LazyContextVariable<List<String>> recommendations = new LazyContextVariable<>() {
            @Override
            protected List<String> loadValue() {
                recommendationLoads.incrementAndGet();
                return List.of("Travel adapter", "Noise-cancelling headphones");
            }
        };

        Context basicCustomer = new Context();
        basicCustomer.setVariable("customerName", "Nora");
        basicCustomer.setVariable("showRecommendations", false);
        basicCustomer.setVariable("recommendations", recommendations);

        String basicPage = templateEngine.process(RECOMMENDATIONS_TEMPLATE, basicCustomer);

        assertTrue(basicPage.contains("<h1>Welcome, Nora</h1>"));
        assertFalse(basicPage.contains("Travel adapter"));
        assertEquals(0, recommendationLoads.get());

        Context subscribedCustomer = new Context();
        subscribedCustomer.setVariable("customerName", "Nora");
        subscribedCustomer.setVariable("showRecommendations", true);
        subscribedCustomer.setVariable("recommendations", recommendations);

        String subscribedPage = templateEngine.process(RECOMMENDATIONS_TEMPLATE, subscribedCustomer);

        assertTrue(subscribedPage.contains("<h2>Recommended for you</h2>"));
        assertTrue(subscribedPage.contains("<li>Travel adapter</li>"));
        assertTrue(subscribedPage.contains("<li>Noise-cancelling headphones</li>"));
        assertEquals(1, recommendationLoads.get());
    }

    private static TemplateEngine newTemplateEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }

    private static final String RECOMMENDATIONS_TEMPLATE = """
            <!doctype html>
            <html xmlns:th="http://www.thymeleaf.org">
            <body>
            <h1 th:text="|Welcome, ${customerName}|">Welcome</h1>
            <section th:if="${showRecommendations}">
              <h2>Recommended for you</h2>
              <ul>
                <li th:each="recommendation : ${recommendations}" th:text="${recommendation}">Product</li>
              </ul>
            </section>
            </body>
            </html>
            """;
}
