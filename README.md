# Silverpeas-Distribution

A project to create a distribution of [Silverpeas](https://www.silverpeas.org). A distribution of 
Silvepeas is an archive made up of an installer, a configurator, a runner and some configuration 
files to setup both Silverpeas and the current supported JEE application server (currently 
[Wildfly](https://www.wildfly.org/)).
The distribution is built upon the [Gradle build platform](https://gradle.org/).

The installer and configurator tools are both defined and carried by the custom Gradle plugin 
[silversetup](https://github.com/Silverpeas/Silverpeas-Setup) whereas the runner is a script that
decorates the installer and configurator with tasks to run and stop Silverpeas. By default, the
distribution contains only the necessary to download and apply the tools to install, configure and
run Silverpeas. All is wrapped into the executable `silverpeas` for Unix and `silverpeas.bat` for 
Windows. At its first execution, the tools are downloaded from our Nexus software repository, cached,
and then applied to perform the asked tasks. Among those tasks, the installation of Silverpeas will
download all the software components that made a Silverpeas Collaborative Platform, cache them, 
unpack them to recombine them into a single Web application archive that will then be deployed
into a Wildfly JEE application server.

Currently, only Wildfly >= 8 is supported.

To build and push the distribution onto our server, please use the `build.sh` script as this script will sign also the archive before pushing the GPG signature onto the server.
