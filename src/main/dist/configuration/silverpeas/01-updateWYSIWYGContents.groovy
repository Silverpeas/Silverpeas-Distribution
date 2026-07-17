import org.apache.commons.text.StringEscapeUtils

import java.nio.file.Files
import java.nio.file.Path

/**
 * This script scans all the existing WYSIWYG contents to replace all HTML-encoded accentuated and
 * special characters into their UTF-8 counterparts. Two kinds of WYSIWYG contents are handled: the
 * rich text of the contributions (files matching *wysiwyg*.txt) and the content of the WYSIWYG form
 * fields (any file within an xmlWysiwyg directory). The HTML entities required to keep the markup
 * valid (&amp;, &lt;, &gt;, &quot;, &apos; and &nbsp;) are left untouched.
 * @author mmoquillon
 */

// The HTML entities that must remain encoded so as not to alter the HTML markup and its structure.
final Set<String> preserved = ['amp', 'lt', 'gt', 'quot', 'apos', 'nbsp',
                               '#38', '#60', '#62', '#34', '#39', '#160',
                               '#x26', '#x3c', '#x3e', '#x22', '#x27', '#xa0'] as Set

// An HTML entity: either named (&eacute;), decimal (&#233;) or hexadecimal (&#xE9;).
final def htmlEntity = ~/&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z][a-zA-Z0-9]+);/

// Replaces in the specified WYSIWYG content file all the HTML-encoded accentuated and special
// characters by their UTF-8 counterpart, but for the entities required to keep the markup valid.
def convertWysiwyg = { File wysiwyg ->
    log.info " -> Found WYSIWYG file: ${wysiwyg.path}"
    String content = wysiwyg.getText('UTF-8')
    String utfContent = content.replaceAll(htmlEntity) { String match, String entity ->
        preserved.contains(entity.toLowerCase()) ? match : StringEscapeUtils.unescapeHtml4(match)
    }
    if (utfContent != content) {
        log.info "    HTML-encoded accentuated characters replaced by their UTF-8 counterparts"
        wysiwyg.write(utfContent, 'UTF-8')
    } else {
        log.info "    No HTML-encoded accentuated characters"
    }
}

Path workspacePath = "${settings.SILVERPEAS_DATA_HOME}/workspaces".asPath()
final String myContext = 'HTML-encoded accentuated characters'
if (Files.exists(workspacePath) && Files.isDirectory(workspacePath) &&
        settings.context[myContext] != 'done') {
    log.info 'Replace all HTML-encoded accentuated characters in existing WYSIWYG contents'
    workspacePath.toFile().eachDirRecurse { dir ->
        if (dir.name == 'xmlWysiwyg') {
            // WYSIWYG form fields: every file in such a directory is a WYSIWYG content.
            dir.eachFile { file -> if (file.isFile()) convertWysiwyg(file) }
        } else {
            // Rich text of the contributions: files named like <id>wysiwyg<lang>.txt.
            dir.eachFileMatch(~/.*wysiwyg.*.txt/) { convertWysiwyg(it) }
        }
    }
    settings.context[myContext] = 'done'
}
