package mdoc.internal.compilers

import ch.epfl.scala.{bsp4j => b}
import mdoc.Reporter
import mdoc.internal.pos.TokenEditDistance
import scala.meta.inputs.Input
import scala.collection.JavaConverters._
import scala.meta.Tree
import scala.meta.inputs.{Input, Position}
import scala.meta.io.AbsolutePath
import bloop.launcher.Launcher
import bloop.launcher.LauncherMain
import java.nio.file.Files
import bloop.config.{Config => C}
import mdoc.internal.BuildInfo
import java.nio.file.Path
import scala.meta.io.Classpath
import coursierapi.Dependency
import ch.epfl.scala.bsp4j.DiagnosticSeverity
import ch.epfl.scala.bsp4j.CompileParams
import ch.epfl.scala.bsp4j.StatusCode
import com.google.gson.Gson
import com.google.gson.JsonElement
import scala.util.control.NonFatal
import ch.epfl.scala.bsp4j.BuildTarget
import ch.epfl.scala.bsp4j.ScalacOptionsParams
import java.nio.file.Paths
import java.net.URI
import java.net.URLClassLoader
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption

object BloopMarkdownCompiler {
  def create(
      classpath: String,
      scalacOptions: String,
      target: AbsolutePath
  ): BloopMarkdownCompiler = {
    val bloopDir = target.toNIO.resolve(".bloop")
    val jsonConfigFile = bloopDir.resolve("mdoc.json")
    val src = Files.createDirectories(target.resolve("src").toNIO)
    _root_.bloop.config.write(
      C.File(
        BuildInfo.bloopVersion,
        newBloopProject(
          target.toNIO,
          bloopDir,
          src,
          classpath,
          scalacOptions
        )
      ),
      jsonConfigFile
    )
    val bloop = BloopClient.create(target)
    new BloopMarkdownCompiler(bloop, src)
  }

  private def newBloopProject(
      directory: Path,
      bloopDir: Path,
      src: Path,
      classpath: String,
      scalacOptions: String
  ): C.Project = {
    val name = "mdoc"
    val classesDir: Path = Files.createDirectories(
      bloopDir.resolve(name).resolve("classes")
    )
    val scalaJars = coursierapi.Fetch
      .create()
      .addDependencies(Dependency.of("org.scala-lang", "scala-compiler", BuildInfo.scalaVersion))
      .fetch()
      .asScala
      .toList
      .map(_.toPath())
    C.Project(
      name = name,
      directory = directory,
      workspaceDir = Some(directory),
      sources = List(src),
      sourcesGlobs = None,
      sourceRoots = None,
      dependencies = Nil,
      classpath = Classpath(classpath).entries.map(_.toNIO),
      out = bloopDir.resolve(name),
      classesDir = classesDir,
      resources = None,
      scala = Some(
        C.Scala(
          organization = "org.scala-lang",
          name = "scala-compiler",
          version = BuildInfo.scalaVersion,
          options = scalacOptions.split(" ").toList.filterNot(_.isEmpty()),
          jars = scalaJars,
          analysis = None,
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
      ),
      java = None,
      sbt = None,
      test = None,
      platform = None,
      resolution = None,
      tags = None
    )
  }
}

class BloopMarkdownCompiler(bloop: BloopClient, src: Path) extends MarkdownCompiler {
  val buildTargets =
    bloop.server.workspaceBuildTargets().get().getTargets().asScala
  require(buildTargets.size == 1, buildTargets)
  val targets =
    buildTargets.map(_.getId()).asJava
  val scalacOptions =
    bloop.server
      .buildTargetScalacOptions(new ScalacOptionsParams(targets))
      .get()
      .getItems()
      .get(0)
  val classpath = scalacOptions
    .getClasspath()
    .asScala
    .map(uri => URI.create(uri).toURL())
  val classDirectory = URI.create(scalacOptions.getClassDirectory()).toURL()
  def compile(
      input: Input.VirtualFile,
      vreporter: Reporter,
      edit: TokenEditDistance,
      className: String
  ): Option[Class[_]] = {
    Files.write(
      src.resolve("mdoc.scala"),
      input.value.getBytes(StandardCharsets.UTF_8),
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING
    )
    val result = bloop.server.buildTargetCompile(new CompileParams(targets)).get()
    if (result.getStatusCode() == StatusCode.OK) {
      val classloader = new URLClassLoader(
        (classpath :+ classDirectory).toArray,
        this.getClass().getClassLoader()
      )
      // yolo, never close classloader
      Try(classloader.loadClass(className)) match {
        case Failure(exception) =>
          exception.printStackTrace()
          None
        case Success(value) => Some(value)
      }
    } else {
      None
    }
  }
  def close(): Unit = bloop.close()
  def hasErrors: Boolean =
    bloop.client.hasDiagnosticSeverity(DiagnosticSeverity.ERROR)
  def hasWarnings: Boolean =
    bloop.client.hasDiagnosticSeverity(DiagnosticSeverity.WARNING)
  def fail(original: Seq[Tree], input: Input, sectionPos: Position): String = ???
  def compileSources(input: Input.VirtualFile, vreporter: Reporter, edit: TokenEditDistance): Unit =
    ???

  def asScalaBuildTarget(buildTarget: BuildTarget): Option[b.ScalaBuildTarget] =
    decodeJson(buildTarget.getData(), classOf[b.ScalaBuildTarget])

  def decodeJson[T](obj: AnyRef, cls: java.lang.Class[T]): Option[T] =
    for {
      data <- Option(obj)
      value <- try {
        Some(
          new Gson().fromJson[T](
            data.asInstanceOf[JsonElement],
            cls
          )
        )
      } catch {
        case NonFatal(e) =>
          e.printStackTrace()
          None
      }
    } yield value
}
