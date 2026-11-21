# CraftLauncher — Minecraft: Java Edition Android Launcher

CraftLauncher is a modern Kotlin + Jetpack Compose (Material 3) Android launcher for **Minecraft: Java Edition**, designed with an Obsidian & Emerald dark UI, adaptive tablet/foldable navigation, official Microsoft authentication, local offline profiles, Mojang `piston-meta` version & instance management, Java 8/17/21 runtime management, Fabric/Forge mod & resource pack inspection, and an OpenGL ES / Touch / Hardware Keyboard & Mouse viewport.

---

## Implemented Architecture & Features

### 1. Home Screen & Launch Preflight
- CraftLauncher branding, hero banner, active profile card, active instance card, download progress indicator, recently played carousel, and a large **PLAY** button.
- Clicking **PLAY** runs `LaunchPreflightEngine` to verify:
  1. Active Account (Microsoft licensed token OR Local Offline profile)
  2. Game Files (`<version>.json`, `<version>.jar` SHA-1, JVM libraries, asset index)
  3. Compatible Java Runtime (`Java 8`, `17`, or `21` matching `javaVersion.majorVersion` & device CPU ABI)
  4. Native Execution Bridge (`libpojavexec.so`, `libgl4es_114.so`, `liblwjgl_opengl.so`, `libopenal.so`)
- Assembles the full JVM command line and environment variables (`LIBGL_ES`, `POJAV_RENDERER`, `JAVA_HOME`, `-Xmx`) before opening `GameActivity`.

### 2. Native Execution Engine Honesty & Upstream Licensing
Running desktop **Minecraft: Java Edition** bytecode natively on Android requires C/C++ JNI shared libraries (`libpojavexec.so`, `libgl4es_114.so`, `liblwjgl_opengl.so`, `libglfw_android.so`, `libopenal.so`) and an Android-bionic **OpenJDK Mobile** runtime (`libjvm.so`).
- **Upstream Projects**: [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) (archived) and its active successor [Amethyst Android (AngelAuraMC)](https://github.com/AngelAuraMC/Amethyst-Android) are licensed under **GNU GPLv3**, bundling **GL4ES** (MIT), **LWJGL3/GLFW** (BSD-3-Clause), **OpenAL-Soft** (LGPLv2), and **OpenJDK Mobile** (GPLv2 + Classpath Exception).
- **Current Status in CraftLauncher**:
  - **Implemented**: Real `GameActivity` with `GLSurfaceView` (OpenGL ES 2.0/3.0), virtual touch controls HUD (WASD, Jump, Sneak, Sprint, Attack, Use, Hotbar 1–9, F3, Chat), hardware keyboard (`KeyEvent`), Android 8.0+ relative mouse pointer capture (`requestPointerCapture()`), `AudioTrack` game audio, and live diagnostics console.
  - **Missing for Full Desktop Bytecode Execution**: Because prebuilt GPLv3 NDK `.so` binaries (`libpojavexec.so`, `libgl4es_114.so`, `liblwjgl_opengl.so`, `libopenal.so`) and OpenJDK Mobile `libjvm.so` are not compiled inside this pure-Kotlin repo, `LaunchPreflightEngine` explicitly reports those missing native libraries instead of faking a launch, and allows launching `GameActivity` in **OpenGL ES Viewport & Controls Test Mode**.
  - **Integrating Upstream Native Binaries**: To enable full desktop JVM execution in your fork, add the GPLv3-compatible C/CMake JNI bridge (`jniLibs/<abi>/*.so`) from AngelAuraMC/Amethyst or PojavLauncher under `app/src/main/jniLibs/` and keep your fork compliant with GPLv3.

### 3. Accounts: Official Microsoft Login & Local / Offline Profiles
- **Local / Offline Profiles**:
  - Create multiple local profiles with any custom username (2–16 chars).
  - Choose from built-in pixel-art avatars or pick any image from your phone using Android's zero-permission **Photo Picker** (copied permanently to app storage).
  - Edit, rename, set per-profile RAM overrides, switch instantly without re-signing in, and delete profiles.
  - Generates standard deterministic Minecraft offline UUID v3 (`OfflinePlayer:<username>`).
  - **What works offline**: Single-player worlds, local LAN worlds, mod/resource-pack management, and offline launching once game files, JRE, and native bridge are present.
  - **What requires Microsoft Login**: Joining online authenticated multiplayer servers (`online-mode=true`), Realms, and Mojang session verification.
- **Official Microsoft Login**:
  - Implements Microsoft OAuth 2.0 Device Code Flow -> Xbox Live -> XSTS -> Minecraft Services (`login_with_xbox`, `entitlements/mcstore`, `minecraft/profile`).
  - Encrypts saved tokens at rest using **Android Keystore (`AES/GCM/NoPadding`)**.
  - Never bypasses Minecraft ownership checks or fabricates tokens. Requires your own free Azure AD Public Client ID entered in the Microsoft Login dialog.

### 4. Versions, Runtimes, Mods & Resource Packs
- **Versions**: Live browser backed by `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json` with Releases/Snapshots/Beta-Alpha filters, Fabric Loader lookup (`https://meta.fabricmc.net/v2/versions/loader/{mcVersion}`), separate per-instance folders, and SHA-1 verified downloads.
- **Java Runtimes**: Detects device ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`), manages Java 8 / 17 / 21, supports Adoptium downloads or importing local `.tar.gz`/`.zip` JRE archives, and verifies `bin/java` / `libjvm.so`.
- **Mods & Resource Packs**: Per-instance `.jar` mod import (auto-parses `fabric.mod.json`, `META-INF/mods.toml`, `neoforge.mods.toml`, `mcmod.info`) and `.zip` resource pack import (parses `pack.mcmeta` and syncs `options.txt`).

---

## Building the APK Using Only an Android Phone (GitHub Actions)

You do **not** need a PC or Android Studio on a computer to compile the `.apk`:

1. **Export / Download the Project ZIP**:
   - In Google AI Studio, open the project menu and click **Export / Download ZIP** (or **Push to GitHub** directly if linked).
2. **Upload to GitHub from your Android Phone**:
   - Open your mobile browser (Chrome) and go to [github.com](https://github.com) (sign in or create a free account).
   - Tap **+** -> **New repository**, name it `CraftLauncher`, choose Public or Private, and tap **Create repository**.
   - If you downloaded a ZIP, extract it on your phone (e.g., using *Files by Google* or *ZArchiver*), or use **Termux** (`pkg install git`, `git init`, `git add .`, `git commit -m "Initial CraftLauncher"`, `git push`).
   - Make sure the `.github/workflows/android-build.yml` file is present in your repository!
3. **Trigger & Download the APK**:
   - On GitHub, tap the **Actions** tab in your repository.
   - Select **Build CraftLauncher Android APK** -> tap **Run workflow** (or it runs automatically on every `push`).
   - Wait ~3–5 minutes for the green checkmark.
   - Scroll down to **Artifacts** on the completed workflow run and tap **CraftLauncher-debug-apk** to download the ZIP containing `app-debug.apk`.
   - Extract the downloaded artifact ZIP and tap `app-debug.apk` on your Android phone to install CraftLauncher.

> **Package ID Note**: Inside AI Studio, `app/build.gradle.kts` uses `applicationId = "com.aistudio.craftlauncher.k8v3mq"` with `namespace = "com.example"` for platform compatibility while Kotlin feature modules live in `com.craftlauncher.android.*`. If you fork independently, you may change `applicationId` to `"com.craftlauncher.android"` in `app/build.gradle.kts`.

---

## Local Build Commands (PC / CI)

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```
The compiled APK is output to:
`app/build/outputs/apk/debug/app-debug.apk`
