const { sequelize, User, Post, PostMedia, PostLike, PostComment, Follow } = require("../models");
const { getFirebaseStorage } = require("../config/firebase");
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
    const bucket = getFirebaseStorage();

    console.log("Starting migration to production...");
    await sequelize.authenticate();

    // Helper to upload to Firebase and get public URL
    const uploadToFirebase = async (localPath) => {
      if (!localPath || !localPath.includes("uploads/")) return localPath;
      
      const fileName = path.basename(localPath);
      const filePath = path.join(__dirname, "../../uploads", fileName);
      
      if (!fs.existsSync(filePath)) {
        console.warn(`File not found: ${filePath}`);
        return localPath;
      }

      console.log(`Uploading ${fileName} to Firebase...`);
      const destination = `uploads/${fileName}`;
      await bucket.upload(filePath, {
        destination,
        public: true,
        metadata: {
          contentType: fileName.endsWith(".jpeg") || fileName.endsWith(".jpg") ? "image/jpeg" : "image/png",
        },
      });

      // Construct public URL
      return `https://storage.googleapis.com/${bucket.name}/${destination}`;
    };

    // 1. Users
    console.log("Migrating users...");
    for (const u of data.users) {
      const avatarUrl = await uploadToFirebase(u.avatar_url);
      await User.upsert({ ...u, avatarUrl });
    }

    // 2. Posts
    console.log("Migrating posts...");
    for (const p of data.posts) {
      await Post.upsert(p);
    }

    // 3. Post Media
    console.log("Migrating media...");
    for (const m of data.postMedia) {
      const mediaUrl = await uploadToFirebase(m.media_url);
      await PostMedia.upsert({ ...m, mediaUrl });
    }

    // 4. Likes, Comments, Follows
    console.log("Migrating social interactions...");
    for (const l of data.postLikes) await PostLike.upsert(l);
    for (const c of data.postComments) await PostComment.upsert(c);
    for (const f of data.follows) await Follow.upsert(f);

    console.log("Migration completed successfully!");
    process.exit(0);
  } catch (error) {
    console.error("Migration failed:", error);
    process.exit(1);
  }
}

importData();
