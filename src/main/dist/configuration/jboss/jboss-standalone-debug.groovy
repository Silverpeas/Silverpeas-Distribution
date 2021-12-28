log.info 'Update debugging statement in Standalone Wildfly for Java 11'

String jbossHome = System.getenv('JBOSS_HOME')
if (jbossHome == null || jbossHome.isBlank()) {
  throw new FileNotFoundException('The JBOSS_HOME environment variable isn\'t set!')
}
File unixStarter = new File("${jbossHome}/bin/standalone.sh")
String unixScript = unixStarter.text
unixStarter.withWriter {
  it << unixScript.replace('address=$DEBUG_PORT', 'address=*:$DEBUG_PORT')
}

File windowsStarter = new File("${jbossHome}/bin/standalone.bat")
String windowsScript = windowsStarter.text
windowsStarter.withWriter {
  it << windowsScript.replace('address=%DEBUG_PORT_VAR%', 'address=*:%DEBUG_PORT_VAR%')
}