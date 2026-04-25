const { verifyAccessToken } = require("../services/jwtService");
const { User } = require("../models");

async function requireAuth(req, res, next) {
  const authHeader = req.headers.authorization || "";
  const [scheme, token] = authHeader.split(" ");
  if (scheme !== "Bearer" || !token) {
    return res.status(401).json({ message: "Missing or invalid Authorization header." });
  }

  try {
    const payload = verifyAccessToken(token);
    const user = await User.findByPk(payload.sub);
    if (!user) {
      return res.status(401).json({ message: "Invalid token user." });
    }
    req.user = user;
    return next();
  } catch (error) {
    return res.status(401).json({ message: "Invalid or expired token." });
  }
}

module.exports = {
  requireAuth,
};
