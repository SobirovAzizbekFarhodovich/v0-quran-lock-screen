#!/bin/bash
# Comprehensive API Test Script

BASE_URL="http://localhost:3000/api/tadabbur"

run_test() {
  local test_name="$1"
  local query="$2"
  
  echo "========================================"
  echo "TEST: $test_name"
  echo "QUERY: $query"
  echo "----------------------------------------"
  
  result=$(curl -s -X POST "$BASE_URL" -H 'Content-Type: application/json' -d "{\"query\":\"$query\"}")
  
  echo "$result" | node -e "
    const data=JSON.parse(require('fs').readFileSync('/dev/stdin','utf8'));
    if (data.error) {
      console.log('ERROR:', data.error);
      return;
    }
    console.log('Intro:', (data.intro||'').slice(0,120));
    console.log('AI Insight:', (data.aiInsight||'none').slice(0,120));
    console.log('Evidence count:', (data.evidence||[]).length);
    (data.evidence||[]).forEach((e,i) => {
      console.log('  '+(i+1)+'. ['+e.type+'] '+e.title);
      console.log('     Text: '+e.text?.slice(0,100)+'...');
      console.log('     Highlights: '+(e.highlights||[]).join(', '));
      console.log('     Source: '+e.source);
    });
  "
  echo ""
}

echo "================================================"
echo "TADABBUR AI - COMPREHENSIVE TEST SUITE"
echo "================================================"
echo ""

# Test 1: Edge cases
run_test "Bo'sh so'rov" ""
run_test "Qisqa so'rov" "a"
run_test "Faqat stop words" "va bu bilan"

# Test 2: Sabr va taskin
run_test "Sabr - Konglim siqilyapti" "konglim siqilyapti"
run_test "Sabr - Hayotim og'ir" "hayotim juda ogir"

# Test 3: Tavba 
run_test "Tavba - gunoh qildim" "gunoh qilib qoydim tavba qilmoqchiman"

# Test 4: Yomon tush
run_test "Yomon tush" "yomon tush kordim qorqinchli tush"

# Test 5: Namoz
run_test "Namoz dangasa" "namozga dangasaman"

# Test 6: Ota-ona
run_test "Ota-ona" "onam xafa boldi menga"

# Test 7: Kasallik
run_test "Kasallik" "kasal bolib qoldim sogligim yomon"

# Test 8: Rizq/ish
run_test "Rizq va ish" "ishim yurishmayapti pulim yoq"

# Test 9: G'azab
run_test "G'azab" "jahlim chiqyapti kechira olmayapman"

# Test 10: Kirill yozuvi
run_test "Kirill yozuvi" "сабр қилолмаяпман"

# Test 11: O'lim/qayg'u
run_test "O'lim" "yaqinim vafot etdi"

# Test 12: Harom istak
run_test "Harom istak" "nafsimni yenga olmayapman"

# Test 13: Shukr
run_test "Shukr" "shukr qilolmayapman baxtsiz his qilaman"

echo "================================================"
echo "ALL TESTS COMPLETED"
echo "================================================"
