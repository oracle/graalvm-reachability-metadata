/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package oracle.sql;

public final class ZONEIDMAP {
    private ZONEIDMAP() {
    }

    public static String getRegion(int regionCode) {
        if (regionCode == 1) {
            return "Europe/Paris";
        }
        throw new IllegalArgumentException("Unsupported test region code: " + regionCode);
    }
}
