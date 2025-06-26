import React, { useState } from "react";
import Header from "../components/Header";
import Sidebar from "../components/Sidebar";
import EmailList from "../components/EmailList";
import MailView from "../components/MailView";
import Compose from "./Compose";
import 'bootstrap/dist/css/bootstrap.min.css';
import "./Inbox.css";

function Inbox() {
  const [selectedEmail, setSelectedEmail] = useState(null);
  const [showCompose, setShowCompose] = useState(false);
  const [searchResults, setSearchResults] = useState(null);

  const handleSearch = async (query) => {
    if (!query.trim()) {
      setSearchResults(null);
      return;
    }
    try {
      const res = await fetch(
        `http://localhost:3000/api/mails/search/${encodeURIComponent(query)}`,
        { credentials: "include" }
      );
      if (!res.ok) throw new Error("Search failed");
      const results = await res.json();
      setSearchResults(results);
    } catch (err) {
      alert("Search failed: " + err.message);
      setSearchResults([]);
    }
  };

  return (
    <div className="container-fluid vh-100 d-flex flex-column p-0">
      <Header onSearch={handleSearch} />
      <div className="flex-grow-1 d-flex overflow-hidden">
        <div className="sidebar-fixed border-end bg-light">
          <Sidebar onComposeClick={() => setShowCompose(true)} />
        </div>
        <div className="flex-grow-1 overflow-auto">
          {selectedEmail ? (
            <MailView emailId={selectedEmail} onBack={() => setSelectedEmail(null)} />
          ) : (
            <EmailList
              setSelectedEmail={setSelectedEmail}
              emails={searchResults}
            />
          )}
        </div>
      </div>

      {showCompose && (
        <div className="compose-box">
          <Compose onClose={() => setShowCompose(false)} />
        </div>
      )}
    </div>
  );
}

export default Inbox;
