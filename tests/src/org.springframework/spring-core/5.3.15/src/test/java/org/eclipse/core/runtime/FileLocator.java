/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.eclipse.core.runtime;

import java.net.URL;

public final class FileLocator {

    private FileLocator() {
    }

    public static URL resolve(URL url) {
        return url;
    }
}
