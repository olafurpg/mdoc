package mdoc

import java.util.ServiceLoader
import mdoc.internal.cli.Settings
import metaconfig.ConfDecoder
import metaconfig.ConfEncoder
import metaconfig.ConfError
import metaconfig.generic.Surface
import scala.meta.inputs.Input
import scala.meta.io.AbsolutePath
import scala.collection.JavaConverters._
import scala.meta.io.RelativePath
import mdoc.internal.pos.PositionSyntax._
import scala.meta.inputs.Position

final class PreModifierContext private[mdoc] (
    val info: String,
    val originalCode: Input,
    val reporter: Reporter,
    val relativePath: RelativePath,
    private[mdoc] val settings: Settings
) {
  def infoInput: Input = {
    val cpos = originalCode.toPosition.toUnslicedPosition
    val start = cpos.start - info.length - 1
    val end = cpos.start - 1
    Input.Slice(cpos.input, start, end)
  }
  def inputFile: AbsolutePath = inDirectory.resolve(relativePath)
  def outputFile: AbsolutePath = outDirectory.resolve(relativePath)
  def inDirectory: AbsolutePath = settings.in
  def outDirectory: AbsolutePath = settings.out
}
