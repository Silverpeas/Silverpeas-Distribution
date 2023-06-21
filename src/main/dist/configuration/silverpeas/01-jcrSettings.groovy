import groovy.xml.XmlUtil

import javax.xml.parsers.SAXParserFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import org.apache.jackrabbit.commons.cnd.CndImporter
import org.apache.jackrabbit.oak.plugins.index.IndexUtils
import org.apache.jackrabbit.oak.spi.state.NodeBuilder

/**
 * The JCR is now provided by Apache Jackrabbit Oak and not anymore by Apache Jackrabbit 2.
 * Apache Jackrabbit Oak is a more suitable solution for enterprise class environment. Two
 * storage backends are available for the JCR with Apache Jackrabbit Oak: either the filesystem for
 * vertical high-scalability or MongoDB for horizontal high-scalability.
 * <p>
 * This script setups the JCR repository configuration file according to the configuration properties
 * defined in the template SILVERPEAS_HOME/silverpeas/resources/silverpeas-oak.properties. If this is
 * a first installation of Silverpeas, to custom the configuration of the JCR, mainly the storage to use
 * by Oak, please update the template before any execution of the silverpeas configuration task because the
 * index definitions are created during the configuration phase of the Silverpeas installation.
 * <p>
 * This script takes in charge three contexts of the installation: a first installation, an upgrade from a previous
 * version in which the JCR was managed by Apache Jackrabbit 2, and an upgrade from a previous version in which the
 * JCR is already managed by Apache Jackrabbit Oak:
 * <ul>
 * <li>
 * In the case of a first installation, the JCR is created in the JCR home directory and the indexation is initialized
 * from the index configuration file SILVERPEAS_HOME/silverpeas/resources/silverpeas-oak-index.properties.
 * <li>
 * If a previous JCR managed by Jackrabbit 2 is found, the JCR home directory is before renamed to jackrabbit
 * and then the repository.xml and workspace.xml configuration files are updated for an eventual migration
 * to Oak. Don't forget to migrate the JCR content from Apache Jackrabbit 2 to the Apache Jackrabbit Oak
 * before starting Silverpeas in order to retrieve your data. Unlike a first installation, the indexation is
 * initialized by the migration process itself. For doing, please contact the Silverpeas team.
 * <li>
 * If the JCR is already managed by Oak, then the index definitions is updated from the index configuration file
 * SILVERPEAS_HOME/silverpeas/resources/silverpeas-oak.properties. In the case there are some new index definitions,
 * it is required to use the tool oak-run to reindex all the content of the JCR according to the new index definitions.
 * </ul>
 * @author mmoquillon
 */

// method to create a definition of an index on the given properties of JCR nodes
void createIndexDefinitionOnProperty(NodeBuilder indexRoot, String indexName, String... propertyName) {
  if (!indexRoot.hasChildNode(indexName)) {
    // additional security to ensure we don't create again the same index definitions
    String prop = propertyName.length > 1 ? 'properties' : 'property'
    log.info "Create index ${indexName} on ${prop} ${String.join(' and ', propertyName)}"
    IndexUtils.createIndexDefinition(indexRoot, indexName, true, false,
        Arrays.asList(propertyName),
        List.of('slv:simpleDocument'));
  }
}

void createWholeIndexDefinitions(Map jcrParams) {
  Path jcrIndexDefinitionsPath =
      "${settings.CONFIGURATION_HOME}/silverpeas/resources/silverpeas-oak-index.properties".asPath()
  Properties indexDefinitions = new Properties()
  indexDefinitions.load(Files.newBufferedReader(jcrIndexDefinitionsPath))
  jcr.performOnNodeRootState(jcrParams, { root ->
    NodeBuilder index = IndexUtils.getOrCreateOakIndex(root);
    indexDefinitions.each {definition ->
      String[] np = definition.value.split(' ').collect { it.trim() }
      createIndexDefinitionOnProperty(index, definition.key, np);
    }
  })
}

log.info 'Configure the JCR repository...'

Path jcrHomePath = settings.JCR_HOME.asPath()
Path jcrConfigurationPath = "${settings.JCR_HOME}/silverpeas-oak.properties".asPath()
Path jcrConfigurationTemplatePath = "${settings.CONFIGURATION_HOME}/silverpeas/resources/silverpeas-oak.properties".asPath()
Map jcrParams = ['JCR_HOME': jcrHomePath.toString()]

/* case of a jcr home directory whose the repository is managed by jackrabbit */
Path jcrConfig = "${settings.JCR_HOME}/repository.xml".asPath()
if (Files.exists(jcrConfig)) {
  Path jackrabbitHomePath = Path.of(jcrHomePath.parent.toString(), 'jackrabbit')
  log.info "Rename the Jackrabbit 2 home directory ${jcrHomePath.toString()} to ${jackrabbitHomePath.toString()}..."

  Files.move(jcrHomePath, Path.of(jcrHomePath.parent.toString(), 'jackrabbit'))

  log.info 'Backup the Jackrabbit 2 configuration files and update them for the JCR migration to Oak...'

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

  // create the JCR home directory and puts into it the JCR configuration file
  log.info 'Create the JCR home directory for Oak and the JCR configuration file from the template...'
  service.createDirectory(jcrHomePath, [readable: true, writable: true, executable: true])
  Files.copy(jcrConfigurationTemplatePath, jcrConfigurationPath)

} /* case of a first installation of Silverpeas */
else if (! Files.exists(jcrConfigurationPath)) {
  // create the JCR home directory and puts into it the JCR configuration file
  log.info 'Create the JCR home directory and the JCR configuration file from the template...'
  service.createDirectory(jcrHomePath, [readable: true, writable: true, executable: true])
  Files.copy(jcrConfigurationTemplatePath, jcrConfigurationPath)

  // create the JCR schema for Silverpeas
  log.info 'Register the Silverpeas schema into the JCR...'
  Path jcrSilverpeasSchemaPath = "${settings.CONFIGURATION_HOME}/silverpeas/resources/silverpeas-jcr.cnd".asPath()
  Files.newBufferedReader(jcrSilverpeasSchemaPath).withCloseable { reader ->
    jcr.openSession(jcrParams).withCloseable { session ->
      CndImporter.registerNodeTypes(reader, session);
    }
  }

  log.info 'Create the index definitions for Silverpeas into the JCR...'
  createWholeIndexDefinitions(jcrParams)
} else {
  createWholeIndexDefinitions(jcrParams)
}


