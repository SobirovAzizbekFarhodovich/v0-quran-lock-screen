#!/usr/bin/env python3
import json
import os
import requests
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HIGHLIGHTS_FILE = ROOT / "app" / "src" / "main" / "assets" / "tadabbur" / "source_highlights.json"
API_KEY = os.getenv("OPENAI_API_KEY", "sk-your-key-here")
API_BASE = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
MODEL = os.getenv("OPENAI_MODEL", "qwen3-4b")

def search(query: str, top_n: int = 5):
    if not HIGHLIGHTS_FILE.exists():
        print("Highlights file not found. Run build_tadabbur_highlights.py first.")
        return

    highlights = json.loads(HIGHLIGHTS_FILE.read_text(encoding="utf-8"))
    
    # 1. Use AI to understand query intent and user state
    print(f"User Query: {query}")
    prompt = f"""
Foydalanuvchi quyidagi holatni yozdi: '{query}'. 
Ushbu holatni tahlil qiling va unga mos keluvchi islomiy kalit so'zlarni, mavzularni (topics), holatlarni (moods) va tavsiya etilgan amallarni (actions) vergul bilan ajratib bering.

Mavzular ro'yxatidan mosini tanlang: 
sabr va taskin, yomon tush va qorquv, charchoq va yengillik, tavba va mag'firat, umid va xavotir, rizq va ish, ota-ona va oila, namoz va ibodat, g'azab va kelishmovchilik, kasallik va shifo, o'lim va qayg'u, nikoh va munosabat, harom istak va nafs, zulm va adolatsizlik, hasad va solishtirish, ilm va o'qish, qaror va maslahat, shukr va qanoat, til va g'iybat.

Javobni FAQAT quyidagi formatda bering:
Topics: mavzu1, mavzu2
Moods: holat1, holat2
Actions: amal1, amal2
Keywords: kalit1, kalit2
"""
    
    try:
        response = requests.post(
            f"{API_BASE}/chat/completions",
            headers={"Authorization": f"Bearer {API_KEY}"},
            json={
                "model": MODEL,
                "messages": [{"role": "user", "content": prompt}],
                "temperature": 0.1
            },
            timeout=15
        )
        ai_response = response.json()['choices'][0]['message']['content'].lower()
        print(f"AI Query Analysis:\n{ai_response}")
        query_analysis = ai_response
    except Exception as e:
        print(f"AI Error: {e}")
        query_analysis = f"keywords: {query.lower()}"

    # 2. Simple semantic scoring (matching query analysis against highlights)
    results = []
    query_terms = set(query_analysis.replace(",", " ").split())
    
    for entry in highlights:
        score = 0
        entry_text = " ".join(entry.get("topics", []) + entry.get("moods", []) + entry.get("highlights", [])).lower()
        
        for term in query_terms:
            if len(term) < 4: continue
            if term in entry_text:
                score += 10
            elif any(term.startswith(t) or t.startswith(term) for t in entry_text.split()):
                score += 5
        
        if score > 0:
            results.append((score, entry))
    
    results.sort(key=lambda x: x[0], reverse=True)
    
    print("\nResults:")
    for score, entry in results[:top_n]:
        print(f"[{score}] {entry['type']} {entry['id']}")
        print(f"  Topics: {', '.join(entry.get('topics', []))}")
        print(f"  Summary: {entry.get('semanticText', '')[:100]}...")
        print("-" * 20)

if __name__ == "__main__":
    import sys
    q = "ichim siqilyapti, hayotdan charchadim" if len(sys.argv) < 2 else sys.argv[1]
    search(q)
