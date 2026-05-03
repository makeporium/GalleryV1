const { 
  sequelize, User, DailyPrompt, Post, PostMedia, PostLike, PostComment, 
  Follow, Conversation, ConversationParticipant, Message, Notification, 
  UserNotificationPref, LeaderboardWeek, WeeklyScore, ScoreEvent, Badge, 
  UserBadge, ModerationCase, UserStrike, Appeal, UserEnforcement 
} = require("../models");
const fs = require("fs");
const path = require("path");

async function importData() {
  try {
    const dataPath = path.join(__dirname, "../../local_data_dump.json");
    if (!fs.existsSync(dataPath)) {
      console.error("local_data_dump.json not found!");
      process.exit(1);
    }

    const data = JSON.parse(fs.readFileSync(dataPath, "utf8"));
    const productionUrl = "https://galleryv1-production.up.railway.app";

    console.log("Starting full migration to production (Railway Storage)...");
    await sequelize.authenticate();

    const fixUrl = (localPath) => {
      if (!localPath || (!localPath.includes("uploads/") && !localPath.includes("uploads\\"))) return localPath;
      const fileName = path.basename(localPath);
      return `${productionUrl}/uploads/${fileName}`;
    };

    const upsertBatch = async (model, items, label) => {
      if (!items || items.length === 0) return;
      console.log(`Migrating ${label}...`);
      for (const item of items) {
        // Handle field mapping for images
        if (item.avatarUrl) item.avatarUrl = fixUrl(item.avatarUrl);
        if (item.mediaUrl) item.mediaUrl = fixUrl(item.mediaUrl);
        if (item.iconUrl) item.iconUrl = fixUrl(item.iconUrl);
        
        await model.upsert(item);
      }
    };

    await upsertBatch(User, data.users, "users");
    await upsertBatch(DailyPrompt, data.dailyPrompts, "daily prompts");
    await upsertBatch(Post, data.posts, "posts");
    await upsertBatch(PostMedia, data.postMedia, "post media");
    await upsertBatch(PostLike, data.postLikes, "post likes");
    await upsertBatch(PostComment, data.postComments, "post comments");
    await upsertBatch(Follow, data.follows, "follows");
    await upsertBatch(Conversation, data.conversations, "conversations");
    await upsertBatch(ConversationParticipant, data.conversationParticipants, "participants");
    await upsertBatch(Message, data.messages, "messages");
    await upsertBatch(Notification, data.notifications, "notifications");
    await upsertBatch(UserNotificationPref, data.userNotificationPrefs, "notification prefs");
    await upsertBatch(LeaderboardWeek, data.leaderboardWeeks, "leaderboard weeks");
    await upsertBatch(WeeklyScore, data.weeklyScores, "weekly scores");
    await upsertBatch(ScoreEvent, data.scoreEvents, "score events");
    await upsertBatch(Badge, data.badges, "badges");
    await upsertBatch(UserBadge, data.userBadges, "user badges");
    await upsertBatch(ModerationCase, data.moderationCases, "moderation cases");
    await upsertBatch(UserStrike, data.userStrikes, "user strikes");
    await upsertBatch(Appeal, data.appeals, "appeals");
    await upsertBatch(UserEnforcement, data.userEnforcements, "user enforcements");

    console.log("Full migration completed successfully!");
    process.exit(0);
  } catch (error) {
    console.error("Migration failed:", error);
    process.exit(1);
  }
}

importData();
