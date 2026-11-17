# Copyright (C) 2000 - 2026 Silverpeas
# 
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
# 
# As a special exception to the terms and conditions of version 3.0 of
# the GPL, you may redistribute this Program in connection with Free/Libre
# Open Source Software ("FLOSS") applications as described in Silverpeas's
# FLOSS exception.  You should have received a copy of the text describing
# the FLOSS exception, and it is also available here:
# "https://www.silverpeas.org/floss_exception.html"
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
# 
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.

"""Validate the installed logging profile in a disposable embedded WildFly.

Run: JBOSS_HOME=/path/to/wildfly python3 src/test/python/configuration_audit_logging_test.py
No running server or existing server configuration is changed.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


class ConfigurationAuditLoggingTest(unittest.TestCase):
  def test_dedicated_rotating_handler(self):
    home = Path(os.environ['JBOSS_HOME'])
    profile = Path(__file__).resolve().parents[2] / 'main/dist/configuration/jboss/silverpeas-logging-profile.cli'
    with tempfile.TemporaryDirectory(prefix='silverpeas-audit-') as temporary:
      base = Path(temporary)
      for folder in ['configuration', 'data', 'deployments', 'log', 'tmp']:
        (base / folder).mkdir()
      script = profile.read_text()
      for key, value in {'SILVERPEAS_LOG': str(base / 'log'),
                         'SILVERPEAS_LOG_BACKUP_MAX': '3',
                         'SILVERPEAS_LOG_BACKUP_SIZE': '10m',
                         'SILVERPEAS_LOGGING_LEVEL': 'WARNING'}.items():
        script = script.replace('${' + key + '}', value)
      commands = base / 'test.cli'
      commands.write_text(
          'embed-server --admin-only=false --server-config=audit-test.xml --empty-config --remove-existing\n'
          '/extension=org.jboss.as.logging:add\n'
          '/subsystem=logging:add\n'
          '/subsystem=logging/pattern-formatter=PATTERN:add(pattern="%s%n")\n'
          '/subsystem=logging/console-handler=CONSOLE:add\n' + script +
          '\nstop-embedded-server\n')
      env = dict(os.environ, JBOSS_BASE_DIR=str(base))
      # Pass the base directory to the CLI JVM and its embedded server, never the installation.
      env['JAVA_OPTS'] = f'-Djboss.server.base.dir={base} -Djboss.server.config.dir={base}/configuration'
      result = subprocess.run([str(home / 'bin/jboss-cli.sh'), '--file=' + str(commands)],
                              env=env, text=True, capture_output=True, timeout=90)
      self.assertEqual(0, result.returncode, result.stdout + result.stderr)
      root = ET.parse(base / 'configuration/audit-test.xml').getroot()
      def find(parent, tag_name, **attributes):
        return next(node for node in parent.iter() if node.tag.split('}')[-1] == tag_name
                    and all(node.get(key) == value for key, value in attributes.items()))
      logging = find(root, 'logging-profile', name='silverpeas')
      audit = find(logging, 'logger', category='silverpeas.core.admin.configuration.audit')
      self.assertEqual('false', audit.get('use-parent-handlers'))
      self.assertEqual('INFO', find(audit, 'level').get('name'))
      self.assertEqual('CONFIGURATION_AUDIT_LOG_FILE', find(audit, 'handler').get('name'))
      handler = find(logging, 'size-rotating-file-handler', name='CONFIGURATION_AUDIT_LOG_FILE')
      self.assertEqual('INFO', find(handler, 'level').get('name'))
      self.assertEqual(str(base / 'log/silverpeas-configuration.log'), find(handler, 'file').get('path'))
      self.assertEqual('3', find(handler, 'max-backup-index').get('value'))
      self.assertEqual('10m', find(handler, 'rotate-size').get('value'))
      self.assertEqual('true', handler.get('autoflush'))
      self.assertEqual('true', find(handler, 'append').get('value'))
      self.assertTrue((base / 'log/silverpeas-configuration.log').exists())


if __name__ == '__main__':
  unittest.main()
