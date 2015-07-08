package com.layer.quick_start_android;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.layer.sdk.LayerClient;
import com.layer.sdk.messaging.Conversation;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.messaging.MessagePart;
import com.layer.sdk.query.Predicate;
import com.layer.sdk.query.Query;
import com.layer.sdk.query.SortDescriptor;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;

/*
 * MessageQueryAdapter.java
 * Drives the RecyclerView in the MessageActivity class. Shows a list of all messages sorted
 *  by the message Position. For each Message, it shows the contents (assuming it is plain text),
 *  the sender, and the date received (downloaded from the server).
 *
 *  This is just one possible implementation. You can edit the message_item.xml view to change
 *   what is shown for each Message, including adding icons for each individual, allowing photos or
 *   other rich media content, etc.
 */

public class MessageQueryAdapter extends QueryAdapter<Message, MessageQueryAdapter.ViewHolder> {

    //Inflates the view associated with each Message object returned by the Query
    private final LayoutInflater mInflater;

    //The parent view is required to ensure proper formatting of the messages (messages sent from
    // the authenticated user are right aligned, and messages from other users are left aligned). In
    // this case, the parent view is the RecyclerView in the MessageActivity class.
    private final ViewGroup mParentView;

    //Handle the callbacks when the Message item is actually clicked. In this case, the
    // MessageActivity class implements the MessageClickHandler
    private final MessageClickHandler mMessageClickHandler;
    public static interface MessageClickHandler {
        public void onMessageClick(Message message);

        public boolean onMessageLongClick(Message message);
    }


    //For each Message item in the RecyclerView list, we show the sender, time, and contents of the
    // message. We also grab some layout items to help with right/left aligning the message
    public static class ViewHolder
            extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnLongClickListener {

        public Message message;
        public LinearLayout contentLayout;
        public final MessageClickHandler messageClickHandler;

        //Registers the click listener callback handler
        public ViewHolder(View itemView, MessageClickHandler messageClickHandler) {
            super(itemView);
            this.messageClickHandler = messageClickHandler;
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        //Execute the callback when the message is clicked
        public void onClick(View v) {
            messageClickHandler.onMessageClick(message);
        }

        //Execute the callback when the conversation is long-clicked
        public boolean onLongClick(View v) {
            return messageClickHandler.onMessageLongClick(message);
        }
    }

    //Constructor for the MessasgeQueryAdapter
    //Sorts all messages belonging to this conversation by its position. This will guarantee all
    // messages will appear "in order"
    public MessageQueryAdapter(Context context, LayerClient client, ViewGroup recyclerView, Conversation conversation, MessageClickHandler messageClickHandler, Callback callback) {
        super(client, Query.builder(Message.class)
                .predicate(new Predicate(Message.Property.CONVERSATION, Predicate.Operator.EQUAL_TO, conversation))
                .sortDescriptor(new SortDescriptor(Message.Property.POSITION, SortDescriptor.Order.ASCENDING))
                .build(), callback);

        //Sets the LayoutInflator, Click callback handler, and the view parent
        mInflater = LayoutInflater.from(context);
        mMessageClickHandler = messageClickHandler;
        mParentView = recyclerView;
    }

    //When a Message is added to this conversation, a new ViewHolder is created
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {

        //The layout_message is just an example view you can use to display each message in a list
        View itemView = mInflater.inflate(R.layout.layout_message, mParentView, false);

        //Tie the view elements to the fields in the actual view after it has been created
        ViewHolder holder = new ViewHolder(itemView, mMessageClickHandler);
        holder.contentLayout = (LinearLayout) itemView.findViewById(R.id.msg_content);

        return holder;
    }

    //After the ViewHolder is created, we need to populate the fields with information from the Message
    public void onBindViewHolder(ViewHolder viewHolder, Message message) {
        if (message == null) {
            // If the item no longer exists, the ID probably migrated.
            refresh();
            return;
        }

        String senderId = "";
        if(message != null)
            senderId = message.getSender().getUserId();

        Context msgContext = viewHolder.contentLayout.getContext();

        //The first part of each message will include the sender and status
        LinearLayout messageDetails = new LinearLayout(msgContext);
        messageDetails.setOrientation(LinearLayout.HORIZONTAL);
        viewHolder.contentLayout.addView(messageDetails);

        //Creates the sender text view, sets the text to be italic, and attaches it to the parent
        TextView senderTV = new TextView(msgContext);
        senderTV.setText(craftSenderText(message));
        senderTV.setTypeface(null, Typeface.ITALIC);
        senderTV.setTextColor(Color.DKGRAY);
        messageDetails.addView(senderTV);

        //The status is displayed with an icon, depending on whether the message has been read, delivered, or sent
        ImageView statusImage = createStatusImage(msgContext, message);
        messageDetails.addView(statusImage);

        //Creates the message text or image view and attaches it to the parent
        View messageTV = craftMessageContent(msgContext, message);
        viewHolder.contentLayout.addView(messageTV);
    }

    private String craftSenderText(Message msg){
        //The User ID
        String senderTxt = msg.getSender().getUserId();

        //Add the timestamp
        if(msg.getSentAt() != null) {
            senderTxt += " @ " + new SimpleDateFormat("HH:mm:ss").format(msg.getReceivedAt());
        }

        //Add some formatting before the status icon
        senderTxt += "   ";

        //Return the formatted text
        return senderTxt;
    }

    private ImageView createStatusImage(Context msgContext, Message msg){
        ImageView status = new ImageView(msgContext);

        switch(getMessageStatus(msg)){

            case SENT:
                status.setImageResource(R.drawable.sent);
                break;

            case DELIVERED:
                status.setImageResource(R.drawable.delivered);
                break;

            case READ:
                status.setImageResource(R.drawable.read);
                break;
        }

        //Have the icon fill the space vertically
        status.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));

        return status;
    }

    //Checks the recipient status of the message (based on all participants)
    private Message.RecipientStatus getMessageStatus(Message msg) {

        //If we didn't send the message, we already know the status - we have read it
        if (!msg.getSender().getUserId().equalsIgnoreCase(MainActivity.getUserID()))
            return Message.RecipientStatus.READ;

        //Assume the message has been sent
        Message.RecipientStatus status = Message.RecipientStatus.SENT;

        //Go through each user to check the status, in this case we check each user and prioritize so
        // that we return the highest status: Sent -> Delivered -> Read
        for (int i = 0; i < MainActivity.getAllParticipants().size(); i++) {

            //Don't check the status of the current user
            String participant = MainActivity.getAllParticipants().get(i);
            if (participant.equalsIgnoreCase(MainActivity.getUserID()))
                continue;

            if (status == Message.RecipientStatus.SENT) {

                if (msg.getRecipientStatus(participant) == Message.RecipientStatus.DELIVERED)
                    status = Message.RecipientStatus.DELIVERED;

                if (msg.getRecipientStatus(participant) == Message.RecipientStatus.READ)
                    return Message.RecipientStatus.READ;

            } else if (status == Message.RecipientStatus.DELIVERED) {
                if (msg.getRecipientStatus(participant) == Message.RecipientStatus.READ)
                    return Message.RecipientStatus.READ;
            }
        }

        return status;
    }

    private View craftMessageContent(Context msgContext, Message msg){

        LinearLayout messageContent = new LinearLayout(msgContext);
        messageContent.setOrientation(LinearLayout.VERTICAL);

        for(MessagePart part : msg.getMessageParts()){
            switch(part.getMimeType()) {
                case "text/plain":
                    String content = getPlainText(part.getData());
                    TextView messageTV = new TextView(msgContext);
                    messageTV.setText(content);
                    messageTV.setTextColor(Color.BLACK);
                    messageContent.addView(messageTV);
                    break;
            }

        }

        return messageContent;
    }

    private String getPlainText(byte[] data){

        try {
            return new String(data, "UTF-8") + "\n";
        } catch (UnsupportedEncodingException e) {

        }

        return "";
    }

    //This example app only has one kind of Message type, but you could support different types
    // (such as images, location, audio, etc) if you wanted
    public int getItemViewType(int i) {
        return 1;
    }
}
