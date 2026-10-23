import onnxruntime.quantization as q, os, time
t=time.time()
q.quantize_dynamic('../models/omni300/model.onnx', '../models/omni300_int8/model.int8.onnx', weight_type=q.QuantType.QInt8, use_external_data_format=False)
print('done', os.path.getsize('../models/omni300_int8/model.int8.onnx')/1e6, 'MB', time.time()-t)
