const express = require("express");
const { Op } = require("sequelize");
const { z } = require("zod");
const fs = require("fs");
const path = require("path");
const { requireAuth } = require("../middleware/authMiddleware");
const {
  User,
  DailyPrompt,
  Post,
  PostMedia,
  PostLike,
  PostComment,
  Follow,
  Conversation,
  ConversationParticipant,
  Message,
  Notification,
  UserNotificationPref,
  LeaderboardWeek,
  WeeklyScore,
  ScoreEvent,
  Badge,
  UserBadge,
  ModerationCase,
  UserStrike,
  Appeal,
  UserEnforcement,
  sequelize,
} = require("../models");

const router = express.Router();

// Ensure uploads directory exists
const uploadsDir = path.join(__dirname, '../../uploads');
if (!fs.existsSync(uploadsDir)) {
  fs.mkdirSync(uploadsDir, { recursive: true });
}

const createPostSchema = z.object({
  caption: z.string().trim().min(1).max(2000),
  mediaUrl: z.string().trim().min(1), // Can be a URL or base64 data
  promptId: z.number().int().positive().optional(),
});

const commentSchema = z.object({
  commentText: z.string().trim().min(1).max(1000),
});

const updateProfileSchema = z.object({
  name: z.string().trim().min(1).max(120).optional(),
  avatarUrl: z.string().trim().nullable().optional(),
  bio: z.string().trim().max(500).nullable().optional(),
  pronouns: z.string().trim().max(32).nullable().optional(),
});

const directConversationSchema = z.object({
  targetUserId: z.number().int().positive(),
});

const messageSchema = z.object({
  body: z.string().trim().max(4000).optional(),
  mediaUrl: z.string().trim().url().optional(),
});

const appealSchema = z.object({
  caseId: z.number().int().positive(),
  appealMessage: z.string().trim().min(10).max(3000),
});

function getWeekBounds(date) {
  const d = new Date(date);
  const day = d.getDay();
  const diffToMonday = (day + 6) % 7;
  const weekStart = new Date(d);
  weekStart.setDate(d.getDate() - diffToMonday);
  const weekEnd = new Date(weekStart);
  weekEnd.setDate(weekStart.getDate() + 6);
  return {
    weekStart: weekStart.toISOString().slice(0, 10),
    weekEnd: weekEnd.toISOString().slice(0, 10),
  };
}

async function getOrCreateActiveWeek() {
  const { weekStart, weekEnd } = getWeekBounds(new Date());
  const [week] = await LeaderboardWeek.findOrCreate({
    where: { weekStart },
    defaults: { weekEnd, status: "active" },
  });
  return week;
}

async function addScoreForUser({ userId, pointsDelta, source, refType, refId }) {
  const week = await getOrCreateActiveWeek();
  const [score] = await WeeklyScore.findOrCreate({
    where: { weekId: week.id, userId },
    defaults: { weekId: week.id, userId, points: 0 },
  });
  score.points += pointsDelta;
  await score.save();
  await ScoreEvent.create({
    userId,
    weekId: week.id,
    source,
    pointsDelta,
    refType,
    refId,
  });
}

function computeTrendingScore({ likesCount, commentsCount, createdAt }) {
  const ageHours = Math.max(1, (Date.now() - new Date(createdAt).getTime()) / (1000 * 60 * 60));
  const engagement = likesCount + commentsCount * 2;
  return engagement / ageHours;
}

function toUserDto(user) {
  return {
    id: user.id,
    firebaseUid: user.firebase_uid ?? user.firebaseUid,
    email: user.email,
    name: user.name,
    avatarUrl: user.avatar_url ?? user.avatarUrl,
    bio: user.bio,
    pronouns: user.pronouns,
    provider: user.provider,
    createdAt: user.created_at ?? user.createdAt,
    updatedAt: user.updated_at ?? user.updatedAt,
  };
}

router.get("/prompts/today", requireAuth, async (_req, res, next) => {
  try {
    const today = new Date().toISOString().slice(0, 10);
    let prompt = await DailyPrompt.findOne({ where: { challengeDate: today } });
    if (!prompt) {
      // Create a dynamic prompt based on the day of the week
      const dayOfWeek = new Date().getDay();
      const prompts = [
        { title: "Sunrise Sunday", description: "Capture the beauty of a sunrise or early morning light" },
        { title: "Monochrome Monday", description: "Share a stunning black and white photo" },
        { title: "Texture Tuesday", description: "Focus on interesting textures and patterns" },
        { title: "Wildlife Wednesday", description: "Photograph nature, animals, or plants" },
        { title: "Throwback Thursday", description: "Share a memorable photo from your archives" },
        { title: "Foodie Friday", description: "Capture delicious food or drinks" },
        { title: "Street Saturday", description: "Document life on the streets" }
      ];
      const dailyPrompt = prompts[dayOfWeek];
      
      prompt = await DailyPrompt.create({
        challengeDate: today,
        title: dailyPrompt.title,
        description: dailyPrompt.description,
        isActive: true,
      });
    }
    return res.status(200).json({ prompt });
  } catch (error) {
    return next(error);
  }
});

router.get("/prompts/history", requireAuth, async (_req, res, next) => {
  try {
    const prompts = await DailyPrompt.findAll({ order: [["challengeDate", "DESC"]], limit: 30 });
    return res.status(200).json({ prompts });
  } catch (error) {
    return next(error);
  }
});

router.post("/posts", requireAuth, async (req, res, next) => {
  try {
    const body = createPostSchema.parse(req.body);
    if (body.promptId) {
      const prompt = await DailyPrompt.findByPk(body.promptId);
      if (!prompt) {
        return res.status(404).json({ message: "Prompt not found." });
      }
    }
    
    let mediaUrl = body.mediaUrl;
    
    // Check if mediaUrl is base64 data
    if (mediaUrl.startsWith('data:image/')) {
      try {
        // Extract base64 data
        const matches = mediaUrl.match(/^data:image\/(\w+);base64,(.+)$/);
        if (matches) {
          const extension = matches[1];
          const base64Data = matches[2];
          const buffer = Buffer.from(base64Data, 'base64');
          
          // Generate unique filename
          const filename = `${Date.now()}-${req.user.id}.${extension}`;
          const filepath = path.join(uploadsDir, filename);
          
          // Save file
          fs.writeFileSync(filepath, buffer);
          
          // Force https in production (Railway) to avoid Cleartext issues on Android
          const host = req.get('host');
          const protocol = host.includes('railway.app') ? 'https' : req.protocol;
          mediaUrl = `${protocol}://${host}/uploads/${filename}`;
          console.log('Saved uploaded image:', mediaUrl);
        }
      } catch (err) {
        console.error('Error processing base64 image:', err);
        return res.status(400).json({ message: "Invalid image data." });
      }
    }
    
    // Use a Transaction directly instead of the Stored Procedure (avoids OUT param driver issues)
    const t = await sequelize.transaction();
    let postId;
    try {
      const [insertPostId] = await sequelize.query(
        `INSERT INTO posts (user_id, prompt_id, caption, status, submitted_at, created_at, updated_at)
         VALUES (?, ?, ?, 'published', NOW(), NOW(), NOW())`,
        { replacements: [req.user.id, body.promptId || null, body.caption], transaction: t }
      );
      postId = insertPostId;
      await sequelize.query(
        `INSERT INTO post_media (post_id, media_url, created_at, updated_at) VALUES (?, ?, NOW(), NOW())`,
        { replacements: [postId, mediaUrl], transaction: t }
      );
      await t.commit();
    } catch (e) {
      await t.rollback();
      throw e;
    }

    await addScoreForUser({
      userId: req.user.id,
      pointsDelta: 10,
      source: "submission_reward",
      refType: "post",
      refId: postId,
    });
    // Notify followers about new post
    try {
      const followers = await sequelize.query(
        "SELECT follower_id FROM follows WHERE followed_id = ?",
        { replacements: [req.user.id], type: sequelize.QueryTypes.SELECT }
      );
      for (const follow of followers) {
        await sequelize.query(
          `INSERT INTO notifications (recipient_id, actor_id, notification_type, entity_type, entity_id, created_at, updated_at) 
           VALUES (?, ?, 'new_post', 'post', ?, NOW(), NOW())`,
          { replacements: [follow.follower_id, req.user.id, postId] }
        ).catch(() => {});
      }
    } catch (_) {}
    return res.status(201).json({ postId: postId });
  } catch (error) {
    if (error.name === "ZodError") {
      return res.status(400).json({ message: "Invalid post payload." });
    }
    return next(error);
  }
});

router.get("/posts/feed", requireAuth, async (req, res, next) => {
  try {
    const sort = req.query.sort || "new";
    
    // Using raw SQL and the View
    let feedData = await sequelize.query(
      "SELECT * FROM feed_view ORDER BY post_created_at DESC LIMIT 50",
      { type: sequelize.QueryTypes.SELECT }
    );

    const postIds = feedData.map((post) => post.post_id);
    if (postIds.length === 0) {
      return res.status(200).json({ feed: [] });
    }

    // Check which posts the current user has liked
    const userLikes = await sequelize.query(
      "SELECT post_id FROM post_likes WHERE user_id = ? AND post_id IN (?)",
      { replacements: [req.user.id, postIds], type: sequelize.QueryTypes.SELECT }
    );
    const likedByMe = new Set(userLikes.map(l => l.post_id));

    let feed = feedData.map((post) => ({
      id: post.post_id,
      caption: post.caption,
      status: post.status,
      createdAt: post.post_created_at,
      user: {
        id: post.user_id,
        name: post.user_name,
        avatarUrl: post.avatar_url,
      },
      media: post.media_url ? [post.media_url] : [],
      likesCount: Number(post.likes_count),
      commentsCount: Number(post.comments_count),
      hasLiked: likedByMe.has(post.post_id),
    }));

    if (sort === "top") {
      feed = feed.sort((a, b) => {
        const aScore = a.likesCount + a.commentsCount * 2;
        const bScore = b.likesCount + b.commentsCount * 2;
        if (bScore !== aScore) return bScore - aScore;
        return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
      });
    } else if (sort === "trending") {
      feed = feed.sort((a, b) => {
        const aScore = computeTrendingScore(a);
        const bScore = computeTrendingScore(b);
        return bScore - aScore;
      });
    }

    return res.status(200).json({ feed });
  } catch (error) {
    return next(error);
  }
});

router.get("/posts/:id", requireAuth, async (req, res, next) => {
  try {
    // 7. JOIN: posts with users and media
    const [postRows] = await sequelize.query(
      `SELECT p.id, p.caption, p.status, p.created_at,
              u.id AS user_id, u.name AS user_name, u.avatar_url, u.email
       FROM posts p
       JOIN users u ON p.user_id = u.id
       WHERE p.id = ?`,
      { replacements: [req.params.id] }
    );
    if (!postRows || postRows.length === 0) {
      return res.status(404).json({ message: "Post not found." });
    }
    const row = postRows[0];
    // 10. Aggregate functions
    const [[likesRow]] = await sequelize.query(
      "SELECT COUNT(*) AS cnt FROM post_likes WHERE post_id = ?",
      { replacements: [req.params.id] }
    );
    const [[commentsRow]] = await sequelize.query(
      "SELECT COUNT(*) AS cnt FROM post_comments WHERE post_id = ?",
      { replacements: [req.params.id] }
    );
    const [mediaRows] = await sequelize.query(
      "SELECT id, media_url FROM post_media WHERE post_id = ?",
      { replacements: [req.params.id] }
    );
    return res.status(200).json({
      post: {
        id: row.id,
        caption: row.caption,
        status: row.status,
        createdAt: row.created_at,
        user: { id: row.user_id, name: row.user_name, avatarUrl: row.avatar_url, email: row.email },
        PostMedia: mediaRows.map(m => ({ id: m.id, mediaUrl: m.media_url })),
        media: mediaRows.map(m => m.media_url),
        likesCount: Number(likesRow.cnt),
        commentsCount: Number(commentsRow.cnt),
      },
    });
  } catch (error) {
    return next(error);
  }
});

router.delete("/posts/:id", requireAuth, async (req, res, next) => {
  try {
    const [[post]] = await sequelize.query(
      "SELECT id, user_id FROM posts WHERE id = ?",
      { replacements: [req.params.id] }
    );
    if (!post) {
      return res.status(404).json({ message: "Post not found." });
    }
    if (post.user_id !== req.user.id) {
      return res.status(403).json({ message: "Not authorized to delete this post." });
    }
    // 13. Transaction: ensure cascade delete is atomic
    const t = await sequelize.transaction();
    try {
      await sequelize.query("DELETE FROM post_likes WHERE post_id = ?", { replacements: [post.id], transaction: t });
      await sequelize.query("DELETE FROM post_comments WHERE post_id = ?", { replacements: [post.id], transaction: t });
      await sequelize.query("DELETE FROM post_media WHERE post_id = ?", { replacements: [post.id], transaction: t });
      await sequelize.query("DELETE FROM posts WHERE id = ?", { replacements: [post.id], transaction: t });
      await t.commit();
    } catch (e) {
      await t.rollback();
      throw e;
    }
    return res.status(200).json({ message: "Post deleted successfully." });
  } catch (error) {
    return next(error);
  }
});

router.get("/posts/:id/likes", requireAuth, async (req, res, next) => {
  try {
    // 7. JOIN: get users who liked the post
    const [users] = await sequelize.query(
      `SELECT u.id, u.name, u.avatar_url AS avatarUrl, u.email
       FROM post_likes pl
       JOIN users u ON pl.user_id = u.id
       WHERE pl.post_id = ?`,
      { replacements: [req.params.id] }
    );
    return res.status(200).json({ users });
  } catch (error) {
    return next(error);
  }
});

router.post("/posts/:id/likes", requireAuth, async (req, res, next) => {
  try {
    const [[post]] = await sequelize.query(
      "SELECT id, user_id FROM posts WHERE id = ?",
      { replacements: [req.params.id] }
    );
    if (!post) return res.status(404).json({ message: "Post not found." });
    // INSERT IGNORE = idempotent like
    await sequelize.query(
      "INSERT IGNORE INTO post_likes (post_id, user_id, created_at, updated_at) VALUES (?, ?, NOW(), NOW())",
      { replacements: [req.params.id, req.user.id] }
    );
    if (post.user_id !== req.user.id) {
      await sequelize.query(
        `INSERT INTO notifications (recipient_id, actor_id, notification_type, entity_type, entity_id, created_at, updated_at)
         VALUES (?, ?, 'like', 'post', ?, NOW(), NOW())`,
        { replacements: [post.user_id, req.user.id, post.id] }
      ).catch(() => {});
    }
    return res.status(200).json({ liked: true });
  } catch (error) {
    return next(error);
  }
});

router.delete("/posts/:id/likes", requireAuth, async (req, res, next) => {
  try {
    const [[post]] = await sequelize.query(
      "SELECT id FROM posts WHERE id = ?",
      { replacements: [req.params.id] }
    );
    if (!post) return res.status(404).json({ message: "Post not found." });
    await sequelize.query(
      "DELETE FROM post_likes WHERE post_id = ? AND user_id = ?",
      { replacements: [req.params.id, req.user.id] }
    );
    return res.status(200).json({ liked: false });
  } catch (error) {
    return next(error);
  }
});

router.post("/posts/:id/comments", requireAuth, async (req, res, next) => {
  try {
    const body = commentSchema.parse(req.body);
    const [[post]] = await sequelize.query(
      "SELECT id, user_id FROM posts WHERE id = ?",
      { replacements: [req.params.id] }
    );
    if (!post) return res.status(404).json({ message: "Post not found." });
    const [commentResult] = await sequelize.query(
      `INSERT INTO post_comments (post_id, user_id, comment_text, created_at, updated_at)
       VALUES (?, ?, ?, NOW(), NOW())`,
      { replacements: [req.params.id, req.user.id, body.commentText] }
    );
    const [commentRows] = await sequelize.query(
      "SELECT * FROM post_comments WHERE id = ?",
      { replacements: [commentResult] }
    );
    const comment = commentRows[0];
    if (post.user_id !== req.user.id) {
      await sequelize.query(
        `INSERT INTO notifications (recipient_id, actor_id, notification_type, entity_type, entity_id, created_at, updated_at)
         VALUES (?, ?, 'comment', 'post', ?, NOW(), NOW())`,
        { replacements: [post.user_id, req.user.id, post.id] }
      ).catch(() => {});
    }
    return res.status(201).json({ comment });
  } catch (error) {
    if (error.name === "ZodError") {
      return res.status(400).json({ message: "Invalid comment payload." });
    }
    return next(error);
  }
});

router.get("/posts/:id/comments", requireAuth, async (req, res, next) => {
  try {
    // 7. JOIN: comments with user info — map to match original Sequelize response shape
    const [rawComments] = await sequelize.query(
      `SELECT pc.id, pc.post_id AS postId, pc.comment_text AS commentText, pc.created_at AS createdAt, pc.updated_at AS updatedAt,
              u.id AS userId, u.name AS userName, u.avatar_url AS userAvatar
       FROM post_comments pc
       JOIN users u ON pc.user_id = u.id
       WHERE pc.post_id = ?
       ORDER BY pc.created_at DESC`,
      { replacements: [req.params.id] }
    );
    const comments = rawComments.map(c => ({
      id: c.id,
      postId: c.postId,
      commentText: c.commentText,
      createdAt: c.createdAt,
      updatedAt: c.updatedAt,
      User: { id: c.userId, name: c.userName, avatarUrl: c.userAvatar },
    }));
    return res.status(200).json({ comments });
  } catch (error) {
    return next(error);
  }
});

router.get("/users/:id", requireAuth, async (req, res, next) => {
  try {
    const [[user]] = await sequelize.query(
      "SELECT * FROM users WHERE id = ?",
      { replacements: [req.params.id] }
    );
    if (!user) return res.status(404).json({ message: "User not found." });
    // 10. Aggregate scalar functions (COUNT)
    const [[postsRow]] = await sequelize.query("SELECT COUNT(*) AS cnt FROM posts WHERE user_id = ?", { replacements: [user.id] });
    const [[followersRow]] = await sequelize.query("SELECT COUNT(*) AS cnt FROM follows WHERE followed_id = ?", { replacements: [user.id] });
    const [[followingRow]] = await sequelize.query("SELECT COUNT(*) AS cnt FROM follows WHERE follower_id = ?", { replacements: [user.id] });
    return res.status(200).json({
      user: toUserDto(user),
      stats: {
        postsCount: Number(postsRow.cnt),
        followersCount: Number(followersRow.cnt),
        followingCount: Number(followingRow.cnt),
      },
    });
  } catch (error) {
    return next(error);
  }
});

router.get("/users/:id/posts", requireAuth, async (req, res, next) => {
  try {
    // 7. JOIN: user posts with media — map to match original Sequelize response shape
    const [rawPosts] = await sequelize.query(
      `SELECT p.id, p.caption, p.status, p.created_at AS createdAt, p.updated_at AS updatedAt,
              pm.media_url AS mediaUrl
       FROM posts p
       LEFT JOIN post_media pm ON p.id = pm.post_id
       WHERE p.user_id = ?
       ORDER BY p.created_at DESC`,
      { replacements: [req.params.id] }
    );
    // Group media per post (a post could have multiple media)
    const postMap = new Map();
    for (const row of rawPosts) {
      if (!postMap.has(row.id)) {
        postMap.set(row.id, { id: row.id, caption: row.caption, status: row.status, createdAt: row.createdAt, PostMedia: [] });
      }
      if (row.mediaUrl) postMap.get(row.id).PostMedia.push({ mediaUrl: row.mediaUrl });
    }
    const posts = Array.from(postMap.values());
    return res.status(200).json({ posts });
  } catch (error) {
    return next(error);
  }
});

router.patch("/me", requireAuth, async (req, res, next) => {
  try {
    const body = updateProfileSchema.parse(req.body);
    if (body.name !== undefined) req.user.name = body.name;
    if (body.bio !== undefined) req.user.bio = body.bio;
    if (body.pronouns !== undefined) req.user.pronouns = body.pronouns;
    
    if (body.avatarUrl !== undefined && body.avatarUrl !== null) {
      let mediaUrl = body.avatarUrl;
      // Check if mediaUrl is base64 data
      if (mediaUrl.startsWith('data:image/')) {
        try {
          // Extract base64 data
          const matches = mediaUrl.match(/^data:image\/(\w+);base64,(.+)$/);
          if (matches) {
            const extension = matches[1];
            const base64Data = matches[2];
            const buffer = Buffer.from(base64Data, 'base64');
            
            // Generate unique filename
            const filename = `avatar-${Date.now()}-${req.user.id}.${extension}`;
            const filepath = path.join(uploadsDir, filename);
            
            // Save file
            fs.writeFileSync(filepath, buffer);
            
            // Get the host from the request to build the URL
            const protocol = req.protocol;
            const host = req.get('host');
            mediaUrl = `${protocol}://${host}/uploads/${filename}`;
            console.log('Saved uploaded avatar:', mediaUrl);
          }
        } catch (err) {
          console.error('Error processing base64 image:', err);
          return res.status(400).json({ message: "Invalid image data." });
        }
      }
      req.user.avatarUrl = mediaUrl;
    } else if (body.avatarUrl === null) {
      req.user.avatarUrl = null;
    }
    
    await req.user.save();
    return res.status(200).json({ user: toUserDto(req.user) });
  } catch (error) {
    if (error.name === "ZodError") {
      return res.status(400).json({ message: "Invalid profile payload." });
    }
    return next(error);
  }
});

router.get("/leaderboard/current", requireAuth, async (_req, res, next) => {
  try {
    const current = await getOrCreateActiveWeek();
    const [rawEntries] = await sequelize.query(
      `SELECT ws.id, ws.points, ws.rank_snapshot AS rankSnapshot,
              u.id AS userId, u.name, u.avatar_url AS avatarUrl
       FROM weekly_scores ws
       JOIN users u ON ws.user_id = u.id
       WHERE ws.week_id = ?
       ORDER BY ws.points DESC
       LIMIT 100`,
      { replacements: [current.id] }
    );
    for (let i = 0; i < rawEntries.length; i += 1) {
      if (rawEntries[i].rankSnapshot !== i + 1) {
        await sequelize.query(
          "UPDATE weekly_scores SET rank_snapshot = ? WHERE id = ?",
          { replacements: [i + 1, rawEntries[i].id] }
        );
        rawEntries[i].rankSnapshot = i + 1;
      }
    }
    const entries = rawEntries.map(e => ({ id: e.id, points: e.points, rankSnapshot: e.rankSnapshot, User: { id: e.userId, name: e.name, avatarUrl: e.avatarUrl } }));
    return res.status(200).json({ week: current, entries });
  } catch (error) {
    return next(error);
  }
});

router.get("/leaderboard/week/:id", requireAuth, async (req, res, next) => {
  try {
    const [[week]] = await sequelize.query(
      "SELECT * FROM leaderboard_weeks WHERE id = ?",
      { replacements: [req.params.id] }
    );
    if (!week) return res.status(404).json({ message: "Week not found." });
    // 9. Subquery: rank users by score within a given week
    const [rawEntries] = await sequelize.query(
      `SELECT ws.id, ws.points, ws.rank_snapshot AS rankSnapshot,
              u.id AS userId, u.name, u.avatar_url AS avatarUrl
       FROM weekly_scores ws
       JOIN users u ON ws.user_id = u.id
       WHERE ws.week_id = (SELECT id FROM leaderboard_weeks WHERE id = ?)
       ORDER BY ws.points DESC`,
      { replacements: [req.params.id] }
    );
    const entries = rawEntries.map(e => ({ id: e.id, points: e.points, rankSnapshot: e.rankSnapshot, User: { id: e.userId, name: e.name, avatarUrl: e.avatarUrl } }));
    return res.status(200).json({ week, entries });
  } catch (error) {
    return next(error);
  }
});

router.get("/me/weekly-wrapup", requireAuth, async (req, res, next) => {
  try {
    const current = await getOrCreateActiveWeek();
    // 8. GROUP BY & HAVING: get score and total events for current user this week
    const [[scoreRow]] = await sequelize.query(
      `SELECT ws.points, ws.rank_snapshot,
              COUNT(se.id) AS event_count
       FROM weekly_scores ws
       LEFT JOIN score_events se ON se.week_id = ws.week_id AND se.user_id = ws.user_id
       WHERE ws.week_id = ? AND ws.user_id = ?
       GROUP BY ws.id, ws.points, ws.rank_snapshot
       HAVING event_count >= 0`,
      { replacements: [current.id, req.user.id] }
    );
    return res.status(200).json({ summary: { week: current, score: scoreRow || null } });
  } catch (error) {
    return next(error);
  }
});

router.get("/me/badges", requireAuth, async (req, res, next) => {
  try {
    // 7. JOIN: user badges with badge details
    const [badges] = await sequelize.query(
      `SELECT ub.id, ub.awarded_at, b.id AS badge_id, b.slug, b.badge_name, b.description, b.icon_url
       FROM user_badges ub
       JOIN badges b ON ub.badge_id = b.id
       WHERE ub.user_id = ?
       ORDER BY ub.awarded_at DESC`,
      { replacements: [req.user.id] }
    );
    return res.status(200).json({ badges });
  } catch (error) {
    return next(error);
  }
});

router.get("/me/strikes", requireAuth, async (req, res, next) => {
  try {
    const [strikes] = await sequelize.query(
      "SELECT * FROM user_strikes WHERE user_id = ? ORDER BY issued_at DESC",
      { replacements: [req.user.id] }
    );
    const [[activeEnforcement]] = await sequelize.query(
      "SELECT * FROM user_enforcements WHERE user_id = ? AND is_active = 1 ORDER BY active_from DESC LIMIT 1",
      { replacements: [req.user.id] }
    );
    return res.status(200).json({ strikes, activeEnforcement: activeEnforcement || null });
  } catch (error) {
    return next(error);
  }
});

router.post("/appeals", requireAuth, async (req, res, next) => {
  try {
    const body = appealSchema.parse(req.body);
    const [[moderationCase]] = await sequelize.query(
      "SELECT id, user_id FROM moderation_cases WHERE id = ?",
      { replacements: [body.caseId] }
    );
    if (!moderationCase || moderationCase.user_id !== req.user.id) {
      return res.status(404).json({ message: "Moderation case not found." });
    }
    // 13. Transaction: atomically create appeal
    const t = await sequelize.transaction();
    try {
      const [appealId] = await sequelize.query(
        `INSERT INTO appeals (case_id, user_id, appeal_message, status, created_at, updated_at)
         VALUES (?, ?, ?, 'pending', NOW(), NOW())`,
        { replacements: [body.caseId, req.user.id, body.appealMessage], transaction: t }
      );
      await t.commit();
      const [[appeal]] = await sequelize.query(
        "SELECT * FROM appeals WHERE id = ?",
        { replacements: [appealId] }
      );
      return res.status(201).json({ appeal });
    } catch (e) {
      await t.rollback();
      throw e;
    }
  } catch (error) {
    if (error.name === "ZodError") {
      return res.status(400).json({ message: "Invalid appeal payload." });
    }
    return next(error);
  }
});

router.get("/appeals/:id", requireAuth, async (req, res, next) => {
  try {
    const [[appeal]] = await sequelize.query(
      "SELECT * FROM appeals WHERE id = ?",
      { replacements: [req.params.id] }
    );
    if (!appeal || appeal.user_id !== req.user.id) {
      return res.status(404).json({ message: "Appeal not found." });
    }
    return res.status(200).json({ appeal });
  } catch (error) {
    return next(error);
  }
});

router.post("/users/:id/follow", requireAuth, async (req, res, next) => {
  try {
    const targetId = Number(req.params.id);
    if (targetId === req.user.id) {
      return res.status(400).json({ message: "Cannot follow yourself." });
    }
    await sequelize.query(
      "INSERT IGNORE INTO follows (follower_id, followed_id, created_at, updated_at) VALUES (?, ?, NOW(), NOW())",
      { replacements: [req.user.id, targetId] }
    );
    return res.status(200).json({ following: true });
  } catch (error) {
    return next(error);
  }
});

router.delete("/users/:id/follow", requireAuth, async (req, res, next) => {
  try {
    await sequelize.query(
      "DELETE FROM follows WHERE follower_id = ? AND followed_id = ?",
      { replacements: [req.user.id, req.params.id] }
    );
    return res.status(200).json({ following: false });
  } catch (error) {
    return next(error);
  }
});

router.get("/users/:id/following", requireAuth, async (req, res, next) => {
  try {
    // 7. JOIN: following list with user info — map avatarUrl for Android
    const [rawFollowing] = await sequelize.query(
      `SELECT u.id, u.name, u.avatar_url AS avatarUrl, u.email
       FROM follows f
       JOIN users u ON f.followed_id = u.id
       WHERE f.follower_id = ?`,
      { replacements: [req.params.id] }
    );
    return res.status(200).json({ following: rawFollowing });
  } catch (error) {
    return next(error);
  }
});

router.get("/users/:id/followers", requireAuth, async (req, res, next) => {
  try {
    // 7. JOIN: followers list with user info — map avatarUrl for Android
    const [rawFollowers] = await sequelize.query(
      `SELECT u.id, u.name, u.avatar_url AS avatarUrl
       FROM follows f
       JOIN users u ON f.follower_id = u.id
       WHERE f.followed_id = ?`,
      { replacements: [req.params.id] }
    );
    return res.status(200).json({ followers: rawFollowers });
  } catch (error) {
    return next(error);
  }
});

router.get("/conversations", requireAuth, async (req, res, next) => {
  try {
    // Raw SQL: find all direct conversations the user is part of, with other participant info
    const [conversations] = await sequelize.query(
      `SELECT
         c.id, c.conversation_type AS conversationType, c.created_at AS createdAt, c.updated_at AS updatedAt,
         u.id AS other_user_id, u.name AS other_user_name, u.avatar_url AS other_user_avatar
       FROM conversation_participants cp
       JOIN conversations c ON cp.conversation_id = c.id
       JOIN conversation_participants cp2 ON c.id = cp2.conversation_id AND cp2.user_id != cp.user_id
       JOIN users u ON cp2.user_id = u.id
       WHERE cp.user_id = ?
       ORDER BY c.updated_at DESC`,
      { replacements: [req.user.id] }
    );
    const result = conversations.map((row) => ({
      id: row.id,
      conversationType: row.conversationType,
      createdAt: row.createdAt,
      updatedAt: row.updatedAt,
      otherUser: row.other_user_id ? {
        id: row.other_user_id,
        name: row.other_user_name,
        avatarUrl: row.other_user_avatar,
      } : null,
    }));
    return res.status(200).json({ conversations: result });
  } catch (error) {
    return next(error);
  }
});

router.post("/conversations/direct", requireAuth, async (req, res, next) => {
  try {
    const { targetUserId } = directConversationSchema.parse(req.body);
    console.log(`Checking/Creating direct conversation: ${req.user.id} -> ${targetUserId}`);
    if (targetUserId === req.user.id) {
      return res.status(400).json({ message: "Cannot create conversation with yourself." });
    }

    const [[existing]] = await sequelize.query(
      `SELECT cp1.conversation_id AS conversationId
       FROM conversation_participants cp1
       INNER JOIN conversation_participants cp2 ON cp1.conversation_id = cp2.conversation_id
       INNER JOIN conversations c ON cp1.conversation_id = c.id
       WHERE cp1.user_id = ? 
         AND cp2.user_id = ? 
         AND c.conversation_type = 'direct'
       LIMIT 1`,
      { replacements: [req.user.id, targetUserId] }
    );

    if (existing) {
      return res.status(200).json({ conversationId: existing.conversationId });
    }

    // 13. Transaction: create conversation and both participants atomically
    const t = await sequelize.transaction();
    try {
      const [convId] = await sequelize.query(
        `INSERT INTO conversations (conversation_type, created_by, created_at, updated_at) VALUES ('direct', ?, NOW(), NOW())`,
        { replacements: [req.user.id], transaction: t }
      );
      await sequelize.query(
        `INSERT INTO conversation_participants (conversation_id, user_id, created_at, updated_at) VALUES (?, ?, NOW(), NOW()), (?, ?, NOW(), NOW())`,
        { replacements: [convId, req.user.id, convId, targetUserId], transaction: t }
      );
      await t.commit();
      return res.status(201).json({ conversationId: convId });
    } catch (e) {
      await t.rollback();
      throw e;
    }
  } catch (error) {
    if (error.name === "ZodError") {
      return res.status(400).json({ message: "Invalid conversation payload." });
    }
    return next(error);
  }
});

router.get("/conversations/:id/messages", requireAuth, async (req, res, next) => {
  try {
    const [[membership]] = await sequelize.query(
      "SELECT id FROM conversation_participants WHERE conversation_id = ? AND user_id = ?",
      { replacements: [req.params.id, req.user.id] }
    );
    if (!membership) return res.status(403).json({ message: "Not a participant in this conversation." });

    const cursor = req.query.cursor ? Number(req.query.cursor) : null;
    let sql = `SELECT m.id, m.conversation_id AS conversationId, m.body, m.media_url AS mediaUrl,
                      m.created_at AS createdAt,
                      u.id AS senderId, u.name AS senderName, u.avatar_url AS senderAvatar
               FROM messages m
               JOIN users u ON m.sender_id = u.id
               WHERE m.conversation_id = ?`;
    const replacements = [req.params.id];
    if (cursor) {
      sql += " AND m.id < ?";
      replacements.push(cursor);
    }
    sql += " ORDER BY m.id DESC LIMIT 30";
    const [rawMessages] = await sequelize.query(sql, { replacements });
    const messages = rawMessages.map(m => ({
      id: m.id,
      conversationId: m.conversationId,
      body: m.body,
      mediaUrl: m.mediaUrl,
      createdAt: m.createdAt,
      User: { id: m.senderId, name: m.senderName, avatarUrl: m.senderAvatar },
    }));
    return res.status(200).json({ messages });
  } catch (error) {
    return next(error);
  }
});

router.post("/conversations/:id/messages", requireAuth, async (req, res, next) => {
  try {
    const body = messageSchema.parse(req.body);
    if (!body.body && !body.mediaUrl) {
      return res.status(400).json({ message: "Message content required." });
    }
    const [[membership]] = await sequelize.query(
      "SELECT id FROM conversation_participants WHERE conversation_id = ? AND user_id = ?",
      { replacements: [req.params.id, req.user.id] }
    );
    if (!membership) return res.status(403).json({ message: "Not a participant in this conversation." });
    // 13. Transaction: send message atomically
    const t = await sequelize.transaction();
    try {
      const [msgId] = await sequelize.query(
        `INSERT INTO messages (conversation_id, sender_id, body, media_url, created_at, updated_at)
         VALUES (?, ?, ?, ?, NOW(), NOW())`,
        { replacements: [req.params.id, req.user.id, body.body || null, body.mediaUrl || null], transaction: t }
      );
      await t.commit();
      const [[message]] = await sequelize.query(
        "SELECT * FROM messages WHERE id = ?",
        { replacements: [msgId] }
      );
      return res.status(201).json({ message });
    } catch (e) {
      await t.rollback();
      throw e;
    }
  } catch (error) {
    if (error.name === "ZodError") {
      return res.status(400).json({ message: "Invalid message payload." });
    }
    return next(error);
  }
});

router.get("/notifications", requireAuth, async (req, res, next) => {
  try {
    const cursor = req.query.cursor ? Number(req.query.cursor) : null;
    let sql = `SELECT n.id, n.notification_type AS notificationType, n.entity_type AS entityType,
                      n.entity_id AS entityId, n.read_at AS readAt, n.created_at AS createdAt,
                      u.id AS actorId, u.name AS actorName, u.avatar_url AS actorAvatar
               FROM notifications n
               LEFT JOIN users u ON n.actor_id = u.id
               WHERE n.recipient_id = ?`;
    const replacements = [req.user.id];
    if (cursor) {
      sql += " AND n.id < ?";
      replacements.push(cursor);
    }
    sql += " ORDER BY n.id DESC LIMIT 30";
    const [rawNotifs] = await sequelize.query(sql, { replacements });
    const notifications = rawNotifs.map(n => ({
      id: n.id,
      notificationType: n.notificationType,
      entityType: n.entityType,
      entityId: n.entityId,
      readAt: n.readAt,
      createdAt: n.createdAt,
      ActorUser: n.actorId ? { id: n.actorId, name: n.actorName, avatarUrl: n.actorAvatar } : null,
    }));
    return res.status(200).json({ notifications });
  } catch (error) {
    return next(error);
  }
});

router.post("/notifications/:id/read", requireAuth, async (req, res, next) => {
  try {
    const [[notification]] = await sequelize.query(
      "SELECT id, recipient_id FROM notifications WHERE id = ?",
      { replacements: [req.params.id] }
    );
    if (!notification || notification.recipient_id !== req.user.id) {
      return res.status(404).json({ message: "Notification not found." });
    }
    await sequelize.query(
      "UPDATE notifications SET read_at = NOW() WHERE id = ?",
      { replacements: [req.params.id] }
    );
    return res.status(200).json({ read: true });
  } catch (error) {
    return next(error);
  }
});

router.post("/notifications/read-all", requireAuth, async (req, res, next) => {
  try {
    await sequelize.query(
      "UPDATE notifications SET read_at = NOW() WHERE recipient_id = ? AND read_at IS NULL",
      { replacements: [req.user.id] }
    );
    return res.status(200).json({ readAll: true });
  } catch (error) {
    return next(error);
  }
});

router.get("/me/notification-prefs", requireAuth, async (req, res, next) => {
  try {
    const [[prefs]] = await sequelize.query(
      "SELECT * FROM user_notification_prefs WHERE user_id = ?",
      { replacements: [req.user.id] }
    );
    if (!prefs) {
      await sequelize.query(
        `INSERT INTO user_notification_prefs (user_id, created_at, updated_at) VALUES (?, NOW(), NOW())`,
        { replacements: [req.user.id] }
      );
      const [[newPrefs]] = await sequelize.query(
        "SELECT * FROM user_notification_prefs WHERE user_id = ?",
        { replacements: [req.user.id] }
      );
      return res.status(200).json({ prefs: newPrefs });
    }
    return res.status(200).json({ prefs });
  } catch (error) {
    return next(error);
  }
});

module.exports = router;
