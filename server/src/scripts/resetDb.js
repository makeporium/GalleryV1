const { sequelize } = require("../models");

async function reset() {
  try {
    await sequelize.authenticate();
    console.log("Dropping all tables...");
    
    // Drop all tables in reverse order of dependencies
    const tables = [
      'messages',
      'conversation_participants',
      'conversations',
      'notifications',
      'user_notification_prefs',
      'appeals',
      'user_strikes',
      'moderation_cases',
      'user_enforcements',
      'score_events',
      'weekly_scores',
      'leaderboard_weeks',
      'user_badges',
      'badges',
      'post_comments',
      'post_likes',
      'post_media',
      'posts',
      'follows',
      'daily_prompts',
      'users'
    ];

    for (const table of tables) {
      try {
        await sequelize.query(`DROP TABLE IF EXISTS ${table}`);
        console.log(`Dropped ${table}`);
      } catch (error) {
        console.log(`Could not drop ${table}: ${error.message}`);
      }
    }

    console.log("Database reset successfully.");
  } catch (error) {
    console.error("Database reset failed:", error.message);
    process.exitCode = 1;
  } finally {
    await sequelize.close();
  }
}

reset();
