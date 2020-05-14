package minc

import metaconfig.cli.CliApp
import mdoc.internal.BuildInfo
import metaconfig.cli.HelpCommand
import metaconfig.cli.VersionCommand
import metaconfig.cli.TabCompleteCommand
import minc.internal.commands.RunCommand

object Main {
  def main(args: Array[String]): Unit = {
    val cli = CliApp(
      version = BuildInfo.version,
      binaryName = "minc",
      commands = List(
        RunCommand,
        HelpCommand,
        VersionCommand,
        TabCompleteCommand
      )
    )
    System.exit(cli.run(args.toList))
  }
}
