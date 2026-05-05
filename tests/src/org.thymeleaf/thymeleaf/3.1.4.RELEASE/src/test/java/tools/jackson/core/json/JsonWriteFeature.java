/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools.jackson.core.json;

public enum JsonWriteFeature {
    ESCAPE_NON_ASCII;

    public JsonWriteFeature mappedFeature() {
        return this;
    }
}
