const sequelize = require("../config/database");
const User = require("./user");
const { DataTypes } = require("sequelize");

const DailyPrompt = sequelize.define(
  "DailyPrompt",
  {
    challengeDate: { type: DataTypes.DATEONLY, allowNull: false, unique: true, field: "challenge_date" },
    title: { type: DataTypes.STRING(160), allowNull: false },
    description: { type: DataTypes.TEXT, allowNull: true },
    isActive: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: true, field: "is_active" },
  },
  { tableName: "daily_prompts", underscored: true }
);

const Post = sequelize.define(
  "Post",
  {
    userId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "user_id" },
    promptId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: true, field: "prompt_id" },
    caption: { type: DataTypes.TEXT, allowNull: true },
    status: { type: DataTypes.STRING(32), allowNull: false, defaultValue: "published" },
    submittedAt: { type: DataTypes.DATE, allowNull: false, defaultValue: DataTypes.NOW, field: "submitted_at" },
  },
  { tableName: "posts", underscored: true }
);

const PostMedia = sequelize.define(
  "PostMedia",
  {
    postId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "post_id" },
    mediaUrl: { type: DataTypes.TEXT, allowNull: false, field: "media_url" },
    storageKey: { type: DataTypes.STRING(255), allowNull: true, field: "storage_key" },
    mimeType: { type: DataTypes.STRING(120), allowNull: true, field: "mime_type" },
    width: { type: DataTypes.INTEGER, allowNull: true },
    height: { type: DataTypes.INTEGER, allowNull: true },
    phash: { type: DataTypes.STRING(255), allowNull: true },
  },
  { tableName: "post_media", underscored: true }
);

const PostLike = sequelize.define(
  "PostLike",
  {
    postId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "post_id" },
    userId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "user_id" },
  },
  { tableName: "post_likes", underscored: true }
);

const PostComment = sequelize.define(
  "PostComment",
  {
    postId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "post_id" },
    userId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "user_id" },
    commentText: { type: DataTypes.TEXT, allowNull: false, field: "comment_text" },
  },
  { tableName: "post_comments", underscored: true }
);

const Follow = sequelize.define(
  "Follow",
  {
    followerId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "follower_id" },
    followedId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "followed_id" },
  },
  { tableName: "follows", timestamps: true, underscored: true }
);
const Conversation = sequelize.define(
  "Conversation",
  {
    conversationType: { type: DataTypes.STRING(32), allowNull: false, defaultValue: "direct", field: "conversation_type" },
    createdBy: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "created_by" },
  },
  { tableName: "conversations", underscored: true }
);
const ConversationParticipant = sequelize.define(
  "ConversationParticipant",
  {
    conversationId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "conversation_id" },
    userId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "user_id" },
    lastReadAt: { type: DataTypes.DATE, allowNull: true, field: "last_read_at" },
  },
  { tableName: "conversation_participants", underscored: true }
);
const Message = sequelize.define(
  "Message",
  {
    conversationId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "conversation_id" },
    senderId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "sender_id" },
    body: { type: DataTypes.TEXT, allowNull: true },
    mediaUrl: { type: DataTypes.TEXT, allowNull: true, field: "media_url" },
  },
  { tableName: "messages", underscored: true }
);

const Notification = sequelize.define(
  "Notification",
  {
    recipientId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "recipient_id" },
    actorId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "actor_id" },
    notificationType: { type: DataTypes.STRING(64), allowNull: false, field: "notification_type" },
    entityType: { type: DataTypes.STRING(64), allowNull: true, field: "entity_type" },
    entityId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: true, field: "entity_id" },
    payloadJson: { type: DataTypes.JSON, allowNull: true, field: "payload_json" },
    readAt: { type: DataTypes.DATE, allowNull: true, field: "read_at" },
  },
  { tableName: "notifications", underscored: true }
);

const UserNotificationPref = sequelize.define(
  "UserNotificationPref",
  {
    userId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "user_id" },
    likesEnabled: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: true, field: "likes_enabled" },
    commentsEnabled: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: true, field: "comments_enabled" },
    followsEnabled: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: true, field: "follows_enabled" },
    messagesEnabled: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: true, field: "messages_enabled" },
    leaderboardEnabled: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: true, field: "leaderboard_enabled" },
    moderationEnabled: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: true, field: "moderation_enabled" },
  },
  { tableName: "user_notification_prefs", underscored: true }
);

const LeaderboardWeek = sequelize.define(
  "LeaderboardWeek",
  {
    weekStart: { type: DataTypes.DATEONLY, allowNull: false, field: "week_start" },
    weekEnd: { type: DataTypes.DATEONLY, allowNull: false, field: "week_end" },
    status: { type: DataTypes.STRING(32), allowNull: false, defaultValue: "active" },
  },
  { tableName: "leaderboard_weeks", underscored: true }
);

const WeeklyScore = sequelize.define(
  "WeeklyScore",
  {
    weekId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "week_id" },
    userId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "user_id" },
    points: { type: DataTypes.INTEGER, allowNull: false, defaultValue: 0 },
    rankSnapshot: { type: DataTypes.INTEGER, allowNull: true, field: "rank_snapshot" },
  },
  { tableName: "weekly_scores", underscored: true }
);

const ScoreEvent = sequelize.define(
  "ScoreEvent",
  {
    userId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "user_id" },
    weekId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: true, field: "week_id" },
    source: { type: DataTypes.STRING(64), allowNull: false },
    pointsDelta: { type: DataTypes.INTEGER, allowNull: false, field: "points_delta" },
    refType: { type: DataTypes.STRING(64), allowNull: true, field: "ref_type" },
    refId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: true, field: "ref_id" },
  },
  { tableName: "score_events", underscored: true }
);

const Badge = sequelize.define(
  "Badge",
  {
    slug: { type: DataTypes.STRING(64), allowNull: false, unique: true },
    badgeName: { type: DataTypes.STRING(120), allowNull: false, field: "badge_name" },
    description: { type: DataTypes.TEXT, allowNull: true },
    iconUrl: { type: DataTypes.TEXT, allowNull: true, field: "icon_url" },
  },
  { tableName: "badges", underscored: true }
);

const UserBadge = sequelize.define(
  "UserBadge",
  {
    userId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "user_id" },
    badgeId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "badge_id" },
    awardedAt: { type: DataTypes.DATE, allowNull: false, defaultValue: DataTypes.NOW, field: "awarded_at" },
  },
  { tableName: "user_badges", underscored: true }
);

const ModerationCase = sequelize.define(
  "ModerationCase",
  {
    userId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "user_id" },
    postId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: true, field: "post_id" },
    reason: { type: DataTypes.STRING(255), allowNull: false },
    evidence: { type: DataTypes.TEXT, allowNull: true },
    caseStatus: { type: DataTypes.STRING(32), allowNull: false, defaultValue: "open", field: "case_status" },
  },
  { tableName: "moderation_cases", underscored: true }
);

const UserStrike = sequelize.define(
  "UserStrike",
  {
    userId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "user_id" },
    caseId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: true, field: "case_id" },
    strikeLevel: { type: DataTypes.INTEGER, allowNull: false, field: "strike_level" },
    reason: { type: DataTypes.STRING(255), allowNull: false },
    issuedAt: { type: DataTypes.DATE, allowNull: false, defaultValue: DataTypes.NOW, field: "issued_at" },
  },
  { tableName: "user_strikes", underscored: true }
);

const Appeal = sequelize.define(
  "Appeal",
  {
    caseId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "case_id" },
    userId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "user_id" },
    appealMessage: { type: DataTypes.TEXT, allowNull: false, field: "appeal_message" },
    status: { type: DataTypes.STRING(32), allowNull: false, defaultValue: "pending" },
    decisionNotes: { type: DataTypes.TEXT, allowNull: true, field: "decision_notes" },
  },
  { tableName: "appeals", underscored: true }
);

const UserEnforcement = sequelize.define(
  "UserEnforcement",
  {
    userId: { type: DataTypes.BIGINT.UNSIGNED, allowNull: false, field: "user_id" },
    enforcementType: { type: DataTypes.STRING(64), allowNull: false, field: "enforcement_type" },
    reason: { type: DataTypes.STRING(255), allowNull: false },
    activeFrom: { type: DataTypes.DATE, allowNull: false, defaultValue: DataTypes.NOW, field: "active_from" },
    activeUntil: { type: DataTypes.DATE, allowNull: true, field: "active_until" },
    isActive: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: true, field: "is_active" },
  },
  { tableName: "user_enforcements", underscored: true }
);

User.hasMany(Post, { foreignKey: "userId" });
Post.belongsTo(User, { foreignKey: "userId" });
DailyPrompt.hasMany(Post, { foreignKey: "promptId" });
Post.belongsTo(DailyPrompt, { foreignKey: "promptId" });
Post.hasMany(PostMedia, { foreignKey: "postId" });
PostMedia.belongsTo(Post, { foreignKey: "postId" });
Post.belongsToMany(User, { through: PostLike, as: "Likers", foreignKey: "postId", otherKey: "userId" });
User.belongsToMany(Post, { through: PostLike, as: "LikedPosts", foreignKey: "userId", otherKey: "postId" });
Post.hasMany(PostComment, { foreignKey: "postId" });
PostComment.belongsTo(Post, { foreignKey: "postId" });
User.hasMany(PostComment, { foreignKey: "userId" });
PostComment.belongsTo(User, { foreignKey: "userId" });
Follow.belongsTo(User, { as: "FollowerUser", foreignKey: "followerId" });
Follow.belongsTo(User, { as: "FollowedUser", foreignKey: "followedId" });
User.hasMany(ConversationParticipant, { foreignKey: "userId" });
ConversationParticipant.belongsTo(User, { foreignKey: "userId" });
Conversation.hasMany(ConversationParticipant, { foreignKey: "conversationId" });
ConversationParticipant.belongsTo(Conversation, { foreignKey: "conversationId" });
User.hasMany(Conversation, { foreignKey: "createdBy" });
Conversation.belongsTo(User, { foreignKey: "createdBy" });
Conversation.hasMany(Message, { foreignKey: "conversationId" });
Message.belongsTo(Conversation, { foreignKey: "conversationId" });
User.hasMany(Message, { foreignKey: "senderId" });
Message.belongsTo(User, { foreignKey: "senderId" });
User.hasMany(Notification, { foreignKey: "recipientId" });
Notification.belongsTo(User, { as: "Recipient", foreignKey: "recipientId" });
Notification.belongsTo(User, { as: "ActorUser", foreignKey: "actorId" });
LeaderboardWeek.hasMany(WeeklyScore, { foreignKey: "weekId" });
WeeklyScore.belongsTo(LeaderboardWeek, { foreignKey: "weekId" });
User.hasMany(WeeklyScore, { foreignKey: "userId" });
WeeklyScore.belongsTo(User, { foreignKey: "userId" });
Badge.hasMany(UserBadge, { foreignKey: "badgeId" });
UserBadge.belongsTo(Badge, { foreignKey: "badgeId" });
User.hasMany(UserBadge, { foreignKey: "userId" });
UserBadge.belongsTo(User, { foreignKey: "userId" });

module.exports = {
  sequelize,
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
};
