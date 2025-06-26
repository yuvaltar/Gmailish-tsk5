const jwt = require("jsonwebtoken");
const { findUserByCredentials } = require("../models/user");

exports.login = (req, res) => {
  const { email, password } = req.body;
  if (!email || !password) {
    return res.status(400).json({ error: "Email and password required" });
  }

  const user = findUserByCredentials(email, password);
  if (!user) {
    return res.status(401).json({ error: "Invalid credentials" });
  }

  const token = jwt.sign({ userId: user.id }, process.env.JWT_SECRET, {
    expiresIn: "2h",
  });

  res
    .cookie("token", token, {
      httpOnly: true,
      sameSite: "Lax",
      secure: false,
      maxAge: 2 * 60 * 60 * 1000,
    })
    .json({ message: "Login successful" });
};

exports.logout = (req, res) => {
  res.clearCookie("token", {
    httpOnly: true,
    sameSite: "Lax",
    secure: false, // change to true in production
  });
  res.status(200).json({ message: "Logged out" });
};

exports.getCurrentUser = (req, res) => {
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

  res.json({
    id,
    firstName,
    lastName,
    username,
    gender,
    birthdate,
    picture: picture || null  // 🛡️ fallback if missing
  });
};

