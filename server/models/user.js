const uuidv4 = require('../utils/uuid');

const users = [];


// Create a new user and add them to the in-memory array
function createUser({ firstName, lastName, username, gender, password, birthdate, picture }) {

  // Check for duplicate username
  if (users.some(user => user.username === username)) {
    return null; // Username already taken
  }

  
  const email = `${username}@gmailish.com`;


  const user = {
    id: uuidv4(),
    firstName,
    lastName,
    username,

    email,
    gender,
    password,
    birthdate,
    picture: picture  // store the filename/path from Multer

  };

  users.push(user);
  return user;
}


// Find a user by their ID

function getUserById(id) {
  return users.find(user => user.id === id);
}


// Find a user by login credentials
function findUserByCredentials(email, password) {
  return users.find(user => user.email === email && user.password === password);
}

// Export everything

module.exports = { users, createUser, getUserById, findUserByCredentials };
