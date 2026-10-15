import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/org/opencharge/teslable'
class MqttSettingsFeedbackTests(unittest.TestCase):
 def test_saved_username_has_a_safe_ui_accessor(self):
  self.assertIn('getUsernameForUi()', (JAVA/'MqttConfigStore.java').read_text())
 def test_password_marker_is_hint_not_stored_secret_or_sentinel(self):
  source=(JAVA/'MqttSettingsActivity.java').read_text()
  self.assertIn('usernameField.setText(store.getUsernameForUi())',source)
  self.assertIn('"******** (saved',source)
  self.assertNotIn('usernameField.setText("")',source)
  self.assertIn('passwordField.setSaveEnabled(false)',source)
 def test_invalid_settings_have_field_specific_feedback(self):
  source=(JAVA/'MqttSettingsActivity.java').read_text()
  self.assertIn('store.saveWithResult(',source)
  self.assertIn('.setError(',source)
  self.assertIn('result.getMessage()',source)
