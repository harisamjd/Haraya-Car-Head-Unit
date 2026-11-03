# DESIGN.md: Haraya Dock

## Identity & Character
- **Product**: Haraya Dock, custom Android automotive head unit launcher for Indonesian drivers.
- **Visual Language**: Tactile Silk & Precision Instrument Cockpit (inspired by `agungpmbd/goldendash` MIT and automotive instrument cluster ergonomics).
- **Dials**: `ENERGY 2 / RHYTHM 3 / MOTION 1`
  - **ENERGY 2 (Balanced)**: High-contrast glanceable instrument typography paired with a multi-fold cubic Bezier silk backdrop (`SilkBackgroundView`) rendered once per theme change.
  - **RHYTHM 3 (Asymmetric Cockpit Composition)**:
    1. Left vertical sidebar rail (`84dp`) for one-thumb access while driving.
    2. Top-left instrument card combining large tabular clock, date, and 270-degree radial `SpeedGaugeView`.
    3. Bottom-left spiritual & schedule card with a 5-column prayer schedule strip (`Subuh`, `Dzuhur`, `Ashar`, `Maghrib`, `Isya`) and interactive daily Quran/Hadith reflection.
    4. Top-right primary navigation & live location card with custom vector cartography (`MapCanvasView`), live GPS status, and direct Google Maps Location Sharing CTA.
    5. Bottom-right dual hardware control sliders (`Volume` and `Kecerahan`) wired to real system audio and window brightness.
  - **MOTION 1 (Tactile States Only)**: Ripple press states and instant theme/slider response; no distracting continuous animations while driving.

## Palette & Surface Hierarchy (R-01, R-10, R-29)
- **Light Mode ("Warm Ivory Silk")**:
  - Silk Backdrop: `#F2E9DC` -> `#DCC7AC` -> `#EFE4D5` -> `#D6BE9F` (gives translucent white cards real depth and prevents sterile white-on-grey).
  - Primary Card Glass: top `#99FFFFFF` to bottom `#6BFFFFFF`, stroke `#B3FFFFFF`.
  - Inner Surface: `#B8FFFFFF` solid-tonal container for nested items (prayer slots, gauge box).
  - Text Primary: `#16161A`, Text Secondary: `#5E5850`, Text Muted: `#8C8479`.
  - Accent: `#1A6DEB` (Cobalt Instrument Blue).
- **Dark Mode ("Midnight Indigo Silk")**:
  - Silk Backdrop: `#1D1D31` -> `#121220` -> `#24243D` -> `#181A2E` with `#B39EA3FF` rim highlights.
  - Primary Card Glass: top `#A623243A` to bottom `#9415162A`, stroke `#2EFFFFFF`.
  - Inner Surface: `#24FFFFFF` tonal container.
  - Text Primary: `#F1F0F8`, Text Secondary: `#B3B2CA`, Text Muted: `#7F7E99`.
  - Accent: `#7076F0` / `#4DA3FF` (Luminous Periwinkle).

## Border Radius Scale (R-11)
- Primary Cockpit Cards: `26dp`
- Inner Containers (Map viewport, Speedometer well, Sidebar rail): `20dp`
- Interactive Controls, Prayer Slots & Action Buttons: `14dp`
- Slider Track: `12dp`

## Written Reasons for Major Decisions (R-31)
- **Why Bezier silk background**: Translucent cards over a flat colour look like flat grey boxes; layered silk folds give glass surfaces visible depth at zero runtime CPU cost.
- **Why asymmetric 2-column + sidebar layout**: Places primary thumb targets (Sidebar, Speedometer, Clock) closest to the driver while giving the Map and Media/Control sliders wide horizontal room.
- **Why custom vector icons instead of emoji**: Emoji render inconsistently across Android OEM head units and look amateurish; geometric vector strokes stay crisp at any DPI and follow theme tinting.
- **Why 3 distinct border radii (26dp / 20dp / 14dp)**: Distinguishes outer cards from inner wells and interactive buttons so hierarchy is clear at a glance.
- **Why dedicated full OBD Telemetry Dashboard via Sidebar**: Cramming vehicle telemetry and prayer schedules into a single 350dp card caused tab text wrapping ("Sta tus Mo bil OB D") and cut off the daily prayer quote. Giving OBD its own dedicated cockpit screen accessible via the sidebar provides space for an authentic twin-dial cluster (270° Tachometer RPM + Speedometer), 6 live ECU sensor capsules, and a DTC error code scanner, while keeping the Home screen clean and peaceful.
- **Why dual-provider location (GPS + Network) and reverse geocoding**: GPS satellites often take minutes or fail entirely inside garages, houses, or office buildings. Triangulating cellular/Wi-Fi (`NETWORK_PROVIDER`) provides an instant fix in under 1 second, and background reverse geocoding displays the driver's real city/district name automatically.
- **Why dedicated launcher status button and RoleManager intent**: Android security prevents third-party apps from taking over the home button silently. Adding an in-cockpit launcher status button directly launches Android's native Home App role picker (`RoleManager.createRequestRoleIntent`) on Android 10+ and settings fallback, while overriding `onBackPressed()` prevents accidental launcher termination.
- **Why in-cockpit Glass App Drawer Grid over dialog popups**: Standard `AlertDialog` text lists break the automotive launcher illusion and look like an unstyled system utility. Rendering a native 6-column grid inside a glass container with real app icons, search filtering, and ripple states preserves the Tactile Silk aesthetic while keeping the sidebar rail anchored on the left.

