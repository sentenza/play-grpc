/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */
package play.grpc

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.grpc.internal.GrpcProtocolNative
import akka.grpc.internal.Identity
import akka.grpc.GrpcProtocol.DataFrame
import akka.grpc.ProtobufSerializer
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.SystemMaterializer
import akka.util.ByteString
import controllers.GreeterServiceImpl
import example.myapp.helloworld.grpc.helloworld._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll
import play.api.libs.typedmap.TypedMap
import play.api.mvc.akkahttp.AkkaHttpHandler
import play.api.mvc.request.RemoteConnection
import play.api.mvc.request.RequestFactory
import play.api.mvc.request.RequestTarget
import play.api.mvc.Headers
import GreeterServiceMarshallers._

class PlayScalaRouterSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with ScalaFutures {
  implicit val sys      = ActorSystem()
  implicit val mat      = SystemMaterializer(sys).materializer
  implicit val ec       = sys.dispatcher
  implicit val patience = PatienceConfig(timeout = 3.seconds, interval = 15.milliseconds)

  val router = new GreeterServiceImpl

  "The generated Play Router" should {

    "not accept requests for other paths" in {
      router.routes.isDefinedAt(playRequestFor(Uri("http://localhost/foo"))) shouldBe false
    }

    "by default accept requests using the service name as prefix" in {
      val uri = Uri(s"http://localhost/${GreeterService.name}/SayHello")
      router.routes.isDefinedAt(playRequestFor(uri)) shouldBe true

      val name = "John"

      val handler  = router.routes(playRequestFor(uri)).asInstanceOf[AkkaHttpHandler]
      val request  = akkaHttpRequestFor(uri, HelloRequest(name))
      val response = handler(request).futureValue
      response.status shouldBe StatusCodes.OK

      val reply = akkaHttpResponse[HelloReply](response).futureValue
      reply.message shouldBe s"Hello, $name!"
    }

    "allow / as identity prefix" in {
      val result = router.withPrefix("/")
      result shouldBe theSameInstanceAs(router)
    }

    "not allow specifying another prefix" in {
      intercept[UnsupportedOperationException] {
        router.withPrefix("/some")
      }
    }

    def akkaHttpRequestFor[T](uri: Uri, msg: T)(implicit serializer: ProtobufSerializer[T]) = {
      HttpRequest(
        uri = uri,
        entity = HttpEntity.Chunked(
          GrpcProtocolNative.contentType,
          Source
            .single(msg)
            .map(serializer.serialize)
            .map(DataFrame(_))
            .via(GrpcProtocolNative.newWriter(Identity).frameEncoder),
        ),
      )
    }
    def akkaHttpResponse[T](response: HttpResponse)(implicit deserializer: ProtobufSerializer[T]) =
      response.entity.dataBytes
        .via(GrpcProtocolNative.newReader(Identity).dataFrameDecoder)
        .runWith(Sink.reduce[ByteString](_ ++ _))
        .map(deserializer.deserialize)

    def playRequestFor(uri: Uri) =
      RequestFactory.plain.createRequest(
        RemoteConnection(uri.authority.host.address, secure = false, clientCertificateChain = None),
        "GET",
        RequestTarget(uri.toString, uri.path.toString, queryString = Map.empty),
        version = "42",
        Headers(),
        attrs = TypedMap.empty,
        body = (),
      )
  }

  override def afterAll(): Unit = {
    super.afterAll()
    sys.terminate()
    ()
  }
}
