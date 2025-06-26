import React, { useState } from "react";
import { useParams, useOutletContext } from "react-router-dom";
import EmailList from "../components/EmailList";
import MailView from "../components/MailView";

export default function EmailPage() {
  const { labelName } = useParams();           
  const { searchQuery } = useOutletContext();  
  const folder = labelName || "inbox";
  const [selectedEmail, setSelectedEmail] = useState(null);

  if (selectedEmail) {
    return <MailView emailId={selectedEmail} onBack={() => setSelectedEmail(null)} />;
  }

  return (
    <EmailList
      labelFilter={folder}
      searchQuery={searchQuery}
      propEmails={null}
      setSelectedEmail={setSelectedEmail}
    />
  );
}
