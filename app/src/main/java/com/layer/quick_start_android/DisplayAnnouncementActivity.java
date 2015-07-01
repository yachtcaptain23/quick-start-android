package com.layer.quick_start_android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import com.layer.sdk.messaging.Announcement;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.query.Query;
import com.layer.sdk.query.SortDescriptor;

import java.util.Hashtable;
import java.util.List;


public class DisplayAnnouncementActivity extends Activity {

    private List<Announcement> announcements;
    private LinearLayout conversationView;
    private Hashtable<String, MessageView> allMessages;
    private TextView announcements_string;
    private RelativeLayout topBar;
    private float red;
    private float blue;
    private float green;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_announcement);
        conversationView = (LinearLayout) findViewById(R.id.conversation);
        topBar = (RelativeLayout) findViewById(R.id.topbar);
        announcements_string = (TextView) findViewById(R.id.announcementsText);
        announcements_string.setVisibility(View.GONE);

    }

    @Override
    public void onResume() {
        super.onResume();

        Query query = Query.builder(Announcement.class)
                .sortDescriptor(new SortDescriptor(Announcement.Property.POSITION, SortDescriptor.Order.ASCENDING))
                .build();
        announcements = ConversationViewController.layerClient.executeQuery(query, Query.ResultType.OBJECTS);
        Intent colors = getIntent();


        if(colors != null)
        {
            Bundle params = colors.getExtras();
            if(params != null)
            {
                red = params.getFloat("Red");
                blue = params.getFloat("Blue");
                green = params.getFloat("Green");
            }
        }

        topBar.setBackgroundColor(Color.argb(255, (int)(255.0f * red), (int)(255.0f * green), (int)(255.0f * blue)));

        if (announcements.size() <= 0)
        {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setMessage("You have no Announcements! Would you like to learn about them?" );
            alert.setCancelable(true);
            alert.setPositiveButton("Yes",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Intent browse = new Intent(Intent.ACTION_VIEW, Uri.parse("http://bit.ly/layer-announcements" ));
                            startActivity(browse);
                        }
                    });
            alert.setNegativeButton("No",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });

            alert.show();
            announcements_string.setVisibility(View.VISIBLE);

        }

        drawAnnouncements();

    }

    private void drawAnnouncements() {

        List<Announcement> allMsgs = announcements;
        allMessages = new Hashtable<String, MessageView>();

        for (int i = 0; i < allMsgs.size(); i++) {
            addMessageToView(allMsgs.get(i));
        }

    }

    private void addMessageToView(Announcement annou){

        //Make sure the message is valid
        if(annou == null)
            return;

        //Grab the message id

        Message message = (Message) annou;
        String msgId = message.getId().toString();
        //If we have already added this message to the GUI, skip it

        if(!announcements.contains(msgId)) {
            //Build the GUI element and save it
            MessageView msgView = new MessageView(conversationView, message);
            allMessages.put(msgId, msgView);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_display_announcement, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
