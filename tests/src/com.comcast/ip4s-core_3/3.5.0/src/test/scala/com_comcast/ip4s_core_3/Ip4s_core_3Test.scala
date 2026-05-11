/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_comcast.ip4s_core_3

import com.comcast.ip4s.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import java.net.InetAddress
import java.net.InetSocketAddress

class Ip4s_core_3Test {
  @Test
  def parsesPortsAndPreservesProductSemantics(): Unit = {
    val zero: Port = Port.fromInt(Port.MinValue).getOrElse(fail("minimum port should parse"))
    val max: Port = Port.fromString(Port.MaxValue.toString).getOrElse(fail("maximum port should parse"))

    assertEquals(0, zero.value)
    assertEquals("0", zero.toString)
    assertEquals(65535, max.value)
    assertTrue(zero < max)
    assertEquals(Some(max), zero.copy(65535))
    assertEquals(None, max.copy(65536))
    assertEquals(Some(65535), Port.unapply(max))
    assertEquals(1, max.productArity)
    assertEquals(65535, max.productElement(0))
    assertThrows(classOf[IndexOutOfBoundsException], () => max.productElement(1))

    assertEquals(None, Port.fromInt(-1))
    assertEquals(None, Port.fromInt(65536))
    assertEquals(None, Port.fromString("not-a-port"))
  }

  @Test
  def parsesAndClassifiesIpv4Addresses(): Unit = {
    val address: Ipv4Address = Ipv4Address.fromString(" 192.168.29.1 ").getOrElse(fail("IPv4 should parse"))

    assertEquals("192.168.29.1", address.toString)
    assertEquals("192.168.29.1", address.toUriString)
    assertEquals(0xc0a81d01L, address.toLong)
    assertArrayEquals(Array[Byte](192.toByte, 168.toByte, 29.toByte, 1.toByte), address.toBytes)
    assertEquals(Some(address), IpAddress.fromBytes(address.toBytes))
    assertEquals(None, Ipv4Address.fromBytes(Array[Byte](1, 2, 3)))
    assertEquals(None, Ipv4Address.fromString("256.1.1.1"))
    assertEquals(None, Ipv4Address.fromString("1.2.3"))

    assertEquals(IpVersion.V4, address.version)
    assertEquals(Some(address), address.asIpv4)
    assertEquals(None, address.asIpv6)
    assertFalse(address.isLoopback)
    assertFalse(address.isLinkLocal)
    assertTrue(address.isPrivate)
    assertFalse(address.isMulticast)
    assertFalse(address.isSourceSpecificMulticast)
    assertEquals("192.168.29.2", address.next.toString)
    assertEquals("192.168.29.0", address.previous.toString)
    assertEquals("0.0.0.0", Ipv4Address.fromBytes(255, 255, 255, 255).next.toString)
    assertEquals("255.255.255.255", Ipv4Address.fromBytes(0, 0, 0, 0).previous.toString)
    assertEquals("255.255.0.0", Ipv4Address.mask(16).toString)
    assertEquals("192.168.0.0", address.masked(Ipv4Address.mask(16)).toString)
    assertEquals("192.168.255.255", address.maskedLast(Ipv4Address.mask(16)).toString)

    assertTrue(Ipv4Address.fromString("127.0.0.1").get.isLoopback)
    assertTrue(Ipv4Address.fromString("169.254.10.20").get.isLinkLocal)
    assertTrue(Ipv4Address.fromString("224.0.0.1").get.isMulticast)
    assertTrue(Ipv4Address.fromString("232.1.2.3").get.isSourceSpecificMulticast)
  }

  @Test
  def exposesIpv4ClassfulSpecialAndPrivateRanges(): Unit = {
    assertEquals("0.0.0.0/1", Ipv4Address.Classes.A.toString)
    assertTrue(Ipv4Address.Classes.A.contains(ipv4"127.255.255.255"))
    assertFalse(Ipv4Address.Classes.A.contains(ipv4"128.0.0.0"))

    assertEquals("128.0.0.0/2", Ipv4Address.Classes.B.toString)
    assertTrue(Ipv4Address.Classes.B.contains(ipv4"191.255.255.255"))
    assertFalse(Ipv4Address.Classes.B.contains(ipv4"192.0.0.0"))

    assertEquals("192.0.0.0/3", Ipv4Address.Classes.C.toString)
    assertTrue(Ipv4Address.Classes.C.contains(ipv4"223.255.255.255"))
    assertFalse(Ipv4Address.Classes.C.contains(ipv4"224.0.0.0"))

    assertEquals("224.0.0.0/4", Ipv4Address.Classes.D.toString)
    assertTrue(Ipv4Address.Classes.D.contains(Ipv4Address.MulticastRangeStart))
    assertTrue(Ipv4Address.Classes.D.contains(Ipv4Address.MulticastRangeEnd))
    assertFalse(Ipv4Address.Classes.D.contains(ipv4"240.0.0.0"))

    assertEquals("240.0.0.0/5", Ipv4Address.Classes.E.toString)
    assertTrue(Ipv4Address.Classes.E.contains(ipv4"247.255.255.255"))
    assertFalse(Ipv4Address.Classes.E.contains(ipv4"239.255.255.255"))
    assertFalse(Ipv4Address.Classes.E.contains(ipv4"248.0.0.0"))

    assertEquals("10.0.0.0/8", Ipv4Address.Classes.Private.A.toString)
    assertTrue(Ipv4Address.Classes.Private.A.contains(ipv4"10.255.255.255"))
    assertFalse(Ipv4Address.Classes.Private.A.contains(ipv4"11.0.0.0"))
    assertEquals("172.16.0.0/12", Ipv4Address.Classes.Private.B.toString)
    assertTrue(Ipv4Address.Classes.Private.B.contains(ipv4"172.31.255.255"))
    assertFalse(Ipv4Address.Classes.Private.B.contains(ipv4"172.32.0.0"))
    assertEquals("192.168.0.0/16", Ipv4Address.Classes.Private.C.toString)
    assertTrue(Ipv4Address.Classes.Private.C.contains(ipv4"192.168.255.255"))
    assertFalse(Ipv4Address.Classes.Private.C.contains(ipv4"192.169.0.0"))

    assertEquals("127.0.0.0/8", Ipv4Address.Classes.Loopback.toString)
    assertTrue(Ipv4Address.Classes.Loopback.contains(ipv4"127.255.255.255"))
    assertFalse(Ipv4Address.Classes.Loopback.contains(ipv4"128.0.0.0"))
    assertEquals("169.254.0.0/16", Ipv4Address.Classes.LinkLocal.toString)
    assertTrue(Ipv4Address.Classes.LinkLocal.contains(ipv4"169.254.255.255"))
    assertFalse(Ipv4Address.Classes.LinkLocal.contains(ipv4"169.255.0.0"))
  }

  @Test
  def parsesAndFormatsIpv6Addresses(): Unit = {
    val address: Ipv6Address = Ipv6Address
      .fromString("2001:0db8:0000:0000:0000:ff00:0042:8329")
      .getOrElse(fail("IPv6 should parse"))

    assertEquals("2001:db8::ff00:42:8329", address.toString)
    assertEquals("[2001:db8::ff00:42:8329]", address.toUriString)
    assertEquals("2001:0db8:0000:0000:0000:ff00:0042:8329", address.toUncondensedString)
    assertEquals(IpVersion.V6, address.version)
    assertEquals(None, address.asIpv4)
    assertEquals(Some(address), address.asIpv6)
    assertEquals(BigInt("20010db8000000000000ff0000428329", 16), address.toBigInt)
    assertEquals(Some(address), Ipv6Address.fromBytes(address.toBytes))
    assertEquals(None, Ipv6Address.fromBytes(Array.fill[Byte](15)(0)))
    assertEquals(None, Ipv6Address.fromString("not::ipv6::address"))

    assertEquals("2001:db8::ff00:42:832a", address.next.toString)
    assertEquals("2001:db8::ff00:42:8328", address.previous.toString)
    assertEquals("::", Ipv6Address.fromString("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff").get.next.toString)
    assertEquals("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", Ipv6Address.fromString("::").get.previous.toString)
    assertEquals("ffff:ffff:ffff:ffff::", Ipv6Address.mask(64).toString)
    assertEquals("2001:db8::", address.masked(Ipv6Address.mask(32)).toString)
    assertEquals("2001:db8:ffff:ffff:ffff:ffff:ffff:ffff", address.maskedLast(Ipv6Address.mask(32)).toString)

    val mixed: Ipv6Address = Ipv6Address.fromString("::ffff:192.168.0.1").getOrElse(fail("mixed IPv6 should parse"))
    assertEquals("::ffff:c0a8:1", mixed.toString)
    assertEquals("::ffff:192.168.0.1", mixed.toMixedString)
    assertTrue(mixed.isMappedV4)
    assertEquals(Ipv4Address.fromString("192.168.0.1"), mixed.asIpv4)
    assertEquals(Ipv4Address.fromString("192.168.0.1").get, mixed.collapseMappedV4)

    assertTrue(Ipv6Address.fromString("::1").get.isLoopback)
    assertTrue(Ipv6Address.fromString("fe80::1").get.isLinkLocal)
    assertTrue(Ipv6Address.fromString("fd00::1").get.isPrivate)
    assertTrue(Ipv6Address.fromString("ff02::1").get.isMulticast)
    assertTrue(Ipv6Address.fromString("ff3e::1").get.isSourceSpecificMulticast)
  }

  @Test
  def convertsIpAddressesWithJvmInetTypesAndCompileTimeLiterals(): Unit = {
    val literalIp: IpAddress = ip"127.0.0.1"
    val literalIpv4: Ipv4Address = ipv4"127.0.0.1"
    val literalIpv6: Ipv6Address = ipv6"::1"

    assertEquals(literalIpv4, literalIp)
    assertEquals("127.0.0.1", literalIpv4.toInetAddress.getHostAddress)
    assertEquals(literalIpv4, Ipv4Address.fromInet4Address(literalIpv4.toInetAddress))
    assertEquals(literalIpv6, Ipv6Address.fromInet6Address(InetAddress.getByName("::1").asInstanceOf[java.net.Inet6Address]))
    assertEquals(literalIpv6, IpAddress.fromInetAddress(InetAddress.getByName("::1")))
    assertEquals("::7f00:1", literalIpv4.toCompatV6.toString)
    assertEquals("::127.0.0.1", literalIpv4.toCompatV6.toMixedString)
    assertEquals("::ffff:7f00:1", literalIpv4.toMappedV6.toString)
  }

  @Test
  def validatesHostnamesIdnsAndHostParsing(): Unit = {
    val hostname: Hostname = Hostname.fromString("Example-Service.COM").getOrElse(fail("hostname should parse"))
    assertEquals(List("Example-Service", "COM"), hostname.labels.map(_.toString))
    assertEquals("example-service.com", hostname.normalized.toString)
    assertEquals(None, Hostname.fromString("-bad.example"))
    assertEquals(None, Hostname.fromString("bad-.example"))
    assertEquals(None, Hostname.fromString("a" * 64))
    assertEquals(None, Hostname.fromString(""))

    val idn: IDN = IDN.fromString("bücher.example").getOrElse(fail("IDN should parse"))
    assertEquals("bücher.example", idn.toString)
    assertEquals("xn--bcher-kva.example", idn.hostname.toString)
    assertEquals(List("bücher", "example"), idn.labels.map(_.toString))
    assertEquals("bücher.example", idn.normalized.toString)
    assertEquals(idn, IDN.fromHostname(idn.hostname))
    assertEquals(None, IDN.fromString(""))

    assertEquals(Some(ipv4"10.0.0.1"), Host.fromString("10.0.0.1"))
    assertEquals(Some(ipv6"::1"), Host.fromString("::1"))
    assertEquals(Some(hostname), Host.fromString("Example-Service.COM"))
    assertEquals(Some(idn), Host.fromString("bücher.example"))
  }

  @Test
  def calculatesCidrMasksRangesAndContainment(): Unit = {
    val cidr: Cidr[Ipv4Address] = Cidr.fromString4("10.11.12.13/8").getOrElse(fail("CIDR should parse"))

    assertEquals("10.11.12.13/8", cidr.toString)
    assertEquals("255.0.0.0", cidr.mask.toString)
    assertEquals("10.0.0.0", cidr.prefix.toString)
    assertEquals("10.255.255.255", cidr.last.toString)
    assertEquals(BigInt(16777216), cidr.totalIps)
    assertTrue(cidr.contains(ipv4"10.100.100.100"))
    assertFalse(cidr.contains(ipv4"11.0.0.1"))
    val strict: Cidr.Strict[Ipv4Address] = cidr.normalized
    assertEquals("10.0.0.0/8", strict.toString)
    assertSame(strict, strict.normalized)
    assertEquals(Cidr(ipv4"10.11.12.13", 16), cidr.copy(prefixBits = 16))
    assertEquals((ipv4"10.11.12.13", 8), Cidr.unapply(cidr).get)

    val byMask: Cidr[Ipv4Address] = Cidr.fromIpAndMask(ipv4"192.168.29.1", ipv4"255.255.0.0")
    assertEquals("192.168.29.1/16", byMask.toString)
    assertEquals("192.168.0.0", byMask.normalized.address.toString)
    assertEquals(0, Cidr(ipv4"10.0.0.1", -10).prefixBits)
    assertEquals(32, Cidr(ipv4"10.0.0.1", 99).prefixBits)
    assertEquals(None, Cidr.fromString4("10.0.0.1/33"))
    assertEquals(None, Cidr.fromString4("10.0.0.1/-1"))

    val ipv6Cidr: Cidr[Ipv6Address] = Cidr.fromString6("2001:db8:abcd:12::/96").getOrElse(fail("IPv6 CIDR should parse"))
    assertEquals("ffff:ffff:ffff:ffff:ffff:ffff::", ipv6Cidr.mask.toString)
    assertEquals("2001:db8:abcd:12::", ipv6Cidr.prefix.toString)
    assertEquals("2001:db8:abcd:12::ffff:ffff", ipv6Cidr.last.toString)
    assertEquals(BigInt(1) << 32, ipv6Cidr.totalIps)
    assertTrue(ipv6Cidr.contains(ipv6"2001:db8:abcd:12::5"))
    assertFalse(ipv6Cidr.contains(ipv6"2001:db8::"))
  }

  @Test
  def parsesSocketAddressesAndConvertsIpSocketsToInetSockets(): Unit = {
    val portValue: Port = port"443"
    val ipv4Socket: SocketAddress[Ipv4Address] = SocketAddress.fromString4("127.0.0.1:443").getOrElse(fail("IPv4 socket should parse"))
    val ipv6Socket: SocketAddress[Ipv6Address] = SocketAddress.fromString6("[::1]:443").getOrElse(fail("IPv6 socket should parse"))
    val hostnameSocket: SocketAddress[Hostname] = SocketAddress.fromStringHostname("example.com:443").getOrElse(fail("hostname socket should parse"))
    val idnSocket: SocketAddress[IDN] = SocketAddress.fromStringIDN("bücher.example:443").getOrElse(fail("IDN socket should parse"))

    assertEquals(SocketAddress(ipv4"127.0.0.1", portValue), ipv4Socket)
    assertEquals("127.0.0.1:443", ipv4Socket.toString)
    assertEquals(SocketAddress(ipv6"::1", portValue), ipv6Socket)
    assertEquals("[::1]:443", ipv6Socket.toString)
    assertEquals("example.com:443", hostnameSocket.toString)
    assertEquals("bücher.example:443", idnSocket.toString)
    assertEquals(Some(ipv4Socket), SocketAddress.fromStringIp("127.0.0.1:443"))
    assertEquals(Some(hostnameSocket), SocketAddress.fromString("example.com:443"))
    assertEquals(None, SocketAddress.fromString4("127.0.0.1:70000"))
    assertEquals(None, SocketAddress.fromString6("::1:443"))

    val inetSocket: InetSocketAddress = ipv4Socket.toInetSocketAddress
    assertEquals("127.0.0.1", inetSocket.getAddress.getHostAddress)
    assertEquals(443, inetSocket.getPort)
    assertEquals(ipv4Socket, SocketAddress.fromInetSocketAddress(inetSocket))
  }

  @Test
  def parsesMulticastAddressesJoinsAndSockets(): Unit = {
    val anySourceGroup: Multicast[Ipv4Address] = ipv4"224.0.0.1".asMulticast.getOrElse(fail("multicast should parse"))
    val sourceSpecificGroup: SourceSpecificMulticast.Strict[Ipv4Address] = ipv4"232.1.2.3".asSourceSpecificMulticast
      .getOrElse(fail("source specific multicast should parse"))
    val lenientGroup: SourceSpecificMulticast[Ipv4Address] = ipv4"224.0.0.1".asSourceSpecificMulticastLenient
      .getOrElse(fail("lenient multicast should parse"))

    assertEquals("224.0.0.1", anySourceGroup.toString)
    assertEquals(ipv4"224.0.0.1", anySourceGroup.address)
    assertEquals(None, ipv4"192.0.2.1".asMulticast)
    assertEquals(None, ipv4"224.0.0.1".asSourceSpecificMulticast)
    assertEquals(None, lenientGroup.strict)
    assertEquals(Some(sourceSpecificGroup), sourceSpecificGroup.strict)
    assertEquals(Some(sourceSpecificGroup), Multicast.fromIpAddress(ipv4"232.1.2.3"))
    assertEquals(Some(sourceSpecificGroup), SourceSpecificMulticast.fromIpAddress(ipv4"232.1.2.3"))

    val asm: MulticastJoin[Ipv4Address] = MulticastJoin.asm(anySourceGroup)
    val ssm: MulticastJoin[Ipv4Address] = MulticastJoin.ssm(ipv4"192.0.2.1", sourceSpecificGroup)
    assertEquals("224.0.0.1", asm.toString)
    assertEquals("192.0.2.1@232.1.2.3", ssm.toString)
    assertEquals((None, anySourceGroup), asm.sourceAndGroup)
    assertEquals((Some(ipv4"192.0.2.1"), sourceSpecificGroup), ssm.sourceAndGroup)
    assertTrue(asm.asAsm.isDefined)
    assertTrue(ssm.asSsm.isDefined)
    assertEquals("asm", asm.fold(_ => "asm", _ => "ssm"))
    assertEquals(Some(asm), MulticastJoin.fromString4("224.0.0.1"))
    assertEquals(Some(ssm), MulticastJoin.fromString4("192.0.2.1@232.1.2.3"))
    assertEquals(None, AnySourceMulticastJoin.fromString4("192.0.2.1@232.1.2.3"))
    assertEquals(None, SourceSpecificMulticastJoin.fromString4("224.0.0.1"))

    val anySourceSocket = MulticastSocketAddress.anySourceFromString4("224.0.0.1:5000").getOrElse(fail("ASM socket should parse"))
    val sourceSpecificSocket = MulticastSocketAddress
      .sourceSpecificFromString4("192.0.2.1@232.1.2.3:5000")
      .getOrElse(fail("SSM socket should parse"))
    val ipv6AnySourceSocket = MulticastSocketAddress.anySourceFromString6("[ff02::1]:5000").getOrElse(fail("IPv6 ASM socket should parse"))
    val ipv6SourceSpecificSocket = MulticastSocketAddress
      .sourceSpecificFromString6("[2001:db8::1]@[ff3e::1]:5000")
      .getOrElse(fail("IPv6 SSM socket should parse"))

    assertEquals("224.0.0.1:5000", anySourceSocket.toString)
    assertEquals("192.0.2.1@232.1.2.3:5000", sourceSpecificSocket.toString)
    assertEquals("[ff02::1]:5000", ipv6AnySourceSocket.toString)
    assertEquals("[2001:db8::1]@[ff3e::1]:5000", ipv6SourceSpecificSocket.toString)
    assertTrue(anySourceSocket.asAsm.isDefined)
    assertTrue(sourceSpecificSocket.asSsm.isDefined)
    assertEquals(None, MulticastSocketAddress.fromString4("192.0.2.1:5000"))
    assertEquals(None, MulticastSocketAddress.fromString6("ff02::1:5000"))
  }

  @Test
  def foldsAndTransformsIpAddressesByVersion(): Unit = {
    val ipv4Address: IpAddress = ipv4"192.0.2.10"
    val ipv6Address: IpAddress = ipv6"2001:db8::10"
    val replacement4: Ipv4Address = ipv4"198.51.100.7"
    val replacement6: Ipv6Address = ipv6"2001:db8::7"

    assertEquals("ipv4:192.0.2.10", ipv4Address.fold(v4 => s"ipv4:$v4", v6 => s"ipv6:$v6"))
    assertEquals("ipv6:2001:db8::10", ipv6Address.fold(v4 => s"ipv4:$v4", v6 => s"ipv6:$v6"))

    assertEquals(replacement4, ipv4Address.transform(_ => replacement4, _ => replacement6))
    assertEquals(replacement6, ipv6Address.transform(_ => replacement4, _ => replacement6))
    assertEquals(replacement4, (ipv4"192.0.2.10": Ipv4Address).transform(_ => replacement4, _ => replacement6))
    assertEquals(replacement6, (ipv6"2001:db8::10": Ipv6Address).transform(_ => replacement4, _ => replacement6))
  }

  @Test
  def parsesMacAddressesAndDefensivelyReturnsByteCopies(): Unit = {
    val mac: MacAddress = MacAddress.fromString("00:11:22:aa:BB:ff").getOrElse(fail("MAC should parse"))

    assertEquals("00:11:22:aa:bb:ff", mac.toString)
    assertEquals(0x001122aabbffL, mac.toLong)
    assertArrayEquals(Array[Byte](0x00, 0x11, 0x22, 0xaa.toByte, 0xbb.toByte, 0xff.toByte), mac.toBytes)
    assertEquals(mac, MacAddress.fromLong(0x001122aabbffL))
    assertEquals(mac, MacAddress.fromBytes(0x00, 0x11, 0x22, 0xaa, 0xbb, 0xff))
    assertEquals(None, MacAddress.fromString("00:11:22:33:44"))
    assertEquals(None, MacAddress.fromString("00:11:22:33:44:gg"))
    assertEquals(None, MacAddress.fromBytes(Array[Byte](1, 2, 3)))

    val bytes: Array[Byte] = mac.toBytes
    bytes(0) = 0x7f.toByte
    assertEquals("00:11:22:aa:bb:ff", mac.toString)
    assertTrue(MacAddress.fromString("00:11:22:aa:bb:fe").get < mac)
  }
}
