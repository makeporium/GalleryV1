const fs = require("fs");
const path = require("path");
const { execSync } = require("child_process");

async function orchestrateMigration() {
  const sentinelPath = "/app/uploads/.migration_done_v2";
  
  // Only run if the sentinel file is missing
  if (fs.existsSync(sentinelPath)) {
    console.log("Migration already completed previously (Sentinel found).");
    return;
  }

  console.log("Running one-time migration to Railway Storage...");

  try {
    // 1. Ensure uploads are populated
    console.log("Step 1: Syncing images to volume...");
    require("./ensureUploads");

    // 2. Run data import (I'll require it, but I need to make sure it doesn't process.exit)
    // Actually, I'll just run it as a child process to be safe
    console.log("Step 2: Importing database records...");
    execSync("node " + path.join(__dirname, "importToProduction.js"), { stdio: "inherit" });

    // 3. Create sentinel file on the PERSISTENT volume
    fs.writeFileSync(sentinelPath, "done at " + new Date().toISOString());
    console.log("Migration orchestration finished successfully.");
  } catch (error) {
    console.error("Migration orchestration failed:", error.message);
    // We don't exit here, let the server try to start anyway
  }
}

module.exports = orchestrateMigration;
