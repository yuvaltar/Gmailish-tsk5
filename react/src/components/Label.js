import React, { useState } from "react";
import { Modal, Button, Form } from "react-bootstrap";

function Label({ show, onClose, onCreate }) {
  const [labelName, setLabelName] = useState("");

  const handleCreate = async () => {
    if (!labelName.trim()) return;

    try {
      const res = await fetch("/api/labels", {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ name: labelName.trim() })
      });

      if (!res.ok) {
        if (res.status === 401) {
          alert("You must be logged in to create a label.");
        } else {
          throw new Error("Label creation failed");
        }
        return;
      }

      onCreate(labelName.trim());
      setLabelName("");
      onClose();
    } catch (err) {
      alert("Failed to create label: " + err.message);
    }
  };

  return (
    <Modal show={show} onHide={onClose} centered>
      <Modal.Header closeButton>
        <Modal.Title>New label</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <Form.Group>
          <Form.Label>Please enter a new label name:</Form.Label>
          <Form.Control
            type="text"
            value={labelName}
            onChange={(e) => setLabelName(e.target.value)}
            placeholder="Label name"
          />
        </Form.Group>
      </Modal.Body>
      <Modal.Footer className="gap-2 d-flex align-items-center">
        <Button
          onClick={onClose}
          className="rounded-pill px-4 py-2 fw-semibold d-inline-flex align-items-center justify-content-center border border-secondary text-secondary bg-white"
          style={{ minWidth: "100px" }}
        >
          Cancel
        </Button>
        <Button
          onClick={handleCreate}
          disabled={!labelName.trim()}
          className="rounded-pill px-4 py-2 fw-semibold d-inline-flex align-items-center justify-content-center text-white"
          style={{ minWidth: "100px", backgroundColor: "#0d6efd", border: "none" }}
        >
          Create
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

export default Label;
