// server/models/user.js
const mongoose = require('mongoose');
const uuidv4 = require('../utils/uuid');


const userSchema = new mongoose.Schema({
  id: { type: String, default: uuidv4 },
  firstName: { type: String, required: true },
  lastName:  { type: String, required: true },
  username:  { type: String, required: true, unique: true },
  email:     { type: String, required: true, unique: true },
  gender:    { type: String, required: true },
  password:  { type: String, required: true },
  birthdate: { type: Date,   required: true },
  picture:   { type: String, required: false, default: null }, // filename from Multer
});

const User = mongoose.model('User', userSchema);
module.exports = User;
