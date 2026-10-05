# Run MoodCanvas in Eclipse (Windows)

These steps are for the project in `D:\Minnu\projects\moodcanvas`. They use a small local database that MoodCanvas creates automatically, so you do **not** need to set up MySQL just to use the app. Your accounts, saved artworks, and mood entries stay on this computer in `%USERPROFILE%\.moodcanvas`.

## Before you start

- Eclipse IDE for Enterprise Java and Web Developers
- Java 17
- Apache Tomcat 10.1
- Internet access in Eclipse the first time, so Maven can download the project's libraries

## One-time Eclipse setup

1. In Eclipse, choose **File → Import… → Maven → Existing Maven Projects**.
2. For **Root Directory**, choose `D:\Minnu\projects\moodcanvas`. Make sure `pom.xml` is checked, then choose **Finish**.
3. In **Project Explorer**, right-click **moodcanvas** and choose **Maven → Update Project…**. Select `moodcanvas`, check **Force Update of Snapshots/Releases**, and choose **OK**. Wait until Eclipse finishes downloading dependencies. The `h2` and `mysql-connector-j` libraries should appear under **Maven Dependencies**.
4. Right-click the project and choose **Properties → Project Facets**. Confirm Java is set to **17** and the Dynamic Web Module is **6.0**. Click **Further configuration available** only if Eclipse asks; the default web content directory is `src/main/webapp`.
5. In the **Servers** view, choose **New → Server → Apache → Tomcat v10.1 Server**. Point it to your Tomcat 10.1 installation and select a Java 17 runtime.
6. Right-click the Tomcat server → **Add and Remove…**. Move **moodcanvas** to **Configured**, then choose **Finish**.
7. In the server settings, leave `MOODCANVAS_DB_URL` unset to use the automatic local database. If you previously added that variable for MySQL, remove it from **Run → Run Configurations… → Apache Tomcat → Environment** for the simple setup.

## Start the app and create your account

1. In the **Servers** view, right-click Tomcat and choose **Clean…** if available, then **Publish**. Right-click it again and choose **Start**.
2. Wait until Eclipse's Console says Tomcat has started.
3. Open **http://localhost:8080/moodcanvas/** in your browser. If your Tomcat uses a different port, replace `8080` with that port.
4. Choose **Get started**, enter your name, email, and a password with at least 8 characters, and create an account.
5. Sign in with the same email and password. On your canvas, write how you feel or select a mood chip, then choose **Find my art**. Save pieces you like to your journal.

The local database is created the first time an account is registered. Do not delete `%USERPROFILE%\.moodcanvas` if you want to keep your accounts and journal. If you prefer MySQL, use the optional setup below instead.

## Optional: use MySQL instead

1. Open MySQL Workbench and run `database\schema.sql` against your local MySQL server.
2. In Eclipse, open **Run → Run Configurations… → Apache Tomcat → your server → Environment**. Add:
   - `MOODCANVAS_DB_URL` = `jdbc:mysql://localhost:3306/moodcanvas_db?useSSL=false&serverTimezone=UTC`
   - `MOODCANVAS_DB_USER` = your MySQL username (often `root`)
   - `MOODCANVAS_DB_PASSWORD` = that MySQL account's password
3. Choose **Apply**, restart Tomcat, and register a new MoodCanvas account.

When `MOODCANVAS_DB_URL` is set, MoodCanvas uses MySQL. When it is not set, MoodCanvas uses its automatic local database.

## Rebuild from PowerShell (optional)

From the project folder, run:

```powershell
Set-Location 'D:\Minnu\projects\moodcanvas'
mvn package
```

In Eclipse, right-click the project → **Maven → Update Project…**, then clean/publish and restart Tomcat to use code changes.

## Art search, more collections, and your own uploads

MoodCanvas searches The Metropolitan Museum of Art and the Art Institute of Chicago public-domain collection. The Chicago collection's public API supplies artwork information and IIIF images; cards identify the museum source. The optional Rijksmuseum search needs a free Rijksmuseum API key in the Tomcat environment variable `RIJKSMUSEUM_API_KEY`. The Met and Art Institute searches need no key. MoodCanvas searches for concrete visual subjects such as moonlit water, flowers, quiet lakes, and lovers, then shows images returned by the museum collections. If those services are unavailable, the app explains that no collection results came back instead of showing unrelated sample art.

Choose **My uploads** from the top navigation to add a JPEG, PNG, GIF, or WebP image up to 5 MB. Add a title and choose the mood it should appear with. Your upload is private to your account; it is not added to the public museum collections. It can appear in your own searches and be removed from the My uploads page. With MySQL, run the updated `database\schema.sql` once to create the new upload table; it uses `CREATE TABLE IF NOT EXISTS` and keeps existing accounts and entries.

## Optional: connect Spotify listening history

You can paste track or artist names into the mood box without connecting an account. To import a Spotify song or playlist link, first configure and connect Spotify below, then paste a link from `open.spotify.com/track/...` or `/playlist/...` into the dashboard and choose **Import music**. Song/artist names are placed in the editable mood box; choose a mood chip if the first mood guess misses. Playlist imports read up to 30 tracks. Spotify requires the signed-in account to own or collaborate on the playlist. To turn on **Connect Spotify**:

1. In the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard), create an app and copy its Client ID and Client Secret.
2. In the app's settings, add this exact Redirect URI: `http://127.0.0.1:8080/moodcanvas/spotify/callback`. If Tomcat uses another port or context path, use that exact value in both Spotify settings and the environment variable below.
3. In Eclipse, open **Run → Run Configurations… → Apache Tomcat → your server → Environment**. Add `MOODCANVAS_SPOTIFY_CLIENT_ID`, `MOODCANVAS_SPOTIFY_CLIENT_SECRET`, and `MOODCANVAS_SPOTIFY_REDIRECT_URI` with your values, then choose **Apply** and restart Tomcat.
4. Open MoodCanvas through **http://127.0.0.1:8080/moodcanvas/**, sign in on that address, and choose **Connect Spotify**. Spotify will ask for permission to read recently played tracks and private playlists. After returning, you can review recent track names or import a track/playlist link, then edit the mood box before searching. If Spotify previously authorized the app, connect again to grant the added playlist permission.

The Spotify connection reads up to 20 recent tracks and requests the `user-read-recently-played` and `playlist-read-private` permissions. Playlist import reads up to 30 track names. Track names and OAuth tokens are kept in the signed-in session, not added to MoodCanvas's mood/journal tables. Spotify developer-mode apps currently require the app owner's Premium account and allow up to five Spotify users; each user must be added to the app allowlist. If Spotify is not configured or available, MoodCanvas still works with typed moods and pasted music names. Keep your Client Secret in Eclipse's server environment; never add it to browser JavaScript or commit it to the project.

## If something goes wrong

- **Sign-in says it cannot reach the database:** choose **Maven → Update Project…**, confirm `h2` appears under Maven Dependencies, then clean/publish and restart Tomcat. Remove `MOODCANVAS_DB_URL` if you want to use the automatic database. If using MySQL, confirm the schema and all three connection variables are correct.
- **A project or JSP still shows `HttpServlet` missing:** check that the server is Tomcat 10.1, the project Dynamic Web Module is 6.0, and Maven dependencies are present. Old Eclipse projects such as MusicAcademy, Hello, or UrbanVogue are separate from MoodCanvas and can have their own errors.
- **The page looks unstyled:** open the app through Tomcat at `http://localhost:8080/moodcanvas/`, not by opening an HTML file from disk. In Eclipse, clean/publish the Tomcat server and refresh the browser.
- **The page will not open:** confirm the Console says Tomcat started and check the server's port under **Servers → Tomcat → Overview**.
- **Search takes a while or only shows sample art:** check that the computer can reach the internet. The Met API needs no key; museum services may be temporarily unavailable.
