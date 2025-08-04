package com.example.gmailish.ui.inbox;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gmailish.R;
import com.example.gmailish.mail.MailViewActivity;
import com.example.gmailish.model.Email;

import java.util.ArrayList;
import java.util.List;

public class EmailAdapter extends RecyclerView.Adapter<EmailAdapter.EmailViewHolder> {

    private final List<Email> emailList;

    public EmailAdapter() {
        this.emailList = new ArrayList<>();
    }

    public void updateData(List<Email> newEmails) {
        emailList.clear();
        emailList.addAll(newEmails);
        notifyDataSetChanged();
    }

    public static class EmailViewHolder extends RecyclerView.ViewHolder {
        TextView senderIcon, sender, subject, content, timestamp;
        ImageView starIcon;

        public EmailViewHolder(View view) {
            super(view);
            senderIcon = view.findViewById(R.id.senderIcon);
            sender = view.findViewById(R.id.sender);
            subject = view.findViewById(R.id.subject);
            content = view.findViewById(R.id.content);
            timestamp = view.findViewById(R.id.timestamp);
            starIcon = view.findViewById(R.id.starIcon);
        }
    }

    @NonNull
    @Override
    public EmailViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.email_item, parent, false);
        return new EmailViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull EmailViewHolder holder, int position) {
        Email email = emailList.get(position);

        holder.sender.setText(email.senderName);
        holder.subject.setText(email.subject);
        holder.content.setText(email.content);
        holder.timestamp.setText(email.timestamp);
        holder.senderIcon.setText(email.senderName.substring(0, 1).toUpperCase());
        holder.itemView.setOnClickListener(v -> {
            android.util.Log.d("DEBUG", "Opening mail with ID: " + email.id);
            Intent intent = new Intent(v.getContext(), MailViewActivity.class);
            intent.putExtra("mailId", String.valueOf(email.id)); // send the ID
            v.getContext().startActivity(intent);
        });

        holder.itemView.setAlpha(email.read ? 0.6f : 1.0f);

        holder.starIcon.setImageResource(email.starred ?
                android.R.drawable.btn_star_big_on :
                android.R.drawable.btn_star_big_off);
    }


    @Override
    public int getItemCount() {
        return emailList.size();
    }
}
