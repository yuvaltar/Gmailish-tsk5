// server/controllers/tokenController.js

const jwt                     = require("jsonwebtoken");
// Use the user service instead of direct model access
const { validateCredentials } = require("../services/user");

exports.login = async (req, res) => {
  const { email, password } = req.body;

  // 1. Basic validation
  if (!email || !password) {
    return res.status(400).json({ error: "Email and password required" });
  }

  try {
    // 2+3. Validate credentials via service (returns user or null)
    const user = await validateCredentials(email, password);
    if (!user) {
      return res.status(401).json({ error: "Invalid credentials" });
    }

    // 4. Sign JWT with your UUID field
    const token = jwt.sign(
      { userId: user.id },
      process.env.JWT_SECRET,
      { expiresIn: "2h" }
    );

    // 5. Set cookie + respond
    return res
      .cookie("token", token, {
        httpOnly: true,
        sameSite: "Lax",
        secure: process.env.NODE_ENV === "production",
        maxAge: 2 * 60 * 60 * 1000, // 2 hours
      })
      .json({ message: "Login successful" });

  } catch (err) {
    console.error("login error:", err);
    return res.status(500).json({ error: "Internal server error" });
  }
};

exports.logout = (req, res) => {
  res.clearCookie("token", {
    httpOnly: true,
    sameSite: "Lax",
    secure: process.env.NODE_ENV === "production",
  });
  return res.status(200).json({ message: "Logged out" });
};

exports.getCurrentUser = (req, res) => {
  // By the time we get here, auth middleware has attached req.user
  if (!req.user) {
    return res.status(401).json({ error: "Not authenticated" });
  }

  const {
    id,
    firstName,
    lastName,
    username,
    gender,
    birthdate,
    picture,
  } = req.user;

  return res.json({
    id,
    firstName,
    lastName,
    username,
    gender,
    birthdate,
    picture: picture || null,
  });
};