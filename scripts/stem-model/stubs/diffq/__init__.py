"""Stand-in for diffq, which only quantised Demucs models use (htdemucs isn't one)."""


def _unavailable(*args, **kwargs):
    raise RuntimeError("diffq is not installed; only needed for quantised Demucs models")


class DiffQuantizer:
    def __init__(self, *args, **kwargs):
        _unavailable()


class UniformQuantizer(DiffQuantizer):
    pass


restore_quantized_state = _unavailable
