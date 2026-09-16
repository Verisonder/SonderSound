import numpy as np
from ai_edge_litert.interpreter import Interpreter
mel=Interpreter(model_path="melspectrogram.tflite"); mi=mel.get_input_details()[0]['index']; mo=mel.get_output_details()[0]['index']
emb=Interpreter(model_path="embedding_model.tflite"); emb.allocate_tensors(); ei=emb.get_input_details()[0]['index']; eo=emb.get_output_details()[0]['index']
cur=[0]
def melspec(x):
    x=np.asarray(x,dtype=np.float32)[None,:]
    if cur[0]!=x.shape[1]:
        mel.resize_tensor_input(mi,[1,x.shape[1]],strict=True); mel.allocate_tensors(); cur[0]=x.shape[1]
    mel.set_tensor(mi,x); mel.invoke(); return np.squeeze(mel.get_tensor(mo))/10+2
def embeddings(x):
    # pad to at least ~1.2 s so a short word gets windows
    x=np.asarray(x,dtype=np.float64)
    if len(x)<16000*1.3: x=np.concatenate([np.zeros(int((16000*1.3-len(x))/2)),x,np.zeros(int((16000*1.3-len(x))/2)+1)])
    s=melspec(np.clip(x,-32768,32767).astype(np.int16))
    out=[]
    for i in range(0,s.shape[0]-76+1,8):
        w=s[i:i+76][None,:,:,None].astype(np.float32); emb.set_tensor(ei,w); emb.invoke(); out.append(np.squeeze(emb.get_tensor(eo)))
    return np.array(out)
