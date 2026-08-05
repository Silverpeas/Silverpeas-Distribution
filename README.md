# Silverpeas-Distribution

**Silverpeas-Distribution** produces the official distribution of
[Silverpeas](https://www.silverpeas.org), the Free and Open Source Collaborative Platform.

A distribution of Silverpeas isn't the platform itself: it is a small archive made up of an
*installer*, a *configurator*, a *runner* and a set of configuration files whose purpose is to
fetch, to assemble, to set up and to run both Silverpeas and the Jakarta EE application server 
hosting it
(currently [Wildfly](https://www.wildfly.org/)).

Everything is driven by the [Gradle build platform](https://gradle.org/) and wrapped into a single
executable: `silverpeas` for Unix-like systems and `silverpeas.bat` for Windows.

## Table of contents

* [How does it work?](#how-does-it-work)
* [Requirements](#requirements)
* [Content of the distribution archive](#content-of-the-distribution-archive)
* [Installing and running Silverpeas](#installing-and-running-silverpeas)
* [Available commands](#available-commands)
* [Configuration](#configuration)
* [Customizing a Silverpeas installation](#customizing-a-silverpeas-installation)
* [Logs](#logs)
* [Development mode](#development-mode)
* [Building the distribution](#building-the-distribution)
* [Source layout](#source-layout)
* [Releasing](#releasing)
* [Related projects](#related-projects)
* [License](#license)

## How does it work?

The distribution ships almost nothing but the ability to get what it needs, when it needs it:

1. **Bootstrap.** The `silverpeas` script is a Gradle wrapper. At its first execution it downloads
   the Gradle distribution declared in `bin/gradle/wrapper/gradle-wrapper.properties` and caches it
   in the Gradle user home.
2. **Tooling.** The installer and the configurator are both carried by
   [silversetup](https://github.com/Silverpeas/Silverpeas-Setup), a custom standalone Gradle plugin.
   It is resolved from the local Maven repository, then from the
   [Silverpeas Nexus](https://nexus3.silverpeas.org/repository/silverpeas), and cached
   (see `bin/settings.gradle`).
3. **Runner.** `bin/build.gradle` is the runner: it decorates the `silversetup` tasks with the tasks
   required to start, to debug, to stop, to redeploy and to get the status of Silverpeas.
4. **Construction.** The `install` command downloads all the software bundles that make up
   Silverpeas (declared in `bin/silverpeas.gradle`, the `silverpeas-assembly` artifact by default),
   caches them, unpacks them and recombines their content into a single web application archive,
   `silverpeas.war`.
5. **Configuration.** Wildfly is configured by applying the JBoss CLI and Groovy scripts of
   `configuration/jboss`, then Silverpeas itself is configured by applying the XML and Groovy
   scripts of `configuration/silverpeas`. Both use the settings of
   `configuration/config.properties`.
6. **Migration.** The data source schema is created or migrated to the version being installed.
7. **Deployment.** `silverpeas.war` and the other artifacts are deployed into Wildfly.

Each step checks the current state before acting: the bundles already extracted aren't extracted
again, a configuration script already applied isn't applied again, and a migration already performed
isn't replayed. Re-running `install` therefore only does what remains to be done.

## Requirements

| Requirement | Version / Notes |
|---|---|
| Java | JDK 17 (Jakarta EE 10 / Wildfly 34 baseline) |
| JEE application server | Wildfly 34 in `standalone` mode (the `domain` mode isn't supported) |
| Database | PostgreSQL, Microsoft SQL Server, Oracle or H2 |
| Network access | The Silverpeas Nexus, the Gradle distribution service and the Maven Central repository, unless everything is already cached |

Optionally:

* an **SMTP server**, for the notifications sent by email;
* a **document conversion service** (LibreOffice running as a daemon), for the preview and the
  conversion of the documents.

## Content of the distribution archive

The distribution is a ZIP archive named `silverpeas-<version>-<server.distribution>.zip` (for
example `silverpeas-6.5-wildfly34.zip`). Once unpacked, the resulting directory is the
`SILVERPEAS_HOME` directory:

```
silverpeas-<version>-<server>/
├── BUILD                          # build version, timestamp and Git commit of the distribution
├── bin/
│   ├── silverpeas                 # the launcher for Unix-like systems
│   ├── silverpeas.bat             # the launcher for Windows
│   ├── settings.gradle            # where to find the silversetup plugin
│   ├── build.gradle               # the runner: start/stop/status/debug/redeploy/reload/clean tasks
│   ├── silverpeas.gradle          # the software bundles making up this Silverpeas installation
│   ├── gradle/wrapper/            # the Gradle wrapper
│   └── lib/                       # drop here the local jars (JDBC drivers, custom libraries)
├── configuration/
│   ├── sample_config.properties   # the documented template of the global configuration
│   ├── jboss/                     # the configuration scripts of Wildfly (*.cli, *.groovy)
│   │   └── modules/               # the additional JBoss modules to install into Wildfly
│   └── silverpeas/                # the configuration scripts of Silverpeas (*.xml, *.groovy)
│       └── resources/             # the resources used by those scripts (JCR schema, indexes, ...)
└── log/                           # where the installation and execution logs are written
```

Once Silverpeas has been installed, some additional directories are generated within
`SILVERPEAS_HOME`, among them `data` (the users' data), `deployments` (the artifacts to deploy into
Wildfly), `properties`, `xmlcomponents`, `resources`, `migrations` and `build` (the working
directory of the installer).

## Installing and running Silverpeas

1. Install a JDK 17 and a Wildfly 34, and set up the database to be used by Silverpeas.
2. Unpack the distribution archive wherever you want.
3. Set the two required environment variables:
   ```bash
   $ export SILVERPEAS_HOME=/path/to/silverpeas-<version>-<server>
   $ export JBOSS_HOME=/path/to/wildfly-34.x.x.Final
   ```
   Both are checked by `silversetup`; the installation fails fast if one of them isn't set or
   doesn't refer to an existing directory. (`SILVERPEAS_HOME` is computed by the launcher itself,
   but exporting it explicitly is safer.)
4. Create the global configuration file from its documented template and edit it:
   ```bash
   $ cp $SILVERPEAS_HOME/configuration/sample_config.properties \
        $SILVERPEAS_HOME/configuration/config.properties
   ```
   Every property is commented out and documented in the template: uncomment and value only the
   ones you want to override. At least the database access, the administrator email address and the
   sender email address should be set.
5. If your database isn't H2 or PostgreSQL, drop its JDBC driver into `$SILVERPEAS_HOME/bin/lib`
   (this is required for Oracle, whose driver cannot be distributed through a Maven repository for
   licensing reasons).
6. Install Silverpeas, then run it:
   ```bash
   $ cd $SILVERPEAS_HOME/bin
   $ ./silverpeas install
   $ ./silverpeas start
   ```
7. Silverpeas is then available at the URL declared by `SERVER_URL` (`http://localhost:8000` by
   default).

To move an existing installation to a newer version, unpack the new distribution over the current
`SILVERPEAS_HOME` and check `configuration/config.properties` against the new
`sample_config.properties`. Then either run `./silverpeas install` again, which performs only the
steps that are required by the new version, or `./silverpeas upgrade`, which first cleans the
artifacts of the previous build before installing the new version from scratch.

## Available commands

All the commands are Gradle tasks and are invoked as `./silverpeas <command>` (or
`silverpeas.bat <command>` on Windows). Use `./silverpeas tasks` to list them all.

Tasks provided by the **silversetup** plugin (the installer and configurator):

| Command | Description |
|---|---|
| `assemble` | Downloads and extracts all the software bundles making up Silverpeas |
| `build` | Generates the Silverpeas web application from the extracted bundles |
| `construct` | `assemble` then `build` |
| `configure_jboss` | Configures Wildfly for Silverpeas |
| `configure_silverpeas` | Configures Silverpeas itself |
| `configure` | Configures both Wildfly and Silverpeas |
| `migrate` | Creates or migrates the data source schema to the current version |
| `install` | The full chain: `construct`, `configure`, `migrate` and deployment into Wildfly |
| `upgrade` | `clean` then `install`, to upgrade an existing installation |

Tasks provided by the **runner** (`bin/build.gradle`):

| Command | Description |
|---|---|
| `start` | Starts Silverpeas (that is, starts Wildfly with Silverpeas deployed) |
| `stop` | Stops Silverpeas |
| `debug` | Starts Silverpeas in debug mode; the port is given by `-Pport=<port>` (5005 by default) |
| `status` | Tells whether Silverpeas is configured, running and active |
| `redeploy` | Undeploys then deploys again all the artifacts in `SILVERPEAS_HOME/deployments` |
| `reload` | Rebuilds and redeploys `silverpeas.war` in a running Wildfly (development mode only) |
| `clean` | Stops Silverpeas if needed and removes all the artifacts produced by a previous build |

## Configuration

### The global configuration file

`configuration/config.properties` is the single entry point for the settings of an installation. It
overrides the default settings embedded within the `silversetup` plugin, and its properties are then
expanded by the configuration scripts into the properties files of both Silverpeas and Wildfly.
`configuration/sample_config.properties` documents every supported property, grouped by concern:

* the location of the system directories (data, temporary files, logs);
* the credentials and the email address of the Silverpeas administrator;
* the global settings of the platform (languages, logging level, log rotation);
* the sizing and the options of the JVM;
* the application server (URL, HTTP port, execution mode, starting timeout, reverse-proxy
  awareness, availability of the management console);
* the database (type, host, port, credentials, connection pools, transaction timeout);
* the JCR (the Java Content Repository storing the metadata of the documents);
* the document conversion service;
* the SMTP server;
* the HTTP(S) proxy.

### The configuration scripts

The configuration is performed by applying, in the lexicographical order of their name, the scripts
found in the two dedicated directories:

* `configuration/jboss` — JBoss CLI scripts (`*.cli`) and Groovy scripts (`*.groovy`) applied to
  Wildfly: the datasource of Silverpeas, the JMS queues and topics, the HTTP port, the transaction
  timeout, the deployment timeout, the maximum size of an HTTP request, the logging profile and the
  log rotation, the removal of the unused Wildfly extensions, the hardening of the HTTP response
  headers, the reverse-proxy awareness, and so on. Its `modules` subdirectory contains the
  additional JBoss modules copied into `JBOSS_HOME/modules` (for instance `org.mnode.ical4j`, which
  requires its own class loader so as not to interfere with Silverpeas).
* `configuration/silverpeas` — the descriptor `00-SilverpeasSettings.xml`, which values the
  properties files of Silverpeas from `config.properties`, followed by the numbered Groovy scripts
  performing the tasks that cannot be expressed declaratively: initialization and migration of the
  JCR, the JDBC dialect of the scheduler, the access to the conversion service, the generation of
  the tickers, and the one-shot fixes applied to the existing contents.

The state of the configuration is persisted into the hidden file `configuration/.context`, so a
script that has already been applied isn't applied again needlessly.

## Customizing a Silverpeas installation

`bin/silverpeas.gradle` declares the version of Silverpeas to install and the software bundles that
make it up, through three dependency configurations:

| Configuration | Purpose |
|---|---|
| `silverpeas` | The standard Silverpeas modules (a war and, optionally, a configuration archive). By default, the single `org.silverpeas:silverpeas-assembly` artifact |
| `custom` | The custom bundles adding specific functionalities to Silverpeas (custom workflows, specific behaviours, ...) |
| `library` | The third-party jars to include into Silverpeas, and the JDBC drivers. Fed by default from `bin/lib` |

Any jar dropped into `bin/lib` is picked up by the `library` configuration. If it is recognized as a
JDBC driver, it is deployed into Wildfly and wired to the datasource of Silverpeas; otherwise it is
added to the libraries of the Silverpeas web application.

## Logs

The `log` directory of `SILVERPEAS_HOME` gathers:

* `build-<timestamp>.log` — the verbose traces of the construction, configuration and migration of
  Silverpeas. **This is the file to look at when an installation fails.**
* `jboss-cli-output.log` — the output of the JBoss CLI scripts applied to Wildfly.
* `jboss_output.log` — the standard output of the running Wildfly/Silverpeas.

The application logs of Silverpeas itself are written where `SILVERPEAS_LOG` points to.

## Development mode

The development mode is meant for the developers of Silverpeas and **must not be used in
production**. It is enabled through environment variables:

| Variable | Effect |
|---|---|
| `SILVERPEAS_DEV_MODE` | `true` to enable the development mode |
| `SILVERPEAS_DIST_DIR` | The directory into which the Silverpeas web application is exploded, instead of the default working directory |

In this mode the JSPs are recompiled on the fly and the `reload` task becomes available: it
disassembles the bundles again, regenerates the web application and redeploys it into the running
Wildfly, without any restart.

## Building the distribution

The build of the distribution archive itself is handled by Maven (the Gradle scripts it contains
are only executed at installation time, on the user's machine):

```bash
$ mvn clean package
```

produces `target/silverpeas-<version>-<server.distribution>.zip` from the assembly descriptor
`src/main/resources/assembly.xml`.

Some noteworthy points of the build:

* the `server.distribution` property of the POM (`wildfly34`) is part of the name of the archive and
  states the targeted application server;
* `BUILD`, `bin/settings.gradle` and `bin/silverpeas.gradle` are filtered by Maven, so that the
  version of the project is injected into them. Because the escape string of the filtering is `''`,
  a `${...}` expression prefixed by `''` is left untouched and remains a Gradle expression evaluated
  at installation time;
* `bin/silverpeas` is made executable (mode 755) within the archive.

To sign the archive and publish it onto the Silverpeas download server:

```bash
$ mvn clean install -Dgpg.keyname=GPG_KEY -Dgpg.passphrase=GPG_PASSPHRASE -Dscp.user=SCP_USER
```

The `verify` phase signs the archive with GPG (producing a detached `.asc` signature) and the
`install` phase uploads both the archive and its signature onto `www.silverpeas.org` over SCP.

## Source layout

```
.
├── pom.xml                        # the Maven build of the distribution archive
├── README.md
├── license.txt                    # the GNU AGPL v3
├── exceptions.txt                 # the Silverpeas FLOSS License Exception
└── src/
    ├── doc/RELEASE.md             # the release process of the whole Silverpeas platform
    ├── main/resources/
    │   └── assembly.xml           # the descriptor of the distribution archive
    └── main/dist/                 # the very content of the distribution archive
        ├── BUILD
        ├── bin/
        ├── configuration/
        └── log/
```

## Releasing

Silverpeas-Distribution belongs to the set of projects made up of `Silverpeas-Core`,
`Silverpeas-Components`, `Silverpeas-Assembly`, `Silverpeas-Setup` and itself: any change in this
set implies a release of the whole set at the same version, and this project is always the last one
to be released.

The complete and ordered release process of the platform, along with the different release modes
(Jenkins pipeline, Maven Release plugin, by hand), is described in
[src/doc/RELEASE.md](src/doc/RELEASE.md).

## Related projects

| Project | Purpose |
|---|---|
| [Silverpeas-Core](https://github.com/Silverpeas/Silverpeas-Core) | The core of the Silverpeas platform |
| [Silverpeas-Components](https://github.com/Silverpeas/Silverpeas-Components) | The applications of the platform |
| [Silverpeas-Assembly](https://github.com/Silverpeas/Silverpeas-Assembly) | The assembly of the core and of the components into the software bundles fetched by this distribution |
| [Silverpeas-Setup](https://github.com/Silverpeas/Silverpeas-Setup) | The `silversetup` Gradle plugin: the installer and the configurator |
| [Silverpeas-Looks](https://github.com/Silverpeas/Silverpeas-Looks) | The graphical looks of the platform |
| [Silverpeas-Project](https://github.com/Silverpeas/Silverpeas-Project) | The parent POM of the Maven projects of the platform |
| [Jenkins-Pipelines](https://github.com/Silverpeas/Jenkins-Pipelines) | The CI/CD pipelines of the platform |

## License

Silverpeas is free software: you can redistribute it and/or modify it under the terms of the
[GNU Affero General Public License](license.txt) as published by the Free Software Foundation,
either version 3 of the License, or (at your option) any later version.

As a special exception to the terms and conditions of version 3.0 of the AGPL, the
[Silverpeas FLOSS License Exception](exceptions.txt) applies; it is also available
[online](https://www.silverpeas.org/floss_exception.html).
