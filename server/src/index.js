const app = require("./app");
const env = require("./config/env");
const { sequelize } = require("./models");

async function start() {
  try {
    await sequelize.authenticate();
    await sequelize.sync();
    app.listen(env.port, () => {
      console.log(`Server listening on port ${env.port}`);
    });
  } catch (error) {
    console.error("Server startup failed:", error.message);
    process.exit(1);
  }
}

start();
