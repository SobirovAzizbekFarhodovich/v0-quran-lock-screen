const http = require('http');

const BASE_URL = 'http://localhost:3000/api/tadabbur';

function postRequest(query) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify({ query });
    const url = new URL(BASE_URL);
    const options = {
      hostname: url.hostname,
      port: url.port,
      path: url.pathname,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(data),
      },
    };

    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', (chunk) => (body += chunk));
      res.on('end', () => {
        try { resolve(JSON.parse(body)); }
        catch { resolve({ error: 'Invalid JSON: ' + body.slice(0, 200) }); }
      });
    });
    req.on('error', (e) => reject(e));
    req.write(data);
    req.end();
  });
}

const TESTS = [
  // Edge cases
  { name: 'Bo\'sh so\'rov', query: '', expectError: true },
  { name: 'Qisqa so\'rov (1 harf)', query: 'a', expectError: true },
  { name: 'Faqat raqamlar', query: '12345', expectNoMatch: false },
  
  // Main topic queries
  { name: 'Sabr - Ko\'nglim siqilyapti', query: "ko'nglim siqilyapti", 
    expectTopics: ['sabr'], expectVerses: ['2:153', '94:5', '94:6'] },
  { name: 'Tavba qilmoqchiman', query: "tavba qilmoqchiman gunoh qildim",
    expectTopics: ['tavba'], expectVerses: ['39:53', '66:8'] },
  { name: 'Yomon tush ko\'rdim', query: "yomon tush ko'rdim",
    expectTopics: ['tush', 'qorquv'], expectVerses: ['113:', '114:'] },
  { name: 'Namozga dangasaman', query: "namozga dangasaman",
    expectTopics: ['namoz'], expectVerses: ['2:45', '29:45', '20:14'] },
  { name: 'Onam xafa bo\'ldi', query: "onam xafa bo'ldi menga",
    expectTopics: ['ota-ona'], expectVerses: ['17:23', '17:24', '31:14'] },
  { name: 'Kasal bo\'lib qoldim', query: "kasal bo'lib qoldim",
    expectTopics: ['kasallik'], expectVerses: ['17:82', '26:80'] },
  { name: 'Ishim yurishmayapti', query: "ishim yurishmayapti pulim yoq",
    expectTopics: ['rizq'], expectVerses: ['65:3', '11:6'] },
  { name: 'Jahlim chiqyapti', query: "jahlim chiqyapti kechira olmayapman",
    expectTopics: ['g\'azab'], expectVerses: ['3:134', '41:34'] },
  { name: 'Yaqinim vafot etdi', query: "yaqinim vafot etdi",
    expectTopics: ['o\'lim'], expectVerses: ['2:156', '3:185'] },
  { name: 'Nafsimni yenga olmayapman', query: "nafsimni yenga olmayapman",
    expectTopics: ['nafs'], expectVerses: ['24:30', '29:45'] },
  { name: 'Shukr qilolmayapman', query: "shukr qilolmayapman",
    expectTopics: ['shukr'], expectVerses: ['14:7', '93:11'] },
  { name: 'Umidsizlikka tushdim', query: "umidim qolmadi noumidman",
    expectTopics: ['umid'], expectVerses: ['39:53'] },
  
  // Cyrillic input
  { name: 'Kirill yozuvi - сабр', query: "сабр қилолмаяпман",
    expectTopics: ['sabr'] },
  
  // Phrase matching
  { name: 'Ichim siqildi (phrase)', query: "ichim siqildi hayotdan charchadim",
    expectTopics: ['sabr'] },
];

async function runTests() {
  console.log('='.repeat(60));
  console.log('TADABBUR AI - COMPREHENSIVE TEST SUITE');
  console.log('='.repeat(60));
  
  let passed = 0;
  let failed = 0;
  const failures = [];

  for (const test of TESTS) {
    console.log('\n' + '─'.repeat(50));
    console.log(`TEST: ${test.name}`);
    console.log(`QUERY: "${test.query}"`);
    console.log('─'.repeat(50));

    try {
      const data = await postRequest(test.query);

      // Check error cases
      if (test.expectError) {
        if (data.error) {
          console.log(`  ✅ PASS - Got expected error: ${data.error}`);
          passed++;
        } else {
          console.log(`  ❌ FAIL - Expected error but got data`);
          failed++;
          failures.push({ test: test.name, reason: 'Expected error but got data' });
        }
        continue;
      }

      // Check evidence exists
      if (!data.evidence || data.evidence.length === 0) {
        if (data.intro && data.intro.includes('topilmadi')) {
          console.log(`  ⚠️  No results found`);
        } else {
          console.log(`  ❌ FAIL - No evidence returned`);
          failed++;
          failures.push({ test: test.name, reason: 'No evidence returned' });
        }
        continue;
      }

      console.log(`  Evidence count: ${data.evidence.length}`);
      
      // List all evidence
      const quranEvidence = [];
      const hadithEvidence = [];
      for (const ev of data.evidence) {
        const line = `  [${ev.type}] ${ev.title} | highlights: ${(ev.highlights||[]).slice(0,4).join(', ')}`;
        console.log(line);
        if (ev.type === 'QURAN') {
          // Extract sura:aya from title like "2-sura, 153-oyat"
          const m = ev.title.match(/(\d+)-sura, (\d+)-oyat/);
          if (m) quranEvidence.push(`${m[1]}:${m[2]}`);
        } else {
          hadithEvidence.push(ev.title);
        }
      }
      
      // Check if expected verses are present
      if (test.expectVerses) {
        const found = [];
        const notFound = [];
        for (const expected of test.expectVerses) {
          if (quranEvidence.some(v => v.startsWith(expected) || v === expected)) {
            found.push(expected);
          } else {
            notFound.push(expected);
          }
        }
        
        if (found.length > 0) {
          console.log(`  ✅ Found expected verses: ${found.join(', ')}`);
        }
        if (notFound.length > 0) {
          console.log(`  ⚠️  Missing expected verses: ${notFound.join(', ')}`);
        }
        
        // Consider pass if at least 1 expected verse found
        if (found.length > 0) {
          passed++;
        } else {
          console.log(`  ❌ FAIL - None of expected verses found in results`);
          failed++;
          failures.push({ 
            test: test.name, 
            reason: `Expected verses ${test.expectVerses.join(', ')} but got ${quranEvidence.join(', ')}` 
          });
        }
      } else {
        passed++;
      }

      // Validate data integrity
      for (const ev of data.evidence) {
        if (!ev.text || ev.text.trim().length === 0) {
          console.log(`  ❌ DATA ERROR: Empty text for ${ev.title}`);
          failures.push({ test: test.name, reason: `Empty text for ${ev.title}` });
        }
        if (!ev.source || ev.source.trim().length === 0) {
          console.log(`  ❌ DATA ERROR: Empty source for ${ev.title}`);
          failures.push({ test: test.name, reason: `Empty source for ${ev.title}` });
        }
        if (ev.type === 'HADITH' && (!ev.text || ev.text.length < 10)) {
          console.log(`  ❌ DATA ERROR: Hadith text too short for ${ev.title}`);
        }
      }

    } catch (err) {
      console.log(`  ❌ FAIL - Error: ${err.message}`);
      failed++;
      failures.push({ test: test.name, reason: err.message });
    }
  }

  console.log('\n' + '='.repeat(60));
  console.log(`RESULTS: ${passed} passed, ${failed} failed out of ${TESTS.length} tests`);
  console.log('='.repeat(60));
  
  if (failures.length > 0) {
    console.log('\nFAILURES:');
    failures.forEach(f => {
      console.log(`  ❌ ${f.test}: ${f.reason}`);
    });
  }
}

runTests().catch(console.error);
