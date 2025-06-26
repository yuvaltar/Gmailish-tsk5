// src/App.js
import { BrowserRouter as Router, Routes, Route } from "react-router-dom";

import Login          from "./pages/Login";
import Register       from "./pages/Register";
import Compose        from "./pages/Compose";
import EmailPage      from "./pages/EmailPage";
import ProtectedRoute from "./utils/ProtectedRoute";
import Layout         from "./components/Layout";

function App() {
  return (
    <Router>
      <Routes>

        {/* Public */}
        <Route path="/"         element={<Login />} />
        <Route path="/login"    element={<Login />} />
        <Route path="/register" element={<Register />} />

        {/* All mail views share the same Layout */}
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          }
        >
          {/* default = / → inbox */}
          <Route index            element={<EmailPage />} />

          {/* /spam, /starred, /MyCustomLabel, etc. */}
          <Route path=":labelName" element={<EmailPage />} />

          {/* Compose & Search */}
          <Route path="send"      element={<Compose />} />
        </Route>
      </Routes>
    </Router>
  );
}

export default App;
