#!/usr/bin/env python3

import json
import re
import sys
from typing import Any


KAFKA_SERVICE = "kafka"
EXPECTED_IMAGE_PATTERN = re.compile(r"^(?:docker\.io/)?apache/kafka(?::|@|$)")
EXPECTED_VOLUME_SOURCE = "workspace_core_kafka_data"
EXPECTED_VOLUME_TARGET = "/tmp/kraft-combined-logs"
VALID_RESTART_POLICIES = {"always", "unless-stopped", "on-failure"}
MEMORY_SIZE_PATTERN = re.compile(r"^(\d+(?:\.\d+)?)\s*([kmgt]?i?b?)?$", re.IGNORECASE)
JAVA_HEAP_SIZE_PATTERN = re.compile(r"^(\d+)([kmgt]?)$", re.IGNORECASE)


class KafkaConfigError(ValueError):
    pass


def parse_memory_size(value: Any, field_name: str) -> int:
    if isinstance(value, bool):
        raise KafkaConfigError(f"{field_name} must be a positive memory size")

    if isinstance(value, (int, float)):
        bytes_value = int(value)
        if bytes_value <= 0 or bytes_value != value:
            raise KafkaConfigError(f"{field_name} must be a positive memory size")
        return bytes_value

    if not isinstance(value, str):
        raise KafkaConfigError(f"{field_name} must be a positive memory size")

    match = MEMORY_SIZE_PATTERN.fullmatch(value.strip())
    if match is None:
        raise KafkaConfigError(f"{field_name} must be a positive memory size")

    amount = float(match.group(1))
    unit = (match.group(2) or "b").lower()
    normalized_unit = unit.removesuffix("b").removesuffix("i")
    multiplier = {
        "": 1,
        "k": 1024,
        "m": 1024**2,
        "g": 1024**3,
        "t": 1024**4,
    }.get(normalized_unit)
    if multiplier is None or amount <= 0:
        raise KafkaConfigError(f"{field_name} must be a positive memory size")

    return int(amount * multiplier)


def parse_java_heap_size(value: str, option_name: str) -> int:
    match = JAVA_HEAP_SIZE_PATTERN.fullmatch(value)
    if match is None:
        raise KafkaConfigError(f"{option_name} must use a positive Java heap size")

    amount = int(match.group(1))
    unit = match.group(2).lower()
    multiplier = {"": 1, "k": 1024, "m": 1024**2, "g": 1024**3, "t": 1024**4}[unit]
    if amount <= 0:
        raise KafkaConfigError(f"{option_name} must use a positive Java heap size")
    return amount * multiplier


def parse_heap_options(heap_options: Any) -> tuple[int, int]:
    if not isinstance(heap_options, str) or not heap_options.strip():
        raise KafkaConfigError("KAFKA_HEAP_OPTS must define exactly one -Xms and one -Xmx")

    xms_values = re.findall(r"(?<!\S)-Xms(\S+)", heap_options)
    xmx_values = re.findall(r"(?<!\S)-Xmx(\S+)", heap_options)
    if len(xms_values) != 1 or len(xmx_values) != 1:
        raise KafkaConfigError("KAFKA_HEAP_OPTS must define exactly one -Xms and one -Xmx")

    return (
        parse_java_heap_size(xms_values[0], "-Xms"),
        parse_java_heap_size(xmx_values[0], "-Xmx"),
    )


def validate_compose_config(config: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    services = config.get("services")
    kafka = services.get(KAFKA_SERVICE) if isinstance(services, dict) else None
    if not isinstance(kafka, dict):
        return ["canonical Kafka service 'kafka' is missing"]

    image = kafka.get("image")
    if not isinstance(image, str) or EXPECTED_IMAGE_PATTERN.match(image) is None:
        errors.append("Kafka image must remain an apache/kafka image")

    restart = kafka.get("restart")
    if not isinstance(restart, str) or not restart.strip():
        errors.append("Kafka restart policy is missing")
    else:
        normalized_restart = restart.strip().lower()
        valid_restart = normalized_restart in VALID_RESTART_POLICIES or normalized_restart.startswith("on-failure:")
        if normalized_restart == "no":
            errors.append("Kafka restart policy must not be 'no'")
        elif not valid_restart:
            errors.append("Kafka restart policy must be a supported non-no policy")

    memory_limit: int | None = None
    memory_reservation: int | None = None
    try:
        memory_limit = parse_memory_size(kafka.get("mem_limit"), "Kafka memory limit")
    except KafkaConfigError as error:
        errors.append(str(error))
    try:
        memory_reservation = parse_memory_size(kafka.get("mem_reservation"), "Kafka memory reservation")
    except KafkaConfigError as error:
        errors.append(str(error))

    if (
        memory_limit is not None
        and memory_reservation is not None
        and memory_reservation > memory_limit
    ):
        errors.append("Kafka memory reservation must not exceed the memory limit")

    environment = kafka.get("environment")
    heap_options = environment.get("KAFKA_HEAP_OPTS") if isinstance(environment, dict) else None
    try:
        xms, xmx = parse_heap_options(heap_options)
        if xms > xmx:
            errors.append("Kafka -Xms must not exceed -Xmx")
        if memory_limit is not None and xmx > memory_limit:
            errors.append("Kafka -Xmx must not exceed the container memory limit")
    except KafkaConfigError as error:
        errors.append(str(error))

    volumes = kafka.get("volumes")
    has_persistent_data_mount = isinstance(volumes, list) and any(
        isinstance(volume, dict)
        and volume.get("type") == "volume"
        and volume.get("source") == EXPECTED_VOLUME_SOURCE
        and volume.get("target") == EXPECTED_VOLUME_TARGET
        for volume in volumes
    )
    if not has_persistent_data_mount:
        errors.append("Kafka persistent data volume must remain mounted at the expected KRaft storage path")

    healthcheck = kafka.get("healthcheck")
    if not isinstance(healthcheck, dict) or not healthcheck.get("test"):
        errors.append("Kafka health check is missing")

    return errors


def main() -> int:
    try:
        config = json.load(sys.stdin)
    except (json.JSONDecodeError, UnicodeDecodeError):
        print("Kafka runtime config validation failed: input is not valid Compose JSON", file=sys.stderr)
        return 1

    if not isinstance(config, dict):
        print("Kafka runtime config validation failed: Compose JSON root must be an object", file=sys.stderr)
        return 1

    errors = validate_compose_config(config)
    if errors:
        print("Kafka runtime config validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print("Kafka runtime config validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
