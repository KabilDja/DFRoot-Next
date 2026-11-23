<p align="center">
  <img src=".github/icon.png" width="96" />
</p>

# DFRoot Next (UI rework for DFRoot)

DFRoot is an Android rooting tool that exploits CVE-2026-43284 (DirtyFrag) — a kernel page cache write primitive — to load a custom kernel module without requiring an unlocked bootloader. It is intentionally designed to be SU Manager agnostic and works with any KernelSU-compatible SU manager.

> [!IMPORTANT]
> This fork is just a UI rework of DFRoot.

## FAQ

**Q: Log says patching failed and that my device isn't vulnerable**

A: Sorry, there is no fix. Either:
  - Your device's kernel is too new
  - Your device manufacturer backported the official mitigation
  - [Accidental mitigation](https://github.com/V4bel/dirtyfrag/issues/23#issuecomment-4405314290) (typically seen on kernel 6.1)

**Q: Log says "SUCCESS" or "ksud exited with error", but I don't have root**

A: First, try disabling "Auto Soft Reboot" in DFRoot settings if you have it enabled, otherwise https://github.com/diabl0w/DFRoot/discussions/69

**Q: I installed a module and now I can't launch root without crashing**

A: Toggle "Disable KSU Modules" in DFRoot settings

**Q: I installed a module and have "Start at Boot" enabled and now I am bootlooping**

A: Typically Android will detect bootloops and boot you into Safe Mode, otherwise you can usually hold the Volume Down button during boot to enter safe mode. From there, disable "Start on Boot" and enable "Disable KSU Modules" in DFRoot settings or just uninstall DFRoot until you stabilize.

## Usage

> [!WARNING]
> I am not responsible for any damage to your device.

1. Install [DFRoot Next](https://github.com/KabilDja/DFRoot-Next/releases/tag/v1.0)
2. Install a SU Manager from below:
   - Samsung:
     - [KabilDja's Samsung-KernelSU](https://github.com/KabilDja/Samsung-KernelSU)
     - [diabl0w's KernelSU](https://github.com/diabl0w/KernelSU/releases/latest)
   - Other:
     - [KernelSU](https://github.com/tiann/KernelSU/releases/latest)
     - [KernelSU-Next](https://github.com/KernelSU-Next/KernelSU-Next/releases/latest)
     - [KowSU](https://github.com/KOWX712/KernelSU/releases/latest)

> If you have a custom KernelSU fork for a specific manufacturer, please make a pull request to add it here.

## Features

- Start on Boot
- Automatic soft reboot (if `late-load --soft-reboot` supported by your SU Manager)
- RO Partition Protection
- Disable all KernelSU modules, to recover from a broken one
- Shizuku not needed — regain root without WiFi!

## Credits

- [diabl0w](https://github.com/diabl0w/DFRoot) for DFRoot.
- Original PoC and various code: https://github.com/lsposed/lspromise
- SELinux Permissive kernel modules and various code: https://github.com/polygraphene/DFReroot
- Unprivileged XFRM socket method: https://github.com/combeng6th/DirtyInit
