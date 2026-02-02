# AGENTS.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

**The Talking Memo** (package: `com.computerproductions.talkingmemo`) is an Android alarm/reminder application that allows users to create voice-announced reminders for appointments, medications, and prescription refills. Despite the repo name "FreeSpeechStopWatch", this is actually an alarm/reminder application.

## Build & Development Commands

### Building the app
```bash
./gradlew build
```

### Building APK
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

### Running tests
```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

### Cleaning build artifacts
```bash
./gradlew clean
```

### Installing on device
```bash
./gradlew installDebug
```

## Architecture

### Core Data Flow

1. **DataSource (Singleton)**: Central data management using file-based persistence
   - Stores alarms in `alarmme.txt` using custom binary serialization
   - All CRUD operations go through this singleton
   - Automatically sorts alarms by next occurrence time
   - Loads data on first access, saves after every modification

2. **Alarm Model**: Represents individual alarm/reminder
   - Supports two occurrence modes: ONCE (one-time) and WEEKLY (recurring)
   - Uses bitwise flags for weekly day selection (7 bits for 7 days)
   - Automatically calculates next occurrence based on current time and recurrence settings
   - Serializes to/from Intent extras for Activity transitions

3. **AlarmManager Integration**: Android system alarm scheduling
   - `AlarmListAdapter` schedules/cancels alarms via AlarmManager
   - Uses `PendingIntent` with alarm ID for unique identification
   - Requires `SCHEDULE_EXACT_ALARM` permission on Android 12+ (SDK 31+)
   - Broadcasts to `AlarmReceiver` when alarm triggers

4. **Alarm Notification Flow**:
   - `AlarmReceiver` (BroadcastReceiver) → `AlarmNotificationActivity`
   - `AlarmNotificationActivity` shows full-screen alarm with lock screen override
   - `SoundService` plays category-specific audio (appointment/medication/prescription)
   - Uses ringtone + vibration + ToneGenerator for alert

### Key Components

**Activities:**
- `FlashActivity`: Launch/splash screen (entry point)
- `MainActivity`: Main alarm list view with context menu operations (edit/delete/duplicate)
- `EditAlarmActivity`: Alarm creation/editing with date/time pickers and recurrence options
- `AlarmNotificationActivity`: Full-screen alarm display with lock screen support
- `PreferencesActivity`: User settings (24h clock, week start day, alarm sound/duration)
- `InfoActivity`: About/info screen

**Services:**
- `SoundService`: Background service for playing reminder-specific audio files

**Receivers:**
- `AlarmReceiver`: Handles alarm broadcasts from AlarmManager
- `BootCompletedReceiver`: Reschedules alarms after device reboot

**Adapters:**
- `AlarmListAdapter`: ListView adapter that bridges DataSource and UI, handles alarm scheduling

**Utilities:**
- `DateTime`: Formats dates/times/days respecting user preferences (24h, week start)

### Permission Requirements

Critical Android permissions (see `AndroidManifest.xml`):
- `SCHEDULE_EXACT_ALARM`: Required for Android 12+ exact alarm scheduling
- `POST_NOTIFICATIONS`: Required for Android 13+ notifications
- `RECEIVE_BOOT_COMPLETED`: Reschedule alarms after reboot
- `WAKE_LOCK`, `DISABLE_KEYGUARD`, `USE_FULL_SCREEN_INTENT`: Lock screen alarm display

### Android SDK Configuration

- **minSdkVersion**: 21 (Android 5.0 Lollipop)
- **targetSdkVersion**: 35 (Android 15)
- **compileSdkVersion**: 35
- **Java Version**: 17 (source/target compatibility)

### Reminder Categories

The app supports three reminder types with unique audio cues:
- **Appointment** (`sound_appointment.m4a`)
- **Daily Medication** (`sound_medication.m4a`)
- **Prescription Refill** (`sound_prescription.m4a`)

Category selection happens via bottom navigation in MainActivity, stored in SharedPreferences.

## Important Implementation Notes

- The app uses custom binary serialization (not JSON) for DataSource persistence
- Alarm IDs are generated sequentially and used as PendingIntent request codes
- Weekly alarm scheduling uses bitwise operations: day flags stored as single integer (bits 0-6 for Mon-Sun)
- Day-of-week calculation: `(Calendar.DAY_OF_WEEK + 5) % 7` converts Calendar's Sunday=1 to Monday=0 indexing
- Lock screen display logic differs between Android 8.1+ and earlier versions
- The AlarmManager permission check is essential on Android 12+ before scheduling exact alarms
