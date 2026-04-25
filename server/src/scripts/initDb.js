const { sequelize } = require("../models");
const fs = require("fs/promises");
const path = require("path");

async function init() {
  try {
    await sequelize.authenticate();
    const sqlDir = path.resolve(__dirname, "../../sql");
    const files = (await fs.readdir(sqlDir))
      .filter((name) => name.endsWith(".sql"))
      .sort();
    for (const file of files) {
      const sql = await fs.readFile(path.join(sqlDir, file), "utf8");
      await sequelize.query(sql);
      console.log(`Applied ${file}`);
    }
    console.log("Database initialized successfully.");
  } catch (error) {
    console.error("Database initialization failed:", error.message);
    process.exitCode = 1;
  } finally {
    await sequelize.close();
  }
}

init();
