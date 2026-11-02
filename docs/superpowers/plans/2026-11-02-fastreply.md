# FastReply Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App Android personale: un tap su un bottone invia un messaggio WhatsApp predefinito al contatto corrente ed esce da WhatsApp.

**Architecture:** Una Activity Compose con due schermate (Messaggi, Contatti) alternate da una variabile di stato. Dati in un file JSON (`org.json`). L'invio apre `wa.me` e un `AccessibilityService` armato per 5 s preme il tasto invio, poi va alla home.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, Gradle 8.14.3 (wrapper), Jetpack Compose (BOM 2024.12.01, Material3), JUnit 4, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-02-fastreply-design.md`

## Global Constraints

- Package / applicationId: `com.mariocostabile.fastreply`.
- `minSdk 26`, `compileSdk 35`, `targetSdk 35`.
- Nessuna dipendenza oltre a Compose/Activity/Material3. Niente Room, ViewModel, Navigation, kotlinx.serialization.
- Nessun permesso nel manifest (niente `READ_CONTACTS`, niente `INTERNET`).
- Package WhatsApp: solo `com.whatsapp`.
- Finestra di armo: 5000 ms.
- Prefisso di default: `39`.
- Testi UI in italiano.
- `org.json` nei test JVM è uno stub: aggiungere `testImplementation("org.json:json:20240303")`.
- Non c'è un Android SDK in locale: i comandi Gradle si verificano nel workflow CI (Task 1). Se `sdkmanager` è installabile in locale, si può usare anche quello.

## Review Focus

1. WhatsApp già aperto su un'altra chat con un testo diverso nel campo → il servizio NON preme invio (test `shouldSend` nel Task 3).
2. Messaggio con `&`, `?`, `#`, emoji, a capo → arriva identico in WhatsApp (test `buildWaUri` nel Task 3).
3. Numero in rubrica con spazi, trattini, parentesi o `0039`, oppure senza cifre → numero normalizzato, oppure `null` e contatto rifiutato (test nel Task 1).
4. `data.json` mancante o corrotto → l'app parte vuota senza crash. Il salvataggio passa da un file temporaneo e poi da un rename, così uno scritto a metà non distrugge i dati (test nel Task 2).
5. Cancellato il contatto corrente → `current()` ripiega sul primo contatto, oppure `null` e si apre il selettore della rubrica (test nel Task 2).

---

### Task 1: Scaffold progetto + CI + normalizzazione numero

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*` (generati con `gradle wrapper --gradle-version 8.14.3`), `.gitignore`
- Create: `app/build.gradle.kts`, `app/debug.keystore`, `app/src/main/AndroidManifest.xml` (solo `<application>` vuota con label "FastReply")
- Create: `app/src/main/java/com/mariocostabile/fastreply/Phone.kt`
- Create: `.github/workflows/build.yml`
- Test: `app/src/test/java/com/mariocostabile/fastreply/PhoneTest.kt`

**Interfaces:**
- Produces: `fun normalizePhone(raw: String, defaultPrefix: String = "39"): String?` — solo cifre, senza `+`; `null` se non resta nessuna cifra.

- [ ] **Step 1: Scaffold Gradle.** In `app/build.gradle.kts`: plugin `com.android.application`, `org.jetbrains.kotlin.android` e `org.jetbrains.kotlin.plugin.compose`, `buildFeatures.compose = true`, e `signingConfigs.getByName("debug") { storeFile = file("debug.keystore") }`. Genera la chiave con `keytool -genkeypair -keystore app/debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -validity 10000 -dname "CN=Android Debug"`.
- [ ] **Step 2: Write the failing test**

```kotlin
class PhoneTest {
    @Test fun plusPrefix() = assertEquals("393331234567", normalizePhone("+39 333 123 4567"))
    @Test fun doubleZero() = assertEquals("393331234567", normalizePhone("0039-333-1234567"))
    @Test fun noPrefix() = assertEquals("393331234567", normalizePhone("(333) 123.4567"))
    @Test fun foreign() = assertEquals("441234567890", normalizePhone("+44 1234 567890"))
    @Test fun empty() = assertNull(normalizePhone("abc"))
}
```

- [ ] **Step 3: Implement `normalizePhone`.** `+` iniziale → si tolgono `+` e i non-cifre; `00` iniziale → si toglie `00`; altrimenti si antepone `defaultPrefix`. Aggiungi il commento `// shortcut: prefisso di default fisso a 39, renderlo configurabile se servono contatti esteri senza prefisso`.
- [ ] **Step 4: Workflow CI** `.github/workflows/build.yml`: `on: [push, workflow_dispatch]`; `ubuntu-latest`; `actions/checkout@v4`, `actions/setup-java@v4` (temurin 17), `gradle/actions/setup-gradle@v4`; `./gradlew testDebugUnitTest assembleDebug`; `actions/upload-artifact@v4` con name `app-debug` e path `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 5: Push e verifica.** Run: `git push`. Expected: run verde su GitHub, 5 test PASS, artifact `app-debug` presente.
- [ ] **Step 6: Commit** — `feat: scaffold project, CI APK build, phone normalization`

### Task 2: Store JSON

**Files:**
- Create: `app/src/main/java/com/mariocostabile/fastreply/Store.kt`
- Test: `app/src/test/java/com/mariocostabile/fastreply/StoreTest.kt`

**Interfaces:**
- Consumes: niente.
- Produces:
  - `data class Contact(val id: String, val name: String, val phone: String, val messages: List<String>)`
  - `data class AppData(val lastContactId: String? = null, val contacts: List<Contact> = emptyList())` con `fun current(): Contact?` (contatto con `lastContactId`, altrimenti il primo, altrimenti `null`)
  - `class Store(private val file: File)` con `fun load(): AppData` e `fun save(data: AppData)`
  - Formato del JSON: esattamente quello della spec, sezione "Dati".

- [ ] **Step 1: Write the failing tests** (con `@get:Rule val tmp = TemporaryFolder()`):
  - `roundTrip`: salva un AppData con 2 contatti, messaggi con `"` ed emoji e `lastContactId="2"`, poi `load()` == originale.
  - `missingFile`: `load()` su un file inesistente == `AppData()`.
  - `corruptFile`: file con contenuto `"{not json"`, poi `load()` == `AppData()`.
  - `currentFallback`: `AppData("x", listOf(c1)).current() == c1`; `AppData().current() == null`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*StoreTest'` → FAIL (classi assenti).
- [ ] **Step 3: Implement.** `load()` intercetta le eccezioni e restituisce `AppData()`. `save()` scrive su `File(file.path + ".tmp")` e poi `renameTo(file)`. L'id del contatto è `System.currentTimeMillis().toString()` (lo assegna chi chiama).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat: JSON store for contacts and messages`

### Task 3: Invio — URI, armo, decisione

**Files:**
- Create: `app/src/main/java/com/mariocostabile/fastreply/Sender.kt`
- Test: `app/src/test/java/com/mariocostabile/fastreply/SenderTest.kt`

**Interfaces:**
- Produces:
  - `data class PendingSend(val phone: String, val text: String, val at: Long)`
  - `object Pending { @Volatile var value: PendingSend? = null }` (stato condiviso tra Activity e servizio, nello stesso processo)
  - `fun buildWaUri(phone: String, text: String): String` → `"https://wa.me/$phone?text=" + URLEncoder.encode(text, "UTF-8").replace("+", "%20")`
  - `fun shouldSend(p: PendingSend?, entryText: String?, now: Long): Boolean` → `p != null && now - p.at <= 5000 && entryText == p.text`
  - `fun send(context: Context, phone: String, text: String): Boolean` → arma `Pending`, apre `Intent(ACTION_VIEW, Uri.parse(buildWaUri(..)))` con `setPackage("com.whatsapp")` e `FLAG_ACTIVITY_NEW_TASK`. Restituisce `false` su `ActivityNotFoundException` e in quel caso disarma.

- [ ] **Step 1: Write the failing tests**
  - `uriEncodesSpecials`: `buildWaUri("39333", "a&b? #1\nok 😀")` == `"https://wa.me/39333?text=a%26b%3F%20%231%0Aok%20%F0%9F%98%80"`.
  - `sendsWhenMatching`: `shouldSend(PendingSend("1","Tutto ok",1000), "Tutto ok", 5999)` == true.
  - `noSendAfterWindow`: stessi dati con `now=6001` → false.
  - `noSendOnOtherDraft`: `entryText="ciao"` → false.
  - `noSendWhenNotArmed`: `p=null` → false.
- [ ] **Step 2: Run** `--tests '*SenderTest'` → FAIL.
- [ ] **Step 3: Implement** con le firme indicate sopra.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat: WhatsApp send URI, arming and send decision`

### Task 4: AccessibilityService

**Files:**
- Create: `app/src/main/java/com/mariocostabile/fastreply/SendService.kt`
- Create: `app/src/main/res/xml/accessibility_config.xml`, `app/src/main/res/values/strings.xml` (`accessibility_description`: "Preme invio su WhatsApp solo per i messaggi partiti da FastReply.")
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `Pending`, `shouldSend` (Task 3).
- Produces: `fun isServiceEnabled(context: Context): Boolean` in `SendService.kt` (legge `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` e cerca il ComponentName del servizio).

- [ ] **Step 1: Config XML:** `packageNames="com.whatsapp"`, `accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"`, `canRetrieveWindowContent="true"`, `accessibilityFeedbackType="feedbackGeneric"`, `notificationTimeout="100"`, `description="@string/accessibility_description"`.
- [ ] **Step 2: Manifest:** `<service android:name=".SendService" android:exported="false" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">` con intent-filter `android.accessibilityservice.AccessibilityService` e meta-data `android.accessibilityservice` → `@xml/accessibility_config`.
- [ ] **Step 3: `onAccessibilityEvent`:** `val p = Pending.value ?: return`. Se `now - p.at > 5000`, disarma e return. Prendi `rootInActiveWindow`. Leggi il testo con `findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry")`. Se `shouldSend(...)` è vero, trova il tasto con l'id `com.whatsapp:id/send`. In alternativa cercalo per content description `"Invia"` o `"Send"` (scorrendo l'albero dei nodi). Poi `Pending.value = null`, `ACTION_CLICK`, `performGlobalAction(GLOBAL_ACTION_HOME)`. Disarma **prima** del click, così due eventi ravvicinati non inviano due volte.
- [ ] **Step 4: Verify** `./gradlew assembleDebug` in CI → verde.
- [ ] **Step 5: Commit** — `feat: accessibility service that taps WhatsApp send`

### Task 5: UI

**Files:**
- Create: `app/src/main/java/com/mariocostabile/fastreply/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml` (activity launcher `exported="true"`)

**Interfaces:**
- Consumes: `Store`, `AppData`, `Contact` (Task 2); `normalizePhone` (Task 1); `send` (Task 3); `isServiceEnabled` (Task 4).

- [ ] **Step 1: Stato.** `Store(File(filesDir, "data.json"))`, `var data by mutableStateOf(store.load())`. Ogni modifica passa da `update(new: AppData)`, che fa `data = new; store.save(new)`. `var screen by mutableStateOf("messages")`. Banner del servizio calcolato in `onResume`.
- [ ] **Step 2: Selettore della rubrica:** `rememberLauncherForActivityResult(StartActivityForResult)` con `Intent(ACTION_PICK, Phone.CONTENT_URI)`. Il risultato si legge con una query su `DISPLAY_NAME` e `NUMBER`. `normalizePhone == null` → Toast "Numero non valido". Telefono già presente → il contatto diventa corrente, senza duplicarlo. Altrimenti si aggiunge e diventa corrente. Se `data.current() == null` all'avvio, il selettore si apre subito.
- [ ] **Step 3: Schermata Messaggi:** il nome in alto apre `screen="contacts"`. Banner "Attiva invio automatico" se il servizio è spento, con il testo sulle impostazioni con limitazioni di Android 13+ (spec). Il tap sul banner apre `Settings.ACTION_ACCESSIBILITY_SETTINGS`. Sotto, `LazyVerticalGrid(GridCells.Adaptive(110.dp))` di bottoni quadrati (`aspectRatio(1f)`) più "+". Tap sul bottone → si salva `lastContactId`. Se `send()` restituisce false → Toast "WhatsApp non installato"; altrimenti `finish()`. Pressione lunga → AlertDialog "Elimina?". "+" → AlertDialog con un TextField e Salva (testo vuoto ignorato).
- [ ] **Step 4: Schermata Contatti:** lista; tap → `lastContactId = id`, `screen="messages"`. Pressione lunga → "Elimina?". Bottone "Aggiungi" → selettore. Il tasto indietro di sistema (`BackHandler`) riporta a Messaggi.
- [ ] **Step 5: Verify** CI verde, poi scarica l'artifact `app-debug`.
- [ ] **Step 6: Commit** — `feat: messages and contacts UI`

### Task 6: Prova su telefono (manuale, utente)

- [ ] Installa la APK. Su Android 13+ sblocca prima le impostazioni con limitazioni, poi attiva il servizio.
- [ ] Aggiungi un contatto e un messaggio, poi tappalo. Expected: il messaggio parte e appare la home.
- [ ] Servizio spento → resti nella chat col testo pronto.
- [ ] Apri WhatsApp a mano e scrivi in una chat: il servizio non deve fare nulla.
- [ ] Se l'invio automatico non funziona: annota gli id reali dei campi con "Layout Inspector" o con un dump del servizio, e correggi `com.whatsapp:id/entry` / `send`.
