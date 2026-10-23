import sys, types, importlib.machinery
from unittest.mock import MagicMock
class _Fake(types.ModuleType):   # the system flash_attn is built for CUDA 12 and unused here
    def __getattr__(self, n):
        if n.startswith('__'): raise AttributeError(n)
        return MagicMock()
for _m in ('flash_attn', 'flash_attn.flash_attn_interface', 'flash_attn.bert_padding', 'flash_attn_2_cuda'):
    _f = _Fake(_m); _f.__spec__ = importlib.machinery.ModuleSpec(_m, None); _f.__path__ = []; sys.modules[_m] = _f
