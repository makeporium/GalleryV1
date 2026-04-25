# Backend Integration Guide — Part 3: Auth Flow, Routes, and Android ↔ Server Connection

> This part covers JWT services, authentication routes, all API endpoints, and how the Android app actually talks to the server.

---

## Table of Contents (Part 3)
1. [Step 15: JWT Service — Creating & Verifying Tokens](#step-15)
2. [Step 16: User Service — Creating Users from Firebase Data](#step-16)
3. [Step 17: Auth Middleware — Protecting Routes](#step-17)
4. [Step 18: Auth Routes — Login & Token Exchange](#step-18)
5. [Step 19: User Routes — Profile Endpoint](#step-19)
6. [Step 20: Phase 2 Routes — All Feature Endpoints](#step-20)
7. [Step 21: SQL Schema Files — Database Table Definitions](#step-21)
8. [Step 22: Database Init Scripts](#step-22)

---

## Step 15: `src/services/jwtService.js` — Creating & Verifying Tokens <a name="step-15"></a>

```javascript
const jwt = require("jsonwebtoken");    // npm package for JWT token operations
const env = require("../config/env");

// Create a new JWT token
// "payload" = the data you want to embed in the token (user ID, email, etc.)
function signAccessToken(payload) {
  return jwt.sign(                      // jwt.sign() creates a new token
    payload,                            // Data to embed (e.g., { sub: 1, email: "..." })
    env.jwtSecret,                      // Secret key used to encrypt/sign the token
    { expiresIn: env.jwtExpiresIn }     // Token auto-expires after this time (e.g., "1h")
  );
  // Returns a string like: "eyJhbGciOiJIUzI1NiIs..."
}

// Verify and decode a JWT token
// Returns the original payload if valid, THROWS an error if invalid/expired
function verifyAccessToken(token) {
  return jwt.verify(token, env.jwtSecret);    // Same secret key used to sign
}

module.exports = { signAccessToken, verifyAccessToken };
```

### How JWT Works (The Full Picture):

```
1. User logs in with Google → Firebase gives idToken
2. Android sends idToken to our server: POST /auth/google { idToken: "..." }
3. Server verifies idToken with Firebase Admin SDK
4. Server creates a JWT with: { sub: userId, email: "..." }
5. Server sends JWT back to Android as "accessToken"
6. Android saves this accessToken in SharedPreferences
7. For every future request, Android sends: Authorization: Bearer <accessToken>
8. Server middleware verifies the JWT and extracts the userId
```

**Why not just use the Firebase token directly?** Firebase tokens expire quickly and require a network call to Firebase servers to verify. Our JWT is verified locally (just math), which is much faster.

**What `sub` means**: "sub" is short for "subject" — a standard JWT claim that identifies who the token belongs to. We set it to the user's database ID.

---

## Step 16: `src/services/userService.js` — Creating Users from Firebase Data <a name="step-16"></a>

```javascript
const { User } = require("../models");

// Determine the authentication provider from the Firebase decoded token
function mapProvider(decodedToken) {
  const signInProvider = decodedToken.firebase?.sign_in_provider;
  // "?." is optional chaining — if firebase is null/undefined, don't crash, just return undefined
  if (signInProvider === "google.com") return "google";
  if (signInProvider === "password") return "password";
  return signInProvider || "firebase";   // Fallback
}

// "Upsert" = Update if exists, Insert if new
// This function handles first-time users AND returning users
async function upsertFirebaseUser(decodedToken) {
  const firebaseUid = decodedToken.uid;         // Firebase's unique ID for this user
  const email = decodedToken.email || "";
  const name = decodedToken.name || null;        // Google provides the name
  const avatarUrl = decodedToken.picture || null; // Google provides the profile photo URL
  const provider = mapProvider(decodedToken);

  // FIRST: Try to find user by Firebase UID (most common case — returning user)
  const existingByUid = await User.findOne({ where: { firebaseUid } });
  if (existingByUid) {
    // User exists — update their info (name/avatar might have changed on Google)
    existingByUid.email = email;
    existingByUid.name = name;
    existingByUid.avatarUrl = avatarUrl;
    existingByUid.provider = provider;
    await existingByUid.save();         // .save() writes changes to database
    return existingByUid;
  }

  // SECOND: Try to find by email (edge case — user existed before Firebase was added)
  const existingByEmail = await User.findOne({ where: { email } });
  if (existingByEmail) {
    existingByEmail.firebaseUid = firebaseUid;  // Link existing account to Firebase
    existingByEmail.name = name;
    existingByEmail.avatarUrl = avatarUrl;
    existingByEmail.provider = provider;
    await existingByEmail.save();
    return existingByEmail;
  }

  // THIRD: Brand new user — create a new record
  return User.create({ firebaseUid, email, name, avatarUrl, provider });
  // User.create() inserts a new row in the "users" table and returns the created object
}

module.exports = { upsertFirebaseUser };
```

---

## Step 17: `src/middleware/authMiddleware.js` — Protecting Routes <a name="step-17"></a>

```javascript
const { verifyAccessToken } = require("../services/jwtService");
const { User } = require("../models");

// Middleware function — runs BEFORE the route handler
// Express middleware always receives (req, res, next) parameters
async function requireAuth(req, res, next) {
  // Read the Authorization header from the request
  // Expected format: "Bearer eyJhbGciOiJIUzI1..."
  const authHeader = req.headers.authorization || "";
  const [scheme, token] = authHeader.split(" ");   // Split "Bearer TOKEN" into ["Bearer", "TOKEN"]
  
  // Validate the format
  if (scheme !== "Bearer" || !token) {
    return res.status(401).json({ message: "Missing or invalid Authorization header." });
    // 401 = Unauthorized (not logged in)
  }

  try {
    const payload = verifyAccessToken(token);    // Decode and verify the JWT
    // payload = { sub: 1, firebaseUid: "...", email: "...", iat: ..., exp: ... }
    
    const user = await User.findByPk(payload.sub);   // findByPk = Find By Primary Key
    if (!user) {
      return res.status(401).json({ message: "Invalid token user." });
    }
    
    req.user = user;   // ATTACH the user object to the request
    // Now, in any route handler, you can access req.user to know WHO is making the request
    
    return next();     // Call next() to continue to the actual route handler
    // If you don't call next(), the request hangs forever
  } catch (error) {
    return res.status(401).json({ message: "Invalid or expired token." });
  }
}

module.exports = { requireAuth };
```

### How Middleware Chains Work:

```javascript
// In a route definition like:
router.get("/posts/feed", requireAuth, async (req, res) => { ... });

// The flow is:
// 1. Request arrives at GET /posts/feed
// 2. requireAuth middleware runs FIRST
// 3. If token is valid → requireAuth calls next() → route handler runs
// 4. If token is invalid → requireAuth sends 401 → route handler NEVER runs
```

---

## Step 18: `src/routes/authRoutes.js` — Login & Token Exchange <a name="step-18"></a>

```javascript
const express = require("express");
const { z } = require("zod");                           // Zod: request validation library
const { getFirebaseAuth } = require("../config/firebase");
const { upsertFirebaseUser } = require("../services/userService");
const { signAccessToken } = require("../services/jwtService");

const router = express.Router();    // Create a "mini-app" for auth routes

// Zod schema: defines what the request body MUST look like
const authFirebaseSchema = z.object({
  idToken: z.string().min(20),      // idToken must be a string with at least 20 characters
});

// Handler for POST /auth/firebase and POST /auth/google
async function handleFirebaseTokenExchange(req, res) {
  try {
    // 1. VALIDATE the request body
    const { idToken } = authFirebaseSchema.parse(req.body);
    // .parse() throws a ZodError if the body doesn't match the schema
    
    // 2. VERIFY the Firebase ID token with Firebase Admin SDK
    const decoded = await getFirebaseAuth().verifyIdToken(idToken, true);
    // This calls Firebase's servers to verify the token is real and not expired
    // "true" means check if the token has been revoked
    // Returns decoded token with: uid, email, name, picture, firebase.sign_in_provider, etc.
    
    // 3. CREATE or UPDATE the user in our MySQL database
    const user = await upsertFirebaseUser(decoded);
    
    // 4. CREATE our own JWT access token
    const accessToken = signAccessToken({
      sub: user.id,                    // "sub" (subject) = our database user ID
      firebaseUid: user.firebaseUid,
      email: user.email,
    });

    // 5. RESPOND with the access token and user data
    return res.status(200).json({
      accessToken,                     // The Android app will save this
      user: {
        id: user.id,
        firebaseUid: user.firebaseUid,
        email: user.email,
        name: user.name,
        avatarUrl: user.avatarUrl,
      },
    });
  } catch (error) {
    if (error.name === "ZodError") {
      return res.status(400).json({ message: "Invalid request body." });   // 400 = Bad Request
    }
    return res.status(401).json({ message: "Failed to authenticate Firebase user." });
  }
}

// Register the handler on TWO routes (both do the same thing)
router.post("/firebase", handleFirebaseTokenExchange);
router.post("/google", handleFirebaseTokenExchange);

module.exports = router;
```

### What is `express.Router()`?
A Router is like a mini Express app. You define routes on it, then "mount" it on the main app with a prefix:
```javascript
// In app.js:
app.use("/auth", authRoutes);   // All routes in authRoutes get prefixed with /auth
// So router.post("/google") becomes accessible at POST /auth/google
```

### What is Zod?
Zod is a validation library. It ensures that incoming data has the correct shape. Without validation, a malicious user could send garbage data and crash your server or corrupt your database.

---

## Step 19: `src/routes/userRoutes.js` — Profile Endpoint <a name="step-19"></a>

```javascript
const express = require("express");
const { requireAuth } = require("../middleware/authMiddleware");
const router = express.Router();

// GET /me — returns the logged-in user's profile
// requireAuth runs first, attaches req.user
router.get("/me", requireAuth, (req, res) => {
  return res.status(200).json({
    user: {
      id: req.user.id,
      firebaseUid: req.user.firebaseUid,
      email: req.user.email,
      name: req.user.name,
      avatarUrl: req.user.avatarUrl,
      bio: req.user.bio,
      pronouns: req.user.pronouns,
      provider: req.user.provider,
      createdAt: req.user.createdAt,
      updatedAt: req.user.updatedAt,
    },
  });
});

module.exports = router;
```

---

## Step 20: Phase 2 Routes — Key Endpoint Examples <a name="step-20"></a>

The `phase2Routes.js` file (863 lines) contains ALL feature endpoints. Here are the key patterns:

### 20.1 Creating a Post (POST /posts)
```javascript
router.post("/posts", requireAuth, async (req, res, next) => {
  try {
    const body = createPostSchema.parse(req.body);   // Validate input
    
    // Handle base64 image upload
    let mediaUrl = body.mediaUrl;
    if (mediaUrl.startsWith('data:image/')) {
      // Extract image type and data from the base64 string
      const matches = mediaUrl.match(/^data:image\/(\w+);base64,(.+)$/);
      const extension = matches[1];          // "jpeg", "png", etc.
      const base64Data = matches[2];         // The actual image data
      const buffer = Buffer.from(base64Data, 'base64');   // Convert to binary
      
      // Save to disk
      const filename = `${Date.now()}-${req.user.id}.${extension}`;
      fs.writeFileSync(path.join(uploadsDir, filename), buffer);
      
      // Build the URL where this image can be accessed
      mediaUrl = `${req.protocol}://${req.get('host')}/uploads/${filename}`;
    }
    
    // Insert into database
    const post = await Post.create({
      userId: req.user.id,          // req.user was set by requireAuth middleware
      caption: body.caption,
      status: "published",
    });
    await PostMedia.create({ postId: post.id, mediaUrl });
    
    // Award points for posting
    await addScoreForUser({
      userId: req.user.id, pointsDelta: 10, source: "submission_reward",
      refType: "post", refId: post.id,
    });
    
    return res.status(201).json({ postId: post.id });   // 201 = Created
  } catch (error) {
    if (error.name === "ZodError") {
      return res.status(400).json({ message: "Invalid post payload." });
    }
    return next(error);   // Pass to global error handler
  }
});
```

### 20.2 Getting the Feed (GET /posts/feed)
```javascript
router.get("/posts/feed", requireAuth, async (req, res, next) => {
  const sort = req.query.sort || "new";     // Read ?sort=new from URL query string
  
  const posts = await Post.findAll({
    include: [                               // "include" = SQL JOIN — fetch related data
      { model: User, attributes: ["id", "name", "avatarUrl"] },    // Who posted it
      { model: PostMedia, attributes: ["id", "mediaUrl"] },        // The images
      { model: PostComment, attributes: ["id"] },                  // Comments (just count)
    ],
    order: [["createdAt", "DESC"]],          // Newest first
    limit: 50,                               // Max 50 posts
  });
  
  // Count likes separately (more efficient)
  const postIds = posts.map(p => p.id);
  const likes = await PostLike.findAll({ where: { postId: { [Op.in]: postIds } } });
  // Op.in = SQL "IN" operator: WHERE post_id IN (1, 2, 3, 4, ...)
  
  // Transform into the response format
  let feed = posts.map(post => ({
    id: post.id,
    caption: post.caption,
    user: post.User,
    media: post.PostMedia.map(item => item.mediaUrl),  // Flatten to URL strings
    likesCount: likeCountMap.get(post.id) || 0,
    commentsCount: post.PostComments.length,
    hasLiked: likedByMe.has(post.id),
  }));
  
  return res.status(200).json({ feed });
});
```

### 20.3 Other Important Routes Summary:

| Method | Path | Purpose |
|--------|------|---------|
| `POST /posts/:id/likes` | Like a post |
| `DELETE /posts/:id/likes` | Unlike a post |
| `POST /posts/:id/comments` | Add a comment |
| `GET /posts/:id/comments` | Get all comments |
| `GET /users/:id` | Get user profile + stats |
| `PATCH /me` | Update own profile |
| `POST /users/:id/follow` | Follow a user |
| `DELETE /users/:id/follow` | Unfollow a user |
| `GET /conversations` | List all chat conversations |
| `POST /conversations/direct` | Start a direct message |
| `GET /conversations/:id/messages` | Get messages in a chat |
| `POST /conversations/:id/messages` | Send a message |
| `GET /notifications` | Get notifications |
| `GET /leaderboard/current` | Get current week leaderboard |

**What `:id` means in routes**: It's a URL parameter. `GET /users/42` → `req.params.id` equals `"42"`. Express captures the value from the URL.

**What `req.query` is**: URL query parameters. `GET /posts/feed?sort=trending` → `req.query.sort` equals `"trending"`.

---

## Step 21: SQL Schema Files <a name="step-21"></a>

### `sql/001_init.sql` — Creates the users table:
```sql
CREATE TABLE IF NOT EXISTS users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,    -- Auto-incrementing unique ID
    firebase_uid VARCHAR(128) NOT NULL,             -- Firebase's user identifier
    email VARCHAR(320) NOT NULL,                    -- User's email address
    name VARCHAR(120) NULL,                         -- Display name (optional)
    avatar_url TEXT NULL,                           -- Profile picture URL (optional)
    provider VARCHAR(32) NOT NULL DEFAULT 'google', -- How they signed up
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- Auto-set on creation
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),                               -- id is the main identifier
    UNIQUE KEY users_firebase_uid_unique (firebase_uid),  -- No duplicate Firebase UIDs
    UNIQUE KEY users_email_unique (email)            -- No duplicate emails
);
```

### `sql/002_phase2_full_schema.sql` — Creates ALL other tables:
This file creates 17 tables: `daily_prompts`, `posts`, `post_media`, `post_likes`, `post_comments`, `follows`, `conversations`, `conversation_participants`, `messages`, `notifications`, `user_notification_prefs`, `leaderboard_weeks`, `weekly_scores`, `score_events`, `badges`, `user_badges`, `moderation_cases`, `user_strikes`, `appeals`, `user_enforcements`.

**What `FOREIGN KEY` does**: It creates a rule: "the value in this column MUST exist in another table." For example, `posts.user_id` must reference a valid `users.id`. If you try to insert a post with a non-existent user_id, MySQL rejects it.

**What `ON DELETE CASCADE` means**: If a user is deleted, all their posts are automatically deleted too. Without this, you'd have "orphan" posts pointing to non-existent users.

---

## Step 22: Database Init Scripts <a name="step-22"></a>

### `src/scripts/initDb.js`
```javascript
const { sequelize } = require("../models");
const fs = require("fs/promises");     // "fs/promises" = async version of fs module
const path = require("path");

async function init() {
  try {
    await sequelize.authenticate();     // Test connection
    const sqlDir = path.resolve(__dirname, "../../sql");
    const files = (await fs.readdir(sqlDir))    // List all files in sql/ directory
      .filter(name => name.endsWith(".sql"))    // Only .sql files
      .sort();                                  // Alphabetical order (001 before 002)
    for (const file of files) {
      const sql = await fs.readFile(path.join(sqlDir, file), "utf8");  // Read file contents
      await sequelize.query(sql);               // Execute the SQL
      console.log(`Applied ${file}`);
    }
    console.log("Database initialized successfully.");
  } catch (error) {
    console.error("Database initialization failed:", error.message);
  } finally {
    await sequelize.close();            // Always close the connection when done
  }
}
init();
```

**Usage**: `npm run db:init` runs this script to create all tables in your MySQL database.

---

> **Continue to Part 4** → `BACKEND_EXPLANATION_PART4.md` for the complete Android-side integration (Retrofit, ApiService, models, and the auth flow).
