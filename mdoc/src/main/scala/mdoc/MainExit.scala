package mdoc

import mdoc.internal.cli.Exit

final class MainExit private[mdoc] (val exit: Exit) {
  def merge(other: MainExit): MainExit = new MainExit(exit.merge(other.exit))
  def isSuccess: Boolean = exit.isSuccess
  def isError: Boolean = exit.isError
}

object MainExit {
  def success(): MainExit = {
    new MainExit(Exit.success)
  }
}
