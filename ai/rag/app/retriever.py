"""
HybridRetriever — ChromaDB semantic search + BM25 keyword search.

Qur'oni Karim, Hisnul Muslim, Hadis kitoblari (Riyozus Solihiyn, Bulugh al-Maram, Al-Adhkar),
Qur'oniy duolar, Ibodat va Muqaddas oylar bo'yicha qidiradi va natijalarni birlashtirib qaytaradi.
"""

import json
import os
from typing import Any

import chromadb
from chromadb.config import Settings
from chromadb.utils.embedding_functions import SentenceTransformerEmbeddingFunction
from rank_bm25 import BM25Okapi

# Xuddi build_chromadb.py dagi model bilan bir xil bo'lishi shart
EMBEDDING_FN = SentenceTransformerEmbeddingFunction(
    model_name="sentence-transformers/paraphrase-multilingual-mpnet-base-v2"
)


# Til kodi → ChromaDB collection nomi mappingi
LANG_TO_COLLECTIONS = {
    "uz": [
        "quran_chunks_uz_cyrillic", "quran_chunks_uz_latin",
        "bukhari_uz_cyrillic", "bukhari_uz_latin",
        "muslim_uz_cyrillic", "muslim_uz_latin",
        "hisnul_muslim_uz_cyrillic", "hisnul_muslim_uz_latin",
        "hadith_uz_cyrillic", "hadith_uz_latin",
        "duas_quranic_uz_cyrillic", "duas_quranic_uz_latin",
        "ibodat_uz_cyrillic", "ibodat_uz_latin",
        "sacred_months_uz_cyrillic", "sacred_months_uz_latin",
        "duas_uz_cyrillic", "duas_uz_latin"
    ],
    "uz_cyrillic": [
        "quran_chunks_uz_cyrillic", "bukhari_uz_cyrillic", "muslim_uz_cyrillic", "hisnul_muslim_uz_cyrillic", "hadith_uz_cyrillic",
        "duas_quranic_uz_cyrillic", "ibodat_uz_cyrillic", "sacred_months_uz_cyrillic", "duas_uz_cyrillic"
    ],
    "uz_latin": [
        "quran_chunks_uz_latin", "bukhari_uz_latin", "muslim_uz_latin", "hisnul_muslim_uz_latin", "hadith_uz_latin",
        "duas_quranic_uz_latin", "ibodat_uz_latin", "sacred_months_uz_latin", "duas_uz_latin"
    ],
    "en": [
        "quran_chunks_en", "bukhari_en", "muslim_en", "hisnul_muslim_en", "hadith_en",
        "duas_quranic_en", "ibodat_en", "sacred_months_en"
    ],
    "ru": [
        "quran_chunks_ru", "bukhari_ru", "muslim_ru", "hisnul_muslim_ru", "hadith_ru",
        "duas_quranic_ru", "ibodat_ru", "sacred_months_ru"
    ],
}

# Aniq oyat qidirish uchun verse collectionlar
LANG_TO_VERSE_COLLECTIONS = {
    "uz": ["quran_verses_uz_cyrillic", "quran_verses_uz_latin"],
    "uz_cyrillic": ["quran_verses_uz_cyrillic"],
    "uz_latin": ["quran_verses_uz_latin"],
    "en": ["quran_verses_en"],
    "ru": ["quran_verses_ru"],
}


class HybridRetriever:
    """ChromaDB semantic search + BM25 keyword search aralashmasi."""

    def __init__(
        self,
        chroma_host: str | None = None,
        chroma_port: int | None = None,
    ) -> None:
        self.chroma_host = chroma_host or os.getenv("CHROMA_HOST", "chromadb")
        self.chroma_port = chroma_port or int(os.getenv("CHROMA_PORT", "8000"))
        self.min_semantic_score = float(os.getenv("RAG_MIN_SEMANTIC_SCORE", "0.15"))
        self._client: chromadb.ClientAPI | None = None
        self._bm25_cache: dict[str, tuple[BM25Okapi, list[dict]]] = {}

    @property
    def client(self) -> chromadb.ClientAPI:
        """Lazy ChromaDB client init."""
        if self._client is None:
            self._client = chromadb.HttpClient(
                host=self.chroma_host,
                port=self.chroma_port,
                settings=Settings(anonymized_telemetry=False),
            )
        return self._client

    def search(
        self,
        query: str,
        language: str = "uz",
        limit: int = 6,
    ) -> list[dict[str, Any]]:
        """Hybrid search: ChromaDB semantic + BM25 keyword search.

        Args:
            query: Qidiruv so'rovi
            language: Til kodi (uz, uz_cyrillic, uz_latin, en, ru)
            limit: Qaytariladigan natijalar soni

        Returns:
            Natijalar ro'yxati: [{text, metadata, score, source_type}]
        """
        semantic_results = self._semantic_search(query, language, limit=limit * 2)
        bm25_results = self._bm25_search(query, language, limit=limit * 2)

        combined = self._rrf_merge(semantic_results, bm25_results, k=60)
        return combined[:limit]

    def collection_stats(self) -> dict[str, Any]:
        """Return ChromaDB collection availability and document counts."""
        names = sorted({
            *[name for values in LANG_TO_COLLECTIONS.values() for name in values],
            *[name for values in LANG_TO_VERSE_COLLECTIONS.values() for name in values],
        })
        counts: dict[str, int] = {}
        missing: list[str] = []
        total = 0

        for name in names:
            try:
                collection = self.client.get_collection(name)
                count = collection.count()
                counts[name] = count
                total += count
            except Exception:
                missing.append(name)

        return {
            "total_documents": total,
            "available_collections": len(counts),
            "missing_collections": missing,
            "collections": counts,
        }

    def search_verse(
        self,
        surah: int,
        ayah: int,
        language: str = "uz",
    ) -> dict[str, Any] | None:
        """Aniq oyatni qidiradi (surah + ayah bo'yicha)."""
        collections = LANG_TO_VERSE_COLLECTIONS.get(language, [])
        for col_name in collections:
            try:
                collection = self.client.get_collection(col_name, embedding_function=EMBEDDING_FN)
                doc_id = f"{language}:{surah}:{ayah}"
                result = collection.get(ids=[doc_id], include=["documents", "metadatas"])
                if result["documents"]:
                    return {
                        "text": result["documents"][0],
                        "metadata": result["metadatas"][0] if result["metadatas"] else {},
                    }
            except Exception:
                continue
        return None

    def _semantic_search(
        self,
        query: str,
        language: str,
        limit: int = 12,
    ) -> list[dict[str, Any]]:
        """ChromaDB semantic search."""
        results = []
        collections = LANG_TO_COLLECTIONS.get(language, [])

        for col_name in collections:
            try:
                collection = self.client.get_collection(col_name, embedding_function=EMBEDDING_FN)
                count = collection.count()
                if count <= 0:
                    continue
                query_result = collection.query(
                    query_texts=[query],
                    n_results=min(limit, count),
                    include=["documents", "metadatas", "distances"],
                )

                if query_result["documents"] and query_result["documents"][0]:
                    for i, doc in enumerate(query_result["documents"][0]):
                        distance = query_result["distances"][0][i] if query_result["distances"] else 1.0
                        meta = query_result["metadatas"][0][i] if query_result["metadatas"] else {}
                        score = max(0.0, 1.0 - distance)
                        if score < self.min_semantic_score:
                            continue
                        results.append({
                            "text": doc,
                            "metadata": meta,
                            "score": score,
                            "source_type": "semantic",
                            "_id": f"{col_name}:{meta.get('title', meta.get('surah', ''))}:{i}",
                        })
            except Exception as e:
                continue

        results.sort(key=lambda x: x["score"], reverse=True)
        return results

    def _bm25_search(
        self,
        query: str,
        language: str,
        limit: int = 12,
    ) -> list[dict[str, Any]]:
        """BM25 keyword search — ChromaDB dagi documentlar ustida."""
        results = []
        collections = LANG_TO_COLLECTIONS.get(language, [])

        for col_name in collections:
            try:
                bm25, docs = self._get_bm25_index(col_name)
                if bm25 is None:
                    continue

                tokenized_query = query.lower().split()
                scores = bm25.get_scores(tokenized_query)

                scored_docs = list(zip(scores, docs))
                scored_docs.sort(key=lambda x: x[0], reverse=True)

                for idx, (score, doc) in enumerate(scored_docs[:limit]):
                    if score <= 0:
                        continue
                    results.append({
                        "text": doc["text"],
                        "metadata": doc["metadata"],
                        "score": float(score),
                        "source_type": "bm25",
                        "_id": f"{col_name}:{doc['metadata'].get('title', doc['metadata'].get('surah', ''))}:{idx}",
                    })
            except Exception as e:
                continue

        return results

    def _get_bm25_index(
        self, collection_name: str
    ) -> tuple[BM25Okapi | None, list[dict]]:
        """BM25 index yaratadi yoki keshdan oladi."""
        if collection_name in self._bm25_cache:
            return self._bm25_cache[collection_name]

        try:
            collection = self.client.get_collection(collection_name)
            all_data = collection.get(include=["documents", "metadatas"])

            if not all_data["documents"]:
                self._bm25_cache[collection_name] = (None, [])
                return None, []

            docs = []
            tokenized = []
            for i, doc_text in enumerate(all_data["documents"]):
                meta = all_data["metadatas"][i] if all_data["metadatas"] else {}
                docs.append({"text": doc_text, "metadata": meta})
                tokenized.append(doc_text.lower().split())

            bm25 = BM25Okapi(tokenized)
            self._bm25_cache[collection_name] = (bm25, docs)
            return bm25, docs
        except Exception as e:
            return None, []

    def _rrf_merge(
        self,
        semantic_results: list[dict],
        bm25_results: list[dict],
        k: int = 60,
    ) -> list[dict[str, Any]]:
        """Reciprocal Rank Fusion (RRF)."""
        rrf_scores: dict[str, float] = {}
        result_map: dict[str, dict] = {}

        for rank, item in enumerate(semantic_results):
            doc_id = item["_id"]
            rrf_scores[doc_id] = rrf_scores.get(doc_id, 0) + 1.0 / (k + rank + 1)
            if doc_id not in result_map:
                result_map[doc_id] = item

        for rank, item in enumerate(bm25_results):
            doc_id = item["_id"]
            rrf_scores[doc_id] = rrf_scores.get(doc_id, 0) + 1.0 / (k + rank + 1)
            if doc_id not in result_map:
                result_map[doc_id] = item

        sorted_ids = sorted(rrf_scores.keys(), key=lambda x: rrf_scores[x], reverse=True)

        merged = []
        for doc_id in sorted_ids:
            item = result_map[doc_id]
            merged.append({
                "text": item["text"],
                "metadata": item["metadata"],
                "score": rrf_scores[doc_id],
                "retrieval_score": item["score"],
                "source_type": item["source_type"],
            })

        return merged

    def invalidate_cache(self) -> None:
        """BM25 keshini tozalaydi."""
        self._bm25_cache.clear()
