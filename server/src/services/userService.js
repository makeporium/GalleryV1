const { User } = require("../models");

function mapProvider(decodedToken) {
  const signInProvider = decodedToken.firebase?.sign_in_provider;
  if (signInProvider === "google.com") {
    return "google";
  }
  if (signInProvider === "password") {
    return "password";
  }
  return signInProvider || "firebase";
}

async function upsertFirebaseUser(decodedToken) {
  const firebaseUid = decodedToken.uid;
  const email = decodedToken.email || "";
  const name = decodedToken.name || null;
  const avatarUrl = decodedToken.picture || null;
  const provider = mapProvider(decodedToken);

  const existingByUid = await User.findOne({ where: { firebaseUid } });
  if (existingByUid) {
    existingByUid.email = email;
    if (!existingByUid.name && name) existingByUid.name = name;
    if (!existingByUid.avatarUrl && avatarUrl) existingByUid.avatarUrl = avatarUrl;
    existingByUid.provider = provider;
    await existingByUid.save();
    return existingByUid;
  }

  const existingByEmail = await User.findOne({ where: { email } });
  if (existingByEmail) {
    existingByEmail.firebaseUid = firebaseUid;
    if (!existingByEmail.name && name) existingByEmail.name = name;
    if (!existingByEmail.avatarUrl && avatarUrl) existingByEmail.avatarUrl = avatarUrl;
    existingByEmail.provider = provider;
    await existingByEmail.save();
    return existingByEmail;
  }

  return User.create({
    firebaseUid,
    email,
    name,
    avatarUrl,
    provider,
  });
}

module.exports = {
  upsertFirebaseUser,
};
