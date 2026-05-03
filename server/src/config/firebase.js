const fs = require("fs");
const path = require("path");
const admin = require("firebase-admin");
const env = require("./env");

let app;

function getFirebaseApp() {
  if (app) {
    return app;
  }

  let serviceAccount;
  if (env.firebaseServiceAccountJson) {
    serviceAccount = JSON.parse(env.firebaseServiceAccountJson);
  } else if (env.firebaseServiceAccountPath) {
    const servicePath = path.resolve(process.cwd(), env.firebaseServiceAccountPath);
    if (!fs.existsSync(servicePath)) {
      throw new Error(`Firebase service account file not found at: ${servicePath}`);
    }
    serviceAccount = JSON.parse(fs.readFileSync(servicePath, "utf8"));
  } else {
    throw new Error("Must provide either FIREBASE_SERVICE_ACCOUNT_JSON or FIREBASE_SERVICE_ACCOUNT_PATH");
  }

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
