import os
import unittest
from unittest.mock import Mock, patch

from app.services.callback_service import CallbackNotifier


class CallbackServiceTest(unittest.TestCase):
    @patch.dict(os.environ, {}, clear=True)
    def test_missing_token_prevents_callback_configuration(self):
        with self.assertRaisesRegex(ValueError, "CONTRACT_IMPORT_TOKEN"):
            CallbackNotifier("http://127.0.0.1:18081/api/contracts/process")

    @patch.dict(os.environ, {"CONTRACT_IMPORT_TOKEN": "test-only-token-not-a-secret-12345"}, clear=True)
    @patch("app.services.callback_service.requests.post")
    def test_import_uses_private_header_and_does_not_follow_redirects(self, post):
        post.return_value = Mock(status_code=200)
        notifier = CallbackNotifier("http://127.0.0.1:18081/api/contracts/process")
        self.assertTrue(notifier.notify("/shared/contracts/batch.parquet", 1))
        _, kwargs = post.call_args
        self.assertEqual(kwargs["headers"]["X-Contract-Import-Token"], os.environ["CONTRACT_IMPORT_TOKEN"])
        self.assertFalse(kwargs["allow_redirects"])
        self.assertEqual(kwargs["json"]["filePath"], "/shared/contracts/batch.parquet")

    @patch.dict(os.environ, {"CONTRACT_IMPORT_TOKEN": "test-only-token-not-a-secret-12345"}, clear=True)
    @patch("app.services.callback_service.requests.post")
    def test_redirect_is_failure_without_logging_response_body(self, post):
        post.return_value = Mock(status_code=302, text="private-response-content")
        notifier = CallbackNotifier("http://127.0.0.1:18081/api/contracts/process")
        with self.assertLogs("app.services.callback_service", level="ERROR") as logs:
            self.assertFalse(notifier.notify("/shared/contracts/batch.parquet", 0))
        self.assertNotIn("private-response-content", " ".join(logs.output))


if __name__ == "__main__":
    unittest.main()
