const { Sequelize } = require("sequelize");
const env = require("./env");

const sequelize = new Sequelize(env.mysqlDatabase, env.mysqlUser, env.mysqlPassword, {
  host: env.mysqlHost,
  port: env.mysqlPort,
  dialect: "mysql",
  logging: false,
  dialectOptions: {
    multipleStatements: true,
  },
});

module.exports = sequelize;
