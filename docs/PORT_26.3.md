# Port auf Minecraft 26.3 – Notizen

**Status:** ✅ abgeschlossen. `client-26.3` baut, das Mixin-Audit läuft fehlerfrei, und der In-Game-Selbsttest
(`gradlew :client-26.3:runSelftest`) ist bestanden. Alle 38 Mods sind portiert.

## Vorgehen (auch für weitere Versionen)
1. Build: Gradle 9.8.2 + Loom 1.19.4. Gradle läuft auf **JDK 25** (Loom-Voraussetzung); die Module kompilieren per
   Toolchain mit Java 21 (1.21.4, Launcher, Backend, Core) bzw. Java 25 (26.3).
   Obfuskierte Versionen nutzen `net.fabricmc.fabric-loom-remap`, 26.x nutzt `net.fabricmc.fabric-loom`.
2. Quellcode des 1.21.4-Clients per `migrateMappings --mappings "net.minecraft:mappings:1.21.4" --output …`
   automatisch von Yarn auf Mojang-Namen übertragen (Methodennamen in Mixins werden mit übersetzt, Ziel-Deskriptoren nicht).
3. Gegen die mit `genSources` dekompilierten 26.3-Quellen die API-Unterschiede angepasst (Tabelle unten).
4. Selbsttest + Screenshots, danach End-to-End über den Launcher.

## Wichtigste API-Änderungen 1.21.4 → 26.3
| Bereich | 26.3 |
|---|---|
| GUI-Zeichnen | `GuiGraphics` → `GuiGraphicsExtractor` (Render-State-Modell), `drawString`→`text`, `renderItem`→`item`, 2D-Matrix `Matrix3x2fStack` |
| HUD | Klasse `Hud` (statt Gui/InGameHud); Fabric: `HudElementRegistry.addLast` statt `HudRenderCallback` |
| Screens | `render`→`extractRenderState`, `renderBackground`→`extractBackground`, `extractPanorama`; Eingabe über `MouseButtonEvent`/`KeyEvent`/`CharacterEvent`; `isPauseScreen`, `onFilesDrop` |
| Screen-Verwaltung | `minecraft.gui.setScreen()/screen()/overlay()`; HUD verstecken: `minecraft.gui.hud.isHidden()` |
| Eingabe | **SDL3 statt GLFW**: Tasten = `InputConstants.KEY_*` (C=6, Rechts-Shift=229), Maus 1=links, 2=Mitte, 3=rechts; `KeyboardHandler.keyPress(long,int,KeyEvent)`, `MouseHandler.onButton(long,MouseButtonInfo,int)`, `turnPlayer` → `LocalPlayer.turn(DD)` |
| Zoom / Freelook | `Camera.calculateFov(float)`; `Camera.alignWithEntity` → `setRotation` (ordinal 0/1) |
| Fullbright | `LightmapRenderStateExtractor.extract` (erstes `Double.floatValue()` = Gamma) |
| Time Changer | Tageszeit über Environment Attributes/Timelines → `ClientClockManager.ClientClockInstance.totalTicks()` |
| Block Overlay | `LevelRenderEvents.BEFORE_BLOCK_OUTLINE` + `SubmitNodeCollector.submitShapeOutline/submitCustomGeometry` |
| Capes | `net.minecraft.world.entity.player.PlayerSkin` mit `ClientAsset.ResourceTexture` |
| Sonstiges | `ResourceLocation`→`Identifier`, `Util`→`net.minecraft.util.Util`, `Blaze3D.openPath`, `ClientCommands` (Fabric), Chat: privates `addMessage(Component, MessageSignature, GuiMessageSource, GuiMessageTag)` |

Tastencodes sind plattformspezifisch: `ClientSettings.platformDefaultMenuKey` wird von jedem Client-Modul vor dem
Start des Cores gesetzt. Profile, die zwischen 1.21.4 und 26.3 exportiert/importiert werden, behalten deshalb ihre
Tastenbelegungen nicht 1:1 (Einstellungen und HUD-Layout schon).
