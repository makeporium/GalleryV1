const { sequelize, User, Post, PostMedia, PostLike, PostComment, Follow } = require("../models");
const fs = require("fs");
const path = require("path");

async function exportData() {
  try {
    console.log("Connecting to local database...");
    await sequelize.authenticate();

    const data = {
      users: await User.findAll({ raw: true }),
      posts: await Post.findAll({ raw: true }),
      postMedia: await PostMedia.findAll({ raw: true }),
      postLikes: await PostLike.findAll({ raw: true }),
      postComments: await PostComment.findAll({ raw: true }),
      follows: await Follow.findAll({ raw: true }),
    };

    const outputPath = path.join(__dirname, "../../local_data_dump.json");
    fs.writeFileSync(outputPath, JSON.stringify(data, null, 2));

    console.log(`Data exported successfully to ${outputPath}`);
    console.log(`Users: ${data.users.length}`);
    console.log(`Posts: ${data.posts.length}`);
    console.log(`Media: ${data.postMedia.length}`);

    process.exit(0);
  } catch (error) {
    console.error("Export failed:", error);
    process.exit(1);
  }
}

exportData();
