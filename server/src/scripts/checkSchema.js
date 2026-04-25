const { sequelize } = require("../models");

async function checkSchema() {
  try {
    await sequelize.authenticate();
    console.log("Connected to database");

    // Check if follows table exists
    const result = await sequelize.query(
      `SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'follows' AND TABLE_SCHEMA = DATABASE()`
    );

    console.log("Columns in follows table:");
    console.log(result[0]);

    // Try to insert a test row
    await sequelize.query(
      `INSERT INTO follows (follower_id, followed_id, created_at, updated_at) VALUES (1, 2, NOW(), NOW())`
    );
    console.log("Successfully inserted test row");

    // Delete the test row
    await sequelize.query(`DELETE FROM follows WHERE follower_id = 1 AND followed_id = 2`);
    console.log("Successfully deleted test row");
  } catch (error) {
    console.error("Error:", error.message);
  } finally {
    await sequelize.close();
  }
}

checkSchema();
