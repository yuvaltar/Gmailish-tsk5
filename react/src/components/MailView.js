import React, { useEffect, useState } from "react";
import { Card, Spinner, Alert } from "react-bootstrap";
import {
  BsArrowLeft,
  BsArchive,
  BsExclamationCircle,
  BsTrash,
  BsStar,
  BsStarFill,
  BsTag
} from "react-icons/bs";
import { useNavigate } from "react-router-dom";
import Compose from "../pages/Compose";


function MailView({ emailId, onBack }) {
  const [mailData, setMailData] = useState(null);
  const [error, setError] = useState(null);
  const [showLabels, setShowLabels] = useState(false);
  const [labels, setLabels] = useState([]);
  const navigate = useNavigate();

  useEffect(() => {
    setMailData(null);
    setError(null);
    if (!emailId) return;

    (async () => {
      try {
        const res = await fetch(`http://localhost:3000/api/mails/${emailId}`, {
          credentials: "include"
        });
        if (!res.ok) {
          const { error } = await res.json();
          throw new Error(error || "Mail not found");
        }
        setMailData(await res.json());
          await fetch(`http://localhost:3000/api/mails/${emailId}/read`, {
          method: "PATCH",
          credentials: "include"
        });
      } catch (err) {
        setError(err.message);
      }
    })();
  }, [emailId]);

  useEffect(() => {
    fetch("http://localhost:3000/api/labels", { credentials: "include" })
      .then((res) => res.json())
      .then(setLabels)
      .catch(() => setLabels([]));
  }, []);

  const updateLabel = async (label) => {
    try {
      const method = mailData.labels.includes(label) ? "DELETE" : "PATCH";
      const url = method === "PATCH"
        ? `http://localhost:3000/api/mails/${emailId}/label`
        : `http://localhost:3000/api/mails/${emailId}/label/${label}`;

      const res = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: method === "PATCH" ? JSON.stringify({ label }) : null
      });

      if (!res.ok) throw new Error("Label toggle failed");
      const updated = await res.json();
      setMailData(updated.mail);
    } catch (err) {
      alert("Failed to toggle label: " + err.message);
    }
  };

  const handleArchive = async () => {
  try {
    const isArchived = mailData.labels.includes("archive");

    if (isArchived) {
      await fetch(`http://localhost:3000/api/mails/${emailId}/label/archive`, {
        method: "DELETE",
        credentials: "include"
      });

      await fetch(`http://localhost:3000/api/mails/${emailId}/label`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ label: "inbox" })
      });

    } else {
      await fetch(`http://localhost:3000/api/mails/${emailId}/label`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ label: "archive" })
      });

      await fetch(`http://localhost:3000/api/mails/${emailId}/label/inbox`, {
        method: "DELETE",
        credentials: "include"
      });
    }
    
    const res = await fetch(`http://localhost:3000/api/mails/${emailId}`, {
      credentials: "include"
    });

    if (!res.ok) throw new Error("Failed to reload mail");
    const updated = await res.json();
    setMailData(updated);

  } catch (err) {
    alert("Failed to toggle archive: " + err.message);
  }
};

  const handleSpam = async () => {
    try {
      const res = await fetch(`http://localhost:3000/api/mails/${emailId}/spam`, {
        method: "POST",
        credentials: "include"
      });
      if (!res.ok) throw new Error("Spam toggle failed");

      const updated = await res.json();
      setMailData(updated.mail);
    } catch (err) {
      alert("Failed to toggle spam: " + err.message);
    }
  };

  const handleDelete = async () => {
    try {
      const isTrashed = mailData.labels.includes("trash");

      if (isTrashed) {
        await fetch(`http://localhost:3000/api/mails/${emailId}/label/trash`, {
          method: "DELETE",
          credentials: "include",
        });

        await fetch(`http://localhost:3000/api/mails/${emailId}/label`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({ label: "inbox" }),
        });

      } else {
        await fetch(`http://localhost:3000/api/mails/${emailId}/label`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({ label: "trash" }),
        });

        await fetch(`http://localhost:3000/api/mails/${emailId}/label/inbox`, {
          method: "DELETE",
          credentials: "include",
        });
      }

      const res = await fetch(`http://localhost:3000/api/mails/${emailId}`, {
        credentials: "include",
      });
      if (!res.ok) throw new Error("Failed to reload mail");
      const updated = await res.json();
      setMailData(updated);
    } catch (err) {
      alert("Failed to toggle trash: " + err.message);
    }
  };

  const handleToggleStar = async () => {
    try {
      const res = await fetch(`http://localhost:3000/api/mails/${emailId}/star`, {
        method: "PATCH",
        credentials: "include"
      });
      if (!res.ok) throw new Error("Star toggle failed");

      const { starred } = await res.json();
      setMailData((prev) => ({ ...prev, starred }));
    } catch (err) {
      alert("Failed to toggle star: " + err.message);
    }
  };

  const handleLabel = () => setShowLabels((s) => !s);

  const handleSelectLabel = async (label) => {
    await updateLabel(label);
    setShowLabels(false);
    navigate(`/${encodeURIComponent(label)}`);
  };

  if (error) {
    return <Alert variant="danger" className="m-3">Error: {error}</Alert>;
  }

  if (!mailData) {
    return <div className="d-flex justify-content-center align-items-center h-100">
      <Spinner animation="border" />
    </div>;
  }

  if (mailData.labels.includes("drafts")) {
    return (
      <Compose
        draft={{
          id: mailData.id,
          to: mailData.recipientEmail || mailData.recipientId,
          subject: mailData.subject,
          content: mailData.content
        }}
        onClose={onBack}
      />
    );
  }

  return (
    <div className="p-3 mail-view-container" style={{ position: "relative" }}>
      <div className="mail-toolbar d-flex align-items-center gap-2 mb-3">
        <button className="gmail-icon-btn" onClick={onBack} title="Back to Inbox">
          <BsArrowLeft size={18} />
        </button>
        <button className="gmail-icon-btn" onClick={handleArchive} title="Archive">
          <BsArchive size={18} className={mailData.labels.includes("archive") ? "text-primary" : ""} />
        </button>
        <button className="gmail-icon-btn" onClick={handleLabel} title="Label">
          <BsTag size={18} className={showLabels ? "text-primary" : ""} />
        </button>
        <button className="gmail-icon-btn" onClick={handleToggleStar} title="Star">
          {mailData.starred ? (
            <BsStarFill className="text-warning" size={18} />
          ) : (
            <BsStar size={18} />
          )}
        </button>
        <button className="gmail-icon-btn" onClick={handleSpam} title="Report spam">
          <BsExclamationCircle size={18} className={mailData.labels.includes("spam") ? "text-danger" : ""} />
        </button>
        <button className="gmail-icon-btn" onClick={handleDelete} title="Move to trash / Untrash">
          <BsTrash size={18} className={mailData.labels.includes("trash") ? "text-danger" : ""} />
        </button>

      </div>

      {showLabels && (
        <div
          style={{
            position: "absolute",
            top: 50,
            left: 120,
            background: "#fff",
            border: "1px solid #ddd",
            borderRadius: 4,
            zIndex: 10,
            boxShadow: "0 2px 8px rgba(0,0,0,0.08)"
          }}>
          {labels.length === 0 ? (
            <div style={{ padding: "1rem" }}>No labels</div>
          ) : (
            labels.map((l) => (
              <div
                key={l.name}
                className="label-picker-item"
                onClick={() => handleSelectLabel(l.name)}
                style={{
                  cursor: "pointer",
                  padding: "0.5rem 1.5rem",
                  borderBottom: "1px solid #eee",
                  background: mailData.labels.includes(l.name) ? "#e0f7fa" : "#fff"
                }}>
                {l.name}
              </div>
            ))
          )}
        </div>
      )}

      <Card>
        <Card.Header className="d-flex justify-content-between align-items-start">
          <div>
            <strong>From:</strong> {mailData.senderName || mailData.senderId}<br />
            <strong>To:</strong> {mailData.recipientName || mailData.recipientId}<br />
            <strong>Subject:</strong> {mailData.subject}
          </div>
          <div onClick={handleToggleStar} style={{ cursor: "pointer" }} title="Toggle star">
            {mailData.starred ? (
              <BsStarFill className="text-warning" size={20} />
            ) : (
              <BsStar size={20} />
            )}
          </div>
        </Card.Header>
        <Card.Body>
          <p>{mailData.content}</p>
        </Card.Body>
      </Card>
    </div>
  );
}

export default MailView;
