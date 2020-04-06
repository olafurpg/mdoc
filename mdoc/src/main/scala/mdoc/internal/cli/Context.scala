package mdoc.internal.cli

import mdoc.Reporter
import mdoc.internal.io.ConsoleReporter
import mdoc.internal.compilers.MarkdownCompiler

case class Context(settings: Settings, reporter: Reporter, compiler: MarkdownCompiler)

object Context {
  def fromOptions(options: Settings, reporter: Reporter = ConsoleReporter.default): Context = {
    val compiler = MarkdownCompiler.fromSettings(options)
    Context(options, reporter, compiler)
  }
}
