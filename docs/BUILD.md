# Build dell'APK

L'APK si builda su **GitHub Actions** a ogni `git push` (o a mano: *Actions → Build APK → Run workflow*).
Il risultato è un APK **release firmato**, scaricabile da *Actions → (run) → Artifacts → FastReply*.

## Setup una tantum: chiave di firma

Android installa un aggiornamento solo se è firmato con **la stessa chiave** della versione installata.
La chiave vive solo nei Secrets di GitHub, mai nel repo. Senza secret la build fallisce di proposito
(un APK non firmato non è installabile).

1. Genera la chiave (serve un JDK, `keytool` è incluso). **Usa la stessa password per entrambe le domande.**
   ```bash
   keytool -genkeypair -v -storetype PKCS12 -keystore fastreply.p12 \
     -alias fastreply -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=FastReply"
   ```
   L'alias deve essere esattamente `fastreply` (lo legge `app/build.gradle.kts`).

2. Converti in base64:
   - Linux: `base64 -w0 fastreply.p12 > fastreply.b64`
   - macOS: `base64 -i fastreply.p12 -o fastreply.b64`
   - Windows (PowerShell): `[Convert]::ToBase64String([IO.File]::ReadAllBytes("fastreply.p12")) > fastreply.b64`

3. Su GitHub: *repo → Settings → Secrets and variables → Actions → New repository secret*
   - `KEYSTORE_BASE64` = contenuto di `fastreply.b64`
   - `KEYSTORE_PASSWORD` = la password scelta al punto 1

4. **Fai un backup di `fastreply.p12` e della password** (password manager). Se li perdi non potrai
   più aggiornare l'app installata: dovrai disinstallarla e perdere i dati. Poi cancella `fastreply.b64`.

5. Rilancia la build (*Actions → Build APK → Run workflow*, oppure un nuovo push).

## Installazione

Scarica l'artifact `FastReply` (è uno zip), estrai `app-release.apk`, aprilo sul telefono.
La prima volta che passi dalla vecchia build debug a quella release la firma cambia:
**disinstalla prima la vecchia app** (i contatti e i messaggi salvati vanno persi una sola volta).

## Build locale (opzionale)

Serve Android Studio (o JDK 17 + Android SDK).
```bash
./gradlew testDebugUnitTest assembleDebug    # APK debug, firmato con la chiave debug locale
# oppure release firmato con la tua chiave:
KEYSTORE_PATH=/percorso/fastreply.p12 KEYSTORE_PASSWORD=... ./gradlew assembleRelease
```
