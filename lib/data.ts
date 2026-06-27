import fs from 'fs';
import path from 'path';

// ====== Data Types ======

export interface QuranVerse {
  id: string;
  sura: string;
  aya: string;
  arabic_text: string;
  translation: string;
  footnotes?: string;
}

export interface QuranData {
  source: string;
  translation_key: string;
  result: QuranVerse[];
}

export interface HadithEntry {
  id: string;
  book: string;
  volume: string;
  chapter: string;
  narrator: string;
  text: string;
  source: string;
  reference: string;
}

export interface SourceHighlight {
  id: string;
  type: 'QURAN' | 'HADITH';
  topics: string[];
  moods: string[];
  actions: string[];
  highlights: string[];
  semanticText: string;
}

// ====== Cached Data ======

let versesCache: QuranVerse[] | null = null;
let hadithCache: HadithEntry[] | null = null;
let highlightsCache: Map<string, SourceHighlight> | null = null;

const ASSETS_BASE = path.join(
  process.cwd(),
  'app',
  'src',
  'main',
  'assets'
);

export function loadVerses(): QuranVerse[] {
  if (versesCache) return versesCache;
  try {
    const raw = fs.readFileSync(
      path.join(ASSETS_BASE, 'quran', 'verses.json'),
      'utf-8'
    );
    const data: QuranData = JSON.parse(raw);
    versesCache = data.result || [];
    return versesCache;
  } catch (e) {
    console.error('Failed to load verses.json:', e);
    return [];
  }
}

export function loadHadiths(): HadithEntry[] {
  if (hadithCache) return hadithCache;
  try {
    const raw = fs.readFileSync(
      path.join(ASSETS_BASE, 'hadith', 'hadith_index.json'),
      'utf-8'
    );
    hadithCache = JSON.parse(raw);
    return hadithCache!;
  } catch (e) {
    console.error('Failed to load hadith_index.json:', e);
    return [];
  }
}

export function loadHighlights(): Map<string, SourceHighlight> {
  if (highlightsCache) return highlightsCache;
  try {
    const raw = fs.readFileSync(
      path.join(ASSETS_BASE, 'tadabbur', 'source_highlights.json'),
      'utf-8'
    );
    const list: SourceHighlight[] = JSON.parse(raw);
    highlightsCache = new Map(list.map((h) => [h.id, h]));
    return highlightsCache;
  } catch (e) {
    console.error('Failed to load source_highlights.json:', e);
    return new Map();
  }
}

// ====== Normalize (Uzbek Cyrillic → Latin) ======

const CYRILLIC_MAP: Record<string, string> = {
  а: 'a', б: 'b', в: 'v', г: 'g', д: 'd', е: 'e', ё: 'yo',
  ж: 'j', з: 'z', и: 'i', й: 'y', к: 'k', л: 'l', м: 'm',
  н: 'n', о: 'o', п: 'p', р: 'r', с: 's', т: 't', у: 'u',
  ф: 'f', х: 'x', ц: 's', ч: 'ch', ш: 'sh', щ: 'sh', ъ: '',
  ь: '', э: 'e', ю: 'yu', я: 'ya', ў: 'o', қ: 'q', ғ: 'g',
  ҳ: 'h',
};

export function normalize(value: string): string {
  let s = value.toLowerCase();
  s = s.replace(/[ʼ''`]/g, "'");
  s = s.replace(/g'/g, 'g').replace(/o'/g, 'o').replace(/ʻ/g, '');
  // Cyrillic to Latin
  let result = '';
  for (const ch of s) {
    result += CYRILLIC_MAP[ch] ?? ch;
  }
  result = result.replace(/[^a-z0-9' ]/g, ' ').replace(/\s+/g, ' ').trim();
  return result;
}

const STOP_WORDS = new Set([
  'men', 'meni', 'menga', 'sen', 'siz', 'bilan', 'uchun', 'ham', 'edi',
  'ekan', 'qilib', 'qildi', 'deb', 'bir', 'shu', 'bu', 'u', 'va', 'yoki',
  'lekin', 'agar', 'juda', 'nima', 'qanday', 'bugun', 'hozir',
]);

export function tokenize(value: string): string[] {
  return normalize(value)
    .split(' ')
    .filter((t) => t.length >= 3 && !STOP_WORDS.has(t));
}

// ====== Search Helpers ======

export interface SearchResult {
  type: 'QURAN' | 'HADITH';
  id: string;
  title: string;
  text: string;
  source: string;
  narrator: string;
  topics: string[];
  moods: string[];
  highlights: string[];
  score: number;
}

export function searchByTopicsAndKeywords(
  topics: string[],
  keywords: string[],
  limit: number = 8,
  anchors: string[] = []
): SearchResult[] {
  const highlights = loadHighlights();
  const verses = loadVerses();
  const hadiths = loadHadiths();

  const normalizedTopics = topics.map((t) => normalize(t));
  const normalizedKeywords = keywords.map((k) => normalize(k)).filter((k) => k.length >= 3);

  // Build anchor set: convert "2:153" → "quran_2_153"
  const anchorIdSet = new Set(
    anchors.map((a) => {
      const [s, ay] = a.split(':');
      return `quran_${s}_${ay}`;
    })
  );

  const results: SearchResult[] = [];

  // Score each highlight entry
  for (const [id, meta] of highlights) {
    let score = 0;

    // Anchor boost (strongest signal — curated verses for this topic)
    const isAnchor = anchorIdSet.has(id);
    if (isAnchor) {
      score += 200;
    }

    // Topic matching — exact match is stronger than partial
    const metaTopics = meta.topics.map((t) => normalize(t));
    for (const topic of normalizedTopics) {
      const topicTokens = topic.split(' ').filter((t) => t.length >= 3);
      if (metaTopics.some((mt) => mt === topic)) {
        score += 60; // exact match
      } else if (topicTokens.length > 0 && metaTopics.some((mt) =>
        topicTokens.every((tt) => mt.includes(tt))
      )) {
        score += 30; // all tokens found
      }
    }

    // Mood matching — require min length to avoid false matches
    const metaMoods = meta.moods.map((m) => normalize(m));
    for (const kw of normalizedKeywords) {
      if (kw.length >= 4 && metaMoods.some((mm) => mm === kw || mm.includes(kw))) {
        score += 15;
      }
    }

    // Keyword matching in semanticText
    const semText = meta.semanticText || '';
    let kwMatches = 0;
    for (const kw of normalizedKeywords) {
      if (kw.length >= 4 && semText.includes(kw)) {
        score += 8;
        kwMatches++;
      }
    }
    // Bonus for multiple keyword matches (more relevant)
    if (kwMatches >= 3) score += 15;

    // Highlight matching
    const metaHighlights = meta.highlights.map((h) => normalize(h));
    for (const kw of normalizedKeywords) {
      if (kw.length >= 3 && metaHighlights.some((mh) => mh === kw)) {
        score += 5;
      }
    }

    // Penalty for entries with too many topics (likely noisy/generic metadata)
    if (meta.topics.length > 4 && !isAnchor) {
      score = Math.floor(score * 0.6);
    }

    if (score < 15) continue;

    // Look up full text
    if (meta.type === 'QURAN') {
      const match = id.match(/^quran_(\d+)_(\d+)$/);
      if (!match) continue;
      const [, sura, aya] = match;
      const verse = verses.find((v) => v.sura === sura && v.aya === aya);
      if (!verse) continue;

      results.push({
        type: 'QURAN',
        id,
        title: `${sura}-sura, ${aya}-oyat`,
        text: verse.translation.replace(/\s+/g, ' ').trim(),
        source: `Qur'oni karim, ${sura}:${aya}. Muhammad Sodiq Muhammad Yusuf tarjimasi.`,
        narrator: '',
        topics: meta.topics,
        moods: meta.moods,
        highlights: meta.highlights.slice(0, 6),
        score,
      });
    } else {
      // HADITH
      const hMatch = id.match(/^hadith_(.+)$/);
      if (!hMatch) continue;
      const hadithId = hMatch[1];
      const hadith = hadiths.find((h) => h.id === hadithId);
      if (!hadith) continue;

      results.push({
        type: 'HADITH',
        id,
        title: hadith.reference || hadith.book,
        text: hadith.text.replace(/\s+/g, ' ').trim(),
        source: hadith.source,
        narrator: hadith.narrator || '',
        topics: meta.topics,
        moods: meta.moods,
        highlights: meta.highlights.slice(0, 6),
        score,
      });
    }
  }

  // Sort by score descending, then limit
  results.sort((a, b) => b.score - a.score);

  // Balance: take top Quran + top Hadith
  const quranResults = results.filter((r) => r.type === 'QURAN').slice(0, Math.ceil(limit / 2));
  const hadithResults = results.filter((r) => r.type === 'HADITH').slice(0, Math.ceil(limit / 2));

  return [...quranResults, ...hadithResults].slice(0, limit);
}

// ====== Local Topic Detection (no AI needed) ======

interface TopicTrigger {
  label: string;
  triggers: string[];
  phrases: string[];
  keywords: string[];
  anchors: string[]; // Known relevant Quran verses
}

const TOPIC_TRIGGERS: TopicTrigger[] = [
  {
    label: 'sabr va taskin',
    triggers: ['sabr', 'qiyin', 'sinov', 'dard', 'xafa', 'siqil', 'siqilyap', 'musibat', 'qaygu', 'gam', 'tushkun', 'charchadim', 'ezildim', 'ogir', 'muammo', 'qiynalyap'],
    phrases: ['konglim siqilyapti', 'ichim siqilyapti', 'hayotim ogir', 'ichim siqildi', 'dilim ogriyapti', 'hayotdan charchadim'],
    keywords: ['sabr', 'musibat', 'xafa', 'qaygu', 'dard', 'rahmat', 'ajr', 'qalb', 'zikr', 'xotirjam', 'osonlik', 'qiyinchilik'],
    anchors: ['2:45', '2:153', '2:155', '2:156', '2:157', '13:28', '39:53', '94:5', '94:6'],
  },
  {
    label: 'yomon tush va qorquv',
    triggers: ['tush', 'qorqinchli', 'qorqdim', 'vahima', 'bosinqira', 'shayton', 'panoh'],
    phrases: ['yomon tush', 'qorqinchli tush', 'tushimda yomon', 'shaytondan panoh'],
    keywords: ['tush', 'uyqu', 'shayton', 'panoh', 'qorq', 'vahima'],
    anchors: ['113:1', '113:2', '113:3', '113:4', '113:5', '114:1', '114:2', '114:3', '114:4', '114:5', '114:6'],
  },
  {
    label: "tavba va mag'firat",
    triggers: ['tavba', 'gunoh', 'kechir', 'afsus', 'magfirat', 'pushaymon'],
    phrases: ['gunoh qilib', 'alloh kechir', 'tavba qil', 'pushaymon bol'],
    keywords: ['tavba', 'gunoh', 'magfirat', 'kechir', 'pushaymon', 'rahmat', 'zulm'],
    anchors: ['3:135', '25:70', '39:53', '66:8'],
  },
  {
    label: 'umid va xavotir',
    triggers: ['umid', 'noumid', 'yolgiz', 'qorq', 'xavotir', 'vahima', 'bezovta', 'tashvish', 'kelajak', 'qorqyapman'],
    phrases: ['hech kimim yoq', 'kelajagimdan qorqaman', 'xavotir bosyapti', 'umidim qolmadi'],
    keywords: ['umid', 'rahmat', 'qorq', 'xavf', 'mahzun', 'qalb', 'xotirjam', 'noumid'],
    anchors: ['2:112', '2:277', '3:139', '13:28', '39:53'],
  },
  {
    label: 'rizq va ish',
    triggers: ['rizq', 'pul', 'ish', 'qarz', 'halol', 'kambagal', 'ishsiz', 'savdo'],
    phrases: ['pulim yoq', 'ishim yurishmayapti', 'qarzim bor', 'halol pul'],
    keywords: ['rizq', 'halol', 'sadaqa', 'kasb', 'tavakkal', 'taqvo', 'qarz'],
    anchors: ['11:6', '29:60', '65:2', '65:3'],
  },
  {
    label: 'ota-ona va oila',
    triggers: ['ota', 'ona', 'farzand', 'onam', 'otam', 'oilam', 'qarindosh'],
    phrases: ['ota onam bilan', 'onam xafa', 'otam bilan'],
    keywords: ['ota', 'ona', 'yaxshilik', 'qarindosh', 'farzand', 'ehson'],
    anchors: ['17:23', '17:24', '31:14', '46:15'],
  },
  {
    label: 'namoz va ibodat',
    triggers: ['namoz', 'ibodat', 'dangasa', 'masjid', 'bomdod', 'qilolmayap'],
    phrases: ['namozga dangasa', 'namoz oqiy olmayap', 'ibodatda sust', 'bomdodga turolmay'],
    keywords: ['namoz', 'ibodat', 'masjid', 'jamoat', 'sajda', 'bomdod', 'zikr'],
    anchors: ['2:45', '2:153', '20:14', '29:45'],
  },
  {
    label: "g'azab va kelishmovchilik",
    triggers: ['gazab', 'jahl', 'kechirolmayap', 'alam', 'urish', 'asab', 'jahlim'],
    phrases: ['jahlim chiqyapti', 'kechira olmayapman', 'asabim buzildi'],
    keywords: ['gazab', 'jahl', 'kechir', 'afv', 'sabr', 'yaxshilik'],
    anchors: ['3:134', '7:199', '41:34', '42:40'],
  },
  {
    label: 'kasallik va shifo',
    triggers: ['kasal', 'ogriyapti', 'shifo', 'davo', 'doktor', 'bemor', 'soglik'],
    phrases: ['kasal bolib', 'sogligim yomon', 'dardimga shifo'],
    keywords: ['kasal', 'bemor', 'shifo', 'dard', 'zarar', 'sabr'],
    anchors: ['17:82', '21:83', '21:84', '26:80', '41:44'],
  },
  {
    label: "o'lim va qayg'u",
    triggers: ['olim', 'vafot', 'janoza', 'qabr', 'yoqotdim', 'judolik', 'motam'],
    phrases: ['yaqinim vafot', 'olimdan qorqaman'],
    keywords: ['olim', 'vafot', 'janoza', 'qabr', 'musibat', 'sabr'],
    anchors: ['2:156', '2:157', '3:185', '21:35', '29:57'],
  },
  {
    label: 'nikoh va munosabat',
    triggers: ['nikoh', 'xotin', 'erim', 'turmush', 'sevgi', 'ajrash', 'uylan'],
    phrases: ['xotinim bilan', 'erim bilan', 'turmushim ogir'],
    keywords: ['nikoh', 'xotin', 'juft', 'oila', 'rahmat', 'yaxshilik'],
    anchors: ['4:19', '30:21', '65:2'],
  },
  {
    label: 'harom istak va nafs',
    triggers: ['nafs', 'shahvat', 'zino', 'harom', 'vasvasa', 'istak'],
    phrases: ['harom narsaga', 'nafsimni yenga', 'zinodan qorq'],
    keywords: ['nafs', 'shahvat', 'zino', 'harom', 'fahsh', 'pok', 'taqvo'],
    anchors: ['17:32', '23:5', '23:6', '24:30', '24:31', '29:45'],
  },
  {
    label: "ilm va o'qish",
    triggers: ['ilm', 'oqish', 'dars', 'imtihon', 'ustoz', 'talaba', 'bilim'],
    phrases: ['oqishim qiyin', 'imtihondan qorq', 'ilm olish'],
    keywords: ['ilm', 'bilim', 'hikmat', 'ustoz', 'talaba'],
    anchors: ['20:114', '39:9', '58:11', '96:1'],
  },
  {
    label: 'shukr va qanoat',
    triggers: ['shukr', 'qanoat', 'noshukur', 'baxtsiz', 'norozi'],
    phrases: ['shukr qilolmay', 'baxtsiz his'],
    keywords: ['shukr', 'nemat', 'qanoat', 'rizq', 'ziyoda', 'fazl'],
    anchors: ['14:7', '16:18', '55:13', '93:11'],
  },
];

export function detectTopicsFromQuery(query: string): {
  topics: string[];
  keywords: string[];
  anchors: string[];
} {
  const normalized = normalize(query);
  const tokens = tokenize(query);
  const tokenSet = new Set(tokens);

  const matched: { label: string; score: number; keywords: string[]; anchors: string[] }[] = [];

  for (const topic of TOPIC_TRIGGERS) {
    let score = 0;

    // Phrase matching (strongest)
    for (const phrase of topic.phrases) {
      if (normalized.includes(normalize(phrase))) {
        score += 20;
      }
    }

    // Trigger matching
    for (const trigger of topic.triggers) {
      const normalizedTrigger = normalize(trigger);
      if (tokenSet.has(normalizedTrigger)) {
        score += 10;
      } else if (tokens.some((t) => t.startsWith(normalizedTrigger) || normalizedTrigger.startsWith(t))) {
        score += 5;
      }
    }

    if (score >= 10) {
      matched.push({
        label: topic.label,
        score,
        keywords: topic.keywords,
        anchors: topic.anchors,
      });
    }
  }

  matched.sort((a, b) => b.score - a.score);
  const topMatches = matched.slice(0, 3);

  return {
    topics: topMatches.map((m) => m.label),
    keywords: [...new Set(topMatches.flatMap((m) => m.keywords))],
    anchors: [...new Set(topMatches.flatMap((m) => m.anchors))],
  };
}
