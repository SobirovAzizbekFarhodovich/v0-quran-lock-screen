#!/usr/bin/env python3
"""
Converts Tarteel's Quran fine-tunes of Whisper (tarteel-ai/whisper-base-ar-quran,
tarteel-ai/whisper-tiny-ar-quran, Apache-2.0) to the CTranslate2 format
faster-whisper loads.

    # ctranslate2 4.8 passes dtype= to from_pretrained: transformers 4.56+ is needed.
    pip install ctranslate2==4.8.2 "transformers>=4.56,<5" torch --extra-index-url https://download.pytorch.org/whl/cpu
    python convert_tarteel_model.py out/whisper-base-ar-quran
    python convert_tarteel_model.py out/whisper-tiny-ar-quran tarteel-ai/whisper-tiny-ar-quran

On the server the output directories live in the whisper_models volume and the
service is pointed at them (.env / defaults):

    WHISPER_MODEL=/root/.cache/huggingface/noorain/whisper-base-ar-quran
    RECITATION_PARTIAL_MODEL=/root/.cache/huggingface/noorain/whisper-tiny-ar-quran

The checkpoint ships vocab.json/merges.txt but no tokenizer.json, which
faster-whisper needs, so it is generated here.
"""

import shutil
import sys
from pathlib import Path

import ctranslate2
from transformers import WhisperTokenizerFast

DEFAULT_SOURCE = "tarteel-ai/whisper-base-ar-quran"


def main() -> None:
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "whisper-base-ar-quran")
    source = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_SOURCE
    shutil.rmtree(out, ignore_errors=True)
    converter = ctranslate2.converters.TransformersConverter(source, copy_files=["preprocessor_config.json"])
    converter.convert(str(out), quantization="int8")
    tokenizer_dir = out / "_tokenizer"
    WhisperTokenizerFast.from_pretrained(source).save_pretrained(tokenizer_dir)
    shutil.copy(tokenizer_dir / "tokenizer.json", out / "tokenizer.json")
    shutil.rmtree(tokenizer_dir)
    print(f"converted {source} -> {out}")


if __name__ == "__main__":
    main()
