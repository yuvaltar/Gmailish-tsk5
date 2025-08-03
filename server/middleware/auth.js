// server/middleware/auth.js

const jwt = require("jsonwebtoken");
const User = require("../models/user");

async function authenticate(req, res, next) {
  // 1. Grab token from cookie or Authorization header
  const token =
    req.cookies?.token ||
    (req.headers.authorization?.startsWith("Bearer ") &&
      req.headers.authorization.split(" ")[1]);

  if (!token) {
    return res
      .status(401)
      .json({ error: "Missing or malformed Authorization header" });
  }

  let payload;
  try {
    payload = jwt.verify(token, process.env.JWT_SECRET);
  } catch (err) {
    return res.status(401).json({ error: "Invalid or expired token" });
  }

  // 2. Look up the user in Mongo by your UUID field
  const user = await User.findOne({ id: payload.userId }).lean();
  if (!user) {
    return res.status(401).json({ error: "Invalid token: user not found" });
  }

  // 3. Attach the user object & proceed
  req.user = user;
  next();
}

module.exports = authenticate;