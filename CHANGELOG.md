# Changelog

All notable changes to SpeedoMate will be documented in this file.

## [Unreleased]

### ✨ Added
- **Custom Themes** — 7 preset accent colors (Cyan, Electric Blue, Neon Green, Amber, Hot Pink, Lime, Gold) + auto-detected Monet wallpaper color on Android 12+
- **Speed Limit Seekbar** — Custom styled seekbar with min/max labels (0–180 km/h or 0–112 mph)
- **Settings UI Redesign** — Card-based layout with icons, gradient top bar, value badges, and theme picker
- **Speed Limit Runtime Updates** — Limit changes apply instantly without app restart via continuous `prefs.speedLimitThreshold` Flow collection
- **AppCompatButton** — Replaced `MaterialButton` with `AppCompatButton` + custom drawables to fix inflation crashes
- **Trip History Accent Colors** — Date text and share images now respect the selected theme

### 🔧 Fixed
- Speed limit danger zone disappears completely when no limit is set (was falling back to 80%)
- Speed limit threshold capped to meter max (180 km/h / 112 mph) so the red line never goes beyond the gauge
- MPH speed limit max corrected to 112 (180 km/h × 0.621371) instead of 120
- Stats card background (MAX/AVG/TRIP box) now properly fills the entire card area
- Seekbar thumb no longer clipped at edges (added 12dp padding)
- Speed graph labels: km/h and m asl vertically centered on their respective axes
- Speed graph left Y-axis labels colored to match the speed graph line
- Speed graph legend text colored to match theme
- SpeedometerView arc line, needle, glow, and center dot now respect dynamic accent color (were hardcoded cyan)
- MIUI hidden API crash (`getWallpaperInfo`) caught via `Throwable` catch block
- Unit toggle seekbar max changes dynamically when switching between km/h and mph

### 🎨 UI Polish
- Icon-based action buttons (Save+Reset, Discard, Trip History, Settings) with compound drawables
- Stats card top accent line follows selected theme color
- Stats card main background tinted with 15% alpha of selected accent color
- Unit text (`km/h`, `km`) styled as subtle grey matching labels/icons
- Speed limit value badge with dynamic background (accent color when active, grey when "Off")
- Settings top gradient accent bar follows theme color
- Settings icons (palette, unit gauge, alert) tinted to match theme
- SpeedGraphView share images use selected theme accent color for top bar and graph elements
- Theme picker swatches use larger size (40dp) with proper spacing (16dp) in a `HorizontalScrollView`
- Removed `MaterialButton` theme dependency to avoid `InflateException` crashes
- Removed Purple (#9B59B6) from preset colors

### 📦 Technical
- `ThemeApplier.kt` utility for programmatic accent color application
- `accentColor` Flow added to `SpeedViewModel` and `PrefsManager`
- `displayedSpeedLimit` Flow converts stored km/h to displayed value based on current unit
- Speed limit stored internally as km/h, converted on display for mph mode
- `SpeedometerView` `accentColor` property dynamically updates all accent paints
- `SpeedGraphView` `accentColor` property updates line, labels, and legend colors
