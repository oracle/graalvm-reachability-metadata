/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package sun.misc;

import java.util.Base64;

public class BASE64Encoder {
    public String encode(byte[] input) {
        return "legacy-" + Base64.getEncoder().encodeToString(input);
    }
}
