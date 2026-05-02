/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.vfs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

public class VirtualFile {

    private final File file;

    public VirtualFile(File file) {
        this.file = file;
    }

    public boolean exists() {
        return this.file.exists();
    }

    public InputStream openStream() throws IOException {
        return new FileInputStream(this.file);
    }

    public long getSize() {
        return this.file.length();
    }

    public long getLastModified() {
        return this.file.lastModified();
    }

    public URI toURI() {
        return this.file.toURI();
    }

    public URL toURL() throws IOException {
        return this.file.toURI().toURL();
    }

    public String getName() {
        return this.file.getName();
    }

    public String getPathName() {
        return this.file.getPath();
    }

    public File getPhysicalFile() {
        return this.file;
    }

    public VirtualFile getChild(String path) {
        return new VirtualFile(new File(this.file, path));
    }

    public void visit(VirtualFileVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return this.file.toString();
    }
}
