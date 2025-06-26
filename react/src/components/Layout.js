import React, { useState } from "react";
import { Outlet } from "react-router-dom";
import Header from "./Header";
import Sidebar from "./Sidebar";
import Compose from "../pages/Compose";
import "./Layout.css";

export default function Layout() {
  const [showCompose, setShowCompose] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  return (
    <div className={`container-fluid vh-100 d-flex flex-column p-0${sidebarCollapsed ? ' sidebar-collapsed' : ''}`}>
      <Header
        setSearchQuery={setSearchQuery}
        sidebarCollapsed={sidebarCollapsed}
        setSidebarCollapsed={setSidebarCollapsed}
      />

      <div className="flex-grow-1 d-flex overflow-hidden position-relative">
        <Sidebar
          onComposeClick={() => setShowCompose(true)}
          collapsed={sidebarCollapsed}
          setCollapsed={setSidebarCollapsed}
        />

        <main className="main-content-area flex-grow-1 overflow-auto" style={{ position: "relative" }}>
          {sidebarCollapsed && <div className="vertical-divider"></div>}
          <Outlet context={{ searchQuery, sidebarCollapsed }} />
        </main>
      </div>

      {showCompose && <Compose onClose={() => setShowCompose(false)} />}
    </div>
  );
}
