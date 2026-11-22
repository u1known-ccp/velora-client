# Microsoft-Login mit eigener Azure-App einrichten

Der Velora Launcher meldet Spieler ausschließlich über das **offizielle Microsoft-OAuth-Verfahren**
(Device Code Flow) an:

```
Microsoft (login.microsoftonline.com, Gerätecode im Browser)
   → Xbox Live (user.auth.xboxlive.com)
   → XSTS (xsts.auth.xboxlive.com, RelyingParty rp://api.minecraftservices.com/)
   → Minecraft Services (login_with_xbox)
   → Besitzprüfung (entitlements/mcstore: product_minecraft / game_minecraft)
   → Profil (minecraft/profile)
```

* Der Launcher sieht **niemals** ein Passwort – die Anmeldung passiert auf microsoft.com im Browser.
* Tokens werden mit **Windows DPAPI** verschlüsselt gespeichert und nur an Microsoft, Xbox Live und Mojang gesendet.
* Gespielt werden kann nur mit einem Account, der **Minecraft: Java Edition besitzt** (Kauf oder Game Pass).

## Mojangs Anforderungen (Stand 22.11.2026, aus dem Freigabeformular)

Quelle: Hilfeartikel <https://help.minecraft.net/hc/en-us/articles/16254801392141> (verlinkt über
`aka.ms/AppRegInfo`) und das Formular <https://aka.ms/mce-reviewappid>:

* Neue Apps werden **manuell geprüft** und in eine Allowlist aufgenommen. Anträge werden **wöchentlich**
  bearbeitet; mehrfaches Einreichen beschleunigt nichts.
* Apps müssen den **Spielbesitz prüfen und eine Anmeldung verlangen**, bevor Gameplay oder Server-Zugang möglich ist.
  Apps, die Authentifizierung, Besitz- oder Lizenzprüfungen umgehen, werden **nicht** freigegeben – unabhängig von
  der Serverkonfiguration.
  → Deshalb gibt es im Velora Launcher **keine Offline-Konten** mehr (nur noch im Entwicklermodus
  `-Dvelora.dev=true` für automatische Tests).
* Der App-Name darf **nicht** „Mojang“, „Minecraft“, „Microsoft“, „Live“, „Xbox“, „Discord“ oder „Hypixel“ enthalten
  („Velora Launcher“ ist in Ordnung).
* Vor dem Antrag müssen EULA und Usage Guidelines (<http://aka.ms/mcusageguidelines>) gelesen werden.
* Die **Kontakt-E-Mail** im Formular wird mit den Kontaktdaten im Azure-Portal abgeglichen – nimm die E-Mail
  deines Azure-/Microsoft-Kontos.
* Pro Formular genau **eine** App-ID.

## Schritt 1 – App in Azure registrieren

1. <https://entra.microsoft.com> öffnen und mit deinem Microsoft-Konto anmelden
   (ein kostenloses Konto genügt; falls nötig wird ein Standardverzeichnis angelegt).
2. **Entra ID → App-Registrierungen → Neue Registrierung**
   * Name: `Velora Launcher`
   * Unterstützte Kontotypen: **Nur persönliche Microsoft-Konten** („Personal accounts only“)
   * Umleitungs-URI: leer lassen → **Registrieren**
3. In der App → **Authentifizierung** → Einstellung **„Öffentliche Clientflows zulassen“ = Ja** → Speichern
   (ohne das funktioniert der Gerätecode-Login nicht).
4. Auf der **Übersicht** die **Anwendungs-ID (Client-ID)** kopieren (Format `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`).

## Schritt 2 – Im Velora Launcher eintragen und prüfen

1. Velora Launcher → **Einstellungen → Microsoft-Login** → Client-ID einfügen.
2. **„Client-ID prüfen“** klicken. Der Launcher fragt bei Microsoft einen Test-Gerätecode an und meldet:
   * ✔ Azure-Konfiguration in Ordnung, oder
   * ✖ eine konkrete Ursache (ID unbekannt, falscher Kontotyp, öffentliche Clientflows aus).

## Schritt 3 – Freigabe bei Mojang beantragen

1. Im Launcher **„Mojang-Freigabe beantragen“** klicken (öffnet <https://aka.ms/mce-reviewappid>).
2. Ausfüllen: EULA bestätigt = Yes · Kontakt-E-Mail (wie im Azure-Portal) · Request type: **New AppID for Approval** ·
   Application Name: `Velora Launcher` · Application ID: deine Client-ID · (ggf. weitere Fragen zur App beantworten).
3. Auf die Freigabe warten (wöchentliche Prüfung).

## Schritt 4 – Anmelden

**Konten → Microsoft-Konto hinzufügen** → Code im Browser eingeben → fertig. Danach funktionieren alle
Online-Server mit deinem echten Account.

Solange Mojang die ID noch nicht freigeschaltet hat, bricht der Login beim Schritt `login_with_xbox` ab und der
Launcher sagt das ausdrücklich. Bis dahin: **Konten → „Velora im Minecraft Launcher installieren“** – dann
übernimmt der offizielle Minecraft Launcher die Anmeldung (siehe [INSTALL.md](INSTALL.md)).

## Client-ID für alle Nutzer fest einbauen

Optional in `launcher/src/main/resources/velora/launcher.properties` → `microsoft_client_id=<ID>` eintragen
und neu bauen (`gradlew.bat release`). Nutzer können sie in den Einstellungen weiterhin überschreiben.
