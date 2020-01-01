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

final class PostModifierContext private[mdoc] (
    val info: String,
    val originalCode: Input,
    val outputCode: String,
    val variables: List[Variable],
    val reporter: Reporter,
    val relativePath: RelativePath,
    private[mdoc] val settings: Settings
) {
  def inputFile: AbsolutePath = inDirectory.resolve(relativePath)
  def outputFile: AbsolutePath = outDirectory.resolve(relativePath)
  def lastValue: Any = variables.lastOption.map(_.runtimeValue).orNull
  def inDirectory: AbsolutePath = settings.in
  def outDirectory: AbsolutePath = settings.out
}
