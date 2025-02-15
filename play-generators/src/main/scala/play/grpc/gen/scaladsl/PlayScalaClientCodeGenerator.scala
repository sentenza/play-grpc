/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */
package play.grpc.gen.scaladsl

import scala.annotation.tailrec
import scala.collection.immutable

import akka.grpc.gen.scaladsl.ScalaCodeGenerator
import akka.grpc.gen.scaladsl.Service
import akka.grpc.gen.Logger
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.PlayScala.txt.AkkaGrpcClientModule
import templates.PlayScala.txt.ClientProvider

object PlayScalaClientCodeGenerator extends PlayScalaClientCodeGenerator

class PlayScalaClientCodeGenerator extends ScalaCodeGenerator {

  val ClientModuleName = "AkkaGrpcClientModule"

  override def name: String = "play-grpc-client-scala"

  override def perServiceContent = super.perServiceContent + generateClientProvider

  private val generateClientProvider: (Logger, Service) => immutable.Seq[CodeGeneratorResponse.File] =
    (logger, service) => {
      val b = CodeGeneratorResponse.File.newBuilder()
      b.setContent(ClientProvider(service).body)
      b.setName(s"${service.packageName.replace('.', '/')}/${service.name}ClientProvider.scala")
      logger.info(s"Generating Play gRPC play client provider for ${service.packageName}.${service.name}")
      immutable.Seq(b.build)
    }

  override def staticContent(logger: Logger, allServices: Seq[Service]): Set[CodeGeneratorResponse.File] = {
    if (allServices.nonEmpty) {
      val packageName = packageForSharedModuleFile(allServices)
      val b           = CodeGeneratorResponse.File.newBuilder()
      b.setContent(AkkaGrpcClientModule(packageName, allServices).body)
      b.setName(s"${packageName.replace('.', '/')}/${ClientModuleName}.scala")
      val set = Set(b.build)
      logger.info(
        s"Generated [${packageName}.${ClientModuleName}] add it to play.modules.enabled and a section " +
          "with Akka gRPC client config under akka.grpc.client.\"servicepackage.ServiceName\" to be able to inject " +
          "client instances.",
      )
      set
    } else Set.empty
  }

  def packageForSharedModuleFile(allServices: Seq[Service]): String =
    // single service or all services in single package - use that
    if (allServices.forall(_.packageName == allServices.head.packageName)) allServices.head.packageName
    else {
      // try to find longest common prefix
      allServices.tail.foldLeft(allServices.head.packageName)((packageName, service) =>
        if (packageName == service.packageName) packageName
        else commonPackage(packageName, service.packageName),
      )
    }

  /** extract the longest common package prefix for two classes */
  def commonPackage(a: String, b: String): String = {
    val aPackages = a.split('.')
    val bPackages = b.split('.')
    @tailrec
    def countIdenticalPackage(pos: Int): Int = {
      if ((aPackages.length < pos + 1) || (bPackages.length < pos + 1)) pos
      else if (aPackages(pos) == bPackages(pos)) countIdenticalPackage(pos + 1)
      else pos
    }

    val prefixLength = countIdenticalPackage(0)
    if (prefixLength == 0) "" // no common, use root package
    else aPackages.take(prefixLength).mkString(".")

  }

}
