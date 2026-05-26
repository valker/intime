# InTime App Icon Design Guide

## Overview

This guide provides specifications for creating the final app icons for InTime v2.0.0.

## Icon Specifications

### Launcher Icon

**Purpose:** Main app icon displayed on home screen and app drawer

**Sizes Required:**
- ldpi: 36x36 px
- mdpi: 48x48 px
- hdpi: 72x72 px
- xhdpi: 96x96 px
- xxhdpi: 144x144 px
- xxxhdpi: 192x192 px

**Design Notes:**
- Remove "DEV" indicator badge
- Create professional, clean design
- Support both light and dark themes (if needed)
- Ensure recognizability at small sizes (home screen)

**Format:** PNG with transparency

**Location:** `app/src/main/res/drawable-*/app_icon.png`

### Adaptive Icon

**Purpose:** Android 8+ adaptive icon (with safe zone for dynamic masking)

**Design Specifications:**
- Canvas: 108x108 dp (or 192x192 px @ mdpi)
- Safe Zone (foreground): Inner 72x72 dp (or 128x128 px @ mdpi)
- Safe Zone (background): Full 108x108 dp (or 192x192 px @ mdpi)
- Foreground: PNG with transparency
- Background: Solid color or simple shape

**Files Needed:**
- `app_icon_foreground.png` — Foreground layer (108x108 dp)
- `app_icon_background.png` or color reference

**Adaptive Icon XML:** `adaptive_icon.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/app_icon_background"/>
    <foreground android:drawable="@drawable/app_icon_foreground"/>
</adaptive-icon>
```

**Location:** `app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml`

### Round Icon (Optional)

**Purpose:** Rounded app icon for devices that support it

**Specifications:**
- Same sizes as launcher icon
- Rounded corners (typically 20-30% radius)
- Fallback for devices without adaptive icon support

**Location:** `app/src/main/res/drawable-*/app_icon_round.png`

## Design Direction

### Visual Style
- Modern, minimal design
- Clean lines and simple shapes
- Professional and trustworthy appearance
- Suitable for a reminder/productivity app

### Color Palette

Consider these options:

**Option A: Blue Focus**
- Primary: Deep blue (#2563EB or similar)
- Secondary: Light background
- Accent: Time/clock element (represents reminders)

**Option B: Green Focus**
- Primary: Green (#10B981 or similar)
- Secondary: White/light background
- Accent: Checkmark or calendar element

**Option C: Purple Focus**
- Primary: Purple (#8B5CF6 or similar)
- Secondary: Light background
- Accent: Bell or notification element

### Icon Concepts

**Concept Ideas:**
1. **Clock/Timer + Checkmark** — Represents timed reminders and completion
2. **Calendar + Bell** — Represents scheduling and notifications
3. **Alarm Clock** — Represents reminder functionality
4. **List + Clock** — Represents task lists with timing

## Current State

Currently using DEV icons with green badge:
- `app_icon_dev.xml` — Composite with green indicator
- `app_icon.png` — Base icon (xhdpi only)

## Implementation Steps

1. **Design** — Create icon artwork (2-4 weeks typical)
2. **Export** — Generate all required sizes and formats
3. **Place Files** — Put PNG files in correct mipmap/drawable directories
4. **Update Manifest** — Change `android:icon` and `android:roundIcon` references
5. **Test** — Verify appearance on different devices
6. **Release** — Include in release build

## File Naming Convention

After design is complete, rename files:
- `app_icon.png` → Used for all sizes (ldpi, mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
- `app_icon_round.png` → Rounded variant
- `app_icon_foreground.png` → Adaptive icon foreground
- `app_icon_background.xml` → Adaptive icon background

## Tools for Designers

Recommended tools for icon design:
- **Figma** — Collaborative design
- **Adobe Illustrator** — Professional vector design
- **Sketch** — macOS design tool
- **Inkscape** — Free, open-source vector tool

## Android Studio Integration

Android Studio has built-in tools:
1. Right-click `res/` → New → Image Asset
2. Create launcher icons from base image
3. Generates all required sizes automatically

This is the recommended approach for generating consistent icon variations.

## Delivery Format

Designer should provide:
- [ ] Launcher icon (all sizes)
- [ ] Rounded icon variant (optional)
- [ ] Adaptive icon foreground (108x108 dp)
- [ ] Adaptive icon background (solid color or image)
- [ ] Design source files (for future updates)
- [ ] Icon usage guide (if custom)

## Questions?

If unclear about specifications, refer to:
- [Android Icon Guidelines](https://developer.android.com/guide/practices/ui_guidelines/icon_design_launcher)
- [Material Design Icons](https://fonts.google.com/icons)
- [Android Studio Image Asset Guide](https://developer.android.com/studio/write/image-asset-studio)
