# Backend Integration Guide — Part 1: Foundations & Firebase Setup

> **What this guide covers**: A complete, beginner-friendly, line-by-line explanation of how we connected a Node.js backend server with a MySQL database to our Android frontend app. If you have only ever built a frontend Android app with hardcoded/mock data, this guide will teach you every single concept needed to add real backend + database support to your own app.

---

## Table of Contents (Part 1)
1. [The Big Picture — What is "Backend" and Why?](#1-the-big-picture)
2. [Key Vocabulary — Every Term Explained](#2-key-vocabulary)
3. [Step 1: Setting Up Firebase for Google Authentication](#3-firebase-setup)
4. [Step 2: The `google-services.json` File (Auto-Generated)](#4-google-services-json)
5. [Step 3: Android Gradle Changes — Adding All Dependencies](#5-gradle-changes)
6. [Step 4: AndroidManifest.xml — Permissions for Internet](#6-android-manifest)

---

## 1. The Big Picture — What is "Backend" and Why? <a name="1-the-big-picture"></a>

### What your app looks like WITHOUT a backend (frontend-only):

```
┌─────────────────────┐
│   Android App       │
│                     │
│  - Hardcoded text   │
│  - Fake user names  │
│  - No real login    │
│  - Data disappears  │
│    when app closes  │
└─────────────────────┘
```

In a frontend-only app, everything is **hardcoded**. For example, your home screen might show:
```java
// HARDCODED — this data is fake, typed by the developer
tvUsername.setText("John Doe");
tvPostCount.setText("42");
tvFollowers.setText("128");
```

### What your app looks like WITH a backend:

```
┌─────────────────┐       HTTP Requests        ┌──────────────────┐        SQL Queries      ┌───────────────┐
│   Android App   │  ───────────────────────►   │  Node.js Server  │  ────────────────────►  │  MySQL DB     │
│   (Frontend)    │  ◄───────────────────────   │  (Backend/API)   │  ◄────────────────────  │  (Database)   │
│                 │       JSON Responses        │                  │        Row Data         │               │
└─────────────────┘                             └──────────────────┘                         └───────────────┘
         │                                              │
         │                                              │
    Google Sign-In                                 Firebase Admin
    (Firebase Auth)                                (Verify tokens)
         │                                              │
         └──────────────► Firebase Cloud ◄──────────────┘
```

Now, the same screen shows **real data from the database**:
```java
// REAL DATA — fetched from the server, which reads from MySQL
apiService.getUserStats(token, userId).enqueue(new Callback<UserStatsResponse>() {
    @Override
    public void onResponse(...) {
        tvPostCount.setText(String.valueOf(response.body().stats.postsCount));   // real number from DB
        tvFollowers.setText(String.valueOf(response.body().stats.followersCount)); // real number from DB
    }
});
```

### The Three Pieces You Need:

| Piece | Technology | Purpose |
|-------|-----------|---------|
| **Frontend** | Android (Java/XML) | What the user sees and taps |
| **Backend** | Node.js + Express | Receives requests, processes logic, talks to database |
| **Database** | MySQL | Stores all data permanently (users, posts, messages, etc.) |

---

## 2. Key Vocabulary — Every Term Explained <a name="2-key-vocabulary"></a>

### What is "Node.js"?
**Node.js** is a program that lets you run JavaScript code *outside* of a web browser. Normally, JavaScript only runs inside Chrome/Firefox/etc. Node.js takes Google Chrome's V8 JavaScript engine and makes it work as a standalone program on your computer. We use it to build our server because:
- It's fast and lightweight
- JavaScript is easy to learn
- Huge ecosystem of ready-made packages (libraries)

### What is "npm"?
**npm** = **Node Package Manager**. It's the tool that comes with Node.js that lets you install third-party libraries (called "packages"). When you run `npm install express`, it downloads the Express library and puts it in a folder called `node_modules/`.

### What is a "REST API"?
**REST** = **RE**presentational **S**tate **T**ransfer. It's a set of rules for how the frontend and backend talk to each other over HTTP.

**API** = **A**pplication **P**rogramming **I**nterface. It's a contract: "If you send me THIS request, I'll send you back THIS response."

Think of it like a restaurant:
- **You (Android app)** = the customer
- **The waiter (HTTP request)** = carries your order to the kitchen
- **The kitchen (Node.js server)** = prepares the food (processes the request)
- **The food (JSON response)** = what comes back to you

### HTTP Methods (Verbs):

| Method | Meaning | Example |
|--------|---------|---------|
| `GET` | "Give me data" (read-only) | Get the list of posts |
| `POST` | "Create something new" | Create a new post, send a message |
| `PATCH` | "Update part of something" | Update your profile bio |
| `PUT` | "Replace something entirely" | Replace entire user record |
| `DELETE` | "Remove something" | Unlike a post, unfollow a user |

### What is "JSON"?
**JSON** = **J**ava**S**cript **O**bject **N**otation. It's a text format for sending data. Both Android and Node.js can read/write JSON.

```json
{
  "user": {
    "id": 1,
    "name": "Ayush",
    "email": "ayush@example.com"
  }
}
```

### What is "Express"?
**Express** is a Node.js library/framework that makes it easy to build a web server. Without Express, you'd have to write hundreds of lines of low-level HTTP code. Express gives you simple functions like `app.get()`, `app.post()`, etc.

### What is "Retrofit"?
**Retrofit** is an Android library that makes it easy to call REST APIs from Java. Without Retrofit, you'd have to manually create HTTP connections, read streams, parse JSON — all very tedious. Retrofit lets you define API calls as simple Java interface methods.

### What is "JWT"?
**JWT** = **J**SON **W**eb **T**oken. It's a secure string that proves "this user is logged in." When you log in, the server creates a JWT and sends it to the app. The app then includes this JWT in every future request so the server knows who you are.

### What is "ORM" (Sequelize)?
**ORM** = **O**bject-**R**elational **M**apping. **Sequelize** is a Node.js ORM that lets you interact with MySQL using JavaScript objects instead of writing raw SQL queries.

Instead of: `SELECT * FROM users WHERE id = 5`
You write: `User.findByPk(5)`

### What is "Middleware"?
In Express, **middleware** is a function that runs BEFORE your actual route handler. It can check things (like "is this user logged in?"), modify the request, or reject it. Think of it as a security guard at a door.

### What is "CORS"?
**CORS** = **C**ross-**O**rigin **R**esource **S**haring. It's a browser/network security rule that controls which apps can talk to your server. We configure it so our Android app is allowed to connect.

### What is "Firebase"?
**Firebase** is Google's platform that provides many services. We use **Firebase Authentication** specifically — it handles the complex parts of Google Sign-In (OAuth tokens, security, etc.) so we don't have to build a login system from scratch.

### What is a ".env" file?
A `.env` (environment) file stores secret configuration values like database passwords, API keys, etc. These should NEVER be committed to git or shared publicly.

---

## 3. Step 1: Setting Up Firebase for Google Authentication <a name="3-firebase-setup"></a>

### Why Firebase?
When a user taps "Sign in with Google" on Android, Google's servers handle the authentication and give you a **token** (a long encrypted string) that proves "this person is who they claim to be." Firebase acts as the middleman that verifies these tokens.

### Step-by-step Firebase Console Setup:

#### 3.1 Create a Firebase Project
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Add Project"
3. Name it (e.g., "chesh-a1ff5" — this is our project ID)
4. Disable Google Analytics if you don't need it
5. Click "Create Project"

#### 3.2 Add an Android App to Firebase
1. In Firebase Console, click "Add app" → Android icon
2. Enter your **package name**: `com.example.chesh`
   - This MUST match `applicationId` in your `app/build.gradle.kts`
3. Enter a nickname (optional)
4. Enter your **SHA-1 certificate fingerprint**:
   - Run this in your terminal:
     ```
     cd C:\Users\YourName\.android
     keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
   - Copy the SHA-1 value (e.g., `c1:46:5c:f6:4b:dc:ba:f8:2c:24:5a:69:d5:90:dc:70:c4:51:da:30`)
   - This SHA-1 is critical — Google uses it to verify that requests come from YOUR app
5. Download `google-services.json` — this file goes in `app/` folder

#### 3.3 Enable Google Sign-In Provider
1. In Firebase Console → Authentication → Sign-in method
2. Click "Google" → Enable it
3. Set a project support email
4. Note the **Web client ID** — you'll need this in your Android app
   - In our case: `750649718921-5utcek3vkl10inrnubgupshoeu1tjuh9.apps.googleusercontent.com`

#### 3.4 Get the Firebase Service Account Key (for the server)
1. Firebase Console → Project Settings → Service accounts
2. Click "Generate new private key"
3. Save the JSON file as `firebase-service-account.json` in your `server/` folder
4. This file lets your Node.js server verify Firebase tokens

---

## 4. Step 2: The `google-services.json` File <a name="4-google-services-json"></a>

This file is **auto-generated by Firebase** and placed at `app/google-services.json`. Let's understand every field:

```json
{
  "project_info": {
    "project_number": "750649718921",       // Unique number Firebase assigns to your project
    "project_id": "chesh-a1ff5",            // The human-readable project ID you chose
    "storage_bucket": "chesh-a1ff5.firebasestorage.app"  // For file storage (not used here)
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:750649718921:android:77fe67af57f9ddebeb2e58",  // Unique ID for THIS Android app
        "android_client_info": {
          "package_name": "com.example.chesh"   // Must match your app's applicationId
        }
      },
      "oauth_client": [
        {
          "client_id": "750649718921-m0u1154u30s0ndf7eme90u890ko3tq0s.apps.googleusercontent.com",
          "client_type": 1,                     // type 1 = Android client
          "android_info": {
            "package_name": "com.example.chesh",
            "certificate_hash": "c1465cf64bdcbaf82c245a69d590dc70c451da30"  // Your SHA-1 fingerprint
          }
        },
        {
          "client_id": "750649718921-5utcek3vkl10inrnubgupshoeu1tjuh9.apps.googleusercontent.com",
          "client_type": 3                      // type 3 = Web client (used for ID token requests)
        }
      ],
      "api_key": [
        {
          "current_key": "AIzaSyB0Mj_Nu8enc0xv2-fs_Owp9LiivrBFW4o"  // API key for Firebase services
        }
      ]
    }
  ]
}
```

**Important**: The `client_type: 3` (Web client) ID is what we use in `strings.xml` as `google_web_client_id`. This is needed because when Android requests an ID token from Google, it needs the *server's* client ID (the web one), not the Android one.

### What the Google Services Gradle Plugin Does
When you add `alias(libs.plugins.google.services)` to your build file, the plugin reads `google-services.json` at build time and automatically generates Android resources (like `R.string.default_web_client_id`) that Firebase libraries use internally. You don't see these files — they're generated during the build process.

---

## 5. Step 3: Android Gradle Changes — Adding All Dependencies <a name="5-gradle-changes"></a>

### 5.1 Root `build.gradle.kts` (Project-level)

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false   // Android build tools — "apply false" means 
                                                           // "register but don't apply here, apply in app/"
    alias(libs.plugins.google.services) apply false        // Google Services plugin for Firebase
}
```

**What `alias(libs.plugins.xxx)` means**: This references the `gradle/libs.versions.toml` file. Instead of writing `id("com.google.gms.google-services") version "4.4.2"`, you define it once in the TOML file and reference it everywhere with `alias()`.

### 5.2 Version Catalog: `gradle/libs.versions.toml`

This file is the **single source of truth** for all dependency versions:

```toml
[versions]
firebaseBom = "34.4.0"        # Firebase Bill of Materials — ensures all Firebase libs are compatible
firebaseAuth = "24.0.1"       # Firebase Authentication library
googleAuth = "21.4.0"         # Google Play Services Auth (for Google Sign-In UI)
retrofit = "2.11.0"           # Retrofit HTTP client for Android
okhttp = "4.12.0"             # OkHttp — the underlying HTTP engine Retrofit uses
gson = "2.11.0"               # Gson — Google's JSON parser for Java
googleServices = "4.4.2"      # Gradle plugin that processes google-services.json

[libraries]
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-auth = { group = "com.google.firebase", name = "firebase-auth", version.ref = "firebaseAuth" }
play-services-auth = { group = "com.google.android.gms", name = "play-services-auth", version.ref = "googleAuth" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-converter-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }

[plugins]
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

### 5.3 App-level `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)    // Apply Android app plugin
    alias(libs.plugins.google.services)        // Apply Google Services (processes google-services.json)
}

dependencies {
    // ── Firebase ──
    implementation(platform(libs.firebase.bom))   // BOM = Bill of Materials. It sets compatible versions
                                                   // for ALL Firebase libraries so you don't have conflicts
    implementation(libs.firebase.auth)             // Firebase Authentication — handles login/signup
    implementation(libs.play.services.auth)        // Google Sign-In UI (the popup where user picks Google account)

    // ── Networking (talking to our backend) ──
    implementation(libs.retrofit)                  // Retrofit — makes HTTP calls easy
    implementation(libs.retrofit.converter.gson)   // Converts JSON responses to Java objects automatically
    implementation(libs.okhttp)                    // OkHttp — the actual HTTP engine under Retrofit
    implementation(libs.okhttp.logging)            // Logs all HTTP requests/responses (for debugging)
    implementation(libs.gson)                      // Gson — Google's JSON↔Java converter

    // ── UI ──
    implementation("com.github.bumptech.glide:glide:4.16.0")      // Glide — loads images from URLs into ImageViews
    implementation("androidx.recyclerview:recyclerview:1.3.2")      // RecyclerView — efficient scrollable lists
    // ... other standard Android libraries (appcompat, material, etc.)
}
```

**Why each networking library is needed:**
- **Retrofit**: Turns your API definition (Java interface) into actual HTTP calls
- **OkHttp**: The engine that actually sends/receives HTTP packets
- **Gson**: Converts JSON text ↔ Java objects (serialization/deserialization)
- **Logging Interceptor**: Prints every request/response in Logcat for debugging

---

## 6. Step 4: AndroidManifest.xml — Permissions <a name="6-android-manifest"></a>

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- ADDED: Without this, Android blocks ALL network calls -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:usesCleartextTraffic="true"
        android:networkSecurityConfig="@xml/network_security_config"
        ...>
```

### Line-by-line:

**`<uses-permission android:name="android.permission.INTERNET" />`**
- By default, Android apps have NO internet access
- This line tells Android: "This app needs to make network calls"
- Without it, every HTTP request silently fails
- This was NOT in the default template — we added it for backend communication

**`android:usesCleartextTraffic="true"`**
- By default (Android 9+), apps can only use HTTPS (encrypted)
- Our local development server runs on HTTP (not HTTPS) at `http://10.0.2.2:8080`
- This flag allows unencrypted HTTP traffic during development
- In production, you'd remove this and use HTTPS

**`android:networkSecurityConfig="@xml/network_security_config"`**
- Points to an XML file that defines fine-grained network security rules
- Specifies which domains are allowed to use cleartext (HTTP) traffic
- More secure than the blanket `usesCleartextTraffic="true"` flag

---

> **Continue to Part 2** → `BACKEND_EXPLANATION_PART2.md` for the Node.js server setup, database configuration, and every server file explained line-by-line.
