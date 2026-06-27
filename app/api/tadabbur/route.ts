import { NextRequest, NextResponse } from 'next/server';
import { searchByTopicsAndKeywords, detectTopicsFromQuery, type SearchResult } from '@/lib/data';

// ====== Qwen AI via OpenRouter ======

const OPENROUTER_API_KEY = process.env.OPENROUTER_API_KEY || '';
const QWEN_MODEL = process.env.QWEN_MODEL || 'qwen/qwen3.6-flash';
const OPENROUTER_URL = 'https://openrouter.ai/api/v1/chat/completions';

interface QwenAnalysis {
  topics: string[];
  moods: string[];
  keywords: string[];
  userState: string;
}

interface QwenResponse {
  intro: string;
  aiInsight: string;
  evidence: {
    type: string;
    title: string;
    text: string;
    source: string;
    narrator: string;
    highlights: string[];
    aiRelevance: string;
  }[];
}

const VALID_TOPICS = [
  'sabr va taskin',
  'yomon tush va qorquv',
  'charchoq va yengillik',
  "tavba va mag'firat",
  'umid va xavotir',
  'rizq va ish',
  'ota-ona va oila',
  'namoz va ibodat',
  "g'azab va kelishmovchilik",
  'kasallik va shifo',
  "o'lim va qayg'u",
  'nikoh va munosabat',
  'harom istak va nafs',
  'zulm va adolatsizlik',
  'hasad va solishtirish',
  "ilm va o'qish",
  'qaror va maslahat',
  'shukr va qanoat',
  "til va g'iybat",
  'niyat va ixlos',
];

async function callQwen(
  messages: { role: string; content: string }[],
  jsonMode: boolean = false
): Promise<string | null> {
  if (!OPENROUTER_API_KEY || OPENROUTER_API_KEY === 'your-openrouter-api-key-here') {
    return null;
  }

  try {
    const body: Record<string, unknown> = {
      model: QWEN_MODEL,
      messages,
      temperature: 0.3,
      max_tokens: 2000,
    };

    if (jsonMode) {
      body.response_format = { type: 'json_object' };
    }

    const response = await fetch(OPENROUTER_URL, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${OPENROUTER_API_KEY}`,
        'Content-Type': 'application/json',
        'HTTP-Referer': 'https://quran-reminder.app',
        'X-Title': 'Quran Reminder Tadabbur AI',
      },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      console.error('OpenRouter error:', response.status, await response.text());
      return null;
    }

    const data = await response.json();
    const content = data?.choices?.[0]?.message?.content;

    if (!content) return null;

    // Qwen3 modellar <think>...</think> teg ichida reasoning qiladi
    // Biz faqat asosiy javobni olamiz
    if (content.includes('</think>')) {
      const thinkEndIndex = content.lastIndexOf('</think>');
      const afterThink = content.slice(thinkEndIndex + '</think>'.length).trim();
      return afterThink || content;
    }

    return content;
  } catch (error) {
    console.error('Qwen API call failed:', error);
    return null;
  }
}

async function analyzeUserQuery(query: string): Promise<QwenAnalysis | null> {
  const prompt = `Siz islomiy ruhiy maslahatchi sifatida foydalanuvchining holatini tahlil qilasiz.

Foydalanuvchi yozdi: "${query}"

Bu insonnning ruhiy, jismoniy yoki hayotiy holatini tahlil qiling. Quyidagi ro'yxatdan ENG MOS mavzularni tanlang:
${VALID_TOPICS.map((t) => `- ${t}`).join('\n')}

FAQAT quyidagi JSON formatida javob bering (boshqa hech narsa qo'shmang):
{
  "topics": ["eng mos mavzu1", "mos mavzu2"],
  "moods": ["foydalanuvchining holati1", "holati2"],
  "keywords": ["kalit_soz1", "kalit_soz2", "kalit_soz3"],
  "userState": "foydalanuvchining holatini qisqacha tavsifi"
}`;

  const content = await callQwen(
    [{ role: 'user', content: prompt }],
    true
  );
  if (!content) return null;

  try {
    // JSON ni topish (ba'zan Qwen qo'shimcha matn qo'shishi mumkin)
    const jsonMatch = content.match(/\{[\s\S]*\}/);
    if (!jsonMatch) return null;
    return JSON.parse(jsonMatch[0]) as QwenAnalysis;
  } catch {
    console.error('Failed to parse Qwen analysis:', content);
    return null;
  }
}

async function generatePersonalResponse(
  query: string,
  analysis: QwenAnalysis,
  evidence: SearchResult[]
): Promise<{ intro: string; aiInsight: string; evidenceNotes: Map<string, string> }> {
  const evidenceSummary = evidence
    .slice(0, 6)
    .map((ev, i) => {
      const shortText = ev.text.length > 300 ? ev.text.slice(0, 300) + '...' : ev.text;
      return `${i + 1}. [${ev.type}] ${ev.title}: "${shortText}"`;
    })
    .join('\n');

  const prompt = `Siz Qur'on va Hadis bo'yicha bilimdon, mehribon islomiy maslahatchi sifatida javob berasiz.

Foydalanuvchining holati: "${query}"
Tahlil: ${analysis.userState}
Mavzular: ${analysis.topics.join(', ')}

Topilgan dalillar:
${evidenceSummary}

Vazifangiz:
1. "intro" — Foydalanuvchiga mehribon, taskin beruvchi 2-3 gaplik kirish so'z yozing. Uning holatini tushunganingizni ko'rsating. Fatvo bermang, faqat dalillarga yo'naltiring.
2. "aiInsight" — 3-5 gaplik shaxsiy maslahat. Foydalanuvchining aniq holatiga mos, amaliy va ruhiy jihatdan foydali bo'lsin. Qur'on va hadis asosida bo'lsin.
3. Har bir dalil uchun "evidence_notes" — nima uchun aynan SHU dalil foydalanuvchining holatiga mos kelishini 1 gapda tushuntiring.

FAQAT JSON formatida javob bering:
{
  "intro": "kirish so'z",
  "aiInsight": "shaxsiy maslahat",
  "evidence_notes": {
    "1": "1-dalil uchun izoh",
    "2": "2-dalil uchun izoh"
  }
}`;

  const content = await callQwen(
    [{ role: 'user', content: prompt }],
    true
  );

  const defaultResult = {
    intro: `Holatingizga mos dalillar quyida. Javob faqat Qur'on oyatlari va hadis matnlaridan tuzildi.`,
    aiInsight: '',
    evidenceNotes: new Map<string, string>(),
  };

  if (!content) return defaultResult;

  try {
    const jsonMatch = content.match(/\{[\s\S]*\}/);
    if (!jsonMatch) return defaultResult;
    const parsed = JSON.parse(jsonMatch[0]);

    const evidenceNotes = new Map<string, string>();
    if (parsed.evidence_notes) {
      for (const [key, value] of Object.entries(parsed.evidence_notes)) {
        evidenceNotes.set(key, value as string);
      }
    }

    return {
      intro: parsed.intro || defaultResult.intro,
      aiInsight: parsed.aiInsight || parsed.ai_insight || '',
      evidenceNotes,
    };
  } catch {
    console.error('Failed to parse Qwen response:', content);
    return defaultResult;
  }
}

// ====== API Route Handler ======

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const query = body.query?.trim();
    const localTopics: string[] = body.localTopics || [];

    if (!query || query.length < 2) {
      return NextResponse.json(
        { error: "Iltimos, holatingizni yozing." },
        { status: 400 }
      );
    }

    // Step 1: AI bilan foydalanuvchi holatini tahlil qilish
    const analysis = await analyzeUserQuery(query);

    // Lokal topic detection (har doim ishlaydi — AI bo'lmasa ham)
    const localDetection = detectTopicsFromQuery(query);

    // AI natijasi bor bo'lsa — uni ishlatamiz, bo'lmasa lokal detection
    const effectiveTopics = analysis?.topics?.length
      ? analysis.topics
      : localDetection.topics.length
        ? localDetection.topics
        : localTopics.length
          ? localTopics
          : ['sabr va taskin'];

    const effectiveKeywords = analysis?.keywords?.length
      ? analysis.keywords
      : localDetection.keywords.length
        ? localDetection.keywords
        : query.toLowerCase().split(/\s+/).filter((w: string) => w.length >= 3);

    // Step 2: Dalillarni qidirish (anchors = curated verse IDs from local detection)
    const effectiveAnchors = localDetection.anchors || [];
    const searchResults = searchByTopicsAndKeywords(
      effectiveTopics,
      effectiveKeywords,
      8,
      effectiveAnchors
    );

    if (searchResults.length === 0) {
      return NextResponse.json({
        intro: "Bu holat bo'yicha Qur'on va hadis manbalaridan aniq mos dalil topilmadi. Boshqa so'zlar bilan qayta yozing.",
        aiInsight: '',
        evidence: [],
      });
    }

    // Step 3: AI bilan shaxsiy javob yaratish
    const personalResponse = analysis
      ? await generatePersonalResponse(query, analysis, searchResults)
      : {
          intro: `Holatingizga mos dalillar quyida. Javob faqat Qur'on oyatlari va hadis matnlaridan tuzildi.`,
          aiInsight: '',
          evidenceNotes: new Map<string, string>(),
        };

    // Step 4: Natijani formatlash
    const evidence = searchResults.slice(0, 6).map((result, index) => ({
      type: result.type,
      title: result.title,
      text: result.text.length > 1200 ? result.text.slice(0, 1200) + '...' : result.text,
      source: result.source,
      narrator: result.narrator,
      highlights: result.highlights,
      aiRelevance: personalResponse.evidenceNotes.get(String(index + 1)) || '',
    }));

    const response: QwenResponse = {
      intro: personalResponse.intro,
      aiInsight: personalResponse.aiInsight,
      evidence,
    };

    return NextResponse.json(response);
  } catch (error) {
    console.error('Tadabbur API error:', error);
    return NextResponse.json(
      { error: "Serverda xatolik yuz berdi." },
      { status: 500 }
    );
  }
}

// Health check
export async function GET() {
  const hasKey = !!OPENROUTER_API_KEY && OPENROUTER_API_KEY !== 'your-openrouter-api-key-here';
  return NextResponse.json({
    status: 'ok',
    model: QWEN_MODEL,
    hasApiKey: hasKey,
    message: hasKey
      ? 'Tadabbur AI tayyor.'
      : 'OPENROUTER_API_KEY .env.local faylida sozlanmagan. OpenRouter.ai dan API key oling.',
  });
}
