object Installer {
  private def installGeneric(
    installUnitCommands: String,
    installScalaCliCommands: String,
  ): Unit = {
    println("Installing required tools for SNUnit...")
    println("""Installing NGINX Unit https://unit.nginx.org/installation
      |""".stripMargin)
    os.proc("bash", "-c", installUnitCommands).call(stdout = os.Inherit)
    println("""Installing scala-cli https://scala-cli.virtuslab.org/install
      |""".stripMargin)
    os.proc("bash", "-c", installScalaCliCommands).call(stdout = os.Inherit)
    println("Everything is installed. You can now develop with SNUnit.")
  }
  def installWithBrew(): Unit = {
    installGeneric(
      installUnitCommands = "brew install nginx/unit/unit",
      installScalaCliCommands = "brew install Virtuslab/scala-cli/scala-cli"
    )
  }
  def installWithAptGet(): Unit = {
    installGeneric(
      installUnitCommands = """set -e +o pipefail
        |apt-get update && apt-get install sudo
        |sudo apt-get update && apt-get install curl
        |sudo curl --output /usr/share/keyrings/nginx-keyring.gpg https://unit.nginx.org/keys/nginx-keyring.gpg
        |source /etc/os-release && echo $UBUNTU_CODENAME
        |sudo tee 'deb [signed-by=/usr/share/keyrings/nginx-keyring.gpg] https://packages.nginx.org/unit/ubuntu/ $UBUNTU_CODENAME unit
        |deb-src [signed-by=/usr/share/keyrings/nginx-keyring.gpg] https://packages.nginx.org/unit/ubuntu/ jammy unit' > sudo tee -a /etc/apt/sources.list.d/unit.list
        |
        |sudo apt-get update
        |sudo apt-get install -y unit unit-dev
        |""".stripMargin,
      installScalaCliCommands = """set -e +o pipefail
        |sudo curl -s --compressed -o /usr/share/keyrings/scala-cli-keyring.gpg "https://virtuslab.github.io/scala-cli-packages/KEY.gpg" | sudo apt-key add -
        |echo 'deb [signed-by=/usr/share/keyrings/nginx-keyring.gpg] https://virtuslab.github.io/scala-cli-packages/debian ./' | sudo tee -a /etc/apt/sources.list.d/scalacli.list
        |sudo apt update
        |sudo apt install -y scala-cli
      """.stripMargin
    )
  }
}
