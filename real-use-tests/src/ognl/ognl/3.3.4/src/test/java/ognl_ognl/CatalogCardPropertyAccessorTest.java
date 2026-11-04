/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl_ognl;

import ognl.Ognl;
import ognl.OgnlContext;
import ognl.OgnlException;
import ognl.OgnlRuntime;
import ognl.PropertyAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises OGNL's property-accessor subsystem for a catalog card editor.
 *
 * A catalog application registers an accessor for its card value object so template expressions
 * can read display attributes and update inventory without exposing the storage representation.
 */
public class CatalogCardPropertyAccessorTest {
    @Test
    @Timeout(60)
    void readsAndUpdatesCatalogCardAttributesThroughRegisteredAccessor() throws OgnlException {
        CatalogCard card = new CatalogCard("SKU-2048", "Travel mug", 7);
        PropertyAccessor previousAccessor = OgnlRuntime.getPropertyAccessor(CatalogCard.class);
        OgnlRuntime.setPropertyAccessor(CatalogCard.class, new CatalogCardAccessor());

        try {
            OgnlContext context = (OgnlContext) Ognl.createDefaultContext(card);
            Object cardSummary = Ognl.getValue("sku + ': ' + title", context, card);
            Ognl.setValue("quantity", context, card, 12);
            Object availableQuantity = Ognl.getValue("quantity", context, card);

            assertThat(cardSummary).isEqualTo("SKU-2048: Travel mug");
            assertThat(availableQuantity).isEqualTo(12);
            assertThat(card.attributes()).containsEntry("quantity", 12);
        } finally {
            OgnlRuntime.setPropertyAccessor(CatalogCard.class, previousAccessor);
        }
    }

    public static final class CatalogCardAccessor implements PropertyAccessor {
        @Override
        public Object getProperty(Map context, Object target, Object name) throws OgnlException {
            CatalogCard card = (CatalogCard) target;
            Object value = card.attributes().get(name);
            if (value == null && !card.attributes().containsKey(name)) {
                throw new OgnlException("Unknown catalog card attribute: " + name);
            }
            return value;
        }

        @Override
        public void setProperty(Map context, Object target, Object name, Object value)
                throws OgnlException {
            CatalogCard card = (CatalogCard) target;
            String attributeName = String.valueOf(name);
            if (!card.attributes().containsKey(attributeName)) {
                throw new OgnlException("Unknown catalog card attribute: " + name);
            }
            card.attributes().put(attributeName, value);
        }

        @Override
        public String getSourceAccessor(OgnlContext context, Object target, Object name) {
            return null;
        }

        @Override
        public String getSourceSetter(OgnlContext context, Object target, Object name) {
            return null;
        }
    }

    public static final class CatalogCard {
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private CatalogCard(String sku, String title, int quantity) {
            attributes.put("sku", sku);
            attributes.put("title", title);
            attributes.put("quantity", quantity);
        }

        public Map<String, Object> attributes() {
            return attributes;
        }
    }
}
