/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.UnparsedTextURIResolver;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Saxon's unparsed-text URI resolution subsystem for application configuration.
 *
 * A pricing service reads a logical, application-owned configuration URI through Saxon's XPath
 * functions, allowing the service to keep configuration in its own store while the query remains
 * independent of filesystem paths. The service returns the parsed pricing tiers to its caller.
 */
public class UnparsedTextConfigurationTest {

    private static final URI PRICING_URI = URI.create("urn:store:pricing-tiers");
    private static final String PRICING_TEXT = "standard|12.50\npremium|8.00\nenterprise|5.00\n";

    @Test
    @Timeout(60)
    public void readsPricingTiersThroughApplicationUriResolver() throws Exception {
        Processor processor = new Processor(false);
        Configuration configuration = processor.getUnderlyingConfiguration();
        configuration.setUnparsedTextURIResolver(new PricingConfigurationResolver(PRICING_TEXT));

        XPathExecutable pricingQuery = processor.newXPathCompiler().compile(
                "for $line in tokenize(unparsed-text('urn:store:pricing-tiers'), '\\n')"
                        + "[string-length(.) gt 0]"
                        + " return tokenize($line, '\\|')[1] || ':' || tokenize($line, '\\|')[2]");
        XPathSelector selector = pricingQuery.load();
        XdmValue result = selector.evaluate();

        List<String> tiers = new ArrayList<>();
        for (XdmItem item : result) {
            tiers.add(item.getStringValue());
        }

        assertEquals(List.of("standard:12.50", "premium:8.00", "enterprise:5.00"), tiers);
    }

    private static final class PricingConfigurationResolver implements UnparsedTextURIResolver {

        private final String configurationText;

        private PricingConfigurationResolver(String configurationText) {
            this.configurationText = configurationText;
        }

        @Override
        public Reader resolve(URI absoluteURI, String encoding, Configuration configuration)
                throws XPathException {
            if (!PRICING_URI.equals(absoluteURI)) {
                throw new XPathException("Unexpected configuration URI: " + absoluteURI);
            }
            return new StringReader(configurationText);
        }
    }
}
