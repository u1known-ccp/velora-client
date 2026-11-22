# Velora Client – Installation

## Variante A: Launcher (empfohlen)

1. **Java 21** installieren (z.B. Eclipse Temurin oder Oracle JDK 21) – nur für die `.jar`-Variante nötig.
   Die `VeloraLauncher.exe` aus `dist/app/VeloraLauncher/` bringt ihre eigene Java-Runtime mit.
2. `VeloraLauncher.exe` (bzw. `java -jar VeloraLauncher-0.1.0.jar`) starten.
3. **Konten** → *Microsoft-Konto hinzufügen* (siehe [MICROSOFT_LOGIN.md](MICROSOFT_LOGIN.md)).
4. **Spielen** klicken. Unter **Profile** wählst du die Minecraft-Version (26.3 oder 1.21.4). Beim ersten Start lädt der Launcher Minecraft, Fabric Loader, Fabric API und die
   Spiel-Assets von den offiziellen Servern (Mojang / FabricMC) – danach startet das Spiel mit Velora.

Daten liegen in `%APPDATA%\.velora\`:

| Ordner | Inhalt |
|---|---|
| `instances\<Profil>\` | Spielordner (Welten, Optionen, `config\velora\` mit Velora-Profilen, `mods\`) |
| `versions\`, `libraries\`, `assets\` | Minecraft- und Fabric-Dateien (geteilt zwischen Profilen) |
| `runtime\` | Optional heruntergeladene Mojang-Java-Runtime |
| `launcher\` | Launcher-Einstellungen, verschlüsselte Konten |

## Multiplayer mit deinem Microsoft-Account (ohne eigene Azure-App)

Online-Server verlangen einen echten Microsoft-Login. Solange keine eigene, von Mojang freigeschaltete
Azure-App-ID eingetragen ist (siehe [MICROSOFT_LOGIN.md](MICROSOFT_LOGIN.md)), übernimmt das der
**offizielle Minecraft Launcher**:

1. Offiziellen Minecraft Launcher von minecraft.net installieren und mit deinem Account anmelden.
2. Velora Launcher → **Konten** → *Velora im Minecraft Launcher installieren*
   (oder: `VeloraLauncher-0.1.0.jar --install-official`).
3. Im Minecraft Launcher unter *Installationen* das Profil **„Velora 1.21.4“** wählen → *Spielen*.

Technisch ist das derselbe Weg wie der offizielle Fabric-Installer: eine Fabric-Version in
`.minecraft\versions` und ein Launcher-Profil. Velora nutzt dabei seinen eigenen Spielordner
(`%APPDATA%\.velora\instances\Velora 1_21_4`), Mods anderer Clients bleiben unberührt. Vor der ersten
Änderung wird `launcher_profiles.json` als `*.velora-backup` gesichert.

## Variante B: Als Fabric-Mod in einem anderen Launcher

1. Fabric Loader installieren: ≥ 0.20.1 für **26.3** (Java 25) bzw. ≥ 0.16.14 für **1.21.4**.
2. Die passende `velora-client-mc<Version>-0.1.0.jar` und **Fabric API** (0.166.2+26.3 bzw. 0.119.4+1.21.4) in den `mods`-Ordner legen.

## Im Spiel

| Taste | Funktion |
|---|---|
| **Rechts-Shift** | Velora-Menü (Mods, Cosmetics, Profile, Einstellungen) |
| Velora-Menü → **HUD-Editor** | HUD-Elemente verschieben, skalieren, ein-/ausblenden |
| **C** (Standard) | Zoom (Mod „Zoom“ aktivieren) |
| **Links-Alt** (Standard) | Freelook (Mod „Perspective“ aktivieren) |
| `/waypoint add <Name>` | Wegpunkt setzen (Mod „Waypoints“) |

Alle Tasten sind im Velora-Menü änderbar; jeder Mod kann zusätzlich eine eigene Umschalt-Taste bekommen.

## Optionales Backend

```
java -jar velora-backend-0.1.0.jar 8787 data
java -jar velora-backend-0.1.0.jar grant <uuid> founder data
```

Im Spiel: Velora-Menü → Einstellungen → *Backend verwenden* + URL. Im Launcher: Einstellungen → *Backend (optional)*.
