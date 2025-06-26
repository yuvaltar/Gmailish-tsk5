// server/controllers/usersController.js
const {
  createUser,
  findUserById,
  findUserByEmail,
} = require('../services/user');

const path = require('path');
const fs = require('fs');

// Utility to validate birthdate
function isValidDate(dateStr) {
  const regex = /^\d{4}-\d{2}-\d{2}$/;
  if (!regex.test(dateStr)) return false;
  const date = new Date(dateStr);
  return !isNaN(date.getTime());
}

// POST /api/users
exports.registerUser = async (req, res) => {
  const { firstName, lastName, username, gender, password, birthdate } = req.body;
  const picture = req.file;

  if (!firstName || !lastName || !username || !gender || !password || !birthdate || !picture) {
    return res.status(400).json({ error: 'All fields are required' });
  }

  if (!isValidDate(birthdate)) {
    return res.status(400).json({ error: 'Birthdate must be in YYYY-MM-DD format' });
  }

  const passwordErrors = [];
  if (password.length < 8) passwordErrors.push("must be at least 8 characters long");
  if (!/[a-z]/.test(password)) passwordErrors.push("must include at least one lowercase letter");
  if (!/[A-Z]/.test(password)) passwordErrors.push("must include at least one uppercase letter");
  if (!/\d/.test(password)) passwordErrors.push("must include at least one digit");
  if (!/[!@#$%^&*()_\-+=[\]{};':\"\\|,.<>/?]/.test(password))
    passwordErrors.push("must include at least one special character");

  if (passwordErrors.length > 0) {
    return res.status(400).json({ error: "Password " + passwordErrors.join(", ") + "." });
  }

  try {
    const newUser = await createUser({
      firstName,
      lastName,
      username,
      gender,
      password,
      birthdate,
      picture: picture.filename,
    });

    if (!newUser) {
      return res.status(409).json({ error: 'Username already exists' });
    }

    console.log('Created user:', newUser);
    res.status(201).json({
      id: newUser.id,
      username: newUser.username,
      email: newUser.email,
    });

  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'User creation failed' });
  }
};

// GET /api/users/:id
exports.getUser = async (req, res) => {
  try {
    const user = await findUserById(req.params.id);
    if (!user) return res.status(404).json({ error: 'User not found' });

    const { id, firstName, lastName, username, gender, birthdate, picture } = user;
    res.status(200).json({ id, firstName, lastName, username, gender, birthdate, picture });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error retrieving user' });
  }
};

// GET /api/users/email/:email → get user ID by email
exports.getUserIdByEmail = async (req, res) => {
  try {
    const user = await findUserByEmail(req.params.email);
    if (!user) return res.status(404).json({ error: 'User not found' });

    res.json({ id: user.id });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Error retrieving user' });
  }
};

// GET /api/users/:id/picture
exports.getUserPicture = async (req, res) => {
  try {
    const user = await findUserById(req.params.id);
    if (!user || !user.picture) {
      return res.status(404).json({ error: "User or picture not found" });
    }

    const picturePath = path.join(__dirname, "../uploads", user.picture);
    fs.access(picturePath, fs.constants.F_OK, (err) => {
      if (err) return res.status(404).json({ error: "Picture file not found" });
      res.sendFile(picturePath);
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Error loading picture" });
  }
};
