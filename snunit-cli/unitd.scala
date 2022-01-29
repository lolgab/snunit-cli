import $dep.`com.lihaoyi::upickle::1.5.0`
import $dep.`com.lihaoyi::os-lib::0.8.0`
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
  def runBackground(config: ujson.Obj): Unit = {
    closeUnitd()
    val state = dest / "state"
    os.makeDir.all(state)
    os.write.over(state / "conf.json", config)
    val control = dest / "control.sock"
    os.remove(control)
    val started = new AtomicBoolean(false)
    val proc = os.proc(
        "unitd",
        "--no-daemon",
        "--log",
        "/dev/stdout",
        "--state",
        state,
        "--control",
        s"unix:$control",
        "--pid",
        pidFile
      ).spawn(stderr = os.ProcessOutput.Readlines(line => {
        line match {
          case s"$_ unit $_ started" =>
            started.set(true)
          case _ =>
        }
        System.err.println(line)
      }))
    while (!started.get()) {
      println("Waiting for unit to start...")
      Thread.sleep(100)
    }
  }
}
