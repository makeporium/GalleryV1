const express = require("express");
const cors = require("cors");
const path = require("path");
const env = require("./config/env");
const authRoutes = require("./routes/authRoutes");
const userRoutes = require("./routes/userRoutes");
const phase2Routes = require("./routes/phase2Routes");

const app = express();

app.use(
  cors({
    origin(origin, callback) {
      if (!origin || env.corsOrigins.includes(origin)) {
        callback(null, true);
        return;
      }
      callback(new Error("Not allowed by CORS."));
    },
  })
);
app.use(express.json({ limit: '50mb' })); // Increase limit for base64 images
app.use(express.urlencoded({ limit: '50mb', extended: true }));

// Serve uploaded images statically
app.use('/uploads', express.static(path.join(__dirname, '../uploads')));

app.get("/health", (_req, res) => {
  res.status(200).json({ ok: true });
});

app.use("/auth", authRoutes);
app.use("/", userRoutes);
app.use("/", phase2Routes);

app.use((err, _req, res, _next) => {
  console.error("Unhandled error:", err);
  console.error("Error stack:", err.stack);
  if (err instanceof SyntaxError && err.status === 400 && "body" in err) {
    return res.status(400).json({ message: "Invalid JSON body." });
  }
  res.status(500).json({ message: err.message || "Internal server error.", error: err.message });
});

module.exports = app;
