# Voice models for Lafz

Download the model for the language you use. Each ZIP includes the speech model and the files Lafz needs to import it.

| Language | Download from GitHub | Download from Forge | Download size |
| --- | --- | --- | --- |
| English | [English model](https://github.com/magnumquest/Lafz/releases/download/v0.7.0/Lafz_English_voice_model.zip) | [English model](https://git.qnap.haizum.net/thomas/Lafz/releases/download/v0.7.0/Lafz_English_voice_model.zip) | 54 MB |
| Urdu | [Urdu model](https://github.com/magnumquest/Lafz/releases/download/v0.7.0/Lafz_Urdu_voice_model.zip) | [Urdu model](https://git.qnap.haizum.net/thomas/Lafz/releases/download/v0.7.0/Lafz_Urdu_voice_model.zip) | 176 MB |
| Arabic | [Arabic model](https://github.com/magnumquest/Lafz/releases/download/v0.7.0/Lafz_Arabic_voice_model.zip) | [Arabic model](https://git.qnap.haizum.net/thomas/Lafz/releases/download/v0.7.0/Lafz_Arabic_voice_model.zip) | 273 MB |

## Use a model on your phone

1. Download a ZIP above and unzip it. Keep all the files together inside the extracted model folder.
2. Open **Lafz Settings → Voice typing → Choose model folder** and select that folder.
3. Turn on **Dictate with the microphone key** and allow microphone access when asked.
4. Switch to the matching keyboard language. Hold the microphone to speak, then release to insert text. Slide up while holding to lock recording; tap to finish.

You can import all three models. Lafz keeps only the model for the selected keyboard language loaded. Once imported, recognition works offline. Existing imported models continue to work with Lafz 0.7.0.

## Models and credits

- **English:** [OpenAI Whisper base.en](https://huggingface.co/openai/whisper-base.en), GGML q5_1, MIT license.
- **Urdu:** [Hamza Amin's Whisper Small Urdu v2](https://huggingface.co/hamza-amin/whisper-small-urdu-v2), based on [khawajaaliarshad/whisper-small-urdu](https://huggingface.co/khawajaaliarshad/whisper-small-urdu) and OpenAI Whisper small; GGML q5_1, Apache-2.0 with original Whisper MIT attribution.
- **Arabic:** [Meta Omnilingual ASR CTC 300M](https://huggingface.co/facebook/omniASR-CTC-300M), ONNX int8, Apache-2.0.

License texts and notices are included in each model folder and ZIP. These model files and their recognition settings are copied unchanged from the project’s `Lafz_voice_files` folder. Use [SHA256SUMS](SHA256SUMS) to verify the downloaded ZIP packs.

## Downloading

The three ZIP packs are attached to the Lafz 0.7.0 releases on both GitHub and Forge. The repository keeps these download links and model notices; Git LFS is not required.
