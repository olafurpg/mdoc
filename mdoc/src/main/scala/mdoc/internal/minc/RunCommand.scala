package mdoc.internal.minc

import metaconfig.cli.Command
import metaconfig.cli.CliApp

class RunCommand extends Command[RunOptions]("run") {
  def run(value: RunOptions, app: CliApp): Int = {
    1
  }
}
