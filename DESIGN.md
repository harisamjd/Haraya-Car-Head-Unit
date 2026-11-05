# DESIGN.md: Haraya Dock

## Identity & Character
- **Product**: Haraya Dock, custom Android automotive head unit launcher for Indonesian drivers.
- **Visual Language**: Precision Carbon & Warm Amber Instrument Cockpit (inspired by OEM automotive instrument clusters and `agungpmbd/goldendash` MIT).
- **Dials**: `ENERGY 2 / RHYTHM 3 / MOTION 1`
  - **ENERGY 2 (Balanced)**: High-contrast glanceable instrument typography paired with matte carbon/alabaster surfaces and tactile capsule controls.
  - **RHYTHM 3 (3-Column Automotive Cockpit + Classic Sidebar Mode)**:
    1. **Car Cockpit Mode (Default)**: 3-column layout with Top Status Bar, Bottom Navigation Dock, Left Column (Live OSM Map + 5-Time Prayer Strip + Daily Quran/Hadith Reflection), Center Stage (200° Speedometer Arc + Digital Clock + Vehicle Illustration + Media Controller), and Right Column (Trip Summary & History + PIN-Protected 2x2 Service Reminders + Open-Meteo Weather).
    2. **Classic Silk Mode**: Left vertical sidebar rail (`76dp`) + 2-column instrument cards + dual hardware sliders.
  - **MOTION 1 (Tactile States Only)**: Ripple press states and instant theme/slider response; no distracting continuous animations while driving.

## Palette & Surface Hierarchy (R-01, R-10, R-29)
- **Zero AI-Slop Purple**: Eliminated all generic indigo/purple (`#6366F1`, `#818CF8`) hues in favor of authentic automotive instrument cluster colors.
- **Dark Mode ("Carbon Slate & Warm Instrument Amber")**:
  - Window Backdrop: `#0B0E11` (Deep Carbon Slate)
  - Primary Card Surface: top `#13171C` to bottom `#101418`, stroke `#242C35` (`1dp` machined slate border).
  - Inner Capsule Surface: top `#1A2027` to bottom `#171C22`.
  - Text Primary: `#F3F5F7`, Text Secondary: `#9BA5B1`, Text Muted: `#6E7885`.
  - Primary Accent: `#E59A28` / `#F5B041` (Warm Automotive Amber).
  - Active Selection: `#282115` -> `#201A11` with `#FBBF24` foreground.
- **Light Mode ("Warm Alabaster & Instrument Teal")**:
  - Window Backdrop: `#ECEAE4` (Warm Alabaster Stone to reduce daytime glare).
  - Primary Card Surface: top `#FAF9F5` to bottom `#F3F1EC`, stroke `#D8D4CA`.
  - Inner Capsule Surface: top `#EAE7DF` to bottom `#E4E0D7`.
  - Text Primary: `#14171A`, Text Secondary: `#4A525A`, Text Muted: `#68707A`.
  - Primary Accent: `#0F766E` / `#0D9488` (Deep Instrument Teal).
  - Active Selection: `#D5EDE9` -> `#C4E5DF` with `#0F534E` foreground.

## Border Radius Scale (R-11)
- Primary Cockpit Cards & Custom Modals: `16dp`
- Inner Containers (Map viewport, Speedometer well, Service cells, Modal inputs): `10dp - 12dp`
- Interactive Pills & Prayer Slots: `8dp`

## Written Reasons for Major Decisions (R-31)
- **Why Carbon Slate + Warm Amber instead of Indigo/Purple**: Automotive cockpits use warm amber, neutral slate, and emerald indicators to preserve night vision and avoid generic AI-generated purple gradients (R-01).
- **Why Custom `HarayaDialog` Modals over Android `AlertDialog`**: Stock system `AlertDialog` windows ignore the launcher's surface hierarchy and render unstyled green/grey boxes with raw white `EditText` fields. Custom-themed `Dialog` containers built on `GlassTheme` keep PIN entry, Trip History, Service Settings, and Vehicle Identity visually unified with the cockpit.
- **Why Horizontal Service Cells & Compact Side-by-Side Weather Row**: On landscape automotive head units (`360dp-410dp` height), stacking an icon + title + status vertically inside a 2x2 grid clips the bottom line. Placing the service icon beside a 2-line text stack and placing weather temperature beside its condition label guarantees 100% legibility with zero text clipping.
