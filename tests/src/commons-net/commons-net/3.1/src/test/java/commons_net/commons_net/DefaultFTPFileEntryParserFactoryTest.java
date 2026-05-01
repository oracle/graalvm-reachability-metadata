/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_net.commons_net;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileEntryParser;
import org.apache.commons.net.ftp.parser.DefaultFTPFileEntryParserFactory;
import org.apache.commons.net.ftp.parser.UnixFTPEntryParser;
import org.junit.jupiter.api.Test;

public class DefaultFTPFileEntryParserFactoryTest {
    @Test
    void createsParserFromFullyQualifiedParserClassName() {
        DefaultFTPFileEntryParserFactory factory = new DefaultFTPFileEntryParserFactory();
        String parserClassName = "org.apache.commons.net.ftp.parser.UnixFTPEntryParser";

        FTPFileEntryParser parser = factory.createFileEntryParser(parserClassName);

        assertThat(parser).isInstanceOf(UnixFTPEntryParser.class);
        FTPFile parsedFile = parser.parseFTPEntry("-rw-r--r--   1 owner group        531 Jan 29 03:26 readme.txt");
        assertThat(parsedFile).isNotNull();
        assertThat(parsedFile.getName()).isEqualTo("readme.txt");
        assertThat(parsedFile.getSize()).isEqualTo(531L);
        assertThat(parsedFile.isFile()).isTrue();
    }
}
