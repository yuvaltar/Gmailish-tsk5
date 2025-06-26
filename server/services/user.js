// services/user
const User = require('../models/user');

async function createUser({ firstName, lastName, username, gender, password, birthdate, picture }) {
  const existing = await User.findOne({ username });
  if (existing) return null;

  const email = `${username}@gmailish.com`;

  const user = new User({
    firstName,
    lastName,
    username,
    email,
    gender,
    password, //  Plaintext (insecure)
    birthdate,
    picture,
  });
  console.log("Using collection:", User.collection.collectionName);
  await user.save();
  return user;
}

async function findUserByEmail(email) {
  return await User.findOne({ email });
}

async function findUserById(id) {
  return await User.findOne({ id });
}

async function validateCredentials(email, rawPassword) {
  const user = await findUserByEmail(email);
  if (!user) return null;

  // Insecure direct string comparison
  return user.password === rawPassword ? user : null;
}

module.exports = {
  createUser,
  findUserByEmail,
  findUserById,
  validateCredentials,
};
