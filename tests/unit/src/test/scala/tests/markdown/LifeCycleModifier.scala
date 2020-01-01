package tests.markdown

import java.util.concurrent.atomic.AtomicInteger
import mdoc._

/**
  * Global counter used to test the [[mdoc.Main]] process counting.
  * Because tests can be executed concurrently, these need to be
  * thread local. The following counters are used for testing within
  * the same thread.
  */
object LifeCycleCounter {
  val numberOfStarts: ThreadLocal[AtomicInteger] =
    ThreadLocal.withInitial(() => new AtomicInteger(0))
  val numberOfExists: ThreadLocal[AtomicInteger] =
    ThreadLocal.withInitial(() => new AtomicInteger(0))

  /**
    * Reset counters to zero.
    */
  def reset(): Unit = {
    LifeCycleCounter.numberOfStarts.remove()
    LifeCycleCounter.numberOfExists.remove()
  }
}

/**
  * PostModifier used for testing the [[onStart()]] and [[onExit()]] calls.
  */
class LifeCycleModifier extends PostModifier {
  val name = "lifecycle"

  // Starts and stops per instance
  var numberOfStarts = 0
  var numberOfExists = 0

  def process(ctx: PostModifierContext): String = {
    // Used for checking the counting between threads
    s"numberOfStarts = $numberOfStarts ; numberOfExists = $numberOfExists"
  }

  /**
    * This is called once when the [[mdoc.Main]] process starts
    * @param settings CLI or API settings used by mdoc
    */
  override def onStart(settings: MainSettings): Unit = {
    numberOfStarts += 1
    LifeCycleCounter.numberOfStarts.get().incrementAndGet()
  }

  /**
    * This is called once when the [[mdoc.Main]] process finishes
    * @param exit is the exit code returned by mdoc's processing
    */
  override def onExit(exit: MainExit): Unit = {
    numberOfExists += 1
    LifeCycleCounter.numberOfStarts.get().decrementAndGet()
  }
}
