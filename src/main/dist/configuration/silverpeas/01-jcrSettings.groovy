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
Path jackrabbitConfig = "${settings.JCR_HOME}/repository.xml".asPath()
Path jackrabbitConfigBackup = "${settings.JCR_HOME}/repository.xml.backup".asPath()
if (Files.exists(jackrabbitConfig)) {
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
    xmlRepositoryConf.Workspace.PersistenceManager.param.find { it.@name == 'url' }.@value = settings.JCR_URL.toString()
    xmlRepositoryConf.Workspace.PersistenceManager.appendNode {
      param(name: 'user', value: settings.JCR_USER)
    }
    xmlRepositoryConf.Workspace.PersistenceManager.appendNode {
      param(name: 'password', value: settings.JCR_PASSWORD)
    }
    xmlRepositoryConf.Versioning.PersistenceManager.param.find { it.@name == 'driver' }.@value = settings.DB_DRIVER
    xmlRepositoryConf.Versioning.PersistenceManager.param.find { it.@name == 'url' }.@value = settings.JCR_URL.toString()
    xmlRepositoryConf.Versioning.PersistenceManager.appendNode {
      param(name: 'user', value: settings.JCR_USER)
    }
    xmlRepositoryConf.Versioning.PersistenceManager.appendNode {
      param(name: 'password', value: settings.JCR_PASSWORD)
    }
    XmlUtil.serialize(xmlRepositoryConf, new FileWriter(jackrabbitConfig.toFile()))

    // backup workspace.xml
    Path jcrWorkspaceConfig = "${settings.JCR_HOME}/workspaces/silverpeas/workspace.xml".asPath()
    Path jcrWorkspaceConfigBackup = "${settings.JCR_HOME}/workspaces/silverpeas/workspace.xml.backup".asPath()
    Files.copy(jcrWorkspaceConfig, jcrWorkspaceConfigBackup, StandardCopyOption.REPLACE_EXISTING)

    // update workspace.xml for an eventual migration to oak
    def xmlWorkspaceConf = new XmlSlurper(factory.newSAXParser()).parse(jcrWorkspaceConfig.toFile())
    xmlWorkspaceConf.PersistenceManager.param.find { it.@name == 'driver' }.@value = settings.DB_DRIVER
    xmlWorkspaceConf.PersistenceManager.param.find { it.@name == 'url' }.@value = settings.JCR_URL.toString()
    xmlWorkspaceConf.PersistenceManager.appendNode {
      param(name: 'user', value: settings.JCR_USER)
    }
    xmlWorkspaceConf.PersistenceManager.appendNode {
      param(name: 'password', value: settings.JCR_PASSWORD)
    }
    XmlUtil.serialize(xmlWorkspaceConf, new FileWriter(jcrWorkspaceConfig.toFile()))
  }

  // update the registry of JCR namespaces to replace deprecated empty entry
  Path namespacesPath = "${settings.JCR_HOME}/repository/namespaces/ns_reg.properties".asPath()
  Properties namespaces = new Properties()
  namespaces.load(new FileInputStream(namespacesPath.toFile()))
  String emptyEntry = namespaces.getProperty('')
  if (emptyEntry?.isEmpty()) {
    namespaces.remove(emptyEntry)
    namespaces['.empty.key']=''
    namespaces.store(new FileOutputStream(namespacesPath.toFile()), null)
  }

  // now move the JCR home to a new location, id est rename the home directory to jackrabbit
  Files.move(jcrHomePath, Path.of(jcrHomePath.parent.toString(), 'jackrabbit'))
}

/* creates the JCR home directory if it doesn't already exist and copies the JCR configuration file
* into this directory */
service.createDirectory(jcrHomePath, [readable: true, writable: true, executable: true])
if (!Files.exists(jcrConfigurationPath)) {
  Files.copy(jcrConfigurationTemplatePath, jcrConfigurationPath)
}


