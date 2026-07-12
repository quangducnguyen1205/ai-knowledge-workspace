import importlib.util
import unittest
from pathlib import Path


VALIDATOR_PATH = Path(__file__).resolve().parents[1] / "validate-kafka-runtime-config.py"
SPEC = importlib.util.spec_from_file_location("validate_kafka_runtime_config", VALIDATOR_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Kafka runtime validator module could not be loaded")
validator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validator)


def valid_config() -> dict:
    return {
        "services": {
            "kafka": {
                "image": "apache/kafka:4.0.2",
                "restart": "unless-stopped",
                "mem_reservation": 512 * 1024**2,
                "mem_limit": 1024 * 1024**2,
                "environment": {"KAFKA_HEAP_OPTS": "-Xms256m -Xmx512m"},
                "volumes": [
                    {
                        "type": "volume",
                        "source": "workspace_core_kafka_data",
                        "target": "/tmp/kraft-combined-logs",
                    }
                ],
                "healthcheck": {"test": ["CMD-SHELL", "kafka health check"]},
            }
        }
    }


class KafkaRuntimeConfigValidatorTest(unittest.TestCase):
    def assert_error_contains(self, config: dict, expected: str) -> None:
        self.assertTrue(
            any(expected in error for error in validator.validate_compose_config(config)),
            msg=f"expected validation error containing {expected!r}",
        )

    def test_accepts_selected_local_baseline(self) -> None:
        self.assertEqual(validator.validate_compose_config(valid_config()), [])

    def test_rejects_missing_restart_policy(self) -> None:
        config = valid_config()
        del config["services"]["kafka"]["restart"]
        self.assert_error_contains(config, "restart policy is missing")

    def test_rejects_restart_policy_no(self) -> None:
        config = valid_config()
        config["services"]["kafka"]["restart"] = "no"
        self.assert_error_contains(config, "must not be 'no'")

    def test_rejects_missing_memory_limit(self) -> None:
        config = valid_config()
        del config["services"]["kafka"]["mem_limit"]
        self.assert_error_contains(config, "memory limit")

    def test_rejects_reservation_larger_than_limit(self) -> None:
        config = valid_config()
        config["services"]["kafka"]["mem_reservation"] = 2 * 1024**3
        self.assert_error_contains(config, "reservation must not exceed")

    def test_rejects_missing_heap_setting(self) -> None:
        config = valid_config()
        del config["services"]["kafka"]["environment"]["KAFKA_HEAP_OPTS"]
        self.assert_error_contains(config, "exactly one -Xms and one -Xmx")

    def test_rejects_xms_larger_than_xmx(self) -> None:
        config = valid_config()
        config["services"]["kafka"]["environment"]["KAFKA_HEAP_OPTS"] = "-Xms768m -Xmx512m"
        self.assert_error_contains(config, "-Xms must not exceed -Xmx")

    def test_rejects_xmx_larger_than_container_limit(self) -> None:
        config = valid_config()
        config["services"]["kafka"]["environment"]["KAFKA_HEAP_OPTS"] = "-Xms256m -Xmx2g"
        self.assert_error_contains(config, "-Xmx must not exceed")

    def test_rejects_missing_persistent_data_mount(self) -> None:
        config = valid_config()
        config["services"]["kafka"]["volumes"] = []
        self.assert_error_contains(config, "persistent data volume")

    def test_rejects_missing_health_check(self) -> None:
        config = valid_config()
        del config["services"]["kafka"]["healthcheck"]
        self.assert_error_contains(config, "health check is missing")


if __name__ == "__main__":
    unittest.main()
