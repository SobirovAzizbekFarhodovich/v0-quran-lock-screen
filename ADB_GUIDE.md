# ADB (Android Debug Bridge) O'rnatish va Foydalanish Bo'yicha Yo'riqnoma

Ushbu yo'riqnoma sizga **Quran Reminder** ilovasini kompyuteringizdan jismoniy Android qurilmangizga (telefonga) ADB orqali o'rnatish va sozlash jarayonini batafsil tushuntiradi.

---

## 1. ADB Nima va U Nima Uchun Kerak?

**ADB (Android Debug Bridge)** — bu Google tomonidan taqdim etilgan universal buyruqlar paneli vositasi bo'lib, u orqali kompyuteringiz yordamida Android qurilmangizni boshqarishingiz, loglarni ko'rishingiz va tayyor ilovalarni (.apk formatida) osonlik bilan o'rnatishingiz mumkin.

Bizning loyihamizda u asosan quyidagi maqsadlar uchun ishlatiladi:
* Qurilgan `app-debug.apk` faylini to'g'ridan-to'g'ri telefonga tezkor o'rnatish.
* Ilova ishlayotgan vaqtdagi xatoliklarni va loglarni terminalda ko'rish (`adb logcat`).
* Qurilma ulanish holatini tekshirish.

---

## 2. ADB'ni Kompyuterga O'rnatish

Operatsion tizimingizga mos keladigan bo'limni tanlang va qadamlarni bajaring:

### A. Linux (Ubuntu / Debian / Fedora / Arch)

Linux tizimlarida ADB paket menejerlari orqali juda tez o'rnatiladi:

* **Ubuntu / Debian / Linux Mint va boshqalar:**
  ```bash
  sudo apt update
  sudo apt install android-tools-adb android-tools-fastboot -y
  ```

* **Fedora / RHEL:**
  ```bash
  sudo dnf install android-tools -y
  ```

* **Arch Linux / Manjaro:**
  ```bash
  sudo pacman -S android-tools --noconfirm
  ```

O'rnatilganini tekshirish uchun terminalda quyidagi buyruqni ishga tushiring:
```bash
adb version
```

### B. Windows

1. Rasmiy havola orqali **SDK Platform-Tools** paketini yuklab oling:
   * [SDK Platform-Tools for Windows](https://dl.google.com/android/repository/platform-tools-latest-windows.zip)
2. Yuklab olingan ZIP faylni arxivdan chiqaring (masalan: `C:\platform-tools` papkasiga).
3. Tizim o'zgaruvchilariga (`Environment Variables` / `PATH`) yo'lni qo'shing:
   * **Start** tugmasini bosing va "Environment Variables" yoki "Tizim o'zgaruvchilarini tahrirlash" deb qidiring.
   * **Environment Variables** tugmasini bosing.
   * **System variables** bo'limidan `Path` o'zgaruvchisini toping va uni tahrirlang (`Edit`).
   * **New** (Yangi) tugmasini bosing va ZIP faylni arxivdan chiqargan yo'lingizni qo'shing (masalan, `C:\platform-tools`).
   * Barcha oynalarda **OK** tugmasini bosing.
4. Yangi **Command Prompt** (CMD) yoki **PowerShell** oynasini ochib, tekshiring:
   ```cmd
   adb version
   ```

### C. macOS

Agar sizda **Homebrew** o'rnatilgan bo'lsa, quyidagi buyruqni bering:
```bash
brew install android-platform-tools
```

Agar Homebrew bo'lmasa, qo'lda o'rnatish:
1. Paketni yuklab oling: [SDK Platform-Tools for Mac](https://dl.google.com/android/repository/platform-tools-latest-darwin.zip)
2. ZIP faylni arxivdan chiqaring (masalan, `~/Library/Android/sdk/platform-tools` yo'liga).
3. Terminal profilingiz fayliga (masalan, `~/.zshrc` yoki `~/.bash_profile`) quyidagi qatorni qo'shing:
   ```bash
   export PATH=$PATH:~/Library/Android/sdk/platform-tools
   ```
4. O'zgarishlarni qo'llang: `source ~/.zshrc` va tekshiring:
   ```bash
   adb version
   ```

---

## 3. Android Qurilmani Tayyorlash

ADB orqali ulanish uchun jismoniy telefoningizda quyidagi sozlamalarni yoqishingiz kerak:

1. Telefoningizda **Sozlamalar** (Settings) -> **Telefon haqida** (About Phone) bo'limiga kiring.
2. **Dasturiy ta'minot haqida** (Software information) bo'limidagi **Build Number** (Sborka raqami) yozuvini ketma-ket **7 marta** bosing.
   * Sizga *"Siz endi dasturchisiz!"* (You are now a developer!) degan xabar ko'rinishi kerak.
3. Sozlamalarga qaytib, eng pastda paydo bo'lgan **Dasturchi sozlamalari** (Developer Options) bo'limiga kiring.
4. Ro'yxatdan **USB orqali sozlash** (USB Debugging) opsiyasini toping va uni yoqing.

---

## 4. Qurilmani Kompyuterga Ulash va Tekshirish

1. Telefoningizni USB kabel orqali kompyuterga ulang.
2. Terminalni oching va quyidagi buyruqni kiriting:
   ```bash
   adb devices
   ```
3. Telefoningiz ekranida *"USB orqali sozlashga ruxsat berilsinmi?"* (Allow USB debugging?) degan oyna paydo bo'ladi.
   * **"Har doim ushbu kompyuterdan ruxsat berilsin"** (Always allow from this computer) katagiga belgi qo'ying va **Ruxsat berish** (Allow) tugmasini bosing.
4. Terminalda buyruqni qayta ishga tushiring. Quyidagi kabi natija chiqishi kerak:
   ```text
   List of devices attached
   4a3b7d12    device
   ```
   Agar qurilma yonida `unauthorized` yozuvi tursa, demak siz telefon ekranida ulanishni hali tasdiqlamadingiz.

### Wi-Fi orqali ulash (Simsiz usul - faqat Android 11 va undan yuqori versiyalar uchun)
1. Telefon va kompyuter bir xil Wi-Fi tarmog'iga ulangan bo'lishi kerak.
2. Dasturchi sozlamalarida **Wireless Debugging** opsiyasini yoqing va unga bosing.
3. **Pair device with pairing code** opsiyasini tanlang (ekranda IP manzil, port va kod paydo bo'ladi).
4. Kompyuterda quyidagilarni kiriting:
   ```bash
   adb pair <IP_MANZIL>:<PORT>
   # Ekrondagi ulanish kodini kiriting
   ```
5. So'ng, asosiy IP-manzil bilan ulaning:
   ```bash
   adb connect <IP_MANZIL>:<MAIN_PORT>
   ```

---

## 5. APK Faylini Qurilmaga O'rnatish

Loyihamizda tayyor bo'lgan debug APK faylini o'rnatish uchun loyiha ildiz papkasida turib quyidagi buyruqni bajaring:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Kutilayotgan natija:**
```text
Performing Streamed Install
Success
```

Agar ilova telefonda allaqachon mavjud bo'lsa va uni yangilamoqchi bo'lsangiz, `-r` (reinstall) parametridan foydalaning:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 6. Muammolarni Bartaraf Etish (Troubleshooting)

### A. Qurilma `unauthorized` holatida qolib ketdi
* **Yechim:** USB kabelni uzing va qaytadan ulang. Telefon ekranida ruxsat berish oynasi chiqishini kuting.
* Chiqmasa, terminalda quyidagi buyruqlarni berib, serverni qayta ishga tushiring:
  ```bash
  adb kill-server
  adb start-server
  ```
  So'ng qaytadan ulab ko'ring.

### B. `List of devices attached` ro'yxati bo'sh (Qurilma topilmadi)
* **Yechim:**
  1. USB kabelingiz faqat zaryadlovchi bo'lmasdan, ma'lumot uzatuvchi (data cable) ekanligiga ishonch hosil qiling.
  2. Boshqa USB portga ulab ko'ring.
  3. Windows foydalanuvchilari uchun drayverlar etishmasligi mumkin. Qurilmangiz brendiga mos keluvchi USB driverlarni yuklab oling (masalan, Samsung USB Driver).

### C. `adb: command not found` xatoligi
* **Yechim:** ADB'ning tizim yo'llariga (`Path`) qo'shilganini tekshiring yoki terminalni yopib, qaytadan oching. Agar vaqtinchalik ishlatmoqchi bo'lsangiz, bevosita yuklab olingan papkaga kirib ishlatishingiz mumkin:
  * Linux/macOS: `./adb devices`
  * Windows: `.\adb devices`

### D. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` xatoligi
* **Yechim:** Bu xatolik telefondagi eski ilovaning imzosi (signature) yangisi bilan mos kelmaganda chiqadi. Telefonga o'rnatilgan avvalgi versiyani butunlay o'chirib tashlang va qaytadan o'rnating.

---

*Ilovani sinab ko'rish va rivojlantirishda omad yor bo'lsin!*
