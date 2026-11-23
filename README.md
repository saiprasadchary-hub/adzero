# AdZero Ultimate — Privacy & Network Protection for Android

**Tagline:** *Block Ads. Protect Privacy. Take Control.*

AdZero Ultimate is a 100% local, privacy-focused Android application built with Kotlin, Jetpack Compose, Material 3, Room, DataStore, WorkManager, and native Android system services.

## Architecture Overview

- **`core/design` & `ui/theme`**: Calm light-mode Material 3 design system (`#FFFFFF` / `#F7F7FB` surfaces, `#7047D7` accent, `#171725` navy typography) with bundled `Plus Jakarta Sans`, `Inter`, and `JetBrains Mono` fonts.
- **`core/database` & `core/datastore`**: Room SQLite persistence for batched DNS logs, custom allow/block rules, blocklist sources, per-app firewall rules, AES-GCM encrypted Ghost Inbox previews, Ghost Mode schedules, and service diagnostic events.
- **`core/security`**: Android Keystore `AES/GCM/NoPadding` encryption (`CryptoManager`) and Jetpack `BiometricPrompt` lock (`BiometricHelper`).
- **`core/network`**: Pure Kotlin IPv4/IPv6 + UDP + DNS wire-format codec (`DnsPacketCodec`), lock-free snapshot `DnsFilterEngine` with allowlist precedence, wildcard rules, and bounded LRU cache, `UpstreamDnsResolver` supporting DoH (RFC 8484), DoT (port 853 TLS with hostname verification), and UDP, plus `BlocklistUpdateManager` via WorkManager.
- **`service/vpn`**: Local-only `AdZeroVpnService` supporting Standard DNS Filtering mode (virtual DNS route only—never captures non-DNS traffic) and opt-in Strict Packet Isolation mode (null-routes only blocked packages).
- **`service/notification`**: Optional `GhostNotificationListenerService` for local WhatsApp / WhatsApp Business preview capture.
- **`service/accessibility`**: Optional consent-gated `YouTubeSkipAccessibilityService` strictly scoped to `com.google.android.youtube`.
- **`service/quicksettings` & `service/scheduler`**: Truthful `AdZeroTileService` and `GhostScheduleManager` combining `AlarmManager` (when exact alarms are permitted) + `WorkManager` + reboot/timezone receivers.

## Building & Testing

### Build Debug APK
```bash
gradle :app:assembleDebug
```
Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### Build Signed Release APK
Provide `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` environment variables, then run:
```bash
gradle :app:assembleRelease
```

### Run Automated Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

## Honest Technical Boundaries

1. **DNS Filtering Scope**: Local DNS filtering cannot reliably remove ads served from the exact same domain as primary content (such as YouTube in-stream video ads or first-party feed ads).
2. **Android Single-VPN Rule**: Android permits only one active `VpnService` at a time. Enabling Strict Packet Isolation isolates blocked apps at the packet level while pausing system-wide DNS filtering until Standard mode is restored.
3. **WhatsApp Ghost Mode**: WhatsApp offers no official API for per-contact one-tick delivery receipt suppression. Ghost Mode blocks WhatsApp connectivity globally so new messages generally remain undelivered until reconnection. Ghost Inbox displays available Android notification previews only.
4. **YouTube Skip Assistant**: Optional AccessibilityService only activates clickable Skip buttons inside YouTube and cannot remove non-skippable ads.
5. **Android Private DNS**: If system Private DNS is set to a strict custom hostname, Android may bypass local VPN DNS queries. Keep system Private DNS set to Automatic or Off and use AdZero's built-in DoH/DoT forwarding instead.
