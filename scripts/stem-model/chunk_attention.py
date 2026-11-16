"""Rewrites the attention in an htdemucs ONNX model to work through its queries in chunks.

Every attention in the model is MatMul(Q, K^T) -> Softmax(last axis) -> MatMul(., V). Done whole,
the score matrices are up to 8 x 2688 x 2688 floats (231 MB each), and ONNX Runtime's allocator
on Android doesn't reuse them well: the separation process peaked at over 4.5 GB. Softmax works
row by row, so splitting Q along its query axis, running each chunk through the same three
nodes and concatenating gives exactly the same result with score matrices a fraction the size.

    python chunk_attention.py in.onnx out.onnx [rows per chunk, default 336]
"""
import sys

import numpy as np
import onnx
from onnx import helper, numpy_helper, shape_inference

src, dst = sys.argv[1], sys.argv[2]
rows = int(sys.argv[3]) if len(sys.argv) > 3 else 336

model = onnx.load(src)
inferred = shape_inference.infer_shapes(model)
shapes = {}
for vi in list(inferred.graph.value_info) + list(inferred.graph.input) + list(inferred.graph.output):
    shapes[vi.name] = [d.dim_value for d in vi.type.tensor_type.shape.dim]

graph = model.graph
nodes = list(graph.node)
producer = {o: n for n in nodes for o in n.output}
consumers = {}
for n in nodes:
    for i in n.input:
        consumers.setdefault(i, []).append(n)

rewritten = 0
for softmax in [n for n in nodes if n.op_type == "Softmax"]:
    scores = producer.get(softmax.input[0])
    users = consumers.get(softmax.output[0], [])
    if scores is None or scores.op_type != "MatMul" or len(users) != 1 or users[0].op_type != "MatMul":
        continue
    if len(consumers.get(scores.output[0], [])) != 1:
        continue
    out = users[0]
    if out.input[0] != softmax.output[0]:
        continue
    q, kt = scores.input
    v = out.input[1]
    q_shape = shapes.get(q)
    if not q_shape or len(q_shape) != 4 or not q_shape[2]:
        continue
    n_q = q_shape[2]
    if n_q <= rows:
        continue
    sizes = [rows] * (n_q // rows) + ([n_q % rows] if n_q % rows else [])
    base = softmax.name or softmax.output[0]
    split_sizes = numpy_helper.from_array(np.array(sizes, dtype=np.int64), base + "/chunk_sizes")
    graph.initializer.append(split_sizes)
    parts = [f"{base}/q_chunk{i}" for i in range(len(sizes))]
    new_nodes = [helper.make_node("Split", [q, split_sizes.name], parts, axis=2, name=base + "/chunk_split")]
    outs = []
    for i, part in enumerate(parts):
        s = f"{base}/scores{i}"
        p = f"{base}/probs{i}"
        o = f"{base}/out{i}"
        new_nodes.append(helper.make_node("MatMul", [part, kt], [s], name=f"{base}/qk{i}"))
        new_nodes.append(helper.make_node("Softmax", [s], [p], axis=-1, name=f"{base}/softmax{i}"))
        new_nodes.append(helper.make_node("MatMul", [p, v], [o], name=f"{base}/pv{i}"))
        outs.append(o)
    new_nodes.append(helper.make_node("Concat", outs, [out.output[0]], axis=2, name=base + "/chunk_concat"))
    # In place of the three originals, keeping the graph topologically sorted.
    at = list(graph.node).index(out)
    for old in (scores, softmax, out):
        graph.node.remove(old)
    at -= 2
    for k, node in enumerate(new_nodes):
        graph.node.insert(at + k, node)
    rewritten += 1

onnx.checker.check_model(model)
onnx.save(model, dst)
print(f"chunked {rewritten} attentions into rows of {rows}")
