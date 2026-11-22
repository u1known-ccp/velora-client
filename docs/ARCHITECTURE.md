# Velora Client – Architektur & Roadmap

> Velora ist ein eigenständiger Minecraft-Client (Fabric-basiert) mit eigenem Launcher,
> eigenem Mod-System, HUD-Editor, Cosmetics und optionalem Backend.
> Inspiriert von Feature-Umfang und Bedienkomfort etablierter Clients
> (Lunar, LabyMod, Badlion, Feather, NoRisk, Cosmic), aber ohne fremden Code,
> fremde Logos oder proprietäre Assets.

---

## 1. Analyse

### 1.1 Feature-Vergleich (Konzepte, nicht Code)

| Bereich            | Typisch bei etablierten Clients                         | Velora-Ansatz                                                  |
|--------------------|---------------------------------------------------------|----------------------------------------------------------------|
| Launcher           | Eigener Launcher, MS-Login, Versionswahl, RAM, News      | JavaFX-Launcher, offizieller MS Device-Code-Flow, Fabric-Install |
| Mod-Menü           | Rechte Shift-Taste, Kacheln, Suche, Kategorien           | Mod-Karten, Suche, Favoriten, Seitenpanel für Einstellungen     |
| HUD                | Frei verschiebbar, skalierbar, Profile                   | Anker-basiertes Layout (auflösungsunabhängig), Snap, Raster     |
| PvP-QoL            | Toggle Sprint, Keystrokes, CPS, Zoom, Freelook           | Alles rein clientseitig/legit, keine Spielmanipulation          |
| Performance        | Eigene Optimierungen + gebündelte Mods (Sodium etc.)     | Eigene, messbare Optimierungen; kompatibel zu Sodium/Lithium    |
| Cosmetics          | Capes, Wings, Hats, Emotes – serverseitig freigeschaltet | Capes (prozedural generiert), Berechtigung via Backend          |
| Backend            | Accounts, Cosmetics, Sync, Statistiken                   | Optional; Client funktioniert vollständig offline               |

### 1.2 Produktidentität

* **Name:** Velora Client
* **Farben:** tiefes Anthrazit (`#0E0F13`) als Basis, Akzent standardmäßig Violett-Blau (`#7C5CFF`), frei einstellbar
* **Formensprache:** abgerundete Panels (6–10 px), weiche Schatten, dezente Fade/Slide-Animationen (150–250 ms, ease-out)
* **Leitsatz:** *„Leicht, schnell, deins.“* – Fokus auf Stabilität und Performance statt Feature-Masse

---

## 2. Technische Architektur

```
                ┌──────────────────────────────┐
                │          launcher            │  JavaFX, MS OAuth, Downloads,
                │  (dev.velora.launcher)       │  Fabric-Installation, Logs
                └───────────────┬──────────────┘
                                │ startet JVM mit Fabric + Velora-Mod
                                ▼
┌───────────────────────────────────────────────────────────────────┐
│ client-1.21.4  (Fabric Mod, Yarn-Mappings, Mixins)                │
│  ├─ Plattform-Adapter: Rendering, Input, Mixins, Screens          │
│  ├─ Mods (HUD / PvP / Visuals / Performance / Utility)            │
│  └─ GUI: Mod-Menü, HUD-Editor, Einstellungen, Titelbildschirm     │
└───────────────┬───────────────────────────────────────────────────┘
                │ nutzt (versionsunabhängig)
                ▼
┌───────────────────────────────────────────────────────────────────┐
│ core  (reines Java 21 + Gson, KEINE Minecraft-Abhängigkeit)        │
│  Module-API · Settings · Config/Profile · EventBus · HUD-Layout    │
│  Cosmetics-Modell · Backend-Client · Animation-Math · Farben       │
└───────────────┬───────────────────────────────────────────────────┘
                │ HTTP/JSON (optional)
                ▼
┌───────────────────────────────────────────────────────────────────┐
│ backend  (JDK HttpServer, optional)                               │
│  /api/news · /api/updates · /api/cosmetics · /api/config · /stats │
│  Auth: Mojang sessionserver "join/hasJoined"-Verfahren            │
└───────────────────────────────────────────────────────────────────┘
```

### 2.1 Warum diese Aufteilung?

* **core** enthält alles, was nicht von Minecraft abhängt. Ein späteres
  `client-1.8.9`-Modul (Legacy Fabric) implementiert nur noch die Plattform-Schicht
  (Rendering/Input/Mixins) und nutzt dieselben Module-Definitionen, Settings, Profile und Configs.
* **client-1.21.4** ist ein normaler Fabric-Mod. Er läuft in jeder Fabric-Installation,
  wird aber normalerweise vom Velora-Launcher installiert und gestartet.
* **launcher** ist komplett unabhängig von Minecraft-Code: Er lädt offizielle
  Mojang-Metadaten (piston-meta), installiert Fabric über meta.fabricmc.net
  und startet das Spiel mit dem gebündelten Velora-Mod.
* **backend** ist optional. Ohne Backend: lokale News, lokale (kostenlose) Cosmetics,
  keine Cloud-Sync – alles andere funktioniert.

### 2.2 Wichtige technische Entscheidungen

| Entscheidung | Begründung |
|---|---|
| Minecraft **1.21.4** als erste moderne Version | Stabile Yarn-Mappings, ausgereifte Fabric API, `HudRenderCallback` verfügbar |
| **Yarn**-Mappings | Offizielle Fabric-Mappings; jede genutzte Methode wird gegen die dekompilierten Quellen (`genSources`) geprüft |
| HUD-Positionen als **Anker + Offset** | Elemente bleiben bei Fenster-/GUI-Scale-Änderungen an der richtigen Stelle |
| Polling statt Event-Spam | Mods ticken nur wenn aktiviert; HUD-Text wird gecacht und max. 20x/s neu berechnet |
| Kein Reflection-EventBus | Typisierte Listener-Listen (`EventBus#subscribe(Class, Consumer)`) → kein Overhead |
| Configs in **JSON** (Gson) | Lesbar, import-/exportierbar, versionsunabhängig |
| MS-Login per **Device Code Flow** | Offizielles OAuth2-Verfahren, kein Passwort berührt jemals den Launcher |
| Token-Speicherung via **Windows DPAPI** | Refresh-Tokens werden benutzergebunden verschlüsselt (Fallback: AES-GCM mit lokalem Schlüssel) |
| Backend-Auth über **sessionserver hasJoined** | Spieler beweist Account-Besitz, ohne Tokens an das Backend zu senden |

---

## 3. Projektstruktur

```
MC Client/
├─ build.gradle / settings.gradle / gradle.properties / gradlew(.bat)
├─ docs/                       Architektur, Roadmap, Entscheidungen
├─ core/
│  └─ src/main/java/dev/velora/core/
│     ├─ VeloraCore.java       Zentrale Instanz (Module, Config, Profile, Events)
│     ├─ module/               Module, Category, ModuleManager
│     ├─ setting/              Boolean/Number/Color/Mode/Text/Keybind-Settings
│     ├─ hud/                  HudLayout, Anchor, HudPosition
│     ├─ config/               ConfigManager, ProfileManager, JSON-Serialisierung
│     ├─ event/                EventBus
│     ├─ cosmetics/            Cosmetic-Modell, CosmeticsService (lokal / remote)
│     ├─ backend/              BackendClient (async HTTP), DTOs
│     └─ util/                 Animation, ColorUtil, Easing
├─ client-1.21.4/
│  └─ src/main/
│     ├─ java/dev/velora/client/
│     │  ├─ VeloraClient.java  Fabric-Entry-Point
│     │  ├─ mixin/             Mixins (Input, Render, Camera, Chat, ...)
│     │  ├─ mods/{hud,pvp,visual,performance,utility}/
│     │  ├─ gui/               ClickGui, HudEditor, Settings, TitleScreen, Widgets
│     │  ├─ render/            Render2D (Rounded Rects, Schatten), Theme
│     │  └─ cosmetics/         CapeTextures, CosmeticsManager
│     └─ resources/            fabric.mod.json, velora.mixins.json, assets/velora
├─ launcher/
│  └─ src/main/java/dev/velora/launcher/
│     ├─ LauncherMain.java / VeloraLauncherApp.java
│     ├─ auth/                 MicrosoftAuth (Device Code → XBL → XSTS → MC), AccountStore, Crypto
│     ├─ game/                 VersionManifest, Downloader, FabricInstaller, GameLauncher
│     ├─ ui/                   Views (Play, News, Accounts, Profiles, Settings, Logs)
│     └─ config/               LauncherSettings, Profiles, ServerShortcuts
└─ backend/
   └─ src/main/java/dev/velora/backend/BackendServer.java (+ handlers, storage)
```

---

## 4. Modul-Liste

Legende: ✅ = in v0.1 implementiert · 🔜 = geplant (Roadmap) · ➰ = mit anderem Modul zusammengelegt

### HUD
| Modul | Status | Hinweis |
|---|---|---|
| FPS Counter | ✅ | |
| CPS Counter | ✅ | Zählt echte Mausklicks (Mixin auf `Mouse#onMouseButton`) |
| Keystrokes | ✅ | WASD, Space, LMB/RMB mit CPS, animierte Tasten |
| Armor Status | ✅ | Haltbarkeit in %, Farben nach Zustand, Hand-Item optional |
| Potion Effects | ✅ | Name, Stufe, Restzeit, Blinken kurz vor Ablauf |
| Coordinates | ✅ | XYZ, Richtung, optional Nether-Umrechnung |
| Direction HUD | ✅ | Kompass-Streifen |
| Ping Display | ✅ | Aus Spielerliste (Server-Latenz) |
| Memory Usage | ✅ | |
| Speedometer | ✅ | Blöcke/s horizontal |
| Biome Display | ✅ | |
| Clock | ✅ | 12/24h, Sekunden optional |
| Scoreboard Customizer | ✅ | Ausblenden, Nummern ausblenden, Hintergrund, Skalierung |
| Server Information | ✅ | (Utility) Server-Adresse + Spielerzahl |

### PvP
| Modul | Status | Hinweis |
|---|---|---|
| Armor HUD | ➰ | = Armor Status |
| Potion HUD | ➰ | = Potion Effects |
| Hit Color | ✅ | Farbe des Treffer-Overlays |
| Combo Counter | ✅ | |
| Reach Display | ✅ | Reine Anzeige der Distanz des letzten Treffers, keine Manipulation |
| Crosshair Customizer | ✅ | Kreuz/Punkt/Kreis, Lücke, Dicke, Farbe, Umrandung |
| Toggle Sprint | ✅ | |
| Toggle Sneak | ✅ | |
| AutoGG | ✅ | Konfigurierbare Trigger + Nachricht, Cooldown |
| Perspective Mod | ✅ | Freelook in 3rd Person (Taste halten oder toggeln) |
| Zoom Mod | ✅ | Smooth Zoom, Scroll-Zoom, Sensitivität angepasst |

### Visuals
| Modul | Status | Hinweis |
|---|---|---|
| Fullbright | ✅ | Gamma-Override ohne Optionen-Datei zu verändern |
| Custom Crosshair | ➰ | = Crosshair Customizer |
| Freelook | ➰ | = Perspective Mod |
| Time Changer | ✅ | Nur visuell, nur clientseitig |
| Weather Changer | ✅ | Regen ausblenden / erzwingen (visuell) |
| Block Overlay | ✅ | Farbe/Deckkraft der Block-Umrandung |
| Nametags | ✅ | Eigenen Nametag in 3rd Person anzeigen (weitere Nametag-Optionen 🔜) |
| TNT Timer | 🔜 | |
| Damage Indicators | 🔜 | |
| Motion Blur | 🔜 | Benötigt eigenen Post-Shader |
| Item Physics | 🔜 | |
| Clear Glass | 🔜 | |
| Custom Sky | 🔜 | |

### Performance
| Modul | Status | Hinweis |
|---|---|---|
| FPS Optimizer | ✅ | FPS-Limit bei unfokussiertem/minimiertem Fenster |
| Entity Culling | ✅ | Distanz-basiertes Entity-Culling (konfigurierbar pro Typ) |
| Particle Optimizer | ✅ | Partikel-Dichte (%) und harte Obergrenze |
| Render Distance Optimizer | ✅ | Dynamische Sichtweite nach Ziel-FPS |
| Memory Optimizer | 🔜 | Nur mit messbarem Effekt – sonst nicht ausgeliefert |
| Animation Optimizer | 🔜 | |

### Utility
| Modul | Status | Hinweis |
|---|---|---|
| Auto Text | ✅ | Bis zu 6 Hotkeys mit Nachrichten/Befehlen |
| Chat Customizer | ✅ | Zeitstempel, Hintergrund-Deckkraft |
| Coordinates Clipboard | ✅ | Hotkey kopiert Koordinaten |
| Quick Join | ✅ | Hotkey verbindet zu konfiguriertem Server |
| Waypoints | ✅ | Pro Welt/Server, HUD-Liste mit Distanz & Richtungspfeil |
| Screenshot Manager | ✅ | Ordner öffnen + letzten Screenshot kopieren (Pfad) |

---

## 5. Abhängigkeiten

| Komponente | Abhängigkeit | Version | Zweck |
|---|---|---|---|
| alle | Java | 21 (LTS) | Laufzeit & Build |
| Build | Gradle (Wrapper) | 8.14.3 | Build-System |
| client | Fabric Loom | 1.10.5 | Minecraft-Dev-Umgebung, Remapping |
| client | Minecraft | 1.21.4 | Spiel |
| client | Yarn | 1.21.4+build.8 | Mappings |
| client | Fabric Loader | 0.16.14 | Mod-Loader |
| client | Fabric API | 0.119.4+1.21.4 | Events (HUD, Ticks, Keybinds, Chat, Rendering) |
| core | Gson | 2.11.0 | JSON (identisch zur Minecraft-Version → kein Konflikt) |
| core | SLF4J API | 2.0.16 | Logging (von Minecraft bereitgestellt) |
| launcher | OpenJFX | 21.0.5 | UI |
| launcher | JNA Platform | 5.15.0 | Windows DPAPI für Token-Verschlüsselung |
| launcher/backend | Shadow | 8.3.5 | Ausführbare Fat-Jars |

---

## 6. Roadmap

| Phase | Inhalt | Status |
|---|---|---|
| 1 Analyse | Anforderungen, Architektur, Struktur | ✅ |
| 2 Core | Mod-System, Settings, Config/Profile, Logging, EventBus | ✅ v0.1 |
| 3 GUI | Mod-Menü, HUD-Editor, Animationen, Einstellungen, Titelbildschirm | ✅ v0.1 |
| 4 Mods | Siehe Modul-Liste | ✅ v0.1 (Kernumfang) |
| 5 Launcher | MS OAuth, Versions-/Download-/Fabric-Installation, Logs, Profile | ✅ v0.1 |
| 6 Testing | Builds, Start in Dev-Umgebung, Performance-Check | ✅ v0.1 |
| 7 Release | Build-System, README, Artefakte | ✅ v0.1 |
| 8 | `client-1.8.9` (Legacy Fabric) auf Basis von `core` | 🔜 |
| 9 | Wings/Hats/Emotes (Modelle + Animationen), Cosmetic-Shop im Backend | 🔜 |
| 10 | Motion Blur, TNT Timer, Damage Indicators, Item Physics | 🔜 |
| 11 | Launcher: Mojang-Java-Runtime-Download, Delta-Updates | 🔜 |

---

## 7. Implementierungsplan v0.1

1. Gradle-Multiprojekt + Wrapper, `genSources` zur API-Verifikation
2. `core`: Settings → Module → ModuleManager → ConfigManager/ProfileManager → EventBus → Backend-Client
3. `client`: Entry-Point, Render2D-Helfer, HUD-Renderpipeline, Keybind-Handling
4. Mods kategorieweise, jeweils mit verifizierten Mixins
5. GUI: Widgets → ClickGui → HUD-Editor → Settings → Titelbildschirm
6. Cosmetics: prozedurale Capes, Berechtigungsprüfung lokal/Backend
7. Launcher: Settings/Accounts → MS-Auth → Downloader/Installer → Launch + Logs → UI
8. Backend: HttpServer + Handler + JSON-Storage
9. Build, Dev-Start (`runClient`), Fehler beheben, README

## 8. Teststrategie

| Ebene | Werkzeug | Prüft |
|---|---|---|
| Core | JUnit 5 (`gradlew :core:test`) | Settings-Validierung, Modul-Serialisierung, Profile (anlegen/wechseln/export/import), beschädigte Configs, HUD-Anker |
| Client | `gradlew :client-1.21.4:runSelftest` | Mixin-Audit (alle Mixins werden angewendet), alle Velora-Screens, Testwelt mit aktiven HUD-/Visual-Mods, Screenshots |
| Launcher | `VeloraLauncher.jar --selftest` | Komplette Installation (Mojang + Fabric + Assets + Mods), Java-Auswahl, Spielstart, Client-Selbsttest im gestarteten Spiel |
| Launcher-UI | `VeloraLauncher.jar --ui-snapshots=<dir>` | Rendert jede Launcher-Seite als PNG |
| Backend | HTTP-Smoke-Test | Health, News, Updates, Challenge, Berechtigungen (401/403), Statistiken |

Wichtige Implementierungsentscheidung: Jede in Mixins verwendete Minecraft-Methode wurde gegen die mit Loom
dekompilierten 1.21.4-Quellen geprüft (`genSources`); das Mixin-Audit im Selbsttest stellt sicher, dass alle
Injection-Points tatsächlich existieren.

## 9. Sicherheit & Datenschutz

* Der Launcher sieht **nie** ein Microsoft-Passwort (Login im Browser via Device-Code).
* Refresh-/Access-Tokens werden mit Windows DPAPI verschlüsselt gespeichert, nie im Klartext.
* Tokens werden ausschließlich an Microsoft-/Xbox-/Mojang-Endpunkte gesendet.
* Das Backend erhält nur UUID + Name; Besitznachweis via `sessionserver.mojang.com/hasJoined`.
* Statistiken sind opt-in und anonym.
