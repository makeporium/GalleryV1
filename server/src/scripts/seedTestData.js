const {
  sequelize,
  User,
  Post,
  PostMedia,
  PostComment,
  Follow,
  Notification,
  Conversation,
  ConversationParticipant,
  Message,
} = require("../models");

async function seedTestData() {
  try {
    await sequelize.authenticate();
    console.log("Creating test users...");

    // Create test users using raw SQL
    await sequelize.query(
      `INSERT IGNORE INTO users (firebase_uid, email, name, avatar_url, provider, created_at, updated_at) VALUES (?, ?, ?, ?, ?, NOW(), NOW())`,
      { replacements: ["firebase_user_1", "user1@test.com", "Alice", "https://i.pravatar.cc/150?img=1", "google"], raw: true }
    );

    await sequelize.query(
      `INSERT IGNORE INTO users (firebase_uid, email, name, avatar_url, provider, created_at, updated_at) VALUES (?, ?, ?, ?, ?, NOW(), NOW())`,
      { replacements: ["firebase_user_2", "user2@test.com", "Bob", "https://i.pravatar.cc/150?img=2", "google"], raw: true }
    );

    await sequelize.query(
      `INSERT IGNORE INTO users (firebase_uid, email, name, avatar_url, provider, created_at, updated_at) VALUES (?, ?, ?, ?, ?, NOW(), NOW())`,
      { replacements: ["firebase_user_3", "user3@test.com", "Charlie", "https://i.pravatar.cc/150?img=3", "google"], raw: true }
    );

    // Get the users we just created
    const user1Result = await sequelize.query(
      `SELECT id FROM users WHERE email = ?`,
      { replacements: ["user1@test.com"], raw: true }
    );
    const user1Id = user1Result[0][0].id;

    const user2Result = await sequelize.query(
      `SELECT id FROM users WHERE email = ?`,
      { replacements: ["user2@test.com"], raw: true }
    );
    const user2Id = user2Result[0][0].id;

    const user3Result = await sequelize.query(
      `SELECT id FROM users WHERE email = ?`,
      { replacements: ["user3@test.com"], raw: true }
    );
    const user3Id = user3Result[0][0].id;

    console.log("Creating test posts...");

    // Create posts for user1 using raw SQL
    for (let i = 1; i <= 5; i++) {
      await sequelize.query(
        `INSERT INTO posts (user_id, caption, status, submitted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW(), NOW())`,
        { replacements: [user1Id, `Beautiful sunset photo #${i}`, "published"], raw: true }
      );
    }

    // Get the posts we just created
    const posts1 = await sequelize.query(
      `SELECT id FROM posts WHERE user_id = ? ORDER BY id DESC LIMIT 5`,
      { replacements: [user1Id], raw: true }
    );

    // Create media for user1 posts
    for (let i = 0; i < posts1[0].length; i++) {
      await sequelize.query(
        `INSERT INTO post_media (post_id, media_url, created_at, updated_at) VALUES (?, ?, NOW(), NOW())`,
        { replacements: [posts1[0][i].id, `https://picsum.photos/400/400?random=${i + 1}`], raw: true }
      );
    }

    // Create posts for user2 using raw SQL
    for (let i = 6; i <= 10; i++) {
      await sequelize.query(
        `INSERT INTO posts (user_id, caption, status, submitted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW(), NOW())`,
        { replacements: [user2Id, `Amazing landscape #${i}`, "published"], raw: true }
      );
    }

    // Get the posts we just created
    const posts2 = await sequelize.query(
      `SELECT id FROM posts WHERE user_id = ? ORDER BY id DESC LIMIT 5`,
      { replacements: [user2Id], raw: true }
    );

    // Create media for user2 posts
    for (let i = 0; i < posts2[0].length; i++) {
      await sequelize.query(
        `INSERT INTO post_media (post_id, media_url, created_at, updated_at) VALUES (?, ?, NOW(), NOW())`,
        { replacements: [posts2[0][i].id, `https://picsum.photos/400/400?random=${i + 6}`], raw: true }
      );
    }

    // Create posts for user3 using raw SQL
    for (let i = 11; i <= 15; i++) {
      await sequelize.query(
        `INSERT INTO posts (user_id, caption, status, submitted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW(), NOW())`,
        { replacements: [user3Id, `Great moment captured #${i}`, "published"], raw: true }
      );
    }

    // Get the posts we just created
    const posts3 = await sequelize.query(
      `SELECT id FROM posts WHERE user_id = ? ORDER BY id DESC LIMIT 5`,
      { replacements: [user3Id], raw: true }
    );

    // Create media for user3 posts
    for (let i = 0; i < posts3[0].length; i++) {
      await sequelize.query(
        `INSERT INTO post_media (post_id, media_url, created_at, updated_at) VALUES (?, ?, NOW(), NOW())`,
        { replacements: [posts3[0][i].id, `https://picsum.photos/400/400?random=${i + 11}`], raw: true }
      );
    }

    console.log("Creating follows...");

    // Create follows using raw SQL
    await sequelize.query(
      `INSERT INTO follows (follower_id, followed_id, created_at, updated_at) VALUES (?, ?, NOW(), NOW())`,
      { replacements: [user2Id, user1Id], raw: true }
    );

    await sequelize.query(
      `INSERT INTO follows (follower_id, followed_id, created_at, updated_at) VALUES (?, ?, NOW(), NOW())`,
      { replacements: [user3Id, user1Id], raw: true }
    );

    await sequelize.query(
      `INSERT INTO follows (follower_id, followed_id, created_at, updated_at) VALUES (?, ?, NOW(), NOW())`,
      { replacements: [user1Id, user2Id], raw: true }
    );

    console.log("Creating comments...");

    // Create comments using raw SQL
    const allPosts = await sequelize.query(
      `SELECT id FROM posts LIMIT 5`,
      { raw: true }
    );
    
    for (const post of allPosts[0]) {
      await sequelize.query(
        `INSERT INTO post_comments (post_id, user_id, comment_text, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())`,
        { replacements: [post.id, user2Id, "Great photo! 📸"], raw: true }
      );

      await sequelize.query(
        `INSERT INTO post_comments (post_id, user_id, comment_text, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())`,
        { replacements: [post.id, user3Id, "Love this! ❤️"], raw: true }
      );
    }

    console.log("Creating notifications...");

    // Create notifications using raw SQL
    const user1Posts = await sequelize.query(
      `SELECT id FROM posts WHERE user_id = ? LIMIT 3`,
      { replacements: [user1Id], raw: true }
    );
    
    for (const post of user1Posts[0]) {
      await sequelize.query(
        `INSERT INTO notifications (recipient_id, actor_id, notification_type, entity_type, entity_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, NOW(), NOW())`,
        { replacements: [user1Id, user2Id, "like", "post", post.id], raw: true }
      );

      await sequelize.query(
        `INSERT INTO notifications (recipient_id, actor_id, notification_type, entity_type, entity_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, NOW(), NOW())`,
        { replacements: [user1Id, user3Id, "comment", "post", post.id], raw: true }
      );
    }

    // Create follow notifications
    await sequelize.query(
      `INSERT INTO notifications (recipient_id, actor_id, notification_type, entity_type, entity_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, NOW(), NOW())`,
      { replacements: [user1Id, user2Id, "follow", "user", user1Id], raw: true }
    );

    await sequelize.query(
      `INSERT INTO notifications (recipient_id, actor_id, notification_type, entity_type, entity_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, NOW(), NOW())`,
      { replacements: [user1Id, user3Id, "follow", "user", user1Id], raw: true }
    );

    console.log("Creating conversations and messages...");

    // Create conversation between user1 and user2 using raw SQL
    const conv1Result = await sequelize.query(
      `INSERT INTO conversations (conversation_type, created_by, created_at, updated_at) VALUES (?, ?, NOW(), NOW())`,
      { replacements: ["direct", user1Id], raw: true }
    );
    const conv1Id = conv1Result[0];

    await sequelize.query(
      `INSERT INTO conversation_participants (conversation_id, user_id, created_at, updated_at) VALUES (?, ?, NOW(), NOW())`,
      { replacements: [conv1Id, user1Id], raw: true }
    );

    await sequelize.query(
      `INSERT INTO conversation_participants (conversation_id, user_id, created_at, updated_at) VALUES (?, ?, NOW(), NOW())`,
      { replacements: [conv1Id, user2Id], raw: true }
    );

    await sequelize.query(
      `INSERT INTO messages (conversation_id, sender_id, body, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())`,
      { replacements: [conv1Id, user1Id, "Hey! How are you?"], raw: true }
    );

    await sequelize.query(
      `INSERT INTO messages (conversation_id, sender_id, body, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())`,
      { replacements: [conv1Id, user2Id, "I'm doing great! How about you?"], raw: true }
    );

    // Create conversation between user1 and user3 using raw SQL
    const conv2Result = await sequelize.query(
      `INSERT INTO conversations (conversation_type, created_by, created_at, updated_at) VALUES (?, ?, NOW(), NOW())`,
      { replacements: ["direct", user1Id], raw: true }
    );
    const conv2Id = conv2Result[0];

    await sequelize.query(
      `INSERT INTO conversation_participants (conversation_id, user_id, created_at, updated_at) VALUES (?, ?, NOW(), NOW())`,
      { replacements: [conv2Id, user1Id], raw: true }
    );

    await sequelize.query(
      `INSERT INTO conversation_participants (conversation_id, user_id, created_at, updated_at) VALUES (?, ?, NOW(), NOW())`,
      { replacements: [conv2Id, user3Id], raw: true }
    );

    await sequelize.query(
      `INSERT INTO messages (conversation_id, sender_id, body, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())`,
      { replacements: [conv2Id, user3Id, "Your photos are amazing!"], raw: true }
    );

    await sequelize.query(
      `INSERT INTO messages (conversation_id, sender_id, body, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())`,
      { replacements: [conv2Id, user1Id, "Thank you so much! 😊"], raw: true }
    );

    console.log("✅ Test data seeded successfully!");
    console.log("\nTest Users Created:");
    console.log("- user1@test.com (Alice) - ID: " + user1Id);
    console.log("- user2@test.com (Bob) - ID: " + user2Id);
    console.log("- user3@test.com (Charlie) - ID: " + user3Id);
    console.log("\nYou can now log in with any of these accounts to see real data!");
  } catch (error) {
    console.error("Test data seed failed:", error.message);
    console.error("Full error:", error);
    process.exitCode = 1;
  } finally {
    await sequelize.close();
  }
}

seedTestData();
