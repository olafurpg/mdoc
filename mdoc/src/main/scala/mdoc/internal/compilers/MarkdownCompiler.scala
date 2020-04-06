package mdoc.internal.compilers

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import mdoc.Reporter
import mdoc.document.Document
import mdoc.document._
import mdoc.internal.document.DocumentBuilder
import mdoc.internal.document.MdocNonFatal
import mdoc.internal.pos.PositionSyntax
import mdoc.internal.pos.PositionSyntax._
import mdoc.internal.pos.TokenEditDistance
import scala.collection.JavaConverters._
import scala.collection.Seq
import scala.meta._
import scala.meta.inputs.Input
import scala.meta.inputs.Position
import scala.reflect.internal.util.AbstractFileClassLoader
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.internal.util.{Position => GPosition}
import scala.tools.nsc.Global
import scala.tools.nsc.Settings
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.io.VirtualDirectory
import sun.misc.Unsafe
import mdoc.internal.markdown.EvaluatedDocument
import mdoc.internal.markdown.SectionInput
import mdoc.internal.markdown.FilterStoreReporter
import mdoc.internal.markdown.CodeBuilder
import java.nio.file.Files

trait MarkdownCompiler {
  def compile(
      input: Input.VirtualFile,
      vreporter: Reporter,
      edit: TokenEditDistance,
      className: String
  ): Option[Class[_]]
  def compileSources(
      input: Input.VirtualFile,
      vreporter: Reporter,
      edit: TokenEditDistance
  ): Unit
  def close(): Unit
  def hasErrors: Boolean
  def hasWarnings: Boolean
  def fail(original: Seq[Tree], input: Input, sectionPos: Position): String
}

object MarkdownCompiler {

  def default(): MarkdownCompiler = fromClasspath(classpath = "", scalacOptions = "")

  def buildDocument(
      compiler: MarkdownCompiler,
      reporter: Reporter,
      sectionInputs: List[SectionInput],
      instrumented: String,
      filename: String
  ): EvaluatedDocument = {
    val instrumentedInput = InstrumentedInput(filename, instrumented)
    reporter.debug(s"$filename: instrumented code\n$instrumented")
    val compileInput = Input.VirtualFile(filename, instrumented)
    val edit = TokenEditDistance.fromTrees(sectionInputs.map(_.source), compileInput)
    val doc = compiler.compile(compileInput, reporter, edit, "repl.Session$") match {
      case Some(cls) =>
        val ctor = cls.getDeclaredConstructor()
        ctor.setAccessible(true)
        val doc = ctor.newInstance().asInstanceOf[DocumentBuilder].$doc
        try {
          doc.build(instrumentedInput)
        } catch {
          case e: DocumentException =>
            val index = e.sections.length - 1
            val input = sectionInputs(index).input
            val pos =
              if (e.pos.isEmpty) {
                Position.Range(input, 0, 0)
              } else {
                val slice = Position.Range(
                  input,
                  e.pos.startLine,
                  e.pos.startColumn,
                  e.pos.endLine,
                  e.pos.endColumn
                )
                slice.toUnslicedPosition
              }
            reporter.error(pos, e.getCause)
            Document(instrumentedInput, e.sections)
          case MdocNonFatal(e) =>
            reporter.error(e)
            Document.empty(instrumentedInput)
        }
      case None =>
        // An empty document will render as the original markdown
        Document.empty(instrumentedInput)
    }
    EvaluatedDocument(doc, sectionInputs)
  }

  def fromSettings(settings: mdoc.internal.cli.Settings): MarkdownCompiler = {
    settings.compiler match {
      case "bloop" =>
        val target = settings.target.getOrElse {
          val tmp = Files.createTempDirectory("mdoc")
          tmp.toFile().deleteOnExit()
          AbsolutePath(tmp)
        }
        BloopMarkdownCompiler.create(settings.classpath, settings.scalacOptions, target)
      case "scalac" =>
        fromClasspath(settings.classpath, settings.scalacOptions)
      case unknown => sys.error(unknown)
    }
  }
  def fromClasspath(classpath: String, scalacOptions: String): MarkdownCompiler = {
    val fullClasspath =
      if (classpath.isEmpty) defaultClasspath(_ => true)
      else {
        val base = Classpath(classpath)
        val runtime = defaultClasspath(path => path.toString.contains("mdoc-runtime"))
        base ++ runtime
      }
    new ScalacMarkdownCompiler(fullClasspath.syntax, scalacOptions)
  }

  private def defaultClasspath(fn: Path => Boolean): Classpath = {
    val paths =
      getURLs(getClass.getClassLoader)
        .map(url => AbsolutePath(Paths.get(url.toURI)))
    Classpath(paths.toList)
  }

  /**
    * Utility to get SystemClassLoader/ClassLoader urls in java8 and java9+
    *   Based upon: https://gist.github.com/hengyunabc/644f8e84908b7b405c532a51d8e34ba9
    */
  private def getURLs(classLoader: ClassLoader): Seq[URL] = {
    if (classLoader.isInstanceOf[URLClassLoader]) {
      classLoader.asInstanceOf[URLClassLoader].getURLs()
      // java9+
    } else if (classLoader
        .getClass()
        .getName()
        .startsWith("jdk.internal.loader.ClassLoaders$")) {
      try {
        val field = classOf[Unsafe].getDeclaredField("theUnsafe")
        field.setAccessible(true)
        val unsafe = field.get(null).asInstanceOf[Unsafe]

        // jdk.internal.loader.ClassLoaders.AppClassLoader.ucp
        val ucpField = classLoader.getClass().getDeclaredField("ucp")
        val ucpFieldOffset: Long = unsafe.objectFieldOffset(ucpField)
        val ucpObject = unsafe.getObject(classLoader, ucpFieldOffset)

        // jdk.internal.loader.URLClassPath.path
        val pathField = ucpField.getType().getDeclaredField("path")
        val pathFieldOffset = unsafe.objectFieldOffset(pathField)
        val paths: Seq[URL] = unsafe
          .getObject(ucpObject, pathFieldOffset)
          .asInstanceOf[java.util.ArrayList[URL]]
          .asScala

        paths
      } catch {
        case ex: Exception =>
          ex.printStackTrace()
          Nil
      }
    } else {
      Nil
    }
  }

}
