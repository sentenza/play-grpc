/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */
package play.grpc

import java.io.File

import example.myapp.helloworld.grpc.GreeterServiceClient
import example.myapp.helloworld.grpc.GreeterServiceClientProvider
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.ProviderConstructionTarget
import play.api.Configuration
import play.api.Environment
import play.api.Mode

class PlayJavaModuleSpec extends AnyWordSpec with Matchers {

  "The generated module" should {

    "provide all clients" in {
      // module in longest common package for the two services
      val module = new example.myapp.helloworld.grpc.AkkaGrpcClientModule()

      val bindings =
        module.bindings(Environment(new File("./"), getClass.getClassLoader, Mode.Prod), Configuration.empty)

      // both clients should be in there
      bindings should have size 1

      bindings.map(_.key.clazz).toSet should ===(Set(classOf[GreeterServiceClient]))

      // not super useful assertions but let's keep for good measure
      bindings.map(_.target.get.asInstanceOf[ProviderConstructionTarget[_]].provider).toSet should ===(
        Set(classOf[GreeterServiceClientProvider]),
      )
    }

  }

}
