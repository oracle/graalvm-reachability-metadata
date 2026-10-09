# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""A minimal reader for compiled Java class files.

Reads the two facts the deep prompt's test trace needs from a compiled test
class (§AR-code-coverage-deep-navigation.2): each method's bytecode-to-source
line table, and the method each invoke instruction names, by its bci. The
library side has the bytecode extractor (`java/CallGraphExtractor.java`); a
test class is read here, in process, since only a handful are needed per pass.
"""

from __future__ import annotations

import struct
from dataclasses import dataclass

_INVOKE_OPCODES: frozenset[int] = frozenset({0xB6, 0xB7, 0xB8, 0xB9})
_TABLESWITCH: int = 0xAA
_LOOKUPSWITCH: int = 0xAB
_WIDE: int = 0xC4
_IINC: int = 0x84

# Instruction lengths by opcode, the opcode byte included; the two switches
# and `wide` are variable and decoded apart.
_LENGTHS: dict[int, int] = {
    0x10: 2, 0x11: 3, 0x12: 2, 0x13: 3, 0x14: 3,
    **{opcode: 2 for opcode in range(0x15, 0x1A)},
    **{opcode: 2 for opcode in range(0x36, 0x3B)},
    _IINC: 3,
    **{opcode: 3 for opcode in range(0x99, 0xA9)},
    0xA9: 2,
    **{opcode: 3 for opcode in range(0xB2, 0xB9)},
    0xB9: 5, 0xBA: 5, 0xBB: 3, 0xBC: 2, 0xBD: 3, 0xC0: 3, 0xC1: 3, 0xC5: 4,
    0xC6: 3, 0xC7: 3, 0xC8: 5, 0xC9: 5,
}


class ClassFileError(ValueError):
    """The bytes are not a class file this reader understands."""


@dataclass(frozen=True)
class MethodCode:
    """One method body: its line table and the method each invoke names."""

    name: str
    #: Parameter types in Java source form, as `MethodRef.params` holds them.
    params: tuple[str, ...]
    #: `(start bci, source line)`, sorted by bci.
    line_numbers: tuple[tuple[int, int], ...]
    #: Invoke bci -> `(owner, name)` of the method the instruction names.
    invokes: dict[int, tuple[str, str]]


class _Reader:
    def __init__(self, data: bytes) -> None:
        self.data: bytes = data
        self.offset: int = 0

    def u1(self) -> int:
        value: int = self.data[self.offset]
        self.offset += 1
        return value

    def u2(self) -> int:
        (value,) = struct.unpack_from(">H", self.data, self.offset)
        self.offset += 2
        return value

    def u4(self) -> int:
        (value,) = struct.unpack_from(">I", self.data, self.offset)
        self.offset += 4
        return value

    def take(self, length: int) -> bytes:
        chunk: bytes = self.data[self.offset:self.offset + length]
        if len(chunk) != length:
            raise ClassFileError("truncated class file")
        self.offset += length
        return chunk


def _constant_pool(reader: _Reader) -> list[object]:
    """Utf8 entries as text, references as index tuples, numbers as `None`."""
    count: int = reader.u2()
    pool: list[object] = [None] * count
    index: int = 1
    while index < count:
        tag: int = reader.u1()
        if tag == 1:
            pool[index] = reader.take(reader.u2()).decode("utf-8", "replace")
        elif tag in (3, 4):
            reader.take(4)
        elif tag in (5, 6):
            reader.take(8)
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = (reader.u2(),)
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[index] = (reader.u2(), reader.u2())
        elif tag == 15:
            reader.take(3)
        else:
            raise ClassFileError(f"unknown constant pool tag {tag}")
        index += 1
    return pool


_PRIMITIVES: dict[str, str] = {
    "B": "byte", "C": "char", "D": "double", "F": "float",
    "I": "int", "J": "long", "S": "short", "Z": "boolean",
}


def _source_type(field_descriptor: str) -> str:
    """`[Ljava/lang/String;` -> `java.lang.String[]`, `I` -> `int`."""
    base: str = field_descriptor.lstrip("[")
    dimensions: int = len(field_descriptor) - len(base)
    name: str = _PRIMITIVES.get(base) or base[1:-1].replace("/", ".")
    return name + "[]" * dimensions


def descriptor_params(descriptor: str) -> tuple[str, ...]:
    """The parameter types of a method descriptor, in Java source form."""
    params: list[str] = []
    position: int = descriptor.index("(") + 1
    while descriptor[position] != ")":
        start: int = position
        while descriptor[position] == "[":
            position += 1
        if descriptor[position] == "L":
            position = descriptor.index(";", position)
        position += 1
        params.append(_source_type(descriptor[start:position]))
    return tuple(params)


def _instruction_length(code: bytes, pc: int) -> int:
    opcode: int = code[pc]
    if opcode in (_TABLESWITCH, _LOOKUPSWITCH):
        base: int = pc + 1 + (-(pc + 1) % 4)
        if opcode == _TABLESWITCH:
            low, high = struct.unpack_from(">ii", code, base + 4)
            return base + 12 + 4 * (high - low + 1) - pc
        (pairs,) = struct.unpack_from(">i", code, base + 4)
        return base + 8 + 8 * pairs - pc
    if opcode == _WIDE:
        return 6 if code[pc + 1] == _IINC else 4
    return _LENGTHS.get(opcode, 1)


def _invokes(code: bytes, pool: list[object]) -> dict[int, tuple[str, str]]:
    def entry(index: int) -> tuple[int, ...]:
        value: object = pool[index]
        if not isinstance(value, tuple):
            raise ClassFileError(f"constant {index} is no reference")
        return value

    def text(index: int) -> str:
        value: object = pool[index]
        if not isinstance(value, str):
            raise ClassFileError(f"constant {index} is no text")
        return value

    invokes: dict[int, tuple[str, str]] = {}
    pc: int = 0
    while pc < len(code):
        if code[pc] in _INVOKE_OPCODES:
            (index,) = struct.unpack_from(">H", code, pc + 1)
            class_index, name_and_type = entry(index)
            owner: str = text(entry(class_index)[0]).replace("/", ".")
            invokes[pc] = (owner, text(entry(name_and_type)[0]))
        pc += _instruction_length(code, pc)
    return invokes


def _skip_attributes(reader: _Reader) -> None:
    for _ in range(reader.u2()):
        reader.u2()
        reader.take(reader.u4())


def read_class_file(path: str) -> list[MethodCode]:
    """Every method with a body in one class file."""
    with open(path, "rb") as class_file:
        reader = _Reader(class_file.read())
    try:
        if reader.u4() != 0xCAFEBABE:
            raise ClassFileError(f"not a class file: {path}")
        reader.take(4)
        pool: list[object] = _constant_pool(reader)
        reader.take(6)
        reader.take(2 * reader.u2())
        for _ in range(reader.u2()):
            reader.take(6)
            _skip_attributes(reader)
        methods: list[MethodCode] = []
        for _ in range(reader.u2()):
            reader.u2()
            name: str = str(pool[reader.u2()])
            descriptor: str = str(pool[reader.u2()])
            for _ in range(reader.u2()):
                attribute: str = str(pool[reader.u2()])
                body: bytes = reader.take(reader.u4())
                if attribute == "Code":
                    methods.append(_method_code(name, descriptor, body, pool))
        return methods
    except ClassFileError:
        raise
    except (struct.error, IndexError, ValueError) as error:
        raise ClassFileError(f"unreadable class file {path}: {error}") from error


def _method_code(name: str, descriptor: str, body: bytes, pool: list[object]) -> MethodCode:
    reader = _Reader(body)
    reader.take(4)
    code: bytes = reader.take(reader.u4())
    reader.take(8 * reader.u2())
    lines: list[tuple[int, int]] = []
    for _ in range(reader.u2()):
        attribute: str = str(pool[reader.u2()])
        length: int = reader.u4()
        if attribute != "LineNumberTable":
            reader.take(length)
            continue
        for _ in range(reader.u2()):
            lines.append((reader.u2(), reader.u2()))
    return MethodCode(
        name=name,
        params=descriptor_params(descriptor),
        line_numbers=tuple(sorted(lines)),
        invokes=_invokes(code, pool),
    )
