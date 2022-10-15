import scala.util._

case class Container(id: String) {
  override def toString = id
}
object Container {
  given Using.Releasable[Container] with
    def release(container: Container): Unit =
      os.proc("docker", "kill", container.id).call()
}
