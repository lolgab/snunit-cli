import java.util.concurrent.atomic.AtomicBoolean
import scala.util.control.NonFatal

object unitd {
  private val dest = os.home / ".cache" / "snunit"
  private val pidFile = dest / "unit.pid"
  private val startupMaxAttempts = 100
  private val startupSleepMs = 50L
  private def closeUnitd(): Unit = {
    try {
      val pid = os.read(pidFile).trim()
      os.proc("kill", pid).call()
      // Wait for Unit to close itself gracefully
      Thread.sleep(100)
    } catch {
      case NonFatal(e) =>
    }
  }
  private def createProc(config: ujson.Obj) = {
    closeUnitd()
    val state = dest / "state"
    os.makeDir.all(state)
    os.write.over(state / "conf.json", config)
    val control = dest / "control.sock"
    os.remove(control)
    val proc = os
      .proc(
        "unitd",
        "--no-daemon",
        "--log",
        "/dev/stderr",
        "--statedir",
        state,
        "--control",
        s"unix:$control",
        "--pid",
        pidFile
      )
      .spawn()
    println("Waiting for FreeUnit to start...")
    var attempts = 0
    while (attempts < startupMaxAttempts && !os.exists(control)) {
      if (!proc.wrapped.isAlive()) {
        throw new RuntimeException("FreeUnit exited before control socket was created.")
      }
      Thread.sleep(startupSleepMs)
      attempts += 1
    }
    if (!os.exists(control)) {
      proc.destroy()
      throw new RuntimeException("Timed out waiting for FreeUnit control socket.")
    }
    proc
  }
  def runBackground(config: ujson.Obj): Long = {
    val proc = createProc(config)
    proc.wrapped.pid()
  }
  def run(config: ujson.Obj): Unit = {
    val proc = createProc(config)
    proc.waitFor()
  }
}
