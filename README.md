# FirstCry Quick Cart — Android native project

AMOLED-black, iOS-style native Android Java app (large titles, rounded grouped cards, floating bottom tab island). No app lock.

## Build APK on GitHub (no Android Studio required)
1. Create a **private** repository on GitHub.
2. Extract this ZIP on your Windows PC.
3. Upload the contents of this extracted folder to the repository root. Include the hidden `.github/workflows/build-apk.yml` file/folder.
4. Open the repository's **Actions** tab and select **Build FirstCry Quick Cart APK**.
5. Click **Run workflow**. Wait for the run to finish successfully.
6. Open the completed run and download the **FirstCry-Quick-Cart-APK** artifact. Extract it; it contains `app-debug.apk`.

The workflow builds with Java 17, Gradle 8.7 and Android SDK 35. It runs on pushes to `main` and can also be started manually.

## What it does
- Store multiple named FirstCry account cURL requests locally.
- Add, edit and delete account profiles.
- Select an account, paste multiple product IDs and process them sequentially.
- For each product, call `SaveProductCart`, then `SaveCartDetail`.
- **Bookmarks:** use the floating bottom island (Cart | Bookmarks) to switch pages. On Bookmarks, add product IDs (one per line, comma- or space-separated), remove them with ✕, tick any and press **Add Selected to Cart** to send them to the Cart page's Product IDs box. Bookmarks are stored locally on the device.

## Important API limitation to test
This is a first native prototype based on the two FirstCry request payloads supplied in the conversation. A browser extension automatically uses the browser's cookie/session context. A native app does not inherit Chrome/Brave/Edge cookies. The app therefore sends the saved `ftk` and the relevant cURL headers (Referer, User-Agent, Cookie if present). If FirstCry requires additional browser-bound cookies or anti-bot/session state, a direct native request may be rejected; the app will show the returned error and we will need to add an account login/session-capture flow.

The user-provided cURL did not show a Cookie header, but that does not prove no browser cookie is needed. Test with one account and one product first.

## Storage and security
Account cURLs are stored in app-private SharedPreferences without encryption, as requested (no PIN/fingerprint). They are not uploaded to a server. The `ftk` value is a sensitive session token; do not share the cURL or screenshots containing it. Clear app data or delete an account to remove locally stored credentials.

## Status
This is source code, not a prebuilt APK. The project and workflow have not yet been successfully compiled or tested. A successful GitHub build is the first compilation check; even then, FirstCry API behavior must be tested with one product before bulk use.
