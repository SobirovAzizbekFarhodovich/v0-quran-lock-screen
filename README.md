# Quran Reminder - Android App

A modern Android application for reading the Quran in Uzbek translation with reminder functionality, bookmarks, and offline support.

## Features

- **Quran Data Import**: Offline Room database with JSON import support for a licensed Uzbek translation
- **Offline Support**: Works completely offline after data is imported
- **Smart Reminders**: Schedule reminders for specific Surahs and Ayahs
- **Lock Screen Notifications**: Important reminders appear on your lock screen
- **Search**: Full-text search across all verses
- **Bookmarks**: Save your favorite verses with notes
- **Dark Mode**: Easy on the eyes with built-in dark theme support
- **Optimized**: Lightweight app (~3-5MB) with efficient database

## Project Structure

```
app/
├── src/main/
│   ├── kotlin/com/quranreminder/app/
│   │   ├── database/          # Room database entities and DAOs
│   │   ├── data/              # Repository and sample data
│   │   ├── notification/      # Reminder and notification system
│   │   ├── ui/
│   │   │   ├── screens/       # Compose UI screens
│   │   │   ├── theme/         # Material 3 theme
│   │   │   └── viewmodel/     # MVVM ViewModels
│   │   └── MainActivity.kt    # App entry point
│   ├── res/                   # Resources (strings, colors, themes)
│   └── AndroidManifest.xml    # App manifest
├── build.gradle.kts           # App dependencies and configuration
└── proguard-rules.pro         # ProGuard rules for release builds
```

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Database**: Room ORM with SQLite
- **Architecture**: MVVM with repositories
- **Async**: Coroutines and Flow
- **Reminders**: WorkManager + BroadcastReceiver
- **Notifications**: Android Notification API with high priority
- **Target**: Android 8.0+ (API 26)

## Building the App

### Prerequisites
- Android Studio (latest version)
- JDK 17 or higher
- Android SDK (API 34+)

### Build Steps

1. Clone the repository or open the project in Android Studio
2. Let Android Studio sync Gradle files automatically
3. Build the project:
   ```bash
   ./gradlew build
   ```

4. Run on emulator or device:
   ```bash
   ./gradlew installDebug
   ```

## Adding Quran Data

The app includes a small sample dataset so the UI works on first launch. To add a complete Uzbek translation, place your licensed data at:

`app/src/main/assets/quran/verses.json`

Format:

```json
{
  "verses": [
    {
      "surahNumber": 1,
      "surahName": "Al-Fatiha",
      "surahNameArabic": "الفاتحة",
      "ayahNumber": 1,
      "ayahText": "optional transliteration or Arabic",
      "ayahTextArabic": "Arabic ayah text",
      "translation": "Licensed Uzbek translation",
      "transliteration": ""
    }
  ]
}
```

On first launch, the app imports this file into Room. If the file is missing, it falls back to the sample data.

## Reminder System

The reminder system uses:
- **WorkManager**: Periodic checks every 15 minutes
- **BroadcastReceiver**: Captures exact reminder times and device boot events
- **AlarmManager**: For precise time scheduling (can be added for exact-time reminders)
- **NotificationManager**: Shows lock-screen notifications with high priority

### Scheduling a Reminder

Reminders are stored in the database with:
- Surah number and Ayah number
- Time (hour and minute)
- Days of week (0=Sunday, 6=Saturday)
- Enabled/disabled status

## Database Schema

### quran_verses
- `id` (PK)
- `surahNumber`: Surah index (1-114)
- `surahName`: English name
- `surahNameArabic`: Arabic name
- `ayahNumber`: Verse number
- `ayahText`: Transliterated text
- `ayahTextArabic`: Original Arabic
- `translation`: Uzbek translation

### reminders
- `id` (PK)
- `surahNumber`: Reference to verse
- `ayahNumber`: Reference to verse
- `hourOfDay`: 0-23
- `minuteOfHour`: 0-59
- `isEnabled`: Boolean
- `daysOfWeek`: Comma-separated days

### bookmarks
- `id` (PK)
- `surahNumber`
- `ayahNumber`
- `notes`: User notes
- `createdAt`: Timestamp

### app_settings
- `notificationsEnabled`
- `vibrateOnReminder`
- `soundEnabled`
- `fontSize`: 12-24
- `darkMode`
- `lockScreenNotifications`

## Permissions

The app requires these permissions:
- `POST_NOTIFICATIONS`: For displaying notifications (Android 13+)
- `RECEIVE_BOOT_COMPLETED`: To restart reminder checks after reboot
- `USE_FULL_SCREEN_INTENT`: To show high-priority lock-screen reminders
- `WAKE_LOCK`: For keeping device awake during reminders

## Release Build

To create a release APK:

```bash
./gradlew bundleRelease
```

This creates an Android App Bundle for Play Store submission.

## Optimization Tips

1. **Database**: Indices on surahNumber and ayahNumber for faster queries
2. **UI**: LazyColumn for efficient list rendering
3. **Memory**: Flow-based state management prevents unnecessary recompositions
4. **Size**: Use ProGuard/R8 to minimize release APK size

## Future Enhancements

- [ ] Add licensed complete Quran data (114 Surahs)
- [ ] Multiple Uzbek translations
- [ ] Tajweed highlighting
- [ ] Audio recitation
- [ ] Tafsir (interpretation) support
- [ ] Advanced reminder patterns (Duaa times, prayer times)
- [ ] Sharing features
- [ ] Sync across devices
- [ ] Quran memorization tracker
- [ ] Offline Tajweed rules

## Troubleshooting

### Reminders not working
- Check if notifications are enabled in app settings
- Check Android Settings > Apps > Quran Reminder > Notifications
- Ensure battery optimization is disabled for this app
- Check WorkManager logs: `adb logcat | grep WorkManager`

### Database errors
- Clear app data: Settings > Apps > Quran Reminder > Storage > Clear Cache/Data
- Uninstall and reinstall the app

### Build errors
- Sync Gradle files in Android Studio
- Invalidate cache and restart: File > Invalidate Caches

## Contributing

Contributions are welcome! Please ensure:
- Code follows Kotlin conventions
- All strings are in `strings.xml`
- UI changes use Material 3 components
- Database changes use Room migrations

## License

This project is designed for personal use and learning. Quran text is provided for educational purposes.

## Support

For issues or questions:
1. Check the troubleshooting section
2. Review log files using Android Studio's Logcat
3. Check Android version compatibility (API 26+)

---

**Quran Reminder** - Read the Quran anytime, anywhere, with smart reminders.
