import React, { useEffect, useState } from "react";
import "./Sidebar.css";
import Label from "./Label";
import { Button, ListGroup, OverlayTrigger, Tooltip } from "react-bootstrap";
import { useNavigate } from "react-router-dom";
import {
  BsInbox,
  BsStar,
  BsSend,
  BsFileEarmarkText,
  BsPencilSquare,
  BsExclamationCircle,
  BsArchive,
  BsTrash,
  BsPlus
} from "react-icons/bs";

function Sidebar({ onComposeClick, collapsed }) {
  const navigate = useNavigate();
  const [showLabelModal, setShowLabelModal] = useState(false);
  const [customLabels, setCustomLabels] = useState([]);

  useEffect(() => {
    const fetchLabels = async () => {
      try {
        const res = await fetch("/api/labels", {
          credentials: "include"
        });
        const data = await res.json();
        setCustomLabels(data.map((label) => label.name));
      } catch (err) {
        console.error("Failed to load labels:", err);
      }
    };
    fetchLabels();
  }, []);

  const addLabel = (newLabel) => {
    setCustomLabels((prev) => (prev.includes(newLabel) ? prev : [...prev, newLabel]));
  };

  const Item = (to, Icon, text) => (
    <OverlayTrigger
      key={text}
      placement="right"
      overlay={collapsed ? <Tooltip>{text}</Tooltip> : <></>}
    >
      <ListGroup.Item
        action
        className="sidebar-item d-flex align-items-center"
        onClick={() => navigate(to)}
      >
        <Icon className="sidebar-icon" />
        {!collapsed && <span className="ms-2">{text}</span>}
      </ListGroup.Item>
    </OverlayTrigger>
  );

  return (
    <aside
      className={`custom-sidebar d-flex flex-column h-100 p-2 ${collapsed ? "icon-only collapsed-float" : ""}`}
    >
      {/* Compose button is hidden when sidebar is collapsed */}
      {collapsed ? (
        <button
          className="compose-icon-collapsed"
          onClick={onComposeClick}
          title="Compose"
        >
          <BsPlus size={24} />
        </button>
      ) : (
        <Button
          variant="primary"
          className="mb-3 w-100"
          onClick={onComposeClick}
        >
          Compose
        </Button>
      )}

      <ListGroup variant="flush" className="flex-grow-1">
        {/* Labels  */}
        <ListGroup.Item
          className="d-flex align-items-center sidebar-labels-header"
          action={!collapsed}
          onClick={() => !collapsed}
        >
          <BsFileEarmarkText className="sidebar-icon" />
          {!collapsed && <span className="fw-bold ms-2">Labels</span>}
          <Button
            variant="link"
            size="sm"
            className="ms-auto sidebar-labels-add d-flex align-items-center"
            onClick={e => {
              e.stopPropagation();
              setShowLabelModal(true);
            }}
          >
            <BsPlus size={collapsed ? 24 : 24} />
          </Button>
        </ListGroup.Item>

        {/* Built-in folders */}
        {Item("/inbox", BsInbox, "Inbox")}
        {Item("/sent", BsSend, "Sent")}
        {Item("/drafts", BsPencilSquare, "Drafts")}
        {Item("/archive", BsArchive, "Archive")}
        {Item("/starred", BsStar, "Starred")}
        {Item("/spam", BsExclamationCircle, "Spam")}
        {Item("/trash", BsTrash, "Trash")}

        {/* User-defined labels */}
        {customLabels.map(lbl =>
          Item(`/${encodeURIComponent(lbl)}`, BsFileEarmarkText, lbl)
        )}
      </ListGroup>

      {/* Create-label modal */}
      <Label
        show={showLabelModal}
        onClose={() => setShowLabelModal(false)}
        onCreate={addLabel}
      />
    </aside>
  );
}

export default Sidebar;
