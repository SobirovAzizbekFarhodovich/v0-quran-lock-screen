#!/bin/bash
# OpenRouter API orqali Qwen AI modelni to'g'ridan-to'g'ri tekshirish

# API kalitni local.properties yoki gradle.properties dan olish
API_KEY=$(grep 'OPENROUTER_API_KEY=' local.properties 2>/dev/null | cut -d= -f2)
if [ -z "$API_KEY" ]; then
    API_KEY=$(grep 'OPENROUTER_API_KEY=' gradle.properties 2>/dev/null | cut -d= -f2)
fi

echo "=== OPENROUTER API KEY TEKSHIRUVI ==="
echo "API KEY: ${API_KEY:0:10}..."
echo ""

if [ "$API_KEY" == "your-openrouter-api-key-here" ] || [ -z "$API_KEY" ]; then
    echo "❌ API kalit o'rnatilmagan!"
    echo ""
    echo "API kalitni o'rnatish uchun:"
    echo "1. https://openrouter.ai ga kiring"
    echo "2. API key oling"
    echo "3. gradle.properties faylida OPENROUTER_API_KEY=<your-key> qilib yozing"
    echo ""
    echo "API key bo'lmasa ham ilova ishlaydi — faqat lokal topic matching ishlaydi."
    echo "AI shaxsiy maslahat berish qismi o'chirilgan bo'ladi."
    exit 0
fi

echo "✅ API kalit topildi"
echo ""

# Test 1: Ko'nglim siqilyapti
echo "=== TEST 1: Ko'nglim siqilyapti ==="
RESPONSE=$(curl -s -X POST https://openrouter.ai/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $API_KEY" \
  -H "HTTP-Referer: https://quran-reminder.app" \
  -H "X-Title: Quran Reminder Test" \
  -d '{
    "model": "qwen/qwen3.6-flash",
    "messages": [{"role": "user", "content": "Siz islomiy ruhiy maslahatchi sifatida foydalanuvchining holatini tahlil qilasiz.\n\nFoydalanuvchi yozdi: \"ko'\''nglim siqilyapti\"\n\nBu insonnning ruhiy, jismoniy yoki hayotiy holatini tahlil qiling.\n\nFAQAT quyidagi JSON formatida javob bering:\n{\"topics\": [\"eng mos mavzu1\"], \"moods\": [\"holati1\"], \"keywords\": [\"kalit_soz1\"], \"userState\": \"qisqacha tavsif\"}"}],
    "temperature": 0.3,
    "max_tokens": 500,
    "response_format": {"type": "json_object"}
  }')

echo "Raw response (first 500 chars):"
echo "$RESPONSE" | head -c 500
echo ""
echo ""

# Check if response has content
if echo "$RESPONSE" | grep -q '"content"'; then
    echo "✅ AI javob berdi!"
    # Extract just the content
    CONTENT=$(echo "$RESPONSE" | python3 -c "import sys,json; r=json.load(sys.stdin); c=r.get('choices',[{}])[0].get('message',{}).get('content',''); print(c.split('</think>')[-1].strip() if '</think>' in c else c)" 2>/dev/null)
    echo "AI Analysis:"
    echo "$CONTENT" | python3 -m json.tool 2>/dev/null || echo "$CONTENT"
else
    echo "❌ AI javob bermadi!"
    echo "$RESPONSE"
fi

echo ""
echo "=== TEST YAKUNLANDI ==="
