# Quran Reminder - Setup & Build Guide

This guide walks you through setting up the Quran Reminder Android app on your development machine.

## System Requirements

- **OS**: macOS, Windows, or Linux
- **Java**: JDK 17 or higher (check with `java -version`)
- **Android SDK**: API 34+ (installed via Android Studio)
- **RAM**: Minimum 4GB (8GB recommended)
- **Disk Space**: ~3GB for Android Studio + SDKs
- **Internet**: Required for initial setup and gradle dependencies

## Step 1: Install Android Studio

1. Download Android Studio from [developer.android.com](https://developer.android.com/studio)
2. Install following the platform-specific instructions
3. Launch Android Studio and complete the initial setup wizard
4. Install the latest Android SDK tools:
   - Go to Settings > Appearance & Behavior > System Settings > Android SDK
   - Install SDK Platform API 34+
   - Install SDK Tools (latest versions)

## Step 2: Clone/Open the Project

**Option A: From v0**
1. Download the project ZIP from v0
2. Extract to a desired location
3. Open the project folder in Android Studio

**Option B: From Terminal**
```bash
cd path/to/your/workspace
unzip QuranReminder.zip
cd QuranReminder
open -a "Android Studio" .  # macOS
# or
studio .  # Linux
```

## Step 3: Sync Gradle

1. Android Studio will prompt to sync Gradle files automatically
2. If not, go to File > Sync Now
3. Wait for gradle sync to complete (2-5 minutes on first run)
4. You may see warnings about Kotlin compiler - these are normal

## Step 4: Build the App

### Debug Build (for testing)
```bash
./gradlew assembleDebug
```

Or in Android Studio:
1. Go to Build > Make Project
2. Wait for the build to complete

### Release Build (for Play Store)
```bash
./gradlew bundleRelease
```

## Step 5: Run on Emulator

### Create an Android Emulator
1. Tools > Device Manager > Create Virtual Device
2. Select device (Pixel 5 recommended)
3. Select API 34+ system image
4. Finish setup

### Run the App
```bash
./gradlew installDebug
```

Or in Android Studio:
1. Select your emulator in the Device Dropdown
2. Click the green Run button (or Ctrl+R)

## Step 6: Run on Physical Device

### Enable Developer Mode
1. Go to Settings > About Phone
2. Tap "Build Number" 7 times
3. Go to Settings > Developer Options
4. Enable "USB Debugging"
5. Connect device to computer via USB

### Install App
```bash
./gradlew installDebug
```

## Project Structure Guide

```
QuranReminder/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/quranreminder/app/
│   │   │   ├── MainActivity.kt              # App entry point
│   │   │   ├── database/                   # Room database
│   │   │   ├── data/                       # Repository & sample data
│   │   │   ├── notification/               # Reminder system
│   │   │   └── ui/
│   │   │       ├── screens/                # Compose screens
│   │   │       ├── theme/                  # Material 3 theme
│   │   │       └── viewmodel/              # MVVM ViewModels
│   │   ├── res/                            # Resources
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts                    # Dependencies
│   └── proguard-rules.pro
├── build.gradle.kts                        # Root build config
├── settings.gradle.kts                     # Project settings
├── gradle.properties                       # Gradle properties
└── README.md                               # Project documentation
```

## Common Issues & Solutions

### Issue: "SDK location not found"
**Solution**:
1. Create local.properties in project root
2. Add: `sdk.dir=/path/to/Android/sdk`
3. Sync gradle again

### Issue: "Execution failed for task ':app:compileDebugKotlin'"
**Solution**:
1. Check Java version: `java -version` (should be 17+)
2. File > Invalidate Caches > Invalidate and Restart
3. Try building again

### Issue: "Unable to resolve dependency"
**Solution**:
1. Check internet connection
2. Go to File > Settings > HTTP Proxy > No proxy
3. Delete .gradle folder and rebuild

### Issue: Emulator won't start
**Solution**:
1. Tools > Device Manager > Select device > More Options > Cold Boot Now
2. Or create a new virtual device with API 34
3. Ensure virtualization is enabled in BIOS (Windows)

### Issue: "Debugging connection timeout"
**Solution**:
1. Unplug and reconnect USB cable
2. Run: `adb kill-server && adb start-server`
3. Restart emulator

## Testing the App

### Verify Database
```kotlin
// In MainActivity, add temporary logging
lifecycleScope.launch {
    val verseCount = database.quranVerseDao().getVerseCount()
    Log.d("QuranApp", "Verses in database: $verseCount")
}
```

### Test Reminders
1. Open the app
2. Go to Reminders tab
3. Click + to add a reminder
4. Set time to current time + 1 minute
5. Wait and check for notification

### Test Search
1. Open Search screen
2. Type an Uzbek word or number
3. Verify results appear with Arabic text and translation

## Debugging Tips

### View Console Logs
```bash
# Real-time logs
adb logcat | grep QuranReminder

# Save logs to file
adb logcat > quran_app.log
```

### Debug Mode
Add debug logging in any file:
```kotlin
Log.d("QuranApp", "Debug message: $variable")
Log.e("QuranApp", "Error occurred", exception)
```

### Database Inspector
1. Run the app in debug mode
2. Tools > App Inspection > Database Inspector
3. Browse database tables and query results

## Performance Optimization

### Reduce APK Size
1. Build release variant with minification:
   ```bash
   ./gradlew bundleRelease
   ```
2. Size is typically 3-5MB

### Speed Up Builds
1. Enable gradle offline mode (if dependencies cached)
2. Reduce database size on first run
3. Use incremental builds

### Monitor Performance
- Tools > Profiler > CPU, Memory, Network tabs
- Tools > App Inspection for live database viewing

## Deploying to Play Store

### Prerequisites
- Google Play Developer Account ($25 one-time fee)
- Signed APK/Bundle
- App screenshots and description

### Build Release Bundle
```bash
./gradlew bundleRelease
```

This creates: `app/build/outputs/bundle/release/app-release.aab`

### Create Signing Key
```bash
keytool -genkey -v -keystore ~/quran-reminder.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias quran-reminder
```

### Sign the Bundle
1. Build > Generate Signed Bundle/APK
2. Select "Bundle" and your .jks file
3. Follow the wizard to generate signed bundle

## Next Steps

1. **Add Complete Quran Data**: Load full 114 Surahs
2. **Add Audio**: Implement Quran recitation
3. **UI Polish**: Enhance design and animations
4. **Testing**: Run tests with Espresso
5. **Publish**: Upload to Play Store

## Getting Help

- Android Developer Docs: [developer.android.com](https://developer.android.com)
- Kotlin Docs: [kotlinlang.org](https://kotlinlang.org)
- Stack Overflow: Tag your questions with "android" and "kotlin"
- Android Studio Help: Help > Android Studio Help

## Tips for Success

- Start with the emulator to test quickly
- Use hot reload for Compose changes
- Keep database queries indexed for performance
- Test on both emulator and real device
- Monitor battery usage with Profiler
- Check logs frequently with Logcat

---

**Happy Coding!** May your journey with this app be smooth and productive.
