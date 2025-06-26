const { Mutex } = require('async-mutex');

const cppServerMutex = new Mutex();

module.exports = cppServerMutex;