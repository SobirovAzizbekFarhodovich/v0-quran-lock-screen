#!/usr/bin/env python3
"""
JSONL ma'lumotlarni ChromaDB collectionlariga joylash skripti.

Qur'oni Karim, Hisnul Muslim, Hadis kitoblari (Riyozus Solihiyn, Bulugh al-Maram, Al-Adhkar),
Qur'oniy duolar, Ibodat va Muqaddas oylar bo'yicha to'plangan ma'lumotlarni ChromaDB ga joylaydi.
"""

import argparse
import hashlib
import json
import os
import sys
from pathlib import Path

import chromadb
from chromadb.config import Settings
from chromadb.utils.embedding_functions import SentenceTransformerEmbeddingFunction

# Embedding modelini faqat indeksni haqiqatan qayta qurish kerak bo'lganda
# yuklaymiz. Oldingi variantda tayyor collectionlar ham har deployda modelni
# yuklab, barcha collectionlarni o'chirib qayta hisoblar edi.
_embedding_fn: SentenceTransformerEmbeddingFunction | None = None


def embedding_function() -> SentenceTransformerEmbeddingFunction:
    global _embedding_fn
    if _embedding_fn is None:
        _embedding_fn = SentenceTransformerEmbeddingFunction(
            model_name="sentence-transformers/paraphrase-multilingual-mpnet-base-v2"
        )
    return _embedding_fn


def fingerprint_file(filepath: Path, suffix: str = "") -> str:
    """Manba fayli va indeks parametrlaridan barqaror fingerprint yaratadi."""
    digest = hashlib.sha256()
    with filepath.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    digest.update(suffix.encode("utf-8"))
    return digest.hexdigest()


def load_jsonl(filepath: Path) -> list[dict]:
    """JSONL faylni o'qiydi."""
    entries = []
    with filepath.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                entries.append(json.loads(line))
    return entries


def create_generic_documents(entries: list[dict]) -> tuple[list[str], list[str], list[dict]]:
    """Generic islomiy manbalar (Hisnul Muslim, Hadis, Ibodat, Muqaddas oylar, Duolar) uchun document tayyorlaydi."""
    ids = []
    documents = []
    metadatas = []

    for entry in entries:
        doc_id = f"{entry.get('type', 'doc')}:{entry['lang']}:{entry.get('id', entry.get('surah', '1'))}"
        
        # Plain text
        text_content = f"{entry.get('title', '')}\n{entry.get('text', '')}".strip()
        if "arabic_raw" in entry and entry["arabic_raw"]:
            text_content += f"\nArabcha: {entry['arabic_raw']}"
        if "transliteration" in entry and entry["transliteration"]:
            text_content += f"\nTransliteratsiya: {entry['transliteration']}"
        if "source" in entry and entry["source"]:
            text_content += f"\nManba: {entry['source']}"

        ids.append(doc_id)
        documents.append(text_content)
        
        meta = {
            "title": str(entry.get("title", "")),
            "lang": str(entry.get("lang", "")),
            "type": str(entry.get("type", "")),
            "source": str(entry.get("source", "")),
            "surah": int(entry["surah"]) if "surah" in entry else 0,
            "ayah": int(entry["ayah"]) if "ayah" in entry else 0,
            "book": str(entry.get("book", "")),
            "hadith_number": int(entry["hadith_number"]) if "hadith_number" in entry else 0,
        }
        metadatas.append(meta)

    return ids, documents, metadatas


def create_verse_documents(entries: list[dict]) -> tuple[list[str], list[str], list[dict]]:
    """Har bir oyatni alohida document sifatida tayyorlaydi."""
    ids = []
    documents = []
    metadatas = []

    for entry in entries:
        doc_id = f"{entry['lang']}:{entry['surah']}:{entry['ayah']}"
        ids.append(doc_id)
        documents.append(entry["text"])
        metadatas.append({
            "surah": entry["surah"],
            "ayah": entry["ayah"],
            "lang": entry["lang"],
            "type": entry.get("type", "quran"),
            "source": entry.get("source", "Qur'oni Karim"),
            "surah_name_ar": entry.get("surah_name_ar", ""),
            "surah_name_en": entry.get("surah_name_en", ""),
            "surah_name": entry.get("surah_name", ""),
            "revelation": entry.get("revelation", ""),
        })

    return ids, documents, metadatas


def create_contextual_chunks(
    entries: list[dict], chunk_size: int = 5, overlap: int = 2
) -> tuple[list[str], list[str], list[dict]]:
    """Oyatlarni 5 tadan guruhlab contextual chunk yaratadi (overlap 2 oyat)."""
    ids = []
    documents = []
    metadatas = []

    by_surah: dict[int, list[dict]] = {}
    for entry in entries:
        by_surah.setdefault(entry["surah"], []).append(entry)

    for surah_num, verses in by_surah.items():
        verses.sort(key=lambda x: x["ayah"])

        i = 0
        chunk_idx = 0
        while i < len(verses):
            chunk_verses = verses[i : i + chunk_size]
            if not chunk_verses:
                break

            chunk_text_parts = []
            for v in chunk_verses:
                chunk_text_parts.append(f"[{v['surah']}:{v['ayah']}] {v['text']}")
            chunk_text = "\n".join(chunk_text_parts)

            surah_name = chunk_verses[0].get("surah_name", "")
            surah_name_ar = chunk_verses[0].get("surah_name_ar", "")
            header = f"Sura {surah_num} ({surah_name} / {surah_name_ar})"
            full_text = f"{header}\n{chunk_text}"

            ayah_start = chunk_verses[0]["ayah"]
            ayah_end = chunk_verses[-1]["ayah"]
            lang = chunk_verses[0]["lang"]
            doc_id = f"chunk:{lang}:{surah_num}:{ayah_start}-{ayah_end}"

            ids.append(doc_id)
            documents.append(full_text)
            metadatas.append({
                "surah": surah_num,
                "ayah_start": ayah_start,
                "ayah_end": ayah_end,
                "lang": lang,
                "type": "quran_chunk",
                "source": chunk_verses[0].get("source", "Qur'oni Karim"),
                "surah_name": surah_name,
                "surah_name_ar": surah_name_ar,
                "revelation": chunk_verses[0].get("revelation", ""),
                "chunk_idx": chunk_idx,
            })

            step = max(1, chunk_size - overlap)
            i += step
            chunk_idx += 1

    return ids, documents, metadatas


def ingest_to_chromadb(
    client: chromadb.ClientAPI,
    collection_name: str,
    ids: list[str],
    documents: list[str],
    metadatas: list[dict],
    batch_size: int = 200,
    source_fingerprint: str = "",
    force: bool = False,
) -> bool:
    """ChromaDB collectionga ma'lumot joylaydi."""
    total = len(ids)
    existing = None
    try:
        existing = client.get_collection(collection_name)
    except Exception:
        existing = None

    is_current = (
        existing is not None
        and not force
        and existing.count() == total
        and (existing.metadata or {}).get("source_fingerprint") == source_fingerprint
    )
    if is_current:
        print(f"⏭️  {collection_name}: o'zgarmagan ({total} documents), o'tkazib yuborildi")
        return False
    if existing is not None:
        client.delete_collection(collection_name)

    collection = client.get_or_create_collection(
        name=collection_name,
        metadata={
            "hnsw:space": "cosine",
            "source_fingerprint": source_fingerprint,
        },
        embedding_function=embedding_function(),
    )

    for start in range(0, total, batch_size):
        end = min(start + batch_size, total)
        collection.add(
            ids=ids[start:end],
            documents=documents[start:end],
            metadatas=metadatas[start:end],
        )
        print(f"  [{collection_name}] {end}/{total} documents joylandi")

    print(f"✅ {collection_name}: {total} documents jami")
    return True


def main() -> None:
    parser = argparse.ArgumentParser(description="JSONL → ChromaDB ingest")
    parser.add_argument("--data-dir", type=Path, required=True,
                        help="JSONL fayllar joylashgan papka")
    parser.add_argument("--chroma-host", type=str,
                        default=os.getenv("CHROMA_HOST", "localhost"))
    parser.add_argument("--chroma-port", type=int,
                        default=int(os.getenv("CHROMA_PORT", "8000")))
    parser.add_argument("--chunk-size", type=int, default=5)
    parser.add_argument("--overlap", type=int, default=2)
    parser.add_argument(
        "--force",
        action="store_true",
        help="Collectionlar o'zgarmagan bo'lsa ham majburan qayta qurish",
    )
    args = parser.parse_args()

    print(f"ChromaDB ga ulanish: {args.chroma_host}:{args.chroma_port}")
    client = chromadb.HttpClient(
        host=args.chroma_host,
        port=args.chroma_port,
        settings=Settings(anonymized_telemetry=False),
    )

    try:
        client.heartbeat()
        print("✅ ChromaDB ulanish muvaffaqiyatli")
    except Exception as e:
        print(f"❌ ChromaDB ga ulanib bo'lmadi: {e}")
        sys.exit(1)

    jsonl_files = sorted(args.data_dir.rglob("*.jsonl"))
    if not jsonl_files:
        print(f"❌ JSONL fayllar topilmadi: {args.data_dir}")
        sys.exit(1)

    print(f"\n{len(jsonl_files)} ta JSONL fayl topildi:")
    for f in jsonl_files:
        print(f"  - {f.relative_to(args.data_dir)}")

    for filepath in jsonl_files:
        entries = load_jsonl(filepath)
        if not entries:
            continue

        entry_type = entries[0].get("type", "quran")
        lang = entries[0].get("lang", filepath.stem)

        print(f"\n{'='*60}")
        print(f"Ishlanmoqda: {filepath.name} ({len(entries)} ta yozuv, tur: {entry_type}, til: {lang})")
        print(f"{'='*60}")

        if entry_type == "quran":
            verse_collection = f"quran_verses_{lang}"
            v_ids, v_docs, v_metas = create_verse_documents(entries)
            ingest_to_chromadb(
                client,
                verse_collection,
                v_ids,
                v_docs,
                v_metas,
                source_fingerprint=fingerprint_file(filepath, "quran-verses-v1"),
                force=args.force,
            )

            chunk_collection = f"quran_chunks_{lang}"
            c_ids, c_docs, c_metas = create_contextual_chunks(
                entries, chunk_size=args.chunk_size, overlap=args.overlap
            )
            ingest_to_chromadb(
                client,
                chunk_collection,
                c_ids,
                c_docs,
                c_metas,
                source_fingerprint=fingerprint_file(
                    filepath,
                    f"quran-chunks-v1:{args.chunk_size}:{args.overlap}",
                ),
                force=args.force,
            )
        else:
            collection_name = f"{entry_type}_{lang}"
            d_ids, d_docs, d_metas = create_generic_documents(entries)
            ingest_to_chromadb(
                client,
                collection_name,
                d_ids,
                d_docs,
                d_metas,
                source_fingerprint=fingerprint_file(filepath, "generic-v1"),
                force=args.force,
            )

    print(f"\n{'='*60}")
    print("Barcha collectionlar:")
    for col in client.list_collections():
        count = client.get_collection(col.name).count()
        print(f"  📚 {col.name}: {count} documents")
    print(f"{'='*60}")
    print("✅ Ingest muvaffaqiyatli yakunlandi!")


if __name__ == "__main__":
    main()
