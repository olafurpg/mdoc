package minc.internal.bloops

import bloop.config.{Config => C}
import mdoc.internal.markdown.FileImport
import mdoc.internal.pos.PositionSyntax._
import mdoc.internal.BuildInfo
import coursierapi.Dependency
import scala.collection.JavaConverters._

class BloopProjects(in: Inputs) {
  val allScalaJars = coursierapi.Fetch
    .create()
    .addDependencies(Dependency.of("org.scala-lang", "scala-library", BuildInfo.scalaVersion))
    .fetch()
    .asScala
    .map(_.toPath())
  def file(i: FileImport): C.File = {
    val directoryName = ""
    val out = in.bloopDir.resolve(directoryName).createDirectories
    val classesDir = out.resolve("classes").createDirectories
    C.File(
      "1.4.0",
      C.Project(
        name = i.path.toString(),
        directory = i.path.parent.toNIO,
        workspaceDir = Some(in.app.workingDirectory),
        sources = Nil,
        sourcesGlobs = None,
        sourceRoots = None,
        dependencies = Nil,
        classpath = Nil,
        out = out.toNIO,
        classesDir = classesDir.toNIO,
        resources = None,
        scala = bloopScala(in.instrumented.scalacOptions),
        java = None,
        sbt = None,
        test = None,
        platform = None,
        resolution = None,
        tags = None
      )
      // BuildInfo.version
    )
  }

  def bloopScala(scalacOptions: List[String]): Option[C.Scala] =
    Some(
      C.Scala(
        "org.scala-lang",
        "scala-compiler",
        BuildInfo.scalaVersion,
        scalacOptions,
        allScalaJars.toList,
        None,
        setup = Some(
          C.CompileSetup(
            C.Mixed,
            addLibraryToBootClasspath = true,
            addCompilerToClasspath = false,
            addExtraJarsToClasspath = false,
            manageBootClasspath = true,
            filterLibraryFromClasspath = true
          )
        )
      )
    )
}

object BloopProjects {
  def create(inputs: Inputs): Int = {
    bloop.config.write(???)
    inputs.bloopDir
    0
  }
}
