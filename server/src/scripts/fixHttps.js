const { PostMedia, User } = require("../models");
const { Op } = require("sequelize");

async function fixHttps() {
  try {
    console.log("Fixing http -> https for Railway URLs...");
    
    // Fix PostMedia
    const media = await PostMedia.findAll({
      where: {
        mediaUrl: { [Op.like]: "http://galleryv1-production.up.railway.app%" }
      }
    });
    
    for (const m of media) {
      m.mediaUrl = m.mediaUrl.replace("http://", "https://");
      await m.save();
    }
    
    // Fix User avatars
    const users = await User.findAll({
      where: {
        avatarUrl: { [Op.like]: "http://galleryv1-production.up.railway.app%" }
      }
    });
    
    for (const u of users) {
      u.avatarUrl = u.avatarUrl.replace("http://", "https://");
      await u.save();
    }

    console.log(`Fixed ${media.length} media URLs and ${users.length} user avatars.`);
  } catch (error) {
    console.error("Failed to fix HTTPS URLs:", error);
  }
}

fixHttps();
