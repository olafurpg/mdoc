package mdoc.internal.markdown

import scala.collection.mutable
import scala.meta.Name
import scala.meta.io.AbsolutePath
import mdoc.internal.cli.InputFile
import mdoc.internal.cli.Settings
import scala.meta.Importer
import mdoc.Reporter
import scala.meta.Importee
import scala.meta.Term
import scala.meta.inputs.Input
import scala.meta.parsers.Parsed.Success
import scala.meta.Source
import scala.meta.Import
import mdoc.internal.pos.PositionSyntax._

class MagicImports(settings: Settings, reporter: Reporter, file: InputFile) {

  val scalacOptions = mutable.ListBuffer.empty[Name.Indeterminate]
  val dependencies = mutable.ListBuffer.empty[Name.Indeterminate]
  val repositories = mutable.ListBuffer.empty[Name.Indeterminate]
  val files = mutable.ListBuffer.empty[FileImport]
  private val isVisitedFile = mutable.Set.empty[AbsolutePath]

  class Printable(inputFile: InputFile) {
    private val File = new FileImport.Matcher(settings, inputFile, reporter)
    def unapply(importer: Importer): Boolean = importer match {
      case File(fileImport) =>
        visitFile(fileImport)
        true
      case _ =>
        false
    }
  }
  object Printable extends Printable(file)

  object NonPrintable {
    def unapply(importer: Importer): Boolean = importer match {
      case Importer(
          Term.Name(qualifier),
          List(Importee.Name(name: Name.Indeterminate))
          ) if Instrumenter.magicImports(qualifier) =>
        qualifier match {
          case "$ivy" | "$dep" =>
            dependencies += name
            true
          case "$repo" =>
            repositories += name
            true
          case "$scalac" =>
            scalacOptions += name
            true
          case _ =>
            false
        }
      case _ => false
    }
  }

  private def visitFile(fileImport: FileImport): Unit = {
    if (!isVisitedFile(fileImport.path)) {
      files += fileImport
      isVisitedFile += fileImport.path
      val input = Input.VirtualFile(fileImport.path.toString(), fileImport.source)
      val FilePrintable = new Printable(
        InputFile.fromRelativeFilename(
          fileImport.path.toRelative(this.file.inputFile.parent).toString(),
          settings
        )
      )
      MdocDialect.scala(input).parse[Source] match {
        case e: scala.meta.parsers.Parsed.Error =>
          reporter.error(e.pos, e.message)
        case Success(source) =>
          source.stats.foreach {
            case i: Import =>
              i.importers.foreach {
                case FilePrintable() | NonPrintable() =>
                case _ =>
              }
            case _ =>
          }
      }
    }
  }
}
