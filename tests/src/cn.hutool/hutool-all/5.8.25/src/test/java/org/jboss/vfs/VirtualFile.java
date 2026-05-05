/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.vfs;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;

public class VirtualFile {
    private final String name;
    private final byte[] content;
    private final URL url;
    private long lastModified;

    public VirtualFile(String name, byte[] content, URL url, long lastModified) {
        this.name = name;
        this.content = Arrays.copyOf(content, content.length);
        this.url = url;
        this.lastModified = lastModified;
    }

    public boolean exists() {
        return true;
    }

    public InputStream openStream() {
        return new ByteArrayInputStream(content);
    }

    public long getSize() {
        return content.length;
    }

    public long getLastModified() {
        return lastModified;
    }

    public URL toURL() {
        return url;
    }

    public String getName() {
        return name;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
}
