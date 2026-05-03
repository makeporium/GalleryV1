const express = require("express");
const { z } = require("zod");
const { getFirebaseAuth } = require("../config/firebase");
const { upsertFirebaseUser } = require("../services/userService");
const { signAccessToken } = require("../services/jwtService");

const router = express.Router();

const authFirebaseSchema = z.object({
  idToken: z.string().min(20),
});

async function handleFirebaseTokenExchange(req, res) {
  try {
    const { idToken } = authFirebaseSchema.parse(req.body);
    const decoded = await getFirebaseAuth().verifyIdToken(idToken, true);
    const user = await upsertFirebaseUser(decoded);
    const accessToken = signAccessToken({
      sub: user.id,
      firebaseUid: user.firebaseUid,
      email: user.email,
    });

    return res.status(200).json({
      accessToken,
      user: {
        id: user.id,
        firebaseUid: user.firebaseUid,
        email: user.email,
        name: user.name,
        avatarUrl: user.avatarUrl,
        bio: user.bio,
        pronouns: user.pronouns,
      },
    });
  } catch (error) {
    if (error.name === "ZodError") {
      return res.status(400).json({ message: "Invalid request body." });
    }
    console.error("Firebase Auth Error:", error);
    return res.status(401).json({ message: "Failed to authenticate Firebase user." });
  }
}

router.post("/firebase", handleFirebaseTokenExchange);
router.post("/google", handleFirebaseTokenExchange);

module.exports = router;
