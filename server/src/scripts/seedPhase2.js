const {
  sequelize,
  DailyPrompt,
  Badge,
  LeaderboardWeek,
} = require("../models");

async function seed() {
  try {
    await sequelize.authenticate();

    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(today.getDate() - 1);

    await DailyPrompt.findOrCreate({
      where: { challengeDate: today.toISOString().slice(0, 10) },
      defaults: {
        title: "Golden Hour",
        description: "Capture a natural light portrait.",
        isActive: true,
      },
    });

    await DailyPrompt.findOrCreate({
      where: { challengeDate: yesterday.toISOString().slice(0, 10) },
      defaults: {
        title: "Street Texture",
        description: "Find an abstract texture in your city.",
        isActive: false,
      },
    });

    const weekStart = new Date(today);
    weekStart.setDate(today.getDate() - today.getDay());
    const weekEnd = new Date(weekStart);
    weekEnd.setDate(weekStart.getDate() + 6);

    await LeaderboardWeek.findOrCreate({
      where: { weekStart: weekStart.toISOString().slice(0, 10) },
      defaults: {
        weekEnd: weekEnd.toISOString().slice(0, 10),
        status: "active",
      },
    });

    const badges = [
      { slug: "top-50", badgeName: "Top 50", description: "Finished in weekly top 50." },
      { slug: "early-bird", badgeName: "Early Bird", description: "Posted before 8 AM three days in a row." },
      { slug: "originality-streak", badgeName: "Originality Streak", description: "7-day verified originality streak." },
    ];

    for (const badge of badges) {
      await Badge.findOrCreate({ where: { slug: badge.slug }, defaults: badge });
    }

    console.log("Phase 2 seed complete.");
  } catch (error) {
    console.error("Phase 2 seed failed:", error.message);
    process.exitCode = 1;
  } finally {
    await sequelize.close();
  }
}

seed();
