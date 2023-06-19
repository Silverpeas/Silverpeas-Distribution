import groovy.xml.XmlUtil

import javax.xml.parsers.SAXParserFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * This script setups the JCR repository configuration file according to the customer configuration
 * properties. If a previous JCR managed by Jackrabbit is found, it is before renamed and then the
 * repository.xml and workspace.xml configuration files are updated for an eventual migration to
 * Oak.
 * @author mmoquillon
 */

log.info 'Configure the JCR repository'

Path jcrHomePath = settings.JCR_HOME.asPath()
Path jcrConfigurationPath = "${settings.JCR_HOME}/silverpeas-oak.properties".asPath()
Path jcrConfigurationTemplatePath = "${settings.CONFIGURATION_HOME}/silverpeas/resources/silverpeas-oak.properties".asPath()

/* case of a jcr home directory whose the repository is managed by jackrabbit */
Path jcrConfig = "${settings.JCR_HOME}/repository.xml".asPath()
if (Files.exists(jcrConfig)) {
  Path jackrabbitHomePath = Path.of(jcrHomePath.parent.toString(), 'jackrabbit')
  log.info "Rename the Jackrabbit 2 home directory ${jcrHomePath.toString()} to ${jackrabbitHomePath.toString()}..."

  Files.move(jcrHomePath, Path.of(jcrHomePath.parent.toString(), 'jackrabbit'))

  log.info 'Backup the Jackrabbit 2 configuration files and update them for the JCR migration...'

  String jcrUrl = (settings.JCR_URL ? settings.JCR_URL : settings.DB_URL).toString()
  String jcrUser = (settings.JCR_USER ? settings.JCR_USER : settings.DB_USER).toString()
  String jcrPassword = (settings.JCR_PASSWORD ? settings.JCR_PASSWORD : settings.DB_PASSWORD).toString()

  Path jackrabbitConfig = jackrabbitHomePath.resolve('repository.xml')
  Path jackrabbitConfigBackup = jackrabbitHomePath.resolve('repository.xml.backup')
  SAXParserFactory factory = SAXParserFactory.newInstance()
  factory.validating = false
  factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",
      false)

  // backup repository.xml
  Files.copy(jackrabbitConfig, jackrabbitConfigBackup, StandardCopyOption.REPLACE_EXISTING)

  // update repository.xml for an eventual migration to oak
  def xmlRepositoryConf = new XmlSlurper(factory.newSAXParser()).parse(jackrabbitConfig.toFile())
  def node = xmlRepositoryConf.Workspace.PersistenceManager.param.find { it.@name == 'user'}
  if (!node) {
    xmlRepositoryConf.Workspace.PersistenceManager.param.find { it.@name == 'driver' }.@value = settings.DB_DRIVER
    xmlRepositoryConf.Workspace.PersistenceManager.param.find { it.@name == 'url' }.@value = jcrUrl
    xmlRepositoryConf.Workspace.PersistenceManager.appendNode {
      param(name: 'user', value: jcrUser)
    }
    xmlRepositoryConf.Workspace.PersistenceManager.appendNode {
      param(name: 'password', value: jcrPassword)
    }
    xmlRepositoryConf.Versioning.PersistenceManager.param.find { it.@name == 'driver' }.@value = settings.DB_DRIVER
    xmlRepositoryConf.Versioning.PersistenceManager.param.find { it.@name == 'url' }.@value = jcrUrl
    xmlRepositoryConf.Versioning.PersistenceManager.appendNode {
      param(name: 'user', value: jcrUser)
    }
    xmlRepositoryConf.Versioning.PersistenceManager.appendNode {
      param(name: 'password', value: jcrPassword)
    }
    XmlUtil.serialize(xmlRepositoryConf, new FileWriter(jackrabbitConfig.toFile()))

    // backup workspace.xml
    Path jcrWorkspaceConfig = jackrabbitHomePath.resolve(Path.of('workspaces', 'silverpeas', 'workspace.xml'))
    Path jcrWorkspaceConfigBackup = jackrabbitHomePath.resolve(Path.of('workspaces', 'silverpeas', 'workspace.xml.backup'))
    Files.copy(jcrWorkspaceConfig, jcrWorkspaceConfigBackup, StandardCopyOption.REPLACE_EXISTING)

    // update workspace.xml for an eventual migration to oak
    def xmlWorkspaceConf = new XmlSlurper(factory.newSAXParser()).parse(jcrWorkspaceConfig.toFile())
    xmlWorkspaceConf.PersistenceManager.param.find { it.@name == 'driver' }.@value = settings.DB_DRIVER
    xmlWorkspaceConf.PersistenceManager.param.find { it.@name == 'url' }.@value = jcrUrl
    xmlWorkspaceConf.PersistenceManager.appendNode {
      param(name: 'user', value: jcrUser)
    }
    xmlWorkspaceConf.PersistenceManager.appendNode {
      param(name: 'password', value: jcrPassword)
    }
    XmlUtil.serialize(xmlWorkspaceConf, new FileWriter(jcrWorkspaceConfig.toFile()))
  }

  // update the registry of JCR namespaces to replace deprecated empty entry
  Path namespacesPath = jackrabbitHomePath.resolve(Path.of('repository', 'namespaces', 'ns_reg.properties'))
  Properties namespaces = new Properties()
  namespaces.load(new FileInputStream(namespacesPath.toFile()))
  String emptyEntry = namespaces.getProperty('')
  if (emptyEntry?.isEmpty()) {
    namespaces.remove(emptyEntry)
    namespaces['.empty.key']=''
    namespaces.store(new FileOutputStream(namespacesPath.toFile()), null)
  }
}

/* creates the JCR home directory if it doesn't already exist and copies the JCR configuration file
* into this directory */
service.createDirectory(jcrHomePath, [readable: true, writable: true, executable: true])
if (!Files.exists(jcrConfigurationPath)) {
  Files.copy(jcrConfigurationTemplatePath, jcrConfigurationPath)
}


