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
    firebaseUid: user.firebaseUid,
    email: user.email,
    name: user.name,
    avatarUrl: user.avatarUrl,
    bio: user.bio,
    pronouns: user.pronouns,
    provider: user.provider,
    createdAt: user.createdAt,
    updatedAt: user.updatedAt,
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
          
          // Get the host from the request to build the URL
          const protocol = req.protocol;
          const host = req.get('host');
          mediaUrl = `${protocol}://${host}/uploads/${filename}`;
          console.log('Saved uploaded image:', mediaUrl);
        }
      } catch (err) {
        console.error('Error processing base64 image:', err);
        return res.status(400).json({ message: "Invalid image data." });
      }
    }
    
    const post = await Post.create({
      userId: req.user.id,
      promptId: body.promptId || null,
      caption: body.caption,
      status: "published",
    });
    await PostMedia.create({
      postId: post.id,
      mediaUrl: mediaUrl,
    });
    await addScoreForUser({
      userId: req.user.id,
      pointsDelta: 10,
      source: "submission_reward",
      refType: "post",
      refId: post.id,
    });
    // Notify followers about new post
    try {
      const followers = await Follow.findAll({ where: { followedId: req.user.id } });
      for (const follow of followers) {
        await Notification.create({
          recipientId: follow.followerId,
          actorId: req.user.id,
          notificationType: "new_post",
          entityType: "post",
          entityId: post.id,
        }).catch(() => {});
      }
    } catch (_) {}
    return res.status(201).json({ postId: post.id });
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
    const orderBy = [["createdAt", "DESC"]];
    const posts = await Post.findAll({
      include: [
        { model: User, attributes: ["id", "name", "avatarUrl"] },
        { model: PostMedia, attributes: ["id", "mediaUrl"] },
        { model: PostComment, attributes: ["id"] },
      ],
      order: orderBy,
      limit: 50,
    });

    const postIds = posts.map((post) => post.id);
    if (postIds.length === 0) {
      return res.status(200).json({ feed: [] });
    }
    const likes = await PostLike.findAll({ where: { postId: { [Op.in]: postIds } } });
    const likeCountMap = new Map();
    for (const like of likes) {
      likeCountMap.set(like.postId, (likeCountMap.get(like.postId) || 0) + 1);
    }

    const likedByMe = new Set(likes.filter(l => l.userId === req.user.id).map(l => l.postId));

    let feed = posts.map((post) => ({
      id: post.id,
      caption: post.caption,
      status: post.status,
      createdAt: post.createdAt,
      user: post.User,
      media: post.PostMedia.map((item) => item.mediaUrl),
      likesCount: likeCountMap.get(post.id) || 0,
      commentsCount: post.PostComments.length,
      hasLiked: likedByMe.has(post.id),
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
    const post = await Post.findByPk(req.params.id, {
      include: [
        { model: User, attributes: ["id", "name", "avatarUrl", "email"] },
        { model: PostMedia, attributes: ["id", "mediaUrl"] },
        { model: PostComment, attributes: ["id"] },
      ],
    });
    if (!post) {
      return res.status(404).json({ message: "Post not found." });
    }
    const likesCount = await PostLike.count({ where: { postId: post.id } });
    return res.status(200).json({
      post: {
        id: post.id,
        caption: post.caption,
        status: post.status,
        createdAt: post.createdAt,
        user: post.User,           // lowercase so Android UserDto.user maps correctly
        PostMedia: post.PostMedia, // Android reads PostMedia for single-post
        media: post.PostMedia.map((m) => m.mediaUrl), // also provide flat list
        likesCount,
        commentsCount: post.PostComments ? post.PostComments.length : 0,
      },
    });
  } catch (error) {
    return next(error);
  }
});

router.delete("/posts/:id", requireAuth, async (req, res, next) => {
  try {
    const post = await Post.findByPk(req.params.id);
    if (!post) {
      return res.status(404).json({ message: "Post not found." });
    }
    if (post.userId !== req.user.id) {
      return res.status(403).json({ message: "Not authorized to delete this post." });
    }
    // Delete the post. The database cascades deletions for PostMedia, PostLike, etc.
    await post.destroy();
    return res.status(200).json({ message: "Post deleted successfully." });
  } catch (error) {
    return next(error);
  }
});

router.get("/posts/:id/likes", requireAuth, async (req, res, next) => {
  try {
    // Get user IDs who liked the post, then look up user info directly
    const likes = await PostLike.findAll({ where: { postId: req.params.id } });
    const userIds = likes.map((l) => l.userId);
    if (userIds.length === 0) return res.status(200).json({ users: [] });
    const users = await User.findAll({
      where: { id: userIds },
      attributes: ["id", "name", "avatarUrl", "email"],
    });
    return res.status(200).json({ users });
  } catch (error) {
    return next(error);
  }
});

router.post("/posts/:id/likes", requireAuth, async (req, res, next) => {
  try {
    const post = await Post.findByPk(req.params.id);
    if (!post) {
      return res.status(404).json({ message: "Post not found." });
    }
    const [, created] = await PostLike.findOrCreate({ where: { postId: req.params.id, userId: req.user.id } });
    // Notify post owner (not self-like)
    if (created && post.userId !== req.user.id) {
      await Notification.create({
        recipientId: post.userId,
        actorId: req.user.id,
        notificationType: "like",
        entityType: "post",
        entityId: post.id,
      }).catch(() => {}); // non-fatal
    }
    return res.status(200).json({ liked: true });
  } catch (error) {
    return next(error);
  }
});

router.delete("/posts/:id/likes", requireAuth, async (req, res, next) => {
  try {
    const post = await Post.findByPk(req.params.id);
    if (!post) {
      return res.status(404).json({ message: "Post not found." });
    }
    await PostLike.destroy({ where: { postId: req.params.id, userId: req.user.id } });
    return res.status(200).json({ liked: false });
  } catch (error) {
    return next(error);
  }
});

router.post("/posts/:id/comments", requireAuth, async (req, res, next) => {
  try {
    const body = commentSchema.parse(req.body);
    const post = await Post.findByPk(req.params.id);
    if (!post) {
      return res.status(404).json({ message: "Post not found." });
    }
    const comment = await PostComment.create({
      postId: req.params.id,
      userId: req.user.id,
      commentText: body.commentText,
    });
    // Notify post owner (not self-comment)
    if (post.userId !== req.user.id) {
      await Notification.create({
        recipientId: post.userId,
        actorId: req.user.id,
        notificationType: "comment",
        entityType: "post",
        entityId: post.id,
      }).catch(() => {}); // non-fatal
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
    const comments = await PostComment.findAll({
      where: { postId: req.params.id },
      include: [{ model: User, attributes: ["id", "name", "avatarUrl"] }],
      order: [["createdAt", "DESC"]],
    });
    return res.status(200).json({ comments });
  } catch (error) {
    return next(error);
  }
});

router.get("/users/:id", requireAuth, async (req, res, next) => {
  try {
    const user = await User.findByPk(req.params.id);
    if (!user) {
      return res.status(404).json({ message: "User not found." });
    }
    const postsCount = await Post.count({ where: { userId: user.id } });
    const followersCount = await Follow.count({ where: { followedId: user.id } });
    const followingCount = await Follow.count({ where: { followerId: user.id } });
    return res.status(200).json({ user: toUserDto(user), stats: { postsCount, followersCount, followingCount } });
  } catch (error) {
    return next(error);
  }
});

router.get("/users/:id/posts", requireAuth, async (req, res, next) => {
  try {
    const posts = await Post.findAll({
      where: { userId: req.params.id },
      include: [{ model: PostMedia, attributes: ["mediaUrl"] }],
      order: [["createdAt", "DESC"]],
    });
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
    const entries = await WeeklyScore.findAll({
      where: { weekId: current.id },
      include: [{ model: User, attributes: ["id", "name", "avatarUrl"] }],
      order: [["points", "DESC"]],
      limit: 100,
    });
    for (let i = 0; i < entries.length; i += 1) {
      if (entries[i].rankSnapshot !== i + 1) {
        entries[i].rankSnapshot = i + 1;
        await entries[i].save();
      }
    }
    return res.status(200).json({ week: current, entries });
  } catch (error) {
    return next(error);
  }
});

router.get("/leaderboard/week/:id", requireAuth, async (req, res, next) => {
  try {
    const week = await LeaderboardWeek.findByPk(req.params.id);
    if (!week) {
      return res.status(404).json({ message: "Week not found." });
    }
    const entries = await WeeklyScore.findAll({
      where: { weekId: week.id },
      include: [{ model: User, attributes: ["id", "name", "avatarUrl"] }],
      order: [["points", "DESC"]],
    });
    return res.status(200).json({ week, entries });
  } catch (error) {
    return next(error);
  }
});

router.get("/me/weekly-wrapup", requireAuth, async (req, res, next) => {
  try {
    const current = await getOrCreateActiveWeek();
    const score = await WeeklyScore.findOne({ where: { weekId: current.id, userId: req.user.id } });
    return res.status(200).json({ summary: { week: current, score } });
  } catch (error) {
    return next(error);
  }
});

router.get("/me/badges", requireAuth, async (req, res, next) => {
  try {
    const badges = await UserBadge.findAll({
      where: { userId: req.user.id },
      include: [{ model: Badge }],
      order: [["awardedAt", "DESC"]],
    });
    return res.status(200).json({ badges });
  } catch (error) {
    return next(error);
  }
});

router.get("/me/strikes", requireAuth, async (req, res, next) => {
  try {
    const strikes = await UserStrike.findAll({ where: { userId: req.user.id }, order: [["issuedAt", "DESC"]] });
    const activeEnforcement = await UserEnforcement.findOne({
      where: { userId: req.user.id, isActive: true },
      order: [["activeFrom", "DESC"]],
    });
    return res.status(200).json({ strikes, activeEnforcement });
  } catch (error) {
    return next(error);
  }
});

router.post("/appeals", requireAuth, async (req, res, next) => {
  try {
    const body = appealSchema.parse(req.body);
    const moderationCase = await ModerationCase.findByPk(body.caseId);
    if (!moderationCase || moderationCase.userId !== req.user.id) {
      return res.status(404).json({ message: "Moderation case not found." });
    }
    const appeal = await Appeal.create({
      caseId: body.caseId,
      userId: req.user.id,
      appealMessage: body.appealMessage,
      status: "pending",
    });
    return res.status(201).json({ appeal });
  } catch (error) {
    if (error.name === "ZodError") {
      return res.status(400).json({ message: "Invalid appeal payload." });
    }
    return next(error);
  }
});

router.get("/appeals/:id", requireAuth, async (req, res, next) => {
  try {
    const appeal = await Appeal.findByPk(req.params.id);
    if (!appeal || appeal.userId !== req.user.id) {
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
    await Follow.findOrCreate({ where: { followerId: req.user.id, followedId: targetId } });
    return res.status(200).json({ following: true });
  } catch (error) {
    return next(error);
  }
});

router.delete("/users/:id/follow", requireAuth, async (req, res, next) => {
  try {
    await Follow.destroy({ where: { followerId: req.user.id, followedId: req.params.id } });
    return res.status(200).json({ following: false });
  } catch (error) {
    return next(error);
  }
});

router.get("/users/:id/following", requireAuth, async (req, res, next) => {
  try {
    const rows = await Follow.findAll({
      where: { followerId: req.params.id },
      include: [{ model: User, as: "FollowedUser", attributes: ["id", "name", "avatarUrl", "email"] }],
    });
    return res.status(200).json({ following: rows.map((row) => row.FollowedUser).filter(Boolean) });
  } catch (error) {
    return next(error);
  }
});

router.get("/users/:id/followers", requireAuth, async (req, res, next) => {
  try {
    const rows = await Follow.findAll({
      where: { followedId: req.params.id },
      include: [{ model: User, as: "FollowerUser", attributes: ["id", "name", "avatarUrl"] }],
    });
    return res.status(200).json({ followers: rows.map((row) => row.FollowerUser).filter(Boolean) });
  } catch (error) {
    return next(error);
  }
});

router.get("/conversations", requireAuth, async (req, res, next) => {
  try {
    const memberships = await ConversationParticipant.findAll({
      where: { userId: req.user.id },
      include: [{
        model: Conversation,
        include: [{
          model: ConversationParticipant,
          include: [{ model: User, attributes: ["id", "name", "avatarUrl"] }],
        }],
      }],
      order: [["updatedAt", "DESC"]],
    });
    const conversations = memberships.map((m) => {
      if (!m.Conversation) return null;
      const conv = m.Conversation;
      // Find the OTHER participant (not the current user)
      const participants = (conv.ConversationParticipants || []);
      const other = participants.find((p) => p.userId !== req.user.id);
      return {
        id: conv.id,
        conversationType: conv.conversationType,
        createdAt: conv.createdAt,
        updatedAt: conv.updatedAt,
        otherUser: other ? other.User : null,
      };
    }).filter(Boolean);
    return res.status(200).json({ conversations });
  } catch (error) {
    return next(error);
  }
});

router.post("/conversations/direct", requireAuth, async (req, res, next) => {
  try {
    const { targetUserId } = directConversationSchema.parse(req.body);
    if (targetUserId === req.user.id) {
      return res.status(400).json({ message: "Cannot create conversation with yourself." });
    }

    // Check for existing direct conversation between these two users
    const myParticipations = await ConversationParticipant.findAll({
      where: { userId: req.user.id },
      attributes: ['conversationId']
    });
    const myConvIds = myParticipations.map(p => p.conversationId);

    const existing = await ConversationParticipant.findOne({
      where: {
        userId: targetUserId,
        conversationId: myConvIds
      },
      include: [{
        model: Conversation,
        where: { conversationType: "direct" }
      }]
    });

    if (existing) {
      return res.status(200).json({ conversationId: existing.conversationId });
    }

    const conversation = await Conversation.create({
      conversationType: "direct",
      createdBy: req.user.id,
    });
    await ConversationParticipant.bulkCreate([
      { conversationId: conversation.id, userId: req.user.id },
      { conversationId: conversation.id, userId: targetUserId },
    ]);
    return res.status(201).json({ conversationId: conversation.id });
  } catch (error) {
    if (error.name === "ZodError") {
      return res.status(400).json({ message: "Invalid conversation payload." });
    }
    return next(error);
  }
});

router.get("/conversations/:id/messages", requireAuth, async (req, res, next) => {
  try {
    const membership = await ConversationParticipant.findOne({
      where: { conversationId: req.params.id, userId: req.user.id },
    });
    if (!membership) {
      return res.status(403).json({ message: "Not a participant in this conversation." });
    }
    const cursor = req.query.cursor ? Number(req.query.cursor) : null;
    const where = { conversationId: req.params.id };
    if (cursor) {
      where.id = { [Op.lt]: cursor };
    }
    const messages = await Message.findAll({
      where,
      include: [{ model: User, attributes: ["id", "name", "avatarUrl"] }],
      order: [["id", "DESC"]],
      limit: 30,
    });
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
    const membership = await ConversationParticipant.findOne({
      where: { conversationId: req.params.id, userId: req.user.id },
    });
    if (!membership) {
      return res.status(403).json({ message: "Not a participant in this conversation." });
    }
    const message = await Message.create({
      conversationId: req.params.id,
      senderId: req.user.id,
      body: body.body || null,
      mediaUrl: body.mediaUrl || null,
    });
    return res.status(201).json({ message });
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
    const where = {
      [Op.or]: [
        { recipientId: req.user.id },
        { actorId: req.user.id }
      ]
    };
    if (cursor) {
      where.id = { [Op.lt]: cursor };
    }
    const notifications = await Notification.findAll({
      where,
      include: [
        { model: User, as: "ActorUser", attributes: ["id", "name", "avatarUrl"] },
        { model: User, as: "Recipient", attributes: ["id", "name", "avatarUrl"] }
      ],
      order: [["id", "DESC"]],
      limit: 30,
    });
    return res.status(200).json({ notifications });
  } catch (error) {
    return next(error);
  }
});

router.post("/notifications/:id/read", requireAuth, async (req, res, next) => {
  try {
    const notification = await Notification.findByPk(req.params.id);
    if (!notification || notification.recipientId !== req.user.id) {
      return res.status(404).json({ message: "Notification not found." });
    }
    notification.readAt = new Date();
    await notification.save();
    return res.status(200).json({ read: true });
  } catch (error) {
    return next(error);
  }
});

router.post("/notifications/read-all", requireAuth, async (req, res, next) => {
  try {
    await Notification.update({ readAt: new Date() }, { where: { recipientId: req.user.id, readAt: null } });
    return res.status(200).json({ readAll: true });
  } catch (error) {
    return next(error);
  }
});

router.get("/me/notification-prefs", requireAuth, async (req, res, next) => {
  try {
    const [prefs] = await UserNotificationPref.findOrCreate({ where: { userId: req.user.id } });
    return res.status(200).json({ prefs });
  } catch (error) {
    return next(error);
  }
});

module.exports = router;
