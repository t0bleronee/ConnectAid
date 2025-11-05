# ConnectAid

ConnectAid is an offline-first emergency communication application developed in Kotlin with Jetpack Compose. Designed as a comprehensive solution for disaster relief and emergency scenarios where traditional infrastructure may be unavailable.

## Description

ConnectAid is a peer-to-peer communication platform that leverages WiFi Direct technology to enable messaging, voice calls, and emergency alerts without requiring Internet connectivity. The application is specifically designed for emergency response scenarios, disaster situations, and areas with limited or no network infrastructure, making it an essential tool for first responders, disaster relief teams, and communities in crisis.

## Key Features

### Core Communication
- **WiFi Direct Messaging**: Peer-to-peer instant messaging without Internet connectivity
- **Group Chat**: Create and manage group conversations for coordinated communication
- **Voice Calls**: Real-time audio communication with echo cancellation and noise suppression
- **File Sharing**: Share images, videos, audio files, and documents directly between devices

### Emergency Features
- **SOS Alert System**: Send emergency alerts with location data to nearby devices
- **Priority Notifications**: Critical alerts with persistent sound and vibration
- **Location Sharing**: Share GPS coordinates during emergencies
- **Background Service**: Emergency alerts work even when app is in background

### Data Management
- **Local Storage**: All messages stored locally using Room database
- **Cloud Backup**: Optional backup and restore functionality via secure API
- **Message Sync**: Keep conversation history across app reinstalls
- **File Management**: Efficient local file storage and retrieval

### User Experience
- **Modern UI**: Built entirely with Jetpack Compose for smooth, responsive interface
- **Dark Mode**: Full dark theme support for all screens
- **Contact Management**: Store and organize contact profiles with photos
- **Settings Customization**: Personalize app behavior and preferences

## Technical Architecture

### Technology Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material3
- **Architecture**: MVVM (Model-View-ViewModel) pattern
- **Database**: Room (SQLite)
- **Data Storage**: DataStore for preferences, local file system for media
- **Networking**: WiFi Direct for P2P, Retrofit for cloud backup
- **Dependency Injection**: Manual DI with AppContainer pattern
- **Serialization**: Kotlinx Serialization

### Key Components

#### Network Layer
- **NetworkManager**: Orchestrates WiFi Direct connections and device discovery
- **CallManager**: Handles audio streaming with echo cancellation and noise suppression
- **ClientService**: Manages outgoing connections for messages, files, calls, and SOS alerts
- **ServerService**: Listens for incoming connections on multiple ports (messages, files, calls, SOS)
- **BackupApiService**: Cloud backup integration for message synchronization

#### Data Layer
- **Repositories**: Abstract data access for accounts, profiles, contacts, and chats
- **DAOs**: Database access objects for local data persistence
- **Entities**: Room database entities for messages, profiles, and accounts
- **Serializers**: DataStore serializers for complex objects

#### Domain Layer
- **Models**: Device, Profile, Contact, Account, and various Message types (Text, File, Audio)
- **Message Types**: Structured message system with states (sent, received, read)

#### UI Layer
- **Screens**: Home, Chat, Groups, Call, Info, Settings
- **Components**: Reusable UI components for messages, contacts, and files
- **Navigation**: Type-safe navigation with NavHost

### Permissions Required
- WiFi access and state changes
- Network state access
- Nearby WiFi devices (Android 13+)
- Location (required for WiFi Direct on Android)
- Audio recording (for voice calls)
- Notifications (for alerts)
- Foreground services (for emergency alerts)

## Installation

### Requirements
- Android device with Android 7.0 (API 24) or higher
- WiFi Direct capable hardware
- Minimum 50MB storage space

### Option 1: Install APK (Recommended for Users)

Download the pre-built APK and install directly on your Android device:

**APK Location**: `app/build/outputs/apk/debug/app-debug.apk`

**Installation Steps:**
1. Download the APK file from the repository
2. Transfer it to your Android device
3. Enable "Install from Unknown Sources" in your device settings
4. Open the APK file and follow the installation prompts
5. Grant the required permissions when launching the app

### Option 2: Build from Source (For Developers)

**Clone the Repository:**
```bash
git clone https://github.com/kushalram-k/DisCo.git
cd app-main
```

**Build and Run in Android Studio:**
1. Open the project in Android Studio
2. Wait for the project to build (this may take a while on the first run)
3. Once the build is complete, run the app: 
(ensure you turn on the developer options in your mobile)
   - Go to **Run → Run 'app'** from the menu
   - Select your target device (emulator or physical device)
4. The app will be installed and launched automatically


## Usage

### Basic Messaging
1. Launch ConnectAid on both devices
2. Tap the WiFi Direct icon to discover nearby devices
3. Connect to another device
4. Select a contact from the home screen
5. Send text messages, files, or audio messages
6. Make voice calls using the call button

### Group Communication
1. Tap the groups icon from the home screen
2. Create a new group with a unique name
3. Share the group name with other users
4. All users join the same group name to communicate
5. Send broadcast messages visible to all group members

### Emergency SOS
1. In emergency situations, send an SOS alert from the chat screen
2. Alert includes your location and emergency details
3. Nearby devices receive priority notifications with persistent alerts
4. Recipients can copy location coordinates to share with emergency services

### Cloud Backup (Optional)
1. Register for a user ID in Settings
2. Enable automatic backup
3. Messages are periodically synced to secure cloud storage
4. Restore messages when reinstalling the app

## Screens

| **Home Screen**               | **Chat Screen**               | **Info Screen**               |
|:-----------------------------:|:-----------------------------:|:-----------------------------:|
| ![home screen](docs/home.jpg) | ![chat screen](docs/chat.jpg) | ![info screen](docs/info.jpg) |
| **Settings Screen**           | **Chat Dark Screen**          | **Info Dark Screen**          |
| ![settings screen](docs/settings.jpg) | ![chat dark screen](docs/chat-dark.jpg) | ![info dark screen](docs/info-dark.jpg) |

### Screen Descriptions
- **Home Screen**: View all contacts and recent conversations, access WiFi Direct connections, groups, and settings
- **Chat Screen**: One-on-one messaging with support for text, files, audio messages, and voice calls
- **Groups Screen**: Create and manage broadcast groups for multi-user communication
- **Call Screen**: Voice call interface with mute, speaker, and end call controls
- **Info Screen**: Contact details, shared media, and chat settings
- **Settings Screen**: App configuration, profile management, and cloud backup options

## Project Structure

```
app/
├── src/main/java/com/connectaid/connectaid/
│   ├── data/                    # Data layer
│   │   ├── local/              # Local data sources
│   │   │   ├── account/        # Account DAO and entities
│   │   │   ├── message/        # Message DAO and entities
│   │   │   ├── profile/        # Profile DAO and entities
│   │   │   ├── serializer/     # DataStore serializers
│   │   │   ├── AppDatabase.kt  # Room database
│   │   │   ├── AppDataStore.kt # DataStore preferences
│   │   │   ├── FileManager.kt  # File system operations
│   │   │   └── LocationProvider.kt # GPS location services
│   │   └── repository/          # Repository implementations
│   ├── domain/                  # Domain layer
│   │   ├── model/              # Domain models
│   │   │   ├── chat/           # Chat-related models
│   │   │   ├── device/         # Device, profile, contact models
│   │   │   └── message/        # Message types (text, file, audio)
│   │   └── repository/          # Repository interfaces
│   ├── media/                   # Media playback
│   │   ├── AudioReplayer.kt    # Audio message playback
│   │   ├── VideoReplayer.kt    # Video file playback
│   │   └── MediaReplayer.kt    # Media interface
│   ├── network/                 # Network layer
│   │   ├── model/              # Network DTOs
│   │   │   └── sos/            # SOS alert models
│   │   ├── service/            # Network services
│   │   │   ├── ClientService.kt # Outgoing connections
│   │   │   └── ServerService.kt # Incoming connections
│   │   ├── BackupApiService.kt # Cloud backup API
│   │   ├── CallManager.kt      # Voice call management
│   │   └── NetworkManager.kt   # WiFi Direct manager
│   ├── notification/            # Notification system
│   │   ├── NotificationHelper.kt # Notification builder
│   │   ├── SosAlertService.kt   # Emergency alert service
│   │   └── SosCopyReceiver.kt   # Clipboard receiver for SOS
│   ├── ui/                      # UI layer
│   │   ├── components/         # Reusable UI components
│   │   ├── navigation/         # Navigation setup
│   │   ├── screen/             # Feature screens
│   │   │   ├── call/           # Voice call screen
│   │   │   ├── chat/           # Chat screen
│   │   │   ├── groups/         # Groups screen
│   │   │   ├── home/           # Home screen
│   │   │   ├── info/           # Contact info screen
│   │   │   └── settings/       # Settings screen
│   │   ├── theme/              # Material3 theming
│   │   └── Screen.kt           # App bars and scaffolds
│   ├── App.kt                   # Application class
│   ├── AppContainer.kt          # Dependency injection
│   ├── HandlerFactory.kt        # Connection handler factory
│   └── MainActivity.kt          # Main activity
├── src/main/res/                # Resources
│   ├── drawable/               # Icons and images
│   ├── mipmap/                 # App icons
│   ├── values/                 # Strings, colors, themes
│   └── xml/                    # File provider paths, backup rules
└── build.gradle.kts            # App-level build configuration
```

## Development Status

⚠️ **Current Status**: Active Development (University Project)

The application is functional with core features implemented. Ongoing work includes:
- Performance optimizations for large group communications
- Enhanced security and encryption
- Improved connection stability
- Extended file type support
- Multi-language support

## Contributing

This is a university project. Contributions, suggestions, and bug reports are welcome through issues and pull requests.

## License

[Specify License]

## Acknowledgments

- Built with modern Android development practices
- Utilizes Jetpack Compose for declarative UI
- Implements WiFi Direct for offline communication
- Designed for emergency and disaster response scenarios

## Contact

For questions, support, or collaboration inquiries, please open an issue in the repository.

---

**ConnectAid** - *Communication Without Boundaries*
