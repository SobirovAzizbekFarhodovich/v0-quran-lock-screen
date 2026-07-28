# Quran Reminder - Android Kotlin Ilovasi

Bu o'zbek tilidagi Qur'on tarjimasini offline o'qish, qidirish, eslatmalar yaratish va lock-screen'ga eslatmalarni chiqarish uchun yozilgan zamonaviy Kotlin Android ilovasi.

## 🚀 Loyiha Qayta Sozlangan Holati

Butun loyiha keraksiz papka va fayllardan (Flutter, Next.js, Go backend, Python AI va b.) butunlay tozalandi va faqatgina ishchi holatdagi Kotlin Android loyihasi saqlandi.

### Asosiy O'zgarishlar va Sozlamalar:
1. **AWS Lightsail Server IP (`18.197.97.234`):**
   - Ilovaning barcha API so'rovlari va ma'lumot yuklash sozlari ushbu serverga yo'naltirildi.
   - `QuranDataLoader` endi Qur'on oyatlarini ushbu serverdan yuklaydi.
   - `TadabburApiClient` sun'iy intellekt (Tadabbur Chat) so'rovlarini ushbu serverdagi API'ga yuboradi.

2. **Til Tanlash Tizimi:**
   - Ilova birinchi marta ochilganda foydalanuvchiga 4 ta til tanlash imkonini beruvchi premium **LanguageSelectionScreen** ko'rsatiladi:
     - 🇬🇧 English
     - 🇷🇺 Русский
     - 🇺🇿 O'zbek (lotin)
     - 🇺🇿 Ўзбек (кирил)
   - Tanlangan til Room DB dagi `app_settings` jadvalida saqlanadi. Kelgusida serverdan mos dars/oyat ma'lumotlarini yuklashda qo'llaniladi.

3. **Serverdan Rasm Yuklash va Keshlash:**
   - Ilova hajmini kichraytirish maqsadida 6 ta og'ir default rasmlar o'chirildi.
   - O'rniga APK ichida faqat 3 ta chiroyli local preview fon rasmlari (`bg_local_1`, `bg_local_2`, `bg_local_3`) qoldirildi.
   - Qo'shimcha 2 ta fon rasmlari serverdan (URL orqali) yuklab olinadigan qilib sozlandi.
   - Ilovaning "Lock-screen preview" ekranidan foydalanuvchi "Yuklab olish" tugmasini bosganda, rasm serverdan yuklanadi, telefon xotirasiga keshlash uchun saqlanadi va offline ishlaydigan bo'ladi.

---

## 🛠️ Loyiha Strukturasi

```
app/
├── src/main/
│   ├── kotlin/com/quranreminder/app/
│   │   ├── database/          # Room database (Qur'on, Sozlamalar, Eslatmalar jadvallari)
│   │   ├── data/              # Oyatlar yuklagichi va repositorylar
│   │   ├── lockscreen/        # Lock screen fon rasmini o'rnatish va generatsiya qilish
│   │   ├── notification/      # Eslatmalar tizimi (WorkManager, Broadcasts)
│   │   ├── ui/
│   │   │   ├── screens/       # Jetpack Compose UI ekranlari (Home, Preview, Language, Chat)
│   │   │   ├── theme/         # Material 3 premium dizayn tizimi
│   │   │   └── viewmodel/     # ViewModel'lar (MVVM)
│   │   └── MainActivity.kt    # Ilova boshlang'ich nuqtasi (Navigation, setContent logikasi)
│   ├── res/                   # Resurslar (animatsiyalar, fon rasmlari, stringlar)
│   └── AndroidManifest.xml    # Manifest fayli
remote_backgrounds/            # Serverga yuklanadigan qo'shimcha rasmlar (bulutli saqlagichingizga yuklang)
```

## ⚙️ Qurish va Ishga Tushirish

### Kerakli Sozlamalar
- JDK 17
- Android SDK (API 34)

### Ishga Tushirish Buyruqlari:

1. Gradle fayllarini tekshirish va ilovani build qilish:
   ```bash
   ./gradlew build
   ```
2. Debug APK yaratish:
   ```bash
   ./gradlew assembleDebug
   ```
3. Release Bundle (Play Store uchun) yaratish:
   ```bash
   ./gradlew bundleRelease
   ```

---

## 💾 Serverga Yuklanadigan Rasmlar (Remote Backgrounds)
`remote_backgrounds` papkasida joylashgan rasmlarni o'zingizning Lightsail serveringizga (`18.197.97.234`) quyidagi manzillar ostida joylashtiring:
- `http://18.197.97.234/backgrounds/bg_remote_1.png`
- `http://18.197.97.234/backgrounds/bg_remote_2.png`

Ilovadagi "Serverdan yuklash" bo'limi ushbu URL'lar orqali rasmlarni yuklab oladi.
