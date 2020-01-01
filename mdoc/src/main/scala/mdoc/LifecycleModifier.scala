package mdoc

trait LifecycleModifier {

  /**
    * This methods is called once just before mdoc starts processing all of the
    * source files. Use this to set-up resources required by the post-modifier.
    *
    * @param settings setting set via the command line or directly vi the API
    */
  def onStart(ctx: OnStartContext): Unit = ()

  /**
    * This methods is called once just after mdoc finished processing all of the
    * source files. Use this to release or deactivate any resources that are not
    * required by the post-modifier anymore.
    *
    * @param exit a value of 0 indicates mdoc processed all files with no error.
    *             a value of 1 indicates mdoc processing resulted in at least
    *             one error.
    */
  def onExit(ctx: OnExitContext): Unit = ()

}
