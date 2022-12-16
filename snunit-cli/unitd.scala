import java.util.concurrent.atomic.AtomicBoolean
import scala.util.control.NonFatal

object unitd {
  private val dest = os.home / ".cache" / "snunit"
  private val pidFile = dest / "unit.pid"
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
        "--state",
        state,
        "--control",
        s"unix:$control",
        "--pid",
        pidFile
      )
      .spawn()
    Thread.sleep(100)
    println("Waiting for unit to start...")
    // This returns after Unit is started
    os.proc("unitc", "GET", "/config")
      .call(stdin = os.Inherit, env = Map("UNIT_CTRL" -> control.toString))
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
