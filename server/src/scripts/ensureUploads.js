const fs = require("fs");
const path = require("path");

const buildUploads = path.join(__dirname, "../../uploads_backup");
const volumeUploads = path.join(__dirname, "../../uploads");

function ensureUploads() {
  console.log("Ensuring persistent uploads volume is populated...");
  
  if (!fs.existsSync(volumeUploads)) {
    fs.mkdirSync(volumeUploads, { recursive: true });
  }

  if (fs.existsSync(buildUploads)) {
    const files = fs.readdirSync(buildUploads);
    files.forEach(file => {
      const src = path.join(buildUploads, file);
      const dest = path.join(volumeUploads, file);
      if (!fs.existsSync(dest)) {
        console.log(`Copying ${file} to persistent volume...`);
        fs.copyFileSync(src, dest);
      }
    });
  }
}

ensureUploads();
