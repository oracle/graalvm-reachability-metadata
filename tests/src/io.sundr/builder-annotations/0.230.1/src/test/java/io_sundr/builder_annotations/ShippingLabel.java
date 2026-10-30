/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.builder_annotations;

public final class ShippingLabel {
    private final String recipient;
    private final int copies;

    public ShippingLabel(String recipient, int copies) {
        this.recipient = recipient;
        this.copies = copies;
    }

    public String getRecipient() {
        return recipient;
    }

    public int getCopies() {
        return copies;
    }
}
