const { createUser, getUserById } = require('../models/user');
const path = require('path');
const fs = require("fs");


function isValidDate(dateStr) {
  // Check format YYYY-MM-DD using regex
  const regex = /^\d{4}-\d{2}-\d{2}$/;
  if (!regex.test(dateStr)) return false;

  // Try creating a Date object
  const date = new Date(dateStr);
  return !isNaN(date.getTime());
}

// POST /api/users
exports.registerUser = (req, res) => {
  const { firstName, lastName, username, gender, password, birthdate } = req.body;
  const picture = req.file;

  // Basic presence check
  if (!firstName || !lastName || !username || !gender || !password || !birthdate || !picture) {
    return res.status(400).json({ error: 'All fields are required' });
  }

  // Birthdate format check
  if (!isValidDate(birthdate)) {
    return res.status(400).json({ error: 'Birthdate must be in YYYY-MM-DD format' });
  }

  // Password validation
  const passwordErrors = [];
  if (password.length < 8) {
    passwordErrors.push("must be at least 8 characters long");
  }
  if (!/[a-z]/.test(password)) {
    passwordErrors.push("must include at least one lowercase letter");
  }
  if (!/[A-Z]/.test(password)) {
    passwordErrors.push("must include at least one uppercase letter");
  }
  if (!/\d/.test(password)) {
    passwordErrors.push("must include at least one digit");
  }
  if (!/[!@#$%^&*()_\-+=[\]{};':"\\|,.<>/?]/.test(password)) {
    passwordErrors.push("must include at least one special character");
  }

  if (passwordErrors.length > 0) {
    return res.status(400).json({ error: "Password " + passwordErrors.join(", ") + "." });
  }

  const newUser = createUser({
    firstName,
    lastName,
    username,
    gender,
    password,
    birthdate,
    picture: picture.filename
  });

  if (!newUser) {
    return res.status(409).json({ error: 'Username already exists' });
  }

  console.log('Created user:', newUser);
  res.status(201).json(newUser);
};


// GET /api/users/:id
exports.getUser = (req, res) => {
  const user = getUserById(req.params.id);
  if (!user) {
    return res.status(404).json({ error: 'User not found' });
  }


  const { id, firstName, lastName, username, gender, birthdate, picture } = user;
  res.status(200).json({ id, firstName, lastName, username, gender, birthdate, picture });

};

exports.getUserIdByEmail = (req, res) => {
  const { users } = require('../models/user');
  const user = users.find(u => u.email === req.params.email);
  if (!user) return res.status(404).json({ error: 'User not found' });
  res.json({ id: user.id });
};

exports.getUserPicture = (req, res) => {
  const user = getUserById(req.params.id);
  if (!user || !user.picture) {
    return res.status(404).json({ error: "User or picture not found" });
  }

  const picturePath = path.join(__dirname, "../uploads", user.picture);

  // Check if file exists before sending
  fs.access(picturePath, fs.constants.F_OK, (err) => {
    if (err) {
      return res.status(404).json({ error: "Picture file not found" });
    }
    res.sendFile(picturePath);
  });
}

