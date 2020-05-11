package mdoc.internal.markdown

import scala.meta.io.AbsolutePath
import java.nio.file.Path
import scala.meta.Name
import scala.meta.inputs.Input
import mdoc.internal.pos.PositionSyntax._
import scala.meta.Importee
import scala.meta.Term
import mdoc.Reporter
import scala.meta.Importer
import mdoc.internal.cli.InputFile
import scala.collection.mutable
import mdoc.internal.cli.Settings
import scala.meta.inputs.Position
import mdoc.internal.pos.TokenEditDistance
import scala.meta.Import

final case class FileImport(
    path: AbsolutePath,
    qualifier: Term,
    importName: Name.Indeterminate,
    objectName: String,
    packageName: String,
    source: String,
    dependencies: List[FileImport],
    renames: List[Rename]
) {
  val fullyQualifiedName = s"$packageName.$objectName"
  val prefix: String =
    s"package $packageName; object $objectName {"
  val toInput: Input = {
    val out = new java.lang.StringBuilder().append(prefix)
    var i = 0
    renames.sortBy(_.from.start).foreach { rename =>
      out
        .append(source, i, rename.from.start)
        .append(rename.to)
      i = rename.from.end
    }
    out
      .append(source, i, source.length())
      .append("\n}\n")
    Input.VirtualFile(path.syntax, out.toString())
  }
  val edit = TokenEditDistance(Input.VirtualFile(path.syntax, source), toInput)
}
object FileImport {
  class Matcher(
      settings: Settings,
      file: InputFile,
      reporter: Reporter
  ) {
    def unapply(importer: Importer): Option[FileImport] = importer match {
      case importer @ Importer(qual, importees) if isFileQualifier(qual) =>
        importees match {
          case List(Importee.Name(name: Name.Indeterminate)) =>
            FileImport.fromImport(file.inputFile, qual, name, reporter, settings)
          case _ =>
            // NOTE(olafur): it would be nice to support this syntax but it requires
            // some custom changes to how we instrument imports so I'm leaving it for
            // future work. We try to report a helpful error instead.
            reporter.error(
              importer.pos,
              "unsupported syntax. " +
                s"To fix this problem, use regular `import $$file.path` imports without curly braces."
            )
            None
        }
      case _ =>
        None
    }
    private def isFileQualifier(qual: Term): Boolean = qual match {
      case Term.Name("$file") => true
      case Term.Select(next, _) => isFileQualifier(next)
      case _ => false
    }
  }

  private def fromImport(
      base: AbsolutePath,
      qual: Term,
      fileImport: Name.Indeterminate,
      reporter: Reporter,
      settings: Settings
  ): Option[FileImport] = {
    def loop(path: Path, parts: List[String]): Path = parts match {
      case Nil => path
      case "^" :: tail =>
        loop(path.getParent, tail)
      case "^^" :: tail =>
        loop(path.getParent.getParent(), tail)
      case "^^^" :: tail =>
        loop(path.getParent.getParent.getParent(), tail)
      case head :: tail =>
        loop(path.resolve(head), tail)
    }
    val parts = Term.Select(qual, Term.Name(fileImport.value)).syntax.split('.').toList
    val relativePath = parts.tail
    val packageName = parts.init.mkString(".")
    val objectName = parts.last
    val importedPath = loop(base.toNIO.getParent(), relativePath)
    val scriptPath = AbsolutePath(importedPath).resolveSibling(_ + ".sc")
    if (scriptPath.isFile) {
      val text = scriptPath.readText
      Some(FileImport(scriptPath, qual, fileImport, objectName, packageName, text, Nil, Nil))
    } else {
      reporter.error(fileImport.pos, s"no such file $scriptPath")
      None
    }
  }
}
