#!/usr/bin/env python3
"""Validate compressed structure NBT and reserved bounds for GameTest fixtures.

This catches malformed GZIP trailers, corrupt NBT payloads, invalid structure
list tags, references to missing structures, and undersized test arenas before
the expensive real Minecraft server matrix starts.
"""
from __future__ import annotations

import gzip
import json
import struct
import zlib
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
STRUCTURES = ROOT / "src/main/resources/data/freakyfriday/structure"
INSTANCES = ROOT / "src/main/resources/data/freakyfriday/test_instance"
MAX_COLLECTION = 1_000_000
MAX_DEPTH = 64


class NBTError(ValueError):
    pass


class NBTList(list):
    def __init__(self, element_type: int, values: list[Any]):
        super().__init__(values)
        self.element_type = element_type


class Reader:
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def take(self, size: int) -> bytes:
        if size < 0 or self.pos + size > len(self.data):
            raise NBTError(f"truncated NBT at byte {self.pos}, requested {size}")
        result = self.data[self.pos:self.pos + size]
        self.pos += size
        return result

    def u8(self) -> int:
        return self.take(1)[0]

    def i16(self) -> int:
        return struct.unpack(">h", self.take(2))[0]

    def i32(self) -> int:
        return struct.unpack(">i", self.take(4))[0]

    def i64(self) -> int:
        return struct.unpack(">q", self.take(8))[0]

    def string(self) -> str:
        size = struct.unpack(">H", self.take(2))[0]
        return self.take(size).decode("utf-8")

    def payload(self, tag_type: int, depth: int = 0) -> Any:
        if depth > MAX_DEPTH:
            raise NBTError("NBT nesting exceeds safety limit")
        if tag_type == 1:
            return struct.unpack(">b", self.take(1))[0]
        if tag_type == 2:
            return self.i16()
        if tag_type == 3:
            return self.i32()
        if tag_type == 4:
            return self.i64()
        if tag_type == 5:
            return struct.unpack(">f", self.take(4))[0]
        if tag_type == 6:
            return struct.unpack(">d", self.take(8))[0]
        if tag_type == 7:
            count = self.i32()
            self._count(count)
            return self.take(count)
        if tag_type == 8:
            return self.string()
        if tag_type == 9:
            element_type = self.u8()
            count = self.i32()
            self._count(count)
            if element_type == 0 and count:
                raise NBTError("non-empty TAG_List cannot contain TAG_End")
            return NBTList(element_type,
                [self.payload(element_type, depth + 1) for _ in range(count)])
        if tag_type == 10:
            result: dict[str, Any] = {}
            while True:
                child_type = self.u8()
                if child_type == 0:
                    return result
                if child_type < 1 or child_type > 12:
                    raise NBTError(f"unknown NBT tag type {child_type}")
                name = self.string()
                if name in result:
                    raise NBTError(f"duplicate compound key {name!r}")
                result[name] = self.payload(child_type, depth + 1)
        if tag_type == 11:
            count = self.i32()
            self._count(count)
            return [self.i32() for _ in range(count)]
        if tag_type == 12:
            count = self.i32()
            self._count(count)
            return [self.i64() for _ in range(count)]
        raise NBTError(f"unsupported NBT tag type {tag_type}")

    @staticmethod
    def _count(count: int) -> None:
        if count < 0 or count > MAX_COLLECTION:
            raise NBTError(f"invalid NBT collection size {count}")


def parse_structure(path: Path) -> tuple[int, int, int]:
    try:
        compressed = path.read_bytes()
        raw = gzip.decompress(compressed)  # validates DEFLATE stream and CRC trailer
    except (OSError, EOFError, zlib.error) as exc:
        raise NBTError(f"{path.relative_to(ROOT)}: invalid GZIP: {exc}") from exc

    reader = Reader(raw)
    root_type = reader.u8()
    if root_type != 10:
        raise NBTError(f"{path.name}: root tag must be TAG_Compound")
    root_name = reader.string()
    if root_name:
        raise NBTError(f"{path.name}: structure root name must be empty")
    root = reader.payload(10)
    if reader.pos != len(raw):
        raise NBTError(f"{path.name}: {len(raw) - reader.pos} trailing NBT bytes")

    size = root.get("size")
    if not isinstance(size, NBTList) or size.element_type != 3 or len(size) != 3:
        raise NBTError(f"{path.name}: size must be TAG_List(TAG_Int) with three values")
    dimensions = tuple(size)
    if any(type(value) is not int or value <= 0 for value in dimensions):
        raise NBTError(f"{path.name}: invalid positive dimensions {dimensions}")

    palette = root.get("palette")
    blocks = root.get("blocks")
    entities = root.get("entities")
    for key, value in (("palette", palette), ("blocks", blocks), ("entities", entities)):
        if not isinstance(value, NBTList):
            raise NBTError(f"{path.name}: missing TAG_List {key}")
    if palette.element_type != 10 or not palette:
        raise NBTError(f"{path.name}: palette must be a non-empty list of compounds")
    if blocks.element_type != 10 or entities.element_type != 10:
        raise NBTError(f"{path.name}: blocks and entities must be lists of compounds")

    for index, block in enumerate(blocks):
        if not isinstance(block, dict):
            raise NBTError(f"{path.name}: block {index} is not a compound")
        position = block.get("pos")
        state = block.get("state")
        if not isinstance(position, NBTList) or position.element_type != 3 or len(position) != 3:
            raise NBTError(f"{path.name}: block {index} pos must be TAG_List(TAG_Int)")
        if any(type(v) is not int or v < 0 or v >= dimensions[axis]
               for axis, v in enumerate(position)):
            raise NBTError(f"{path.name}: block {index} position {list(position)} is outside {dimensions}")
        if type(state) is not int or state < 0 or state >= len(palette):
            raise NBTError(f"{path.name}: block {index} references invalid palette state {state}")

    return dimensions  # type: ignore[return-value]


# Minimum dimensions required by the hand-built fixture geometry in the
# corresponding GameTest functions. These are lower bounds, not exact sizes.
MINIMUM_SIZES: dict[str, tuple[int, int, int]] = {
    "villager_zombie_brain_swap": (20, 8, 10),
    "partial_native_swap_fails_closed": (26, 8, 12),
    "zombie_chicken_behavior_acceptance": (8, 8, 8),
    "native_speed_collision": (9, 8, 24),
    "native_fall_profiles": (11, 20, 8),
    "possessed_mob_attack": (10, 8, 10),
    "player_controls_chicken": (22, 10, 12),
    "reciprocal_player_control": (13, 8, 13),
    "player_death_ejects_both": (10, 8, 7),
    "external_native_worker": (14, 8, 14),
    "detached_dragon_chicken": (6, 8, 8),
    "detached_cross_species": (7, 8, 6),
    "physical_world_mirror": (7, 8, 8),
}


def main() -> int:
    if not STRUCTURES.is_dir() or not INSTANCES.is_dir():
        raise SystemExit("GameTest structure or test_instance directory is missing")

    dimensions_by_path: dict[Path, tuple[int, int, int]] = {}
    for structure in sorted(STRUCTURES.glob("*.nbt")):
        dimensions = parse_structure(structure)
        dimensions_by_path[structure] = dimensions
        print(f"FF-STRUCTURE {structure.stem} PASS size={dimensions}")

    instance_count = 0
    for instance in sorted(INSTANCES.glob("*.json")):
        try:
            data = json.loads(instance.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise NBTError(f"{instance.name}: invalid test instance JSON: {exc}") from exc
        identifier = data.get("structure")
        if not isinstance(identifier, str) or ":" not in identifier:
            raise NBTError(f"{instance.name}: invalid structure identifier")
        namespace, name = identifier.split(":", 1)
        if namespace != "freakyfriday" or not name:
            raise NBTError(f"{instance.name}: unexpected structure namespace {identifier!r}")
        structure_path = STRUCTURES / f"{name}.nbt"
        dimensions = dimensions_by_path.get(structure_path)
        if dimensions is None:
            raise NBTError(f"{instance.name}: referenced structure {identifier} is missing")
        minimum = MINIMUM_SIZES.get(instance.stem)
        if minimum and any(actual < required for actual, required in zip(dimensions, minimum)):
            raise NBTError(
                f"{instance.name}: arena {dimensions} is smaller than fixture minimum {minimum}")
        instance_count += 1
        print(f"FF-TEST-STRUCTURE {instance.stem} PASS arena={name} size={dimensions}")

    print(f"FF-STRUCTURE-SUITE PASS structures={len(dimensions_by_path)} testInstances={instance_count}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except NBTError as failure:
        print(f"FF-STRUCTURE-SUITE FAIL: {failure}", file=sys.stderr)
        raise SystemExit(1)
