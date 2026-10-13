# Arabic (abc → عربي) model v2: better data, phonetic channel, glued-word decoding

Goal (owner): the Arabic keyboard should convert the way people actually type scripture and everyday Arabic in Roman letters
(`innama al'amalu binniyyaat`, `inna ma'al'usri yusra`, `innamal'amal`) into correct Arabic, for new words by *phonetic interpretation*
rather than by looking up fixed spellings. Must stay offline and run on a phone. Failures observed: see `results.md`.
Owner allowed: any data source regardless of license (app is not published), and my local machine for training
(RTX 3090 Ti, 32 cores, 500 GB RAM). Raw data stays out of git (`data/` is untracked).

Approach (research conclusion): keep the proven on-device design (lexicon + language model decoded through a noisy-channel
transliteration model, cf. Hellsten et al. 2017 WFST paper that Gboard used), because a neural seq2seq decoder per keystroke is too slow
and too big for a keyboard on mid-range phones, and dictionary-only output is an owner requirement. Improve each part of it with far more
data and the missing linguistic structure (clitics, definite article assimilation, glued words). The GPU is used for any neural
helper models we need offline (not shipped).

### Task 1: Collect corpora
- [ ] OpenSubtitles Arabic (conversational/dialect), Arabic Wikipedia (7 parquet shards), CC-100 Arabic, Tashkeela (diacritized, ~75M words)
- [ ] Normalize + tokenize into `data/Arabic/corpus2/` (plain + diacritized sentence files, per-source counts)

### Task 2: Evaluation sets (before changing anything)
- [ ] `eval/owner_phrases.tsv`: scripture/everyday phrases typed in the owner's styles (apostrophe-ʿayn, glued article, digits)
- [ ] baseline numbers for current model on owner phrases + existing dev sets (`results.md`)

### Task 3: Phonetic romanizer v2
- [ ] phrase-level: optional word gluing, ال assimilation (an-niyyat), hamzat-wasl elision (innamal'amal), proclitics (bi/li/wa/fa)
- [ ] sources: Tashkeela + Qur'an + Hadith diacritized text (vowels known), many typing styles incl. apostrophe/digit ʿayn

### Task 4: Channel model v2
- [ ] context-sensitive pseudo-letters (assimilated lam, wasl alif, tanween, taa marbuta), EM at scale on 32 cores
- [ ] mix real Arabizi pairs (ATAR etc.) as an additional source

### Task 5: Vocabulary + language model v2
- [ ] unigram/bigram counts from all corpora (scripture-boosted mix), vocabulary with clitic-fused forms, pruned bigrams

### Task 6: Decoder: glued words
- [ ] segmentation lattice over the typed token (≤3 words) using the word-level channel scores and the bigram LM
- [ ] latency budget on desktop JVM, then phone-sized estimate

### Task 7: Integrate and verify
- [ ] unit tests (JDK 21), rebuild APK, emulator run on the owner's phrases
- [ ] update `results.md`, `tools/arabic/README.md`, CLAUDE.md notes
