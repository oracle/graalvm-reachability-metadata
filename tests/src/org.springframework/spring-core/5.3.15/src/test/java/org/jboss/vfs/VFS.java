/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.vfs;

import java.io.File;
import java.net.URI;
import java.net.URL;

public final class VFS {

    private VFS() {
    }

    public static VirtualFile getChild(URL url) throws Exception {
        return getChild(url.toURI());
    }

    public static VirtualFile getChild(URI uri) {
        return new VirtualFile(new File(uri));
    }
}
