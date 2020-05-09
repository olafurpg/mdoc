package mdoc.internal.markdown

import scala.meta.io.AbsolutePath
import java.nio.file.Path
import scala.meta.Name
import scala.meta.inputs.Input
import mdoc.internal.pos.PositionSyntax._
import scala.meta.Importee
import scala.meta.Term
import mdoc.Reporter

final case class FileImport(
    path: AbsolutePath,
    qualifier: Term,
    importName: Name.Indeterminate,
    objectName: String,
    packageName: String,
    source: String
) {
  def toInput: Input = {
    val text = new StringBuilder()
      .append("package ")
      .append(packageName)
      .append("\n")
      .append("\n")
      .append("object ")
      .append(objectName)
      .append(" {\n")
      .append(source)
      .append("\n}\n")
      .toString()
    Input.VirtualFile(path.syntax, text)
  }
}
object FileImport {
  def fromImport(
      base: AbsolutePath,
      qual: Term,
      fileImport: Name.Indeterminate,
      reporter: Reporter
  ): Option[FileImport] = {
    def loop(path: Path, parts: List[String]): Path = parts match {
      case Nil => path
      case "^" :: tail =>
        loop(path.getParent, tail)
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
      Some(FileImport(scriptPath, qual, fileImport, objectName, packageName, scriptPath.readText))
    } else {
      reporter.error(fileImport.pos, s"no such file $scriptPath")
      None
    }
  }
}
