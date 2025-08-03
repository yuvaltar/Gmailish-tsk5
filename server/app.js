//app.js
require("dotenv").config();

const mongoose = require("mongoose");

const cookieParser = require("cookie-parser");
const express = require("express");
const path = require("path");
const cors = require("cors");

// Connect to MongoDB
mongoose.connect(process.env.MONGODB_URI)
.then(() => {
  console.log(" MongoDB connected successfully");
})
.catch((err) => {
  console.error(" MongoDB connection error:", err.message);
});

const app = express();
const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || '127.0.0.1';

// CORS configuration
app.use(cors({
  origin: "http://localhost:3001",  // React frontend
  credentials: true
}));

// Middleware
app.use(cookieParser());
app.use(express.json());

// Static uploads directory
app.use("/uploads", express.static(path.join(__dirname, "uploads")));

// API routes
app.use("/api/blacklist", require("./routes/blacklist"));
app.use("/api/users",     require("./routes/users"));
app.use("/api/mails",     require("./routes/mails"));
app.use("/api/labels",    require("./routes/labels"));
app.use("/api/tokens",    require("./routes/tokens"));

// Health check endpoint
app.get("/ping", (req, res) => {
  res.json({ msg: "pong" });
});

// Serve React static assets ONLY in production
if (process.env.NODE_ENV === "production") {
  const buildPath = path.join(__dirname, "../react/build");
  
  // Serve static files from React build folder
  app.use(express.static(buildPath));
  
  // React Router fallback: serve index.html for any non-API route
  // FIXED: Using simple wildcard instead of regex to avoid path-to-regexp error
  app.get('*', (req, res) => {
    // Skip API routes - they should have been handled above
    if (req.path.startsWith('/api/')) {
      return res.status(404).json({ error: 'API route not found' });
    }
    
    // Skip static file routes
    if (req.path.startsWith('/uploads/') || req.path === '/ping') {
      return res.status(404).json({ error: 'Route not found' });
    }
    
    // Serve React app for all other routes
    res.sendFile(path.join(buildPath, "index.html"));
  });
}

// Error handling middleware
app.use((err, req, res, next) => {
  console.error('Error:', err);
  res.status(500).json({ 
    error: 'Internal Server Error',
    message: process.env.NODE_ENV === 'development' ? err.message : 'Something went wrong'
  });
});

// Start server
app.listen(PORT, HOST, () => {
  console.log(`Server running at http://${HOST}:${PORT}`);
  console.log(`Environment: ${process.env.NODE_ENV || 'development'}`);
});