const express = require("express");
const { requireAuth } = require("../middleware/authMiddleware");

const router = express.Router();

router.get("/me", requireAuth, (req, res) => {
  return res.status(200).json({
    user: {
      id: req.user.id,
      firebaseUid: req.user.firebaseUid,
      email: req.user.email,
      name: req.user.name,
      avatarUrl: req.user.avatarUrl,
      bio: req.user.bio,
      pronouns: req.user.pronouns,
      provider: req.user.provider,
      createdAt: req.user.createdAt,
      updatedAt: req.user.updatedAt,
    },
  });
});

module.exports = router;
