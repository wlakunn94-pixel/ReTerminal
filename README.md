# Looking for contributors
I currently don't have enough time to actively maintain ReTerminal.
If you're interested in keeping the project alive, contributions are very welcome!



# ReTerminal
**ReTerminal** is a sleek, Material 3-inspired terminal emulator designed as a modern alternative to the legacy [Jackpal Terminal](https://github.com/jackpal/Android-Terminal-Emulator). Built on [Termux's](https://github.com/termux/termux-app) robust TerminalView

Download the latest APK from the [Releases Section](https://github.com/RohitKushvaha01/ReTerminal/releases/latest).

# Features
- [x] Basic Terminal
- [x] Virtual Keys
- [x] Multiple Sessions
- [x] Alpine Linux support
- [x] Configurable Keyboard Shortcuts (Paste, Session Management)
- [x] Native USB-host detection for Android ADB and Fastboot devices

## Android-to-Android USB tools

Open the USB icon in the terminal drawer, connect the target phone with a USB-C OTG cable,
and tap **Scan USB devices**. ReTerminal uses Android's native `UsbManager`, so the host
phone does not need root, Shizuku, or a desktop computer.

- ADB interfaces are detected and Android's normal USB-debugging authorization dialog is used.
- Fastboot devices support a native `getvar:product` query over the USB bulk transport.
- USB host support depends on the host phone and cable supporting USB OTG. The target must
  expose ADB or Fastboot; MTP-only devices are intentionally ignored.

# Screenshots
<div>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="32%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/02.jpg" width="32%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/03.jpg" width="32%" />
</div>

## Community
> [!TIP]
Join the reTerminal community to stay updated and engage with other users:
- [Telegram](https://t.me/reTerminal)
