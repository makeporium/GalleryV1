const fs = require("fs");
const path = require("path");
const admin = require("firebase-admin");
const env = require("./env");

let app;

function getFirebaseApp() {
  if (app) {
    return app;
  }

  const servicePath = path.resolve(process.cwd(), env.firebaseServiceAccountPath);
  if (!fs.existsSync(servicePath)) {
    throw new Error(`Firebase service account file not found at: ${servicePath}`);
  }

  const serviceAccount = JSON.parse(fs.readFileSync(servicePath, "utf8"));
  app = admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    projectId: env.firebaseProjectId,
  });

  return app;
}

module.exports = {
  getFirebaseAuth() {
    return getFirebaseApp().auth();
  },
};
