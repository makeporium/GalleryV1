const { sequelize, User, Post, PostMedia, PostLike, PostComment, Follow } = require("../models");
const fs = require("fs");
const path = require("path");

async function importData() {
  try {
    const dataPath = path.join(__dirname, "../../local_data_dump.json");
    if (!fs.existsSync(dataPath)) {
      console.error("local_data_dump.json not found! Did you run exportLocalData.js?");
      process.exit(1);
    }

    const data = JSON.parse(fs.readFileSync(dataPath, "utf8"));
    const productionUrl = "https://galleryv1-production.up.railway.app";

    console.log("Starting migration to production (Railway Storage)...");
    await sequelize.authenticate();

    // Helper to fix local paths to Railway paths
    const fixUrl = (localPath) => {
      if (!localPath || (!localPath.includes("uploads/") && !localPath.includes("uploads\\"))) return localPath;
      
      const fileName = path.basename(localPath);
      // In production, we serve from /uploads route
      return `${productionUrl}/uploads/${fileName}`;
    };

    // 1. Users
    console.log("Migrating users...");
    for (const u of data.users) {
      const avatarUrl = fixUrl(u.avatarUrl);
      console.log(`Upserting user: ${u.email}`);
      await User.upsert({
        id: u.id,
        firebaseUid: u.firebaseUid,
        email: u.email,
        name: u.name,
        avatarUrl: avatarUrl,
        bio: u.bio,
        pronouns: u.pronouns,
        provider: u.provider,
        createdAt: u.createdAt,
        updatedAt: u.updatedAt
      });
    }

    // 2. Posts
    console.log("Migrating posts...");
    for (const p of data.posts) {
      console.log(`Upserting post: ${p.id}`);
      await Post.upsert({
        id: p.id,
        userId: p.userId,
        promptId: p.promptId,
        caption: p.caption,
        status: p.status,
        submittedAt: p.submittedAt,
        createdAt: p.createdAt,
        updatedAt: p.updatedAt
      });
    }

    // 3. Post Media
    console.log("Migrating media...");
    for (const m of data.postMedia) {
      const mediaUrl = fixUrl(m.mediaUrl);
      console.log(`Upserting media for post ${m.postId}: ${mediaUrl}`);
      await PostMedia.upsert({
        id: m.id,
        postId: m.postId,
        mediaUrl: mediaUrl,
        storageKey: m.storageKey,
        mimeType: m.mimeType,
        width: m.width,
        height: m.height,
        phash: m.phash,
        createdAt: m.createdAt,
        updatedAt: m.updatedAt
      });
    }

    // 4. Likes, Comments, Follows
    console.log("Migrating social interactions...");
    for (const l of data.postLikes) await PostLike.upsert(l);
    for (const c of data.postComments) await PostComment.upsert(c);
    for (const f of data.follows) await Follow.upsert(f);

    console.log("Migration completed successfully using Railway storage!");
    process.exit(0);
  } catch (error) {
    console.error("Migration failed:", error);
    process.exit(1);
  }
}

importData();
