"""
HybridRetriever — ChromaDB semantic search + BM25 keyword search.

Qur'oni Karim, Hisnul Muslim, Hadis kitoblari (Riyozus Solihiyn, Bulugh al-Maram, Al-Adhkar),
Qur'oniy duolar, Ibodat va Muqaddas oylar bo'yicha qidiradi va natijalarni birlashtirib qaytaradi.

All methods are synchronous (ChromaDB HTTP client + local embedding model);
async callers must run them in a worker thread (asyncio.to_thread).
"""

import hashlib
import logging
import os
import threading
import time
from typing import Any

import chromadb
from chromadb.config import Settings
from chromadb.utils.embedding_functions import SentenceTransformerEmbeddingFunction
from rank_bm25 import BM25Okapi

from .helpers import (
    LANG_TO_COLLECTIONS,
    LANG_TO_VERSE_COLLECTIONS,
    enrich_metadata,
    normalize_and_rank,
    tokenize,
    verse_doc_id,
)

logger = logging.getLogger("rag.retriever")

# Xuddi build_chromadb.py dagi model bilan bir xil bo'lishi shart
EMBEDDING_MODEL = "sentence-transformers/paraphrase-multilingual-mpnet-base-v2"

__all__ = ["HybridRetriever", "LANG_TO_COLLECTIONS", "LANG_TO_VERSE_COLLECTIONS"]

_embedding_fn: SentenceTransformerEmbeddingFunction | None = None
_embedding_lock = threading.Lock()


def embedding_function() -> SentenceTransformerEmbeddingFunction:
    """Load the embedding model lazily (not at import time)."""
    global _embedding_fn
    if _embedding_fn is None:
        with _embedding_lock:
            if _embedding_fn is None:
                _embedding_fn = SentenceTransformerEmbeddingFunction(model_name=EMBEDDING_MODEL)
    return _embedding_fn


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
        # A BM25 index built while the collection was still being (re)built
        # would otherwise be stale forever; re-check the count periodically.
        self.bm25_refresh_seconds = float(os.getenv("RAG_BM25_REFRESH_SECONDS", "600"))
        self._client: chromadb.ClientAPI | None = None
        self._client_lock = threading.Lock()
        # name -> (bm25, docs, doc_count, built_at)
        self._bm25_cache: dict[str, tuple[BM25Okapi | None, list[dict], int, float]] = {}
        self._bm25_locks: dict[str, threading.Lock] = {}
        self._bm25_locks_guard = threading.Lock()

    @property
    def client(self) -> chromadb.ClientAPI:
        """Lazy ChromaDB client init."""
        if self._client is None:
            with self._client_lock:
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
            language: Til kodi (uz=uz_latin, uz_cyrillic, uz_latin, en, ru)
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
            except Exception as exc:
                logger.info("collection %s unavailable: %s", name, exc)
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
                collection = self.client.get_collection(col_name)
                doc_id = verse_doc_id(col_name, surah, ayah)
                result = collection.get(ids=[doc_id], include=["documents", "metadatas"])
                if result["documents"]:
                    return {
                        "text": result["documents"][0],
                        "metadata": result["metadatas"][0] if result["metadatas"] else {},
                    }
            except Exception:
                logger.warning("verse lookup failed in %s", col_name, exc_info=True)
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
        if not collections:
            return results

        # Embed the query ONCE instead of once per collection.
        try:
            query_embeddings = embedding_function()([query])
        except Exception:
            logger.exception("query embedding failed")
            return results

        for col_name in collections:
            try:
                collection = self.client.get_collection(col_name)
                count = collection.count()
                if count <= 0:
                    continue
                query_result = collection.query(
                    query_embeddings=query_embeddings,
                    n_results=min(limit, count),
                    include=["documents", "metadatas", "distances"],
                )

                if query_result["documents"] and query_result["documents"][0]:
                    ids = (query_result.get("ids") or [[]])[0]
                    for i, doc in enumerate(query_result["documents"][0]):
                        distance = query_result["distances"][0][i] if query_result["distances"] else 1.0
                        meta = query_result["metadatas"][0][i] if query_result["metadatas"] else {}
                        score = max(0.0, 1.0 - distance)
                        if score < self.min_semantic_score:
                            continue
                        doc_id = ids[i] if i < len(ids) else ""
                        doc_hash = hashlib.md5(doc.encode("utf-8")).hexdigest()[:16]
                        results.append({
                            "text": doc,
                            "metadata": enrich_metadata(meta or {}, doc, doc_id),
                            "score": score,
                            "source_type": "semantic",
                            "_id": f"{col_name}:{doc_hash}",
                        })
            except Exception as exc:
                logger.warning("semantic search failed in %s: %s", col_name, exc)
                continue

        results.sort(key=lambda x: x["score"], reverse=True)
        return results

    def _bm25_search(
        self,
        query: str,
        language: str,
        limit: int = 12,
    ) -> list[dict[str, Any]]:
        """BM25 keyword search — ChromaDB dagi documentlar ustida.

        Scores are normalized per collection and the hits are sorted globally,
        so the RRF rank reflects relevance rather than collection order.
        """
        tokenized_query = tokenize(query)
        if not tokenized_query:
            return []
        per_collection: list[list[dict[str, Any]]] = []
        collections = LANG_TO_COLLECTIONS.get(language, [])

        for col_name in collections:
            try:
                bm25, docs = self._get_bm25_index(col_name)
                if bm25 is None:
                    continue

                scores = bm25.get_scores(tokenized_query)
                scored_docs = sorted(zip(scores, docs), key=lambda x: x[0], reverse=True)

                hits = []
                for score, doc in scored_docs[:limit]:
                    if score <= 0:
                        continue
                    doc_hash = hashlib.md5(doc["text"].encode("utf-8")).hexdigest()[:16]
                    hits.append({
                        "text": doc["text"],
                        "metadata": doc["metadata"],
                        "score": float(score),
                        "source_type": "bm25",
                        "_id": f"{col_name}:{doc_hash}",
                    })
                per_collection.append(hits)
            except Exception as exc:
                logger.warning("bm25 search failed in %s: %s", col_name, exc)
                continue

        return normalize_and_rank(per_collection, limit)

    def _bm25_lock(self, collection_name: str) -> threading.Lock:
        with self._bm25_locks_guard:
            return self._bm25_locks.setdefault(collection_name, threading.Lock())

    def _get_bm25_index(
        self, collection_name: str
    ) -> tuple[BM25Okapi | None, list[dict]]:
        """BM25 index yaratadi yoki keshdan oladi."""
        cached = self._bm25_cache.get(collection_name)
        if cached is not None and time.monotonic() - cached[3] < self.bm25_refresh_seconds:
            return cached[0], cached[1]

        with self._bm25_lock(collection_name):
            cached = self._bm25_cache.get(collection_name)
            now = time.monotonic()
            if cached is not None and now - cached[3] < self.bm25_refresh_seconds:
                return cached[0], cached[1]
            try:
                collection = self.client.get_collection(collection_name)
                count = collection.count()
                if cached is not None and cached[2] == count:
                    # Unchanged: keep the index, just restart the timer.
                    self._bm25_cache[collection_name] = (cached[0], cached[1], count, now)
                    return cached[0], cached[1]

                all_data = collection.get(include=["documents", "metadatas"])
                if not all_data["documents"]:
                    self._bm25_cache[collection_name] = (None, [], 0, now)
                    return None, []

                docs = []
                tokenized = []
                ids = all_data.get("ids") or []
                for i, doc_text in enumerate(all_data["documents"]):
                    meta = all_data["metadatas"][i] if all_data["metadatas"] else {}
                    doc_id = ids[i] if i < len(ids) else ""
                    docs.append({
                        "text": doc_text,
                        "metadata": enrich_metadata(meta or {}, doc_text, doc_id),
                    })
                    tokenized.append(tokenize(doc_text) or ["_"])

                bm25 = BM25Okapi(tokenized)
                self._bm25_cache[collection_name] = (bm25, docs, len(docs), now)
                return bm25, docs
            except Exception as exc:
                logger.warning("bm25 index build failed for %s: %s", collection_name, exc)
                if cached is not None:
                    return cached[0], cached[1]
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
