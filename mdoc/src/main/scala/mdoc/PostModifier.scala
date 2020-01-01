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

/**
  * Interface of classes used for processing Markdown code fences.
  * It provides method calls to set-up resources before processing
  * sources, process code fences of all source files and release
  * resource just before mdoc terminates.
  *
  */
trait PostModifier extends LifecycleModifier {
  val name: String

  def process(ctx: PostModifierContext): String

}

object PostModifier {
  def default(): List[PostModifier] = default(this.getClass.getClassLoader)
  def default(classLoader: ClassLoader): List[PostModifier] =
    ServiceLoader.load(classOf[PostModifier], classLoader).iterator().asScala.toList
  implicit val surface: Surface[Settings] = new Surface(Nil)
  implicit val decoder: ConfDecoder[PostModifier] =
    ConfDecoder.instanceF[PostModifier](_ => ConfError.message("unsupported").notOk)
  implicit val encoder: ConfEncoder[PostModifier] =
    ConfEncoder.StringEncoder.contramap(mod => s"<${mod.name}>")
}
