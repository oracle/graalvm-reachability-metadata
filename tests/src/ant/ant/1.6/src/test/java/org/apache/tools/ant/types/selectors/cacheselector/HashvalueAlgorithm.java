/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.tools.ant.types.selectors.cacheselector;

import java.io.File;
import org.apache.tools.ant.types.selectors.modifiedselector.Algorithm;

public class HashvalueAlgorithm implements Algorithm {
    public HashvalueAlgorithm() {
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public String getValue(File file) {
        return Long.toString(file.length());
    }
}
