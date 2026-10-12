#!/usr/bin/env python3
"""
Converts Tarteel's Quran fine-tune of Whisper (tarteel-ai/whisper-base-ar-quran,
Apache-2.0) to the CTranslate2 format faster-whisper loads.

    pip install ctranslate2 "transformers<4.47" torch --extra-index-url https://download.pytorch.org/whl/cpu
    python convert_tarteel_model.py out/whisper-base-ar-quran

On the server the output directory lives in the whisper_models volume and the
service is pointed at it (.env):

    WHISPER_MODEL=/root/.cache/huggingface/noorain/whisper-base-ar-quran

The checkpoint ships vocab.json/merges.txt but no tokenizer.json, which
faster-whisper needs, so it is generated here.
"""

import shutil
import sys
from pathlib import Path

import ctranslate2
from transformers import WhisperTokenizerFast

SOURCE = "tarteel-ai/whisper-base-ar-quran"


def main() -> None:
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "whisper-base-ar-quran")
    shutil.rmtree(out, ignore_errors=True)
    converter = ctranslate2.converters.TransformersConverter(SOURCE, copy_files=["preprocessor_config.json"])
    converter.convert(str(out), quantization="int8")
    tokenizer_dir = out / "_tokenizer"
    WhisperTokenizerFast.from_pretrained(SOURCE).save_pretrained(tokenizer_dir)
    shutil.copy(tokenizer_dir / "tokenizer.json", out / "tokenizer.json")
    shutil.rmtree(tokenizer_dir)
    print(f"converted {SOURCE} -> {out}")


if __name__ == "__main__":
    main()
