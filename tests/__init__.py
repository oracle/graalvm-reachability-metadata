import sys

_MIN_VERSION = (3, 7)
assert sys.version_info >= _MIN_VERSION, \
    "This code is only supported in Python >= %s" % ".".join([str(i) for i in _MIN_VERSION])
