import os
import unittest
from unittest.mock import patch

from app.services.task_service import ContractFetchTask


class SecretConfigTest(unittest.TestCase):
    @patch.dict(os.environ, {}, clear=True)
    def test_required_env_fails_without_secret(self):
        with self.assertRaisesRegex(
            ValueError,
            "Required environment variable is missing: DUNE_API_KEY",
        ):
            ContractFetchTask._required_env("DUNE_API_KEY")

    @patch.dict(os.environ, {"DUNE_API_KEY": " test-dune-key "}, clear=True)
    def test_required_env_trims_value(self):
        self.assertEqual(
            "test-dune-key",
            ContractFetchTask._required_env("DUNE_API_KEY"),
        )

    @patch.dict(
        os.environ,
        {"ETHERSCAN_API_KEY": " key-one, key-two ,, "},
        clear=True,
    )
    @patch("app.services.task_service.init_api_key_pool")
    def test_etherscan_keys_are_loaded_from_csv_environment(self, init_pool):
        task = ContractFetchTask.__new__(ContractFetchTask)
        task._init_etherscan()
        init_pool.assert_called_once_with(["key-one", "key-two"])


if __name__ == "__main__":
    unittest.main()
