# Voice typing spike (desktop measurements)

Scripts used for `docs/research/voice-typing.md`. They run outside the app: models and test audio are downloaded into `~/AI_Works/voice-spike/` (not in the repo).

* Test data: Google FLEURS test split, `ur_pk` (299 clips) and `ar_eg` (428 clips, first 150 used), from `huggingface.co/datasets/google/fleurs` (`data/<lang>/test.tsv` + `audio/test.tar.gz`).
* `evalcommon.py`: text normalisation (diacritics, punctuation, Urdu/Arabic letter variants, digits dropped) and CER/WER with `jiwer`.
* `eval_sherpa.py <omni> <model_dir> <ur|ar> <n|0=all> <threads>`: sherpa-onnx (`pip install sherpa-onnx`) CPU run, prints `RESULT ... CER WER RTF`.
* `eval_whisper.py <model_dir> <ur|ar> <n>` and `eval_moonshine.py`: transformers on the GPU. `fakeflash.py` stubs a broken CUDA-12 `flash_attn` on this machine; not needed elsewhere.
* `quant.py`: onnxruntime dynamic int8 quantisation of the Omnilingual 300M CTC model (1.3 GB -> 328 MB).
* `deva2roman.py`, `Batch.java`, `omni_ur_pipeline.py`: Omnilingual writes Urdu speech in Devanagari; this converts it to Roman letters and decodes with Lafz's engine
  (`javac -d out Batch.java app/.../urdu/RomanTransliterator.java app/.../urdu/PersonalModel.java`). Only a workaround for that one model.
