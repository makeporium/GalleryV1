# Backend Integration Guide — Part 2: The Node.js Server (Every File Explained)

> This part explains every single file in the `server/` folder — what it does, what each line means, and why it exists.

---

## Table of Contents (Part 2)
1. [Step 5: Installing Node.js & Creating the Server Project](#step-5)
2. [Step 6: `package.json` — The Server's Identity Card](#step-6)
3. [Step 7: `.env` — Secret Configuration](#step-7)
4. [Step 8: `src/config/env.js` — Loading Environment Variables](#step-8)
5. [Step 9: `src/config/database.js` — Connecting to MySQL](#step-9)
6. [Step 10: `src/config/firebase.js` — Firebase Admin SDK](#step-10)
7. [Step 11: `src/index.js` — The Server Entry Point](#step-11)
8. [Step 12: `src/app.js` — Express App Configuration](#step-12)
9. [Step 13: `src/models/user.js` — The User Database Model](#step-13)
10. [Step 14: `src/models/index.js` — All Models & Relationships](#step-14)

---

## Step 5: Installing Node.js & Creating the Server Project <a name="step-5"></a>

### 5.1 Install Node.js
1. Download from [nodejs.org](https://nodejs.org/) (LTS version)
2. Install it — this gives you both `node` and `npm` commands
3. Verify: `node --version` and `npm --version`

### 5.2 Create the server folder structure
```
GalleryV2/
├── app/                    ← Android frontend (already exists)
├── server/                 ← NEW: Backend server
│   ├── .env                ← Secret config (passwords, keys)
│   ├── package.json        ← Project definition + dependencies
│   ├── firebase-service-account.json  ← Firebase admin credentials
│   ├── sql/                ← Database schema files
│   │   ├── 001_init.sql
│   │   └── 002_phase2_full_schema.sql
│   └── src/                ← Source code
│       ├── index.js        ← Entry point (starts the server)
│       ├── app.js          ← Express app setup
│       ├── config/
│       │   ├── env.js      ← Loads .env variables
│       │   ├── database.js ← MySQL connection
│       │   └── firebase.js ← Firebase Admin init
│       ├── middleware/
│       │   └── authMiddleware.js  ← JWT token verification
│       ├── models/
│       │   ├── user.js     ← User model definition
│       │   └── index.js    ← All models + relationships
│       ├── routes/
│       │   ├── authRoutes.js    ← Login/signup endpoints
│       │   ├── userRoutes.js    ← User profile endpoints
│       │   └── phase2Routes.js  ← All feature endpoints
│       ├── services/
│       │   ├── jwtService.js    ← JWT token create/verify
│       │   └── userService.js   ← User create/update logic
│       └── scripts/
│           ├── initDb.js        ← Creates database tables
│           ├── resetDb.js       ← Drops and recreates tables
│           └── seedTestData.js  ← Inserts fake data for testing
```

### 5.3 Initialize the project
```bash
cd server
npm init -y          # Creates package.json with defaults
npm install express cors dotenv mysql2 sequelize firebase-admin jsonwebtoken zod
npm install --save-dev nodemon
```

Each package explained:
| Package | What it does |
|---------|-------------|
| `express` | Web framework — handles HTTP routes |
| `cors` | Allows cross-origin requests (Android → Server) |
| `dotenv` | Loads `.env` file variables into `process.env` |
| `mysql2` | MySQL database driver for Node.js |
| `sequelize` | ORM — interact with MySQL using JavaScript objects |
| `firebase-admin` | Server-side Firebase SDK to verify user tokens |
| `jsonwebtoken` | Creates and verifies JWT tokens |
| `zod` | Request validation — ensures incoming data is correct |
| `nodemon` (dev) | Auto-restarts server when you edit code |

---

## Step 6: `package.json` — The Server's Identity Card <a name="step-6"></a>

```json
{
  "name": "server",
  "version": "1.0.0",
  "main": "src/index.js",        // "main" tells Node: "this is the starting file"
  "scripts": {
    "dev": "nodemon src/index.js",      // npm run dev → starts server with auto-reload
    "start": "node src/index.js",       // npm start → starts server (production)
    "db:reset": "node src/scripts/resetDb.js",    // npm run db:reset → drops & recreates all tables
    "db:init": "node src/scripts/initDb.js",      // npm run db:init → creates tables from SQL files
    "db:seed:phase2": "node src/scripts/seedPhase2.js",     // Inserts initial data
    "db:seed:test": "node src/scripts/seedTestData.js"      // Inserts test/fake data
  },
  "dependencies": {
    "cors": "^2.8.6",
    "dotenv": "^17.4.2",
    "express": "^5.2.1",
    "firebase-admin": "^13.8.0",
    "jsonwebtoken": "^9.0.3",
    "mysql2": "^3.22.2",
    "sequelize": "^6.37.8",
    "zod": "^4.3.6"
  },
  "devDependencies": {
    "nodemon": "^3.1.14"       // Only needed during development, not production
  }
}
```

**What `^` means in versions**: `^2.8.6` means "any version ≥ 2.8.6 but < 3.0.0". It allows minor updates but not major breaking changes.

**What `scripts` do**: Instead of typing `nodemon src/index.js`, you type `npm run dev`. It's a shortcut system.

---

## Step 7: `.env` — Secret Configuration <a name="step-7"></a>

```env
NODE_ENV=development                                    # "development" or "production"
PORT=8080                                               # Which port the server listens on

MYSQL_HOST=127.0.0.1                                    # MySQL server address (127.0.0.1 = your own computer)
MYSQL_PORT=3306                                         # MySQL default port
MYSQL_DATABASE=gallery_v2                               # Name of the database to use
MYSQL_USER=root                                         # MySQL username
MYSQL_PASSWORD=your_mysql_password                      # MySQL password (KEEP SECRET!)

JWT_SECRET=replace_with_long_random_secret              # Secret key for signing JWT tokens
JWT_EXPIRES_IN=1h                                       # Tokens expire after 1 hour

FIREBASE_PROJECT_ID=your_firebase_project_id            # Firebase project ID
FIREBASE_SERVICE_ACCOUNT_PATH=./firebase-service-account.json  # Path to Firebase credentials

CORS_ORIGINS=http://10.0.2.2:8080,http://localhost:8080 # Which origins can connect to this server
```

**Why `.env` exists**: You NEVER put passwords in your code. If you push code to GitHub with passwords, hackers will find them. The `.env` file is listed in `.gitignore` so it never gets uploaded.

**What `10.0.2.2` is**: On the Android Emulator, `10.0.2.2` is a special IP that means "my host computer." The emulator runs in a virtual machine, so `localhost` inside the emulator refers to the emulator itself, not your PC. `10.0.2.2` is the emulator's alias for your PC's `localhost`.

---

## Step 8: `src/config/env.js` — Loading Environment Variables <a name="step-8"></a>

```javascript
const path = require("path");          // "path" is built into Node.js — handles file paths safely
const dotenv = require("dotenv");       // Third-party: reads .env file

// Load the .env file from the server root directory
// path.resolve() creates an absolute path. process.cwd() returns the Current Working Directory.
dotenv.config({ path: path.resolve(process.cwd(), ".env") });

// Helper function: crashes the server if a required variable is missing
// This prevents the server from starting with broken config
function requireEnv(key) {
  const value = process.env[key];       // process.env is a global object with all environment variables
  if (!value) {
    throw new Error(`Missing required environment variable: ${key}`);
  }
  return value;
}

// Export all config as a clean JavaScript object
// Other files import this instead of reading process.env directly
module.exports = {
  nodeEnv: process.env.NODE_ENV || "development",   // || means "use this default if undefined"
  port: Number(process.env.PORT || 8080),            // Number() converts string "8080" to number 8080
  mysqlHost: requireEnv("MYSQL_HOST"),               // REQUIRED — crashes if missing
  mysqlPort: Number(process.env.MYSQL_PORT || 3306),
  mysqlDatabase: requireEnv("MYSQL_DATABASE"),
  mysqlUser: requireEnv("MYSQL_USER"),
  mysqlPassword: requireEnv("MYSQL_PASSWORD"),
  jwtSecret: requireEnv("JWT_SECRET"),
  jwtExpiresIn: process.env.JWT_EXPIRES_IN || "1h",
  corsOrigins: (process.env.CORS_ORIGINS || "http://10.0.2.2:8080,http://localhost:8080")
    .split(",")                 // Split "url1,url2" into ["url1", "url2"]
    .map((origin) => origin.trim())   // Remove whitespace
    .filter(Boolean),           // Remove empty strings
  firebaseProjectId: requireEnv("FIREBASE_PROJECT_ID"),
  firebaseServiceAccountPath: requireEnv("FIREBASE_SERVICE_ACCOUNT_PATH"),
};
```

### What is `require()`?
In Node.js, `require()` is how you import code from other files or installed packages:
- `require("path")` → imports a built-in Node.js module
- `require("dotenv")` → imports an npm package from node_modules/
- `require("./env")` → imports a local file (relative path, .js extension optional)

### What is `module.exports`?
This is how a Node.js file "exports" (shares) its code with other files. Whatever you assign to `module.exports` is what other files get when they `require()` this file.

---

## Step 9: `src/config/database.js` — Connecting to MySQL <a name="step-9"></a>

```javascript
const { Sequelize } = require("sequelize");    // Import the Sequelize class from the sequelize package
// { Sequelize } is "destructuring" — it's like saying: const Sequelize = require("sequelize").Sequelize
const env = require("./env");                   // Import our config from env.js

// Create a new Sequelize connection instance
// Parameters: (database_name, username, password, options)
const sequelize = new Sequelize(env.mysqlDatabase, env.mysqlUser, env.mysqlPassword, {
  host: env.mysqlHost,              // Where MySQL is running (127.0.0.1 = your computer)
  port: env.mysqlPort,              // Port number (3306 = MySQL default)
  dialect: "mysql",                 // Which database engine (could be "postgres", "sqlite", etc.)
  logging: false,                   // Set to true to see SQL queries in the console (noisy but useful for debugging)
  dialectOptions: {
    multipleStatements: true,       // Allow running multiple SQL statements in one call (needed for our .sql init files)
  },
});

module.exports = sequelize;         // Export the connection so other files can use it
```

**What Sequelize does under the hood**: When you create `new Sequelize(...)`, it establishes a "connection pool" to MySQL. This means it keeps several database connections open and reuses them, which is much faster than opening a new connection for every request.

### MySQL Prerequisites
Before this works, you need MySQL installed and running:
1. Install MySQL Server (or use XAMPP/WAMP)
2. Create the database: `CREATE DATABASE gallery_v2;`
3. Set the credentials in `.env`

---

## Step 10: `src/config/firebase.js` — Firebase Admin SDK <a name="step-10"></a>

```javascript
const fs = require("fs");                      // "fs" = File System. Built-in Node.js module for reading/writing files.
const path = require("path");                  // Built-in: handles file paths
const admin = require("firebase-admin");       // Firebase Admin SDK — server-side Firebase
const env = require("./env");                  // Our config

let app;   // Variable to store the initialized Firebase app (singleton pattern)

// Singleton pattern: only initialize Firebase ONCE, then reuse
function getFirebaseApp() {
  if (app) {
    return app;                                // Already initialized? Return existing instance
  }

  // Build the absolute path to the service account JSON file
  const servicePath = path.resolve(process.cwd(), env.firebaseServiceAccountPath);
  
  // Check if the file exists before trying to read it
  if (!fs.existsSync(servicePath)) {
    throw new Error(`Firebase service account file not found at: ${servicePath}`);
  }

  // Read the JSON file and parse it into a JavaScript object
  const serviceAccount = JSON.parse(fs.readFileSync(servicePath, "utf8"));
  
  // Initialize Firebase Admin with credentials
  app = admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),   // Authenticate using the service account
    projectId: env.firebaseProjectId,                     // Which Firebase project to connect to
  });

  return app;
}

module.exports = {
  // Export a helper function that returns Firebase Auth
  getFirebaseAuth() {
    return getFirebaseApp().auth();   // .auth() gives access to Firebase Authentication features
  },
};
```

**Why `fs` (File System)?**: `fs` is Node.js's built-in module for interacting with files on disk. `fs.existsSync()` checks if a file exists, `fs.readFileSync()` reads a file's contents as text. We use it to read the Firebase service account JSON file.

**What the Service Account JSON is**: This is a private key file downloaded from Firebase Console. It proves that THIS server is authorized to verify Firebase tokens. It contains a private key, client email, project ID, etc. NEVER share this file publicly.

**What is a Singleton?**: A design pattern where only ONE instance of something exists. We only want ONE Firebase app instance because creating multiple would waste resources and cause errors.

---

## Step 11: `src/index.js` — The Server Entry Point <a name="step-11"></a>

```javascript
const app = require("./app");                  // Import the configured Express app
const env = require("./config/env");           // Import config
const { sequelize } = require("./models");     // Import the Sequelize connection (destructured from models/index.js)

// "async function" = a function that can use "await" for asynchronous operations
// Asynchronous = the operation takes time (like connecting to a database) and we wait for it
async function start() {
  try {
    await sequelize.authenticate();            // Test the MySQL connection — throws error if it fails
    await sequelize.sync();                    // Create/update tables to match our model definitions
                                               // sync() looks at all models and creates tables that don't exist yet
    
    // Start listening for HTTP requests on the specified port
    app.listen(env.port, () => {
      console.log(`Server listening on port ${env.port}`);   // This runs once the server is ready
    });
  } catch (error) {
    console.error("Server startup failed:", error.message);
    process.exit(1);                           // Exit with error code 1 (non-zero = error)
  }
}

start();   // Call the function to actually start the server
```

**What `await` means**: JavaScript is single-threaded. When you talk to a database, it takes milliseconds to respond. `await` says "pause here until this operation completes, then continue." Without `await`, the code would continue before the database responds, causing errors.

**What `try/catch` does**: If any line inside `try { }` throws an error, execution jumps to `catch { }`. This prevents the server from crashing with an ugly error. Instead, we log a friendly message and exit cleanly.

**What `sequelize.sync()` does**: It compares your JavaScript model definitions (like `User.init(...)`) with the actual MySQL tables. If a table doesn't exist, it creates it. If a column is missing, it adds it. This is how your JavaScript code stays in sync with the database structure.

---

## Step 12: `src/app.js` — Express App Configuration <a name="step-12"></a>

```javascript
const express = require("express");            // Import Express framework
const cors = require("cors");                  // Import CORS middleware
const path = require("path");                  // Built-in path module
const env = require("./config/env");           // Our config
const authRoutes = require("./routes/authRoutes");       // Import route handlers
const userRoutes = require("./routes/userRoutes");
const phase2Routes = require("./routes/phase2Routes");

const app = express();                         // Create an Express application instance
                                               // This "app" object is the core of our server

// ── Middleware Stack (runs on EVERY request, in order) ──

// CORS middleware — controls which origins (apps) can call our API
app.use(
  cors({
    origin(origin, callback) {                 // Custom origin checker
      if (!origin || env.corsOrigins.includes(origin)) {
        callback(null, true);                  // Allow the request
        return;
      }
      callback(new Error("Not allowed by CORS."));   // Block the request
    },
  })
);

// Parse JSON request bodies — without this, req.body would be undefined
app.use(express.json({ limit: '50mb' }));      // limit: '50mb' allows large image uploads (base64)
// Parse URL-encoded form data
app.use(express.urlencoded({ limit: '50mb', extended: true }));

// Serve uploaded images as static files
// When someone requests /uploads/photo.jpg, Express serves the file from the uploads/ folder
app.use('/uploads', express.static(path.join(__dirname, '../uploads')));

// ── Routes ──

// Health check endpoint — Android calls this to verify the server is running
app.get("/health", (_req, res) => {
  res.status(200).json({ ok: true });          // Always responds with { ok: true }
});
// _req means "I receive this parameter but don't use it" (the underscore is a convention)

// Mount route groups:
app.use("/auth", authRoutes);                  // /auth/google, /auth/firebase
app.use("/", userRoutes);                      // /me
app.use("/", phase2Routes);                    // /posts, /leaderboard, /conversations, etc.

// ── Global Error Handler ──
// Express calls this when any route handler throws an error or calls next(error)
// The 4 parameters (err, req, res, next) tell Express "this is an error handler"
app.use((err, _req, res, _next) => {
  console.error("Unhandled error:", err);
  if (err instanceof SyntaxError && err.status === 400 && "body" in err) {
    return res.status(400).json({ message: "Invalid JSON body." });
  }
  res.status(500).json({ message: err.message || "Internal server error." });
});

module.exports = app;                          // Export so index.js can use it
```

### What is `app.use()`?
`app.use()` registers **middleware** — functions that run on every request. They execute in the ORDER they're added. Think of it as a pipeline:

```
Request arrives → CORS check → JSON parser → Route handler → Response sent
```

### What is `express.static()`?
It serves files from a folder directly. If you have `uploads/photo.jpg`, a request to `GET /uploads/photo.jpg` automatically returns that file. No route handler needed.

### What is `__dirname`?
A special Node.js variable that contains the absolute path of the CURRENT file's directory. `path.join(__dirname, '../uploads')` means "go up one folder from src/, then into uploads/".

---

## Step 13: `src/models/user.js` — The User Database Model <a name="step-13"></a>

```javascript
const { DataTypes, Model } = require("sequelize");     // Import Sequelize types
const sequelize = require("../config/database");        // Import our database connection

// Define a User class that extends Sequelize's Model
class User extends Model {}     // The class body is empty — Sequelize fills it via User.init()

// Define the columns (fields) of the "users" table
User.init(
  {
    id: {
      type: DataTypes.BIGINT.UNSIGNED,      // Large positive integer (up to ~18 quintillion)
      autoIncrement: true,                   // MySQL auto-generates: 1, 2, 3, 4...
      primaryKey: true,                      // This column uniquely identifies each row
    },
    firebaseUid: {
      type: DataTypes.STRING(128),           // String up to 128 characters
      allowNull: false,                      // REQUIRED — cannot be empty
      unique: true,                          // No two users can have the same firebaseUid
      field: "firebase_uid",                 // "field" maps JavaScript camelCase to SQL snake_case
    },
    email: {
      type: DataTypes.STRING(320),           // 320 = max email length per RFC standard
      allowNull: false,
      unique: true,
    },
    name: {
      type: DataTypes.STRING(120),
      allowNull: true,                       // OPTIONAL — can be null
    },
    avatarUrl: {
      type: DataTypes.TEXT,                  // TEXT = unlimited length string (for long URLs)
      allowNull: true,
      field: "avatar_url",
    },
    bio: { type: DataTypes.TEXT, allowNull: true },
    pronouns: { type: DataTypes.STRING(32), allowNull: true },
    provider: {
      type: DataTypes.STRING(32),
      allowNull: false,
      defaultValue: "google",               // If not specified, defaults to "google"
    },
  },
  {
    sequelize,                               // Pass the database connection
    modelName: "User",                       // JavaScript name for the model
    tableName: "users",                      // Actual MySQL table name
    underscored: true,                       // Automatically converts createdAt → created_at in SQL
  }
);

module.exports = User;
```

**What `field: "firebase_uid"` does**: In JavaScript, we use camelCase (`firebaseUid`). In SQL/MySQL, the convention is snake_case (`firebase_uid`). The `field` option maps between the two, so Sequelize translates automatically.

**What `underscored: true` does**: Sequelize auto-creates `createdAt` and `updatedAt` columns. With `underscored: true`, they become `created_at` and `updated_at` in the actual SQL table.

---

## Step 14: `src/models/index.js` — All Models & Relationships <a name="step-14"></a>

This is the largest model file. It defines ALL database tables and how they relate to each other:

```javascript
const sequelize = require("../config/database");
const User = require("./user");
const { DataTypes } = require("sequelize");

// ── Each sequelize.define() creates a database table ──

const Post = sequelize.define("Post", {
  userId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "user_id" },
  promptId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: true, field: "prompt_id" },
  caption: { type: DataTypes.TEXT, allowNull: true },
  status: { type: DataTypes.STRING(32), defaultValue: "published" },
}, { tableName: "posts", underscored: true });

// ... (similar definitions for PostMedia, PostLike, PostComment, Follow, 
//      Conversation, Message, Notification, LeaderboardWeek, WeeklyScore, Badge, etc.)
```

### Relationships (Associations):

```javascript
// One-to-Many: One user can have MANY posts
User.hasMany(Post, { foreignKey: "userId" });      // "User HAS MANY Posts"
Post.belongsTo(User, { foreignKey: "userId" });    // "Post BELONGS TO one User"

// Many-to-Many: Users can like many posts, posts can be liked by many users
Post.belongsToMany(User, { through: PostLike, as: "Likers", foreignKey: "postId", otherKey: "userId" });
User.belongsToMany(Post, { through: PostLike, as: "LikedPosts", foreignKey: "userId", otherKey: "postId" });
```

**What `foreignKey` means**: A foreign key is a column in one table that references the primary key of another table. `Post.userId` points to `User.id`. This creates the relationship: "this post was created by this user."

**What `belongsToMany` with `through` means**: A many-to-many relationship needs a "junction table" (PostLike) that stores pairs: (postId, userId). The `through` option tells Sequelize which table connects the two.

---

> **Continue to Part 3** → `BACKEND_EXPLANATION_PART3.md` for routes, services, JWT auth flow, and how Android connects to everything.
