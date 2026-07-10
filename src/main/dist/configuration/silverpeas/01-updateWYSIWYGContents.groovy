import org.apache.commons.text.StringEscapeUtils

import java.nio.file.Files
import java.nio.file.Path

/**
 * This script scans all the existing WYSIWYGs to replace all HTML-encoded accentuated and special
 * characters into their UTF-8 counterparts. The HTML entities required to keep the markup valid
 * (&amp;, &lt;, &gt;, &quot;, &apos; and &nbsp;) are left untouched.
 * @author mmoquillon
 */

// The HTML entities that must remain encoded so as not to alter the HTML markup and its structure.
final Set<String> preserved = ['amp', 'lt', 'gt', 'quot', 'apos', 'nbsp',
                               '#38', '#60', '#62', '#34', '#39', '#160',
                               '#x26', '#x3c', '#x3e', '#x22', '#x27', '#xa0'] as Set

// An HTML entity: either named (&eacute;), decimal (&#233;) or hexadecimal (&#xE9;).
final def htmlEntity = ~/&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z][a-zA-Z0-9]+);/

Path workspacePath = "${settings.SILVERPEAS_DATA_HOME}/workspaces".asPath()
final String myContext = 'HTML-encoded accentuated characters'
if (Files.exists(workspacePath) && Files.isDirectory(workspacePath) &&
        settings.context[myContext] != 'done') {
    log.info 'Replace all HTML-encoded accentuated characters in existing WYSIWYG contents'
    workspacePath.toFile().eachDirRecurse { dir ->
        dir.eachFileMatch(~/.*wysiwyg.*.txt/) { wysiwyg ->
            String content = wysiwyg.getText('UTF-8')
            String utfContent = content.replaceAll(htmlEntity) { String match, String entity ->
                preserved.contains(entity.toLowerCase()) ? match : StringEscapeUtils.unescapeHtml4(match)
            }
            if (utfContent != content) {
                log.info " -> HTML-encoded accentuated characters replaced by their UTF-8 " +
                        "counterparts in ${wysiwyg.path}"
                wysiwyg.write(utfContent, 'UTF-8')
            }
        }
    }
    settings.context[myContext] = 'done'
}
