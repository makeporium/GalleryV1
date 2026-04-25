const { DataTypes, Model } = require("sequelize");
const sequelize = require("../config/database");

class User extends Model {}

User.init(
  {
    id: {
      type: DataTypes.BIGINT.UNSIGNED,
      autoIncrement: true,
      primaryKey: true,
    },
    firebaseUid: {
      type: DataTypes.STRING(128),
      allowNull: false,
      unique: true,
      field: "firebase_uid",
    },
    email: {
      type: DataTypes.STRING(320),
      allowNull: false,
      unique: true,
    },
    name: {
      type: DataTypes.STRING(120),
      allowNull: true,
    },
    avatarUrl: {
      type: DataTypes.TEXT,
      allowNull: true,
      field: "avatar_url",
    },
    bio: {
      type: DataTypes.TEXT,
      allowNull: true,
    },
    pronouns: {
      type: DataTypes.STRING(32),
      allowNull: true,
    },
    provider: {
      type: DataTypes.STRING(32),
      allowNull: false,
      defaultValue: "google",
    },
  },
  {
    sequelize,
    modelName: "User",
    tableName: "users",
    underscored: true,
  }
);

module.exports = User;
