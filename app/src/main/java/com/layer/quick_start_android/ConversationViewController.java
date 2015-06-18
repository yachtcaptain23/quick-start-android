package com.layer.quick_start_android;

import android.app.Activity;
import android.graphics.Color;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.layer.sdk.LayerClient;
import com.layer.sdk.changes.LayerChange;
import com.layer.sdk.changes.LayerChangeEvent;
import com.layer.sdk.exceptions.LayerException;
import com.layer.sdk.listeners.LayerChangeEventListener;
import com.layer.sdk.listeners.LayerSyncListener;
import com.layer.sdk.listeners.LayerTypingIndicatorListener;
import com.layer.sdk.messaging.Conversation;
import com.layer.sdk.messaging.LayerObject;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.messaging.MessagePart;
import com.layer.sdk.query.Predicate;
import com.layer.sdk.query.Query;
import com.layer.sdk.query.SortDescriptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Handles the conversation between the pre-defined participants (Device, Emulator) and displays
 * messages in the GUI.
 */
public class ConversationViewController implements View.OnClickListener, LayerChangeEventListener.MainThread,
        TextWatcher, LayerTypingIndicatorListener, LayerSyncListener, MessageQueryAdapter.MessageClickHandler {

    private LayerClient layerClient;
    private Activity mainActivity;

    //GUI elements
    private Button sendButton;
    private LinearLayout topBar;
    private EditText userInput;
    private ScrollView conversationScroll;
    private LinearLayout conversationView;
    private TextView typingIndicator;

    //The Query Adapter that grabs all Messages and displays them based on their Position
    private MessageQueryAdapter mMessagesAdapter;

    //List of all users currently typing
    private ArrayList<String> typingUsers;

    //Current conversation
    private Conversation activeConversation;

    //This is the view that contains all the messages
    private RecyclerView mMessagesView;


    public ConversationViewController(MainActivity ma, LayerClient client) {

        //Cache the passed in parameters
        mainActivity = ma;
        layerClient = client;

        //When conversations/messages change, capture them
        layerClient.registerEventListener(this);

        //List of users that are typing which is used with LayerTypingIndicatorListener
        typingUsers = new ArrayList<>();

        //Change the layout
        ma.setContentView(R.layout.activity_main);

        //Cache off gui objects
        sendButton = (Button) ma.findViewById(R.id.send);
        topBar = (LinearLayout)ma.findViewById(R.id.topbar);
        userInput = (EditText) ma.findViewById(R.id.input);
        conversationScroll = (ScrollView) ma.findViewById(R.id.scrollView);
        conversationView = (LinearLayout) ma.findViewById(R.id.conversation);
        typingIndicator = (TextView) ma.findViewById(R.id.typingIndicator);
        mMessagesView = (RecyclerView) ma.findViewById(R.id.mRecyclerView);

        //Capture user input
        sendButton.setOnClickListener(this);
        topBar.setOnClickListener(this);
        userInput.setText(getInitialMessage());
        userInput.addTextChangedListener(this);

        if(activeConversation != null)
            getTopBarMetaData();

        //Create the appropriate RecyclerView
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(ma, LinearLayoutManager.VERTICAL, false);
        mMessagesView.setLayoutManager(layoutManager);

        //If there is an active conversation between the Device, Simulator, and Dashboard (web client), cache it
        activeConversation = getConversation();

        if(activeConversation != null)
            createMessagesAdapter();
    }

    private void createMessagesAdapter(){

        //The Query Adapter drives the RecyclerView, and handles all the heavy lifting of checking
        // for new Messages, and updating the RecyclerView
        mMessagesAdapter = new MessageQueryAdapter(mainActivity.getApplicationContext(), layerClient,
            mMessagesView, activeConversation, this, new QueryAdapter.Callback() {

            public void onItemInserted() {
                //When a new item is inserted into the RecyclerView, scroll to the bottom so the
                // most recent Message is always displayed
                mMessagesView.smoothScrollToPosition(Integer.MAX_VALUE);
            }
        });
        mMessagesView.setAdapter(mMessagesAdapter);

        //Execute the Query
        mMessagesAdapter.refresh();

        //Start by scrolling to the bottom (newest Message)
        mMessagesView.smoothScrollToPosition(Integer.MAX_VALUE);
    }



    //Create a new message and send it
    private void sendButtonClicked(){

        //Check to see if there is an active conversation between the pre-defined participants
        if(activeConversation == null){
            activeConversation = getConversation();

            //If there isn't, create a new conversation with those participants
            if(activeConversation == null){
                activeConversation = layerClient.newConversation(MainActivity.getAllParticipants());
                createMessagesAdapter();
            }
        }

        sendMessage(userInput.getText().toString());

        //Clears the text input field
        userInput.setText("");
    }

    private void sendMessage(String text) {

        //Put the user's text into a message part, which has a MIME type of "text/plain" by default
        MessagePart messagePart = layerClient.newMessagePart(text);

        //Creates and returns a new message object with the given conversation and array of message parts
        Message message = layerClient.newMessage(Arrays.asList(messagePart));

        //Formats the push notification that the other participants will receive
        Map<String, String> metadata = new HashMap<>();
        metadata.put("layer-push-message", MainActivity.getUserID() + ": " + text);
        message.setMetadata(metadata);

        //Sends the message
        if(activeConversation != null)
            activeConversation.send(message);
    }

    //Checks to see if there is already a conversation between the device and emulator
    private Conversation getConversation(){

        if(activeConversation == null){

            Query query = Query.builder(Conversation.class)
                    .predicate(new Predicate(Conversation.Property.PARTICIPANTS, Predicate.Operator.EQUAL_TO, MainActivity.getAllParticipants()))
                    .sortDescriptor(new SortDescriptor(Conversation.Property.CREATED_AT, SortDescriptor.Order.DESCENDING)).build();

            List<Conversation> results = layerClient.executeQuery(query, Query.ResultType.OBJECTS);
            if(results != null && results.size() > 0) {
                return results.get(0);
            }
        }

        //Returns the active conversation (which is null by default)
        return activeConversation;
    }

    public static String getInitialMessage(){
        return "Hey, everyone! This is your friend, " + MainActivity.getUserID();
    }

    //================================================================================
    // MessageQueryAdapter.MessageClickHandler methods
    //================================================================================

    //You can choose to present additional options when a Message is tapped
    public void onMessageClick(Message message) {

    }

    //You can choose to present additional options when a Message is long tapped
    public boolean onMessageLongClick(Message message) {
        return false;
    }

    //================================================================================
    // Metadata methods
    //================================================================================

    //Create a random color and apply it to the Layer logo bar
    private void topBarClicked(){

        Random r = new Random();
        float red = r.nextFloat();
        float green = r.nextFloat();
        float blue = r.nextFloat();

        setTopBarMetaData(red, green, blue);
        setTopBarColor(red, green, blue);
    }

    //Stores RGB values in the conversation's metadata
    private void setTopBarMetaData(float red, float green, float blue){
        if(activeConversation != null) {
            Map<String, Object> metadata = new HashMap<String, Object>();

            Map<String, Object> colors = new HashMap<String, Object>();
            colors.put("red", Float.toString(red));
            colors.put("green", Float.toString(green));
            colors.put("blue", Float.toString(blue));

            metadata.put("backgroundColor", colors);

            //Merge this new information with the existing metadata (passing in false will replace
            // the existing Map, passing in true ensures existing key/values are preserved)
            activeConversation.putMetadata(metadata, true);
        }
    }

    //Check the conversation's metadata for RGB values
    private void getTopBarMetaData(){
        if(activeConversation != null) {

            Map<String, Object> current = activeConversation.getMetadata();
            if(current.containsKey("backgroundColor")) {

                Map<String, Object> colors = (Map<String, Object>)current.get("backgroundColor");

                if(colors != null) {

                    float red = Float.parseFloat((String)colors.get("red"));
                    float green = Float.parseFloat((String)colors.get("green"));
                    float blue = Float.parseFloat((String)colors.get("blue"));

                    setTopBarColor(red, green, blue);
                }
            }
        }
    }

    //Takes RGB values and sets the top bar color
    private void setTopBarColor(float red, float green, float blue){
        if(topBar != null) {
            topBar.setBackgroundColor(Color.argb(255, (int)(255.0f * red), (int)(255.0f * green), (int)(255.0f * blue)));
        }
    }


    //================================================================================
    // View.OnClickListener methods
    //================================================================================

    public void onClick(View v) {
        //When the "send" button is clicked, grab the ongoing conversation (or create it) and send the message
        if(v == sendButton){
            sendButtonClicked();
        }

        //When the Layer logo bar is clicked, randomly change the color and store it in the conversation's metadata
        if(v == topBar){
            topBarClicked();
        }
    }

    //================================================================================
    // LayerChangeEventListener methods
    //================================================================================

    public void onEventMainThread(LayerChangeEvent event) {

        //You can choose to handle changes to conversations or messages however you'd like:
        List<LayerChange> changes = event.getChanges();
        for(int i = 0; i < changes.size(); i++){
            LayerChange change = changes.get(i);
            if(change.getObjectType() == LayerObject.Type.CONVERSATION){

                Conversation conversation = (Conversation)change.getObject();
                System.out.println("Conversation " + conversation.getId() + " attribute " + change.getAttributeName() + " was changed from " + change.getOldValue() + " to " + change.getNewValue());

                switch (change.getChangeType()){
                    case INSERT:
                        break;

                    case UPDATE:
                        break;

                    case DELETE:
                        break;
                }

            } else if(change.getObjectType() == LayerObject.Type.MESSAGE) {

                Message message = (Message)change.getObject();
                System.out.println("Message " + message.getId() + " attribute " + change.getAttributeName() + " was changed from " + change.getOldValue() + " to " + change.getNewValue());

                switch (change.getChangeType()){
                    case INSERT:
                        break;

                    case UPDATE:
                        break;

                    case DELETE:
                        break;
                }
            }
        }

        //Check the meta-data for color changes
        getTopBarMetaData();
    }

    //================================================================================
    // TextWatcher methods
    //================================================================================

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    public void afterTextChanged(Editable s) {
        //After the user has changed some text, we notify other participants that they are typing
        if(activeConversation != null)
            activeConversation.send(TypingIndicator.STARTED);
    }

    //================================================================================
    // LayerTypingIndicatorListener methods
    //================================================================================

    @Override
    public void onTypingIndicator(LayerClient layerClient, Conversation conversation, String userID, TypingIndicator indicator) {

        //Only show the typing indicator for the active (displayed) converation
        if(conversation != activeConversation)
            return;

        switch (indicator) {
            case STARTED:
                // This user started typing, so add them to the typing list if they are not already on it.
                if(!typingUsers.contains(userID))
                    typingUsers.add(userID);
                break;

            case FINISHED:
                // This user isn't typing anymore, so remove them from the list.
                typingUsers.remove(userID);
                break;
        }

        //Format the text to display in the conversation view
        if(typingUsers.size() == 0){

            //No one is typing, so clear the text
            typingIndicator.setText("");

        } else if (typingUsers.size() == 1) {

            //Name the one user that is typing (and make sure the text is grammatically correct)
            typingIndicator.setText(typingUsers.get(0) + " is typing");

        } else if(typingUsers.size() > 1) {

            //Name all the users that are typing (and make sure the text is grammatically correct)
            String users = "";
            for(int i = 0; i < typingUsers.size(); i++){
                users += typingUsers.get(i);
                if(i < typingUsers.size() - 1)
                    users += ", ";
            }

            typingIndicator.setText(users + " are typing");
        }
    }

    //Called before syncing with the Layer servers
    public void onBeforeSync(LayerClient layerClient) {
        System.out.println("Sync starting");
    }

    //Called after syncing with the Layer servers
    public void onAfterSync(LayerClient layerClient) {
        System.out.println("Sync complete");
    }

    //Captures any errors with syncing
    public void onSyncError(LayerClient layerClient, List<LayerException> layerExceptions) {

    }
}
