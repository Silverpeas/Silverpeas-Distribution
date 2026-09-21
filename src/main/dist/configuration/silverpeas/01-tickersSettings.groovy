import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * This script generates the tickers from sample files.
 * @neysseric
 */

log.info 'Generate the tickers from samples'
Path tickerHome = "${settings.SILVERPEAS_DATA_HOME}/web/weblib.war/ticker".asPath()
if (Files.exists(tickerHome) && Files.isDirectory(tickerHome)) {
  tickerHome.toFile().eachFileMatch(~/sample_.*/) {
    Path aTicker = tickerHome.resolve(it.name.replaceAll('sample_', ''))
    if (!Files.exists(aTicker)) {
      log.info " -> ${aTicker.fileName.toString()}"
      Files.move(Paths.get(it.path), aTicker)
    }
  }
}
