/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dnsjava.dnsjava;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.APLRecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Cache;
import org.xbill.DNS.ClientSubnetOption;
import org.xbill.DNS.Credibility;
import org.xbill.DNS.DClass;
import org.xbill.DNS.DNAMERecord;
import org.xbill.DNS.DNSKEYRecord;
import org.xbill.DNS.DNSSEC;
import org.xbill.DNS.DSRecord;
import org.xbill.DNS.EDNSOption;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Header;
import org.xbill.DNS.LOCRecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.Master;
import org.xbill.DNS.Message;
import org.xbill.DNS.NAPTRRecord;
import org.xbill.DNS.NSEC3Record;
import org.xbill.DNS.NSECRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.OPTRecord;
import org.xbill.DNS.Opcode;
import org.xbill.DNS.Options;
import org.xbill.DNS.RRset;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.ReverseMap;
import org.xbill.DNS.SOARecord;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.Section;
import org.xbill.DNS.SetResponse;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TSIG;
import org.xbill.DNS.TSIGRecord;
import org.xbill.DNS.TLSARecord;
import org.xbill.DNS.TTL;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.Type;
import org.xbill.DNS.URIRecord;
import org.xbill.DNS.Update;
import org.xbill.DNS.Zone;
import org.xbill.DNS.ZoneTransferIn;
import org.xbill.DNS.utils.base32;
import org.xbill.DNS.utils.base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class DnsjavaTest {

    @Test
    void parsesNamesMnemonicsTtlsAndAddressMappings() throws Exception {
        Name origin = Name.fromString("example.com.");
        Name service = Name.fromString("_sip._tcp", origin);
        Name absolute = Name.concatenate(Name.fromString("api"), origin);
        Name wildcard = absolute.wild(1);

        assertThat(service.toString()).isEqualTo("_sip._tcp.example.com.");
        assertThat(absolute.isAbsolute()).isTrue();
        assertThat(absolute.relativize(origin).toString()).isEqualTo("api");
        assertThat(wildcard.isWild()).isTrue();
        assertThat(Name.fromString("API.Example.COM.").canonicalize()).isEqualTo(absolute);
        assertThat(new Name(absolute.toWire())).isEqualTo(absolute);
        assertThat(ReverseMap.fromAddress("192.0.2.7").toString()).isEqualTo("7.2.0.192.in-addr.arpa.");
        assertThat(ReverseMap.fromAddress(InetAddress.getByName("2001:db8::1")).toString())
                .endsWith("ip6.arpa.");

        assertThat(Type.value("AAAA")).isEqualTo(Type.AAAA);
        assertThat(Type.string(Type.MX)).isEqualTo("MX");
        assertThat(Type.isRR(Type.OPT)).isFalse();
        assertThat(DClass.value("IN")).isEqualTo(DClass.IN);
        assertThat(Section.longString(Section.ADDITIONAL)).contains("ADDITIONAL");
        assertThat(Opcode.value("UPDATE")).isEqualTo(Opcode.UPDATE);
        assertThat(Rcode.TSIGstring(Rcode.BADKEY)).isEqualTo("BADKEY");
        assertThat(Flags.value("rd")).isEqualTo(Flags.RD);
        assertThat(TTL.parseTTL("1H30M")).isEqualTo(5_400L);
        assertThat(TTL.format(90_061L)).isEqualTo("1D1H1M1S");

        Options.clear();
        Options.set("verbose", "true");
        assertThat(Options.check("verbose")).isTrue();
        assertThat(Options.value("verbose")).isEqualTo("true");
        Options.unset("verbose");
    }

    @Test
    void roundTripsCommonRecordTypesThroughTextAndWireFormats() throws Exception {
        Name origin = Name.fromString("example.com.");
        Name host = Name.fromString("www.example.com.");
        List<Record> records = new ArrayList<>();
        records.add(new ARecord(host, DClass.IN, 300, InetAddress.getByName("192.0.2.10")));
        records.add(new AAAARecord(host, DClass.IN, 300, InetAddress.getByName("2001:db8::10")));
        records.add(new MXRecord(origin, DClass.IN, 3_600, 10, Name.fromString("mail.example.com.")));
        records.add(new TXTRecord(origin, DClass.IN, 120, Arrays.asList("v=spf1", "-all")));
        records.add(new SRVRecord(Name.fromString("_sip._tcp.example.com."), DClass.IN, 450,
                1, 5, 5060, Name.fromString("sip.example.com.")));
        records.add(new SOARecord(origin, DClass.IN, 3_600, Name.fromString("ns.example.com."),
                Name.fromString("hostmaster.example.com."), 2024050501L, 7_200L, 3_600L, 1_209_600L, 300L));
        records.add(new NAPTRRecord(origin, DClass.IN, 60, 100, 10, "U", "E2U+sip",
                "!^.*$!sip:info@example.com!", Name.root));
        records.add(new URIRecord(Name.fromString("_web.example.com."), DClass.IN, 60,
                10, 1, "https://example.com/service"));
        records.add(new LOCRecord(origin, DClass.IN, 60, 37.386, -122.084, 15.5, 1.0, 10.0, 10.0));
        records.add(new APLRecord(origin, DClass.IN, 60,
                Collections.singletonList(new APLRecord.Element(false, InetAddress.getByName("192.0.2.0"), 24))));
        records.add(new TLSARecord(Name.fromString("_443._tcp.example.com."), DClass.IN, 60,
                3, 1, 1, new byte[] {1, 2, 3, 4}));
        records.add(new NSECRecord(origin, DClass.IN, 60, Name.fromString("next.example.com."),
                new int[] {Type.A, Type.AAAA, Type.RRSIG, Type.NSEC}));
        records.add(new NSEC3Record(Name.fromString("HASH.example.com."), DClass.IN, 60,
                NSEC3Record.SHA1_DIGEST_ID, 0, 2, new byte[] {9, 8}, new byte[] {1, 2, 3},
                new int[] {Type.SOA, Type.NS, Type.DNSKEY}));
        records.add(new DNSKEYRecord(origin, DClass.IN, 60, DNSKEYRecord.Flags.ZONE_KEY,
                DNSKEYRecord.Protocol.DNSSEC, DNSSEC.Algorithm.RSASHA256, new byte[] {3, 1, 0, 1}));
        records.add(new DSRecord(origin, DClass.IN, 60, 12_345, DNSSEC.Algorithm.RSASHA256,
                DSRecord.SHA256_DIGEST_ID, new byte[] {10, 11, 12, 13}));

        for (Record record : records) {
            Record parsed = Record.fromWire(record.toWire(Section.ANSWER), Section.ANSWER);
            assertThat(parsed).isEqualTo(record);
            assertThat(Record.fromString(record.getName(), record.getType(), record.getDClass(), record.getTTL(),
                    record.rdataToString(), origin)).isEqualTo(record);
            assertThat(parsed.toString()).contains(Type.string(record.getType()));
        }

        TXTRecord text = (TXTRecord) records.get(3);
        assertThat(text.getStrings()).containsExactly("v=spf1", "-all");
        MXRecord mx = (MXRecord) records.get(2);
        assertThat(mx.getAdditionalName()).isEqualTo(Name.fromString("mail.example.com."));
        NSECRecord nsec = (NSECRecord) records.get(11);
        assertThat(nsec.hasType(Type.RRSIG)).isTrue();
        NSEC3Record nsec3 = (NSEC3Record) records.get(12);
        assertThat(nsec3.hashName(origin)).isNotEmpty();

        String encoded = base64.toString(new byte[] {1, 2, 3, 4, 5});
        assertThat(base64.fromString(encoded)).containsExactly(1, 2, 3, 4, 5);
        base32 base32Hex = new base32("0123456789ABCDEFGHIJKLMNOPQRSTUV=", false, false);
        assertThat(base32Hex.fromString(base32Hex.toString(new byte[] {11, 12, 13}))).containsExactly(11, 12, 13);
    }

    @Test
    void buildsMessagesWithHeadersSectionsEdnsAndTsig() throws Exception {
        Name name = Name.fromString("www.example.com.");
        Record question = Record.newRecord(name, Type.A, DClass.IN);
        Message query = Message.newQuery(question);
        query.getHeader().setID(0xBEEF);
        query.getHeader().setFlag(Flags.RD);

        ARecord answer = new ARecord(name, DClass.IN, 300, InetAddress.getByName("192.0.2.55"));
        query.addRecord(answer, Section.ANSWER);
        ClientSubnetOption subnet = new ClientSubnetOption(24, InetAddress.getByName("192.0.2.0"));
        OPTRecord opt = new OPTRecord(1_232, 0, 0, Flags.DO, Collections.singletonList(subnet));
        query.addRecord(opt, Section.ADDITIONAL);

        Message parsed = new Message(query.toWire());
        Header header = parsed.getHeader();
        assertThat(header.getID()).isEqualTo(0xBEEF);
        assertThat(header.getFlag(Flags.RD)).isTrue();
        assertThat(header.getCount(Section.QUESTION)).isEqualTo(1);
        assertThat(parsed.getQuestion()).isEqualTo(question);
        assertThat(parsed.findRRset(name, Type.A, Section.ANSWER)).isTrue();
        assertThat(parsed.getOPT().getPayloadSize()).isEqualTo(1_232);
        assertThat((EDNSOption) parsed.getOPT().getOptions(EDNSOption.Code.CLIENT_SUBNET).get(0))
                .isInstanceOf(ClientSubnetOption.class);
        assertThat(parsed.sectionToString(Section.ANSWER)).contains("192.0.2.55");

        Record removed = parsed.getSectionArray(Section.ANSWER)[0];
        assertThat(parsed.removeRecord(removed, Section.ANSWER)).isTrue();
        assertThat(parsed.findRecord(removed)).isFalse();
        parsed.addRecord(removed, Section.ANSWER);
        assertThat(parsed.getSectionRRsets(Section.ANSWER)).hasSize(1);

        TSIG tsig = new TSIG(TSIG.HMAC_SHA256, Name.fromString("tsig-key.example."), base64.fromString("c2VjcmV0"));
        tsig.apply(parsed, null);
        byte[] signedWire = parsed.toWire();
        Message signed = new Message(signedWire);
        assertThat(signed.getTSIG()).isNotNull();
        assertThat(tsig.verify(signed, signedWire, (TSIGRecord) null)).isEqualTo(Rcode.NOERROR);
    }

    @Test
    void managesRrsetsCacheLookupAndDynamicUpdateMessages() throws Exception {
        Name zoneName = Name.fromString("example.com.");
        Name host = Name.fromString("www.example.com.");
        ARecord first = new ARecord(host, DClass.IN, 600, InetAddress.getByName("192.0.2.1"));
        ARecord second = new ARecord(host, DClass.IN, 300, InetAddress.getByName("192.0.2.2"));
        RRset rrset = new RRset(first);
        rrset.addRR(second);

        assertThat(rrset.size()).isEqualTo(2);
        assertThat(rrset.getTTL()).isEqualTo(300L);
        assertThat(rrset.first().getTTL()).isEqualTo(300L);
        assertThat(iteratorToList(rrset.rrs())).contains(first.withName(host), second.withName(host));
        rrset.deleteRR(first);
        assertThat(rrset.size()).isEqualTo(1);

        Cache cache = new Cache(DClass.IN);
        cache.addRRset(rrset, Credibility.AUTH_ANSWER);
        SetResponse success = cache.lookupRecords(host, Type.A, Credibility.NORMAL);
        assertThat(success.isSuccessful()).isTrue();
        assertThat(success.answers()).hasSize(1);
        assertThat(cache.findRecords(host, Type.A)).hasSize(1);

        SOARecord soa = new SOARecord(zoneName, DClass.IN, 3_600, Name.fromString("ns.example.com."),
                Name.fromString("hostmaster.example.com."), 1L, 2L, 3L, 4L, 5L);
        cache.addNegative(Name.fromString("missing.example.com."), Type.A, soa, Credibility.AUTH_AUTHORITY);
        assertThat(cache.lookupRecords(Name.fromString("missing.example.com."), Type.A, Credibility.NORMAL).isNXRRSET())
                .isTrue();
        cache.flushSet(host, Type.A);
        assertThat(cache.lookupRecords(host, Type.A, Credibility.NORMAL).isUnknown()).isTrue();

        Update update = new Update(zoneName);
        update.present(host, Type.A);
        update.absent(Name.fromString("old.example.com."), Type.A);
        update.add(host, Type.TXT, 60, "\"new text\"");
        update.delete(host, Type.MX, "10 mail.example.com.");
        update.replace(new ARecord(host, DClass.IN, 120, InetAddress.getByName("192.0.2.9")));

        Message parsedUpdate = new Message(update.toWire());
        assertThat(parsedUpdate.getHeader().getOpcode()).isEqualTo(Opcode.UPDATE);
        assertThat(parsedUpdate.getSectionArray(Section.ZONE)).hasSize(1);
        assertThat(parsedUpdate.getSectionArray(Section.PREREQ)).hasSize(2);
        assertThat(parsedUpdate.getSectionArray(Section.UPDATE)).hasSize(4);
    }

    @Test
    void loadsMasterFileAndServesZoneQueries() throws Exception {
        Name origin = Name.fromString("example.com.");
        String zoneText = """
                $ORIGIN example.com.
                $TTL 3600
                @ IN SOA ns.example.com. hostmaster.example.com. 2024050501 7200 3600 1209600 300
                @ IN NS ns.example.com.
                ns IN A 192.0.2.53
                www 300 IN A 192.0.2.10
                alias IN CNAME www.example.com.
                branch IN DNAME delegated.example.net.
                _sip._tcp 450 IN SRV 1 5 5060 sip.example.com.
                $GENERATE 1-3 host$ 60 IN A 192.0.2.$
                """;

        Master master = new Master(new ByteArrayInputStream(zoneText.getBytes(StandardCharsets.UTF_8)), origin);
        master.expandGenerate(true);
        List<Record> records = new ArrayList<>();
        Record record;
        while ((record = master.nextRecord()) != null) {
            records.add(record);
        }

        assertThat(records).extracting(Record::getName)
                .contains(Name.fromString("host1.example.com."), Name.fromString("host2.example.com."),
                        Name.fromString("host3.example.com."));

        Zone zone = new Zone(origin, records.toArray(new Record[0]));
        assertThat(zone.getOrigin()).isEqualTo(origin);
        assertThat(zone.getSOA().getSerial()).isEqualTo(2024050501L);
        assertThat(zone.getNS().size()).isEqualTo(1);
        assertThat(zone.findExactMatch(Name.fromString("www.example.com."), Type.A).first().rdataToString())
                .isEqualTo("192.0.2.10");
        assertThat(zone.findRecords(Name.fromString("www.example.com."), Type.A).isSuccessful()).isTrue();
        assertThat(zone.findRecords(Name.fromString("alias.example.com."), Type.A).isCNAME()).isTrue();
        SetResponse dnameResponse = zone.findRecords(Name.fromString("leaf.branch.example.com."), Type.A);
        assertThat(dnameResponse.isDNAME()).isTrue();
        DNAMERecord dname = (DNAMERecord) dnameResponse.getDNAME();
        assertThat(dname.getTarget()).isEqualTo(Name.fromString("delegated.example.net."));
        assertThat(zone.toMasterFile()).contains("SOA", "host1", "SRV");
        assertThat(iteratorSize(zone.AXFR())).isEqualTo(records.size() + 1);
    }

    @Test
    void performsAuthoritativeZoneTransferOverTcp() throws Exception {
        Name origin = Name.fromString("example.com.");
        SOARecord soa = new SOARecord(origin, DClass.IN, 3_600, Name.fromString("ns.example.com."),
                Name.fromString("hostmaster.example.com."), 2024050502L, 7_200L, 3_600L, 1_209_600L, 300L);
        ARecord address = new ARecord(Name.fromString("www.example.com."), DClass.IN, 300,
                InetAddress.getByName("192.0.2.90"));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            serverSocket.setSoTimeout(5_000);
            Future<?> server = executor.submit(() -> answerOneTcpZoneTransfer(serverSocket, origin, soa, address));

            ZoneTransferIn transfer = ZoneTransferIn.newAXFR(origin,
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort()), null);
            transfer.setTimeout(2);
            List<Record> transferredRecords = transfer.run();

            assertThat(transfer.getName()).isEqualTo(origin);
            assertThat(transfer.getType()).isEqualTo(Type.AXFR);
            assertThat(transfer.isAXFR()).isTrue();
            assertThat(transfer.isIXFR()).isFalse();
            assertThat(transfer.getAXFR()).isEqualTo(transferredRecords);
            assertThat(transferredRecords).containsExactly(soa, address, soa);
            server.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThatCode(() -> executor.awaitTermination(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
        }
    }

    @Test
    void performsLookupFromCacheAndLocalUdpResolver() throws Exception {
        Name name = Name.fromString("cached.example.com.");
        Cache cache = new Cache(DClass.IN);
        ARecord cachedAnswer = new ARecord(name, DClass.IN, 60, InetAddress.getByName("192.0.2.80"));
        cache.addRecord(cachedAnswer, Credibility.AUTH_ANSWER, this);

        Lookup lookup = new Lookup(name, Type.A, DClass.IN);
        lookup.setCache(cache);
        lookup.setNdots(1);
        lookup.setSearchPath(new Name[] {Name.fromString("example.com.")});
        Record[] answers = lookup.run();
        assertThat(lookup.getResult()).isEqualTo(Lookup.SUCCESSFUL);
        assertThat(answers).containsExactly(cachedAnswer);
        assertThat(lookup.getErrorString()).isEqualTo("successful");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))) {
            socket.setSoTimeout(5_000);
            Future<?> server = executor.submit(() -> answerOneUdpQuery(socket, "198.51.100.42"));

            SimpleResolver resolver = new SimpleResolver("127.0.0.1");
            resolver.setPort(socket.getLocalPort());
            resolver.setTimeout(2);
            resolver.setEDNS(0);
            Message response = resolver.send(Message.newQuery(Record.newRecord(Name.fromString("resolver.example."),
                    Type.A, DClass.IN)));

            assertThat(response.getRcode()).isEqualTo(Rcode.NOERROR);
            assertThat(response.getSectionArray(Section.ANSWER)).hasSize(1);
            assertThat(((ARecord) response.getSectionArray(Section.ANSWER)[0]).getAddress().getHostAddress())
                    .isEqualTo("198.51.100.42");
            server.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThatCode(() -> executor.awaitTermination(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
        }
    }

    private static void answerOneTcpZoneTransfer(ServerSocket serverSocket, Name origin, SOARecord soa,
            ARecord address) {
        try (Socket socket = serverSocket.accept();
                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            socket.setSoTimeout(5_000);
            int length = input.readUnsignedShort();
            byte[] queryBytes = input.readNBytes(length);
            Message query = new Message(queryBytes);
            Record question = query.getQuestion();

            assertThat(question.getName()).isEqualTo(origin);
            assertThat(question.getType()).isEqualTo(Type.AXFR);

            Message response = new Message(query.getHeader().getID());
            response.getHeader().setFlag(Flags.QR);
            response.getHeader().setFlag(Flags.AA);
            response.addRecord(question, Section.QUESTION);
            response.addRecord(soa, Section.ANSWER);
            response.addRecord(address, Section.ANSWER);
            response.addRecord(soa, Section.ANSWER);

            byte[] responseBytes = response.toWire();
            output.writeShort(responseBytes.length);
            output.write(responseBytes);
            output.flush();
        } catch (Exception exception) {
            throw new IllegalStateException("DNS TCP zone transfer test server failed", exception);
        }
    }

    private static void answerOneUdpQuery(DatagramSocket socket, String address) {
        try {
            byte[] buffer = new byte[512];
            DatagramPacket request = new DatagramPacket(buffer, buffer.length);
            socket.receive(request);
            byte[] queryBytes = Arrays.copyOf(request.getData(), request.getLength());
            Message query = new Message(queryBytes);
            Record question = query.getQuestion();

            Message response = new Message(query.getHeader().getID());
            response.getHeader().setFlag(Flags.QR);
            response.getHeader().setFlag(Flags.AA);
            response.getHeader().setFlag(Flags.RA);
            response.addRecord(question, Section.QUESTION);
            response.addRecord(new ARecord(question.getName(), DClass.IN, 30, InetAddress.getByName(address)),
                    Section.ANSWER);
            byte[] responseBytes = response.toWire();
            DatagramPacket packet = new DatagramPacket(responseBytes, responseBytes.length,
                    request.getAddress(), request.getPort());
            socket.send(packet);
        } catch (Exception exception) {
            throw new IllegalStateException("DNS UDP test server failed", exception);
        }
    }

    private static List<Record> iteratorToList(Iterator<?> iterator) {
        List<Record> records = new ArrayList<>();
        while (iterator.hasNext()) {
            records.add((Record) iterator.next());
        }
        return records;
    }

    private static int iteratorSize(Iterator<?> iterator) {
        int size = 0;
        while (iterator.hasNext()) {
            iterator.next();
            size++;
        }
        return size;
    }
}
