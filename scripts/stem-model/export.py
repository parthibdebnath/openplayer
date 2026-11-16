"""Builds app/src/main/assets/stems/htdemucs.onnx, the DJ mix's stem separation model.

The model is Demucs v4 (htdemucs, MIT) with its STFT and inverse STFT taken out of the graph
(ONNX can't export them; the app does them itself, in audio/stems/DemucsDsp.kt). That needs the
ONNX-friendly Demucs fork from https://github.com/sevagh/demucs.onnx (MIT):

    git clone --depth 1 https://github.com/sevagh/demucs.onnx
    python3 -m venv venv
    venv/bin/pip install "torch<2.9" "torchaudio<2.9" --index-url https://download.pytorch.org/whl/cpu
    venv/bin/pip install onnx onnxruntime numpy dora-search omegaconf openunmix einops julius lameenc pyyaml tqdm
    PYTHONPATH=demucs.onnx/demucs-for-onnx:scripts/stem-model/stubs venv/bin/python scripts/stem-model/export.py

(diffq, which the fork imports but htdemucs doesn't use, doesn't build on Python 3.12: the stub in
stubs/ stands in for it.) The weights download from Meta's server on first run.

The weights are stored as fp16, each cast back to fp32 by a Cast node that ONNX Runtime folds at
load, so inference is full precision; the outputs differ from all-fp32 weights by 69 dB (frequency
branch) and 105 dB (time branch) SNR - far below hearing - at half the size (91 MB).
"""
from pathlib import Path

import numpy as np
import onnx
import torch
from onnx import TensorProto, helper, numpy_helper

from demucs.htdemucs import HTDemucs, standalone_magnitude, standalone_spec
from demucs.pretrained import get_model

OUT = Path(__file__).resolve().parents[2] / "app/src/main/assets/stems/htdemucs.onnx"

# nn.MultiheadAttention's fused inference kernel (aten::_native_multi_head_attention) has no ONNX
# export; with the fast path off it's traced as plain matmuls and softmax - the same maths.
torch.backends.mha.set_fastpath_enabled(False)

model = get_model("htdemucs")
core = model if isinstance(model, HTDemucs) else model.models[0]
core.eval()
for p in core.parameters():
    p.requires_grad_(False)

length = int(core.segment * core.samplerate)  # 343980: 7.8 s at 44.1 kHz
wave = torch.randn(1, 2, length)
spec = standalone_magnitude(standalone_spec(wave))
full = OUT.with_name("htdemucs_fp32.onnx")
OUT.parent.mkdir(parents=True, exist_ok=True)
torch.onnx.export(
    core, (wave, spec), str(full), export_params=True, opset_version=17, do_constant_folding=True,
    input_names=["mix", "spec"], output_names=["freq_out", "time_out"], dynamo=False,
)

m = onnx.load(str(full))
graph = m.graph
casts, halves = [], []
for init in list(graph.initializer):
    if init.data_type == TensorProto.FLOAT and np.prod(init.dims) >= 1024:
        half = numpy_helper.from_array(numpy_helper.to_array(init).astype(np.float16), init.name + "__fp16")
        halves.append(half)
        casts.append(helper.make_node("Cast", [half.name], [init.name], to=TensorProto.FLOAT, name=init.name + "__cast"))
        graph.initializer.remove(init)
graph.initializer.extend(halves)
for node in reversed(casts):
    graph.node.insert(0, node)
onnx.save(m, str(OUT))
full.unlink()
print("wrote", OUT, OUT.stat().st_size // 1_000_000, "MB")
