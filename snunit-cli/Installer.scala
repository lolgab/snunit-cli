object Installer {
  private def runCommands(commands: String) = os.proc("bash", "-c", commands).call(stdin = os.Inherit, stdout = os.Inherit)

  private def installGeneric(
    installClangCommands: String,
    installUnitCommands: String,
    installScalaCliCommands: String,
  ): Unit = {
    println("Installing required tools for SNUnit...")
    println("Installing Clang\n")
    runCommands(installUnitCommands)
    println("""Installing NGINX Unit https://unit.nginx.org/installation
      |""".stripMargin)
    runCommands(installUnitCommands)
    println("""Installing scala-cli https://scala-cli.virtuslab.org/install
      |""".stripMargin)
    runCommands(installScalaCliCommands)
    println("Everything is installed. You can now develop with SNUnit.")
  }
  def installWithBrew(): Unit = {
    installGeneric(
      installClangCommands = "brew install clang",
      installUnitCommands = "brew install nginx/unit/unit",
      installScalaCliCommands = "brew install Virtuslab/scala-cli/scala-cli"
    )
  }
  def installWithAptGet(): Unit = {
    installGeneric(
      installClangCommands = "sudo apt-get update && sudo apt-get install clang",
      installUnitCommands = """
        |set -e +o pipefail
        |sudo apt-get update && sudo apt-get install -y curl
        |sudo curl -s --compressed --output /usr/share/keyrings/nginx-keyring.gpg https://unit.nginx.org/keys/nginx-keyring.gpg
        |source /etc/os-release && echo $UBUNTU_CODENAME
        |echo "deb [signed-by=/usr/share/keyrings/nginx-keyring.gpg] https://packages.nginx.org/unit/ubuntu/ $UBUNTU_CODENAME unit
        |deb-src [signed-by=/usr/share/keyrings/nginx-keyring.gpg] https://packages.nginx.org/unit/ubuntu/ jammy unit" | sudo tee -a /etc/apt/sources.list.d/unit.list
        |
        |sudo apt-get update
        |sudo apt-get install -y unit unit-dev
        |""".stripMargin,
      installScalaCliCommands = """set -e +o pipefail
        |curl -fLo scala-cli.deb https://github.com/Virtuslab/scala-cli/releases/latest/download/scala-cli-x86_64-pc-linux.deb
        |sudo dpkg -i scala-cli.deb
        |""".stripMargin
    )
  }
}
