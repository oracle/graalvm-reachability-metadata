/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dnsjava.dnsjava;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

import static org.assertj.core.api.Assertions.assertThat;

public class DnsjavaTest {
    @Test
    public void roundTripsDnsResponseThroughWireFormat() throws Exception {
        Name host = Name.fromString("wire.example.");
        Record question = Record.newRecord(host, Type.A, DClass.IN);
        ARecord answer = new ARecord(host, DClass.IN, 300,
                InetAddress.getByAddress(new byte[]{(byte) 192, 0, 2, 42}));
        Message response = Message.newQuery(question);
        response.getHeader().setFlag(Flags.QR);
        response.addRecord(answer, Section.ANSWER);

        Message decoded = new Message(response.toWire());

        assertThat(decoded.getHeader().getID()).isEqualTo(response.getHeader().getID());
        assertThat(decoded.getHeader().getFlag(Flags.QR)).isTrue();
        assertThat(decoded.getQuestion()).isEqualTo(question);
        assertThat(decoded.getSectionArray(Section.ANSWER)).containsExactly(answer);
        assertThat(decoded.getSectionArray(Section.AUTHORITY)).isEmpty();
        assertThat(decoded.getSectionArray(Section.ADDITIONAL)).isEmpty();
    }
}
