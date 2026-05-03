const { 
  sequelize, User, DailyPrompt, Post, PostMedia, PostLike, PostComment, 
  Follow, Conversation, ConversationParticipant, Message, Notification, 
  UserNotificationPref, LeaderboardWeek, WeeklyScore, ScoreEvent, Badge, 
  UserBadge, ModerationCase, UserStrike, Appeal, UserEnforcement 
} = require("../models");
const fs = require("fs");
const path = require("path");

async function exportData() {
  try {
    console.log("Connecting to local database...");
    await sequelize.authenticate();

    const data = {
      users: await User.findAll({ raw: true }),
      dailyPrompts: await DailyPrompt.findAll({ raw: true }),
      posts: await Post.findAll({ raw: true }),
      postMedia: await PostMedia.findAll({ raw: true }),
      postLikes: await PostLike.findAll({ raw: true }),
      postComments: await PostComment.findAll({ raw: true }),
      follows: await Follow.findAll({ raw: true }),
      conversations: await Conversation.findAll({ raw: true }),
      conversationParticipants: await ConversationParticipant.findAll({ raw: true }),
      messages: await Message.findAll({ raw: true }),
      notifications: await Notification.findAll({ raw: true }),
      userNotificationPrefs: await UserNotificationPref.findAll({ raw: true }),
      leaderboardWeeks: await LeaderboardWeek.findAll({ raw: true }),
      weeklyScores: await WeeklyScore.findAll({ raw: true }),
      scoreEvents: await ScoreEvent.findAll({ raw: true }),
      badges: await Badge.findAll({ raw: true }),
      userBadges: await UserBadge.findAll({ raw: true }),
      moderationCases: await ModerationCase.findAll({ raw: true }),
      userStrikes: await UserStrike.findAll({ raw: true }),
      appeals: await Appeal.findAll({ raw: true }),
      userEnforcements: await UserEnforcement.findAll({ raw: true }),
    };

    const outputPath = path.join(__dirname, "../../local_data_dump.json");
    fs.writeFileSync(outputPath, JSON.stringify(data, null, 2));

    console.log(`Data exported successfully to ${outputPath}`);
    console.log(`Users: ${data.users.length}`);
    console.log(`Posts: ${data.posts.length}`);
    console.log(`Media: ${data.postMedia.length}`);
    console.log(`Notifications: ${data.notifications.length}`);

    process.exit(0);
  } catch (error) {
    console.error("Export failed:", error);
    process.exit(1);
  }
}

exportData();
