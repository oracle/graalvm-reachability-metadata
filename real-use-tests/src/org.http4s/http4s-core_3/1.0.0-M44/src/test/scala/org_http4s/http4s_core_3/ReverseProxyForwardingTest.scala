/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import cats.data.NonEmptyList
import cats.effect.IO
import com.comcast.ip4s.*
import org.http4s.Headers
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.headers.Forwarded
import org.http4s.headers.Host
import org.http4s.headers.`Max-Forwards`
import org.http4s.headers.`X-Forwarded-For`
import org.http4s.headers.`X-Forwarded-Host`
import org.http4s.headers.`X-Forwarded-Proto`
import org.http4s.syntax.literals.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.ci.*

/**
 * Resolves the original client behind reverse proxies with the `Forwarded`, `X-Forwarded-For`,
 * `X-Forwarded-Proto`, `X-Forwarded-Host`, and `Max-Forwards` headers of `org.http4s.headers`.
 *
 * A storefront application runs behind a chain of load balancers. It reads the RFC 7239
 * `Forwarded` chain or the legacy `X-Forwarded-*` headers to find the shopper's address and the
 * public URL they typed, appends its own hop when it proxies onwards, and answers `TRACE` itself
 * once the `Max-Forwards` budget is spent.
 */
class ReverseProxyForwardingTest {

  @Test
  def readsTheClientAndPublicOriginFromAForwardedChain(): Unit = {
    val request: Request[IO] = Request[IO](
      uri = uri"/checkout",
      headers = Headers(
        "Host" -> "storefront.internal:8080",
        "Forwarded" ->
          "for=192.0.2.60;proto=https;by=203.0.113.43;host=shop.example.com, for=\"[2001:db8:cafe::17]:4711\""
      )
    ).withAttribute(Request.Keys.ConnectionInfo, connection(ipv4"10.0.0.5"))

    val forwarded: Forwarded =
      request.headers.get[Forwarded].getOrElse(throw new AssertionError("no Forwarded header"))
    val hops: List[Forwarded.Element] = forwarded.values.toList
    assertEquals(2, hops.size)

    val edge: Forwarded.Element = hops.head
    assertEquals(Some(Forwarded.Node(Forwarded.Node.Name.Ipv4(ipv4"192.0.2.60"))), edge.maybeFor)
    assertEquals(Some(Forwarded.Node(Forwarded.Node.Name.Ipv4(ipv4"203.0.113.43"))), edge.maybeBy)
    assertEquals(Some(Uri.Scheme.https), edge.maybeProto)
    assertEquals(Some(Uri.RegName("shop.example.com")), edge.maybeHost.map(_.host))
    assertEquals(None, edge.maybeHost.flatMap(_.port))

    val inner: Forwarded.Node = hops(1).maybeFor.getOrElse(throw new AssertionError("no for="))
    assertEquals(Forwarded.Node.Name.Ipv6(ipv6"2001:db8:cafe::17"), inner.nodeName)
    assertEquals(Some(Forwarded.Node.Port.Numeric(4711)), inner.nodePort)

    val origin: ClientOrigin = resolveOrigin(request)
    assertEquals(Some(ipv4"192.0.2.60"), origin.address)
    assertEquals(uri"https://shop.example.com/checkout", origin.publicUri)
  }

  @Test
  def appendsItsOwnHopWhenProxyingToTheInventoryService(): Unit = {
    val incoming: Request[IO] = Request[IO](
      method = Method.POST,
      uri = uri"/inventory/reserve",
      headers = Headers("Forwarded" -> "for=198.51.100.17;proto=https")
    ).withAttribute(Request.Keys.ConnectionInfo, connection(ipv4"10.0.0.7"))

    val onward: Request[IO] = proxyOnwards(incoming)
    val chain: Forwarded =
      onward.headers.get[Forwarded].getOrElse(throw new AssertionError("no Forwarded header"))
    assertEquals(2, chain.values.size)
    assertEquals(
      Some(Forwarded.Node(Forwarded.Node.Name.Ipv4(ipv4"10.0.0.7"))),
      chain.values.last.maybeFor
    )
    assertEquals(
      Some(Forwarded.Node(Forwarded.Node.Name.Ipv4(ipv4"10.0.0.1"), Forwarded.Node.Port.Numeric(8443))),
      chain.values.last.maybeBy
    )
    assertEquals(
      List("for=198.51.100.17;proto=https", "by=\"10.0.0.1:8443\";for=10.0.0.7;proto=http"),
      rawValues(onward.headers, ci"Forwarded")
    )
    assertEquals(Some(ipv4"198.51.100.17"), resolveOrigin(onward).address)

    val built: Forwarded = Forwarded(
      Forwarded.Element
        .fromFor(Forwarded.Node(Forwarded.Node.Name.Ipv6(ipv6"2001:db8::1"), Forwarded.Node.Port.Numeric(443)))
        .withHost(Forwarded.Host.fromHostAndPort(Uri.RegName("shop.example.com"), 443).fold(throw _, identity))
        .withProto(Uri.Scheme.https)
    )
    assertEquals(
      Forwarded.parse("for=\"[2001:db8::1]:443\";host=\"shop.example.com:443\";proto=https"),
      Right(built)
    )
  }

  @Test
  def keepsObfuscatedAndUnknownHopsWithoutLeakingAddresses(): Unit = {
    val parsed: Forwarded = Forwarded
      .parse("for=unknown;by=_edge-lb, for=\"_shopper7:_tls\"")
      .fold(failure => throw failure, identity)
    val first: Forwarded.Element = parsed.values.head
    assertEquals(Some(Forwarded.Node(Forwarded.Node.Name.Unknown)), first.maybeFor)
    assertEquals(Some(Forwarded.Node(obfuscated("_edge-lb"))), first.maybeBy)

    val second: Forwarded.Node =
      parsed.values.toList(1).maybeFor.getOrElse(throw new AssertionError("no for="))
    assertEquals(obfuscated("_shopper7"), second.nodeName)
    assertEquals(Some(obfuscated("_tls")), second.nodePort)

    val request: Request[IO] = Request[IO](uri = uri"/catalog")
      .putHeaders(parsed, Host("shop.example.com"))
      .withAttribute(Request.Keys.ConnectionInfo, connection(ipv4"10.0.0.9"))
    val origin: ClientOrigin = resolveOrigin(request)
    assertEquals(None, origin.address)
    assertEquals(uri"http://shop.example.com/catalog", origin.publicUri)
  }

  @Test
  def fallsBackToLegacyXForwardedHeaders(): Unit = {
    val legacy: Request[IO] = Request[IO](
      uri = uri"/orders?page=2",
      headers = Headers(
        "X-Forwarded-For" -> "203.0.113.195, unknown, 2001:db8:85a3::8a2e:370:7334",
        "X-Forwarded-Proto" -> "https",
        "X-Forwarded-Host" -> "shop.example.com:8443"
      )
    ).withAttribute(Request.Keys.ConnectionInfo, connection(ipv4"10.0.0.5"))

    val xff: `X-Forwarded-For` =
      legacy.headers.get[`X-Forwarded-For`].getOrElse(throw new AssertionError("no X-Forwarded-For"))
    assertEquals(
      NonEmptyList.of(Some(ipv4"203.0.113.195"), None, Some(ipv6"2001:db8:85a3::8a2e:370:7334")),
      xff.values
    )
    assertEquals(Some(ipv4"203.0.113.195"), legacy.from)
    assertEquals(Some(ipv4"10.0.0.5"), legacy.remoteAddr)
    assertEquals(Some(Uri.Scheme.https), legacy.headers.get[`X-Forwarded-Proto`].map(_.scheme))
    assertEquals(Some(`X-Forwarded-Host`("shop.example.com", 8443)), legacy.headers.get[`X-Forwarded-Host`])

    val origin: ClientOrigin = resolveOrigin(legacy)
    assertEquals(Some(ipv4"203.0.113.195"), origin.address)
    assertEquals(uri"https://shop.example.com:8443/orders?page=2", origin.publicUri)

    val rendered: Request[IO] = Request[IO](uri = uri"/orders").putHeaders(
      `X-Forwarded-For`(Some(ipv4"198.51.100.4"), None),
      `X-Forwarded-Proto`(Uri.Scheme.https)
    )
    assertEquals(List("198.51.100.4, unknown"), rawValues(rendered.headers, ci"X-Forwarded-For"))
    assertEquals(List("https"), rawValues(rendered.headers, ci"X-Forwarded-Proto"))

    val direct: Request[IO] = Request[IO](uri = uri"/orders", headers = Headers("Host" -> "10.0.0.2"))
      .withAttribute(Request.Keys.ConnectionInfo, connection(ipv4"192.0.2.33"))
    val directOrigin: ClientOrigin = resolveOrigin(direct)
    assertEquals(Some(ipv4"192.0.2.33"), directOrigin.address)
    assertEquals("http://10.0.0.2/orders", directOrigin.publicUri.renderString)
  }

  @Test
  def answersTraceItselfOnceTheMaxForwardsBudgetIsSpent(): Unit = {
    val trace: Request[IO] = Request[IO](method = Method.TRACE, uri = uri"/health")
      .putHeaders(`Max-Forwards`.unsafeFromLong(1))

    val forwardedOnce: Either[Response[IO], Request[IO]] = traceHop(trace)
    val relayed: Request[IO] = forwardedOnce.fold(_ => throw new AssertionError("answered too early"), identity)
    assertEquals(Some(`Max-Forwards`.zero), relayed.headers.get[`Max-Forwards`])
    assertEquals(List("0"), rawValues(relayed.headers, ci"Max-Forwards"))

    val answered: Response[IO] =
      traceHop(relayed).fold(identity, _ => throw new AssertionError("relayed past the budget"))
    assertEquals(Status.Ok, answered.status)
    assertEquals(List("storefront-edge"), rawValues(answered.headers, ci"X-Answered-By"))
  }

  @Test
  def rejectsMalformedForwardingValues(): Unit = {
    assertTrue(Forwarded.parse("").isLeft)
    assertTrue(Forwarded.parse("for=999.1.1.1").isLeft)
    assertTrue(Forwarded.Node.Port.fromInt(70000).isLeft)
    assertTrue(Forwarded.Node.Obfuscated.fromString("no-underscore").isLeft)
    assertTrue(Forwarded.Host.fromHostAndPort(Uri.RegName("shop.example.com"), -1).isLeft)
    assertTrue(`X-Forwarded-For`.parse("not-an-address").isLeft)
    assertTrue(`Max-Forwards`.fromLong(-1).isLeft)
  }

  /** The address of the shopper and the URL they used, as seen past every proxy. */
  final case class ClientOrigin(address: Option[IpAddress], publicUri: Uri)

  /** The storefront's own listening address on the internal network. */
  val StorefrontAddress: SocketAddress[IpAddress] = SocketAddress(ipv4"10.0.0.1", port"8443")

  /** Describes a plain-text connection accepted by the storefront from `peer`. */
  def connection(peer: IpAddress): Request.Connection =
    Request.Connection(StorefrontAddress, SocketAddress(peer, port"51000"), secure = false)

  /**
   * Prefers the first hop of `Forwarded`, then the legacy `X-Forwarded-*` headers, then the
   * `Host` header and the TCP peer; obfuscated or unknown hops yield no address.
   */
  def resolveOrigin(request: Request[IO]): ClientOrigin = {
    val firstHop: Option[Forwarded.Element] = request.headers.get[Forwarded].map(_.values.head)
    val address: Option[IpAddress] = firstHop match {
      case Some(hop) =>
        hop.maybeFor.map(_.nodeName).collect {
          case Forwarded.Node.Name.Ipv4(ip) => ip
          case Forwarded.Node.Name.Ipv6(ip) => ip
        }
      case None => request.from
    }
    val scheme: Uri.Scheme = firstHop
      .flatMap(_.maybeProto)
      .orElse(request.headers.get[`X-Forwarded-Proto`].map(_.scheme))
      .getOrElse(Uri.Scheme.http)
    val authority: Option[Uri.Authority] = firstHop
      .flatMap(_.maybeHost)
      .map(host => Uri.Authority(host = host.host, port = host.port))
      .orElse(request.headers.get[`X-Forwarded-Host`].map { host =>
        Uri.Authority(host = Uri.RegName(host.host), port = host.port)
      })
      .orElse(request.headers.get[Host].map(host => Uri.Authority(host = Uri.RegName(host.host), port = host.port)))
    ClientOrigin(address, request.uri.copy(scheme = Some(scheme), authority = authority))
  }

  /** Relays a request to the next tier, appending the storefront's hop to the `Forwarded` chain. */
  def proxyOnwards(request: Request[IO]): Request[IO] = {
    val peer: IpAddress = request.remoteAddr.getOrElse(throw new AssertionError("no peer address"))
    val peerName: Forwarded.Node.Name = peer match {
      case v4: Ipv4Address => Forwarded.Node.Name.Ipv4(v4)
      case v6: Ipv6Address => Forwarded.Node.Name.Ipv6(v6)
    }
    val by: Forwarded.Node = Forwarded.Node(
      Forwarded.Node.Name.Ipv4(ipv4"10.0.0.1"),
      Forwarded.Node.Port.fromInt(StorefrontAddress.port.value).fold(throw _, identity)
    )
    val hop: Forwarded.Element = Forwarded.Element
      .fromBy(by)
      .withFor(Forwarded.Node(peerName))
      .withProto(if (request.isSecure.contains(true)) Uri.Scheme.https else Uri.Scheme.http)
    request.addHeader(Forwarded(hop))
  }

  /** Relays a `TRACE` while `Max-Forwards` allows it, and answers it locally once it reaches zero. */
  def traceHop(request: Request[IO]): Either[Response[IO], Request[IO]] =
    request.headers.get[`Max-Forwards`] match {
      case Some(budget) if budget == `Max-Forwards`.zero =>
        Left(Response[IO](Status.Ok).putHeaders("X-Answered-By" -> "storefront-edge"))
      case Some(budget) =>
        val remaining: `Max-Forwards` =
          `Max-Forwards`.fromLong(budget.count - 1).fold(throw _, identity)
        Right(request.putHeaders(remaining))
      case None =>
        Right(request)
    }

  /** Parses an RFC 7239 obfuscated identifier, which always starts with an underscore. */
  def obfuscated(identifier: String): Forwarded.Node.Obfuscated =
    Forwarded.Node.Obfuscated.fromString(identifier).fold(throw _, identity)

  /** Returns every rendered value of a header the way it goes out on the wire. */
  def rawValues(headers: Headers, name: CIString): List[String] =
    headers.get(name).map(_.toList.map(_.value)).getOrElse(Nil)
}
