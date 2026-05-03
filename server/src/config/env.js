const path = require("path");
const dotenv = require("dotenv");

dotenv.config({ path: path.resolve(process.cwd(), ".env") });

function requireEnv(key) {
  const value = process.env[key];
  if (!value) {
    throw new Error(`Missing required environment variable: ${key}`);
  }
  return value;
}

module.exports = {
  nodeEnv: process.env.NODE_ENV || "development",
  port: Number(process.env.PORT || 8080),
  mysqlHost: requireEnv("MYSQL_HOST"),
  mysqlPort: Number(process.env.MYSQL_PORT || 3306),
  mysqlDatabase: requireEnv("MYSQL_DATABASE"),
  mysqlUser: requireEnv("MYSQL_USER"),
  mysqlPassword: requireEnv("MYSQL_PASSWORD"),
  jwtSecret: requireEnv("JWT_SECRET"),
  jwtExpiresIn: process.env.JWT_EXPIRES_IN || "1h",
  corsOrigins: (process.env.CORS_ORIGINS || "http://10.0.2.2:8080,http://localhost:8080")
    .split(",")
    .map((origin) => origin.trim())
    .filter(Boolean),
  firebaseProjectId: requireEnv("FIREBASE_PROJECT_ID"),
  firebaseServiceAccountPath: process.env.FIREBASE_SERVICE_ACCOUNT_PATH,
  firebaseServiceAccountJson: process.env.FIREBASE_SERVICE_ACCOUNT_JSON,
};
