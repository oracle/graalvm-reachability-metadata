/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl_ognl;

import ognl.Ognl;
import ognl.OgnlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises OGNL's type-conversion subsystem for binding submitted checkout preferences.
 *
 * A checkout form submits all field values as text, while the application model stores the
 * resulting settings as typed properties.
 */
public class FormBindingTypeConversionTest {
    @Test
    @Timeout(60)
    void convertsSubmittedFormValuesIntoTypedProperties() throws OgnlException {
        CheckoutPreferences preferences = new CheckoutPreferences();
        Map context = Ognl.createDefaultContext(preferences);

        Ognl.setValue("maximumItems", context, preferences, "12");
        Ognl.setValue("expressDelivery", context, preferences, "true");

        assertThat(preferences.getMaximumItems()).isEqualTo(12);
        assertThat(preferences.isExpressDelivery()).isTrue();
        assertThat(Ognl.getValue("maximumItems", context, preferences)).isEqualTo(12);
        assertThat(Ognl.getValue("expressDelivery", context, preferences)).isEqualTo(true);
    }

    public static final class CheckoutPreferences {
        private int maximumItems;
        private boolean expressDelivery;

        public int getMaximumItems() {
            return maximumItems;
        }

        public void setMaximumItems(int maximumItems) {
            this.maximumItems = maximumItems;
        }

        public boolean isExpressDelivery() {
            return expressDelivery;
        }

        public void setExpressDelivery(boolean expressDelivery) {
            this.expressDelivery = expressDelivery;
        }
    }
}
